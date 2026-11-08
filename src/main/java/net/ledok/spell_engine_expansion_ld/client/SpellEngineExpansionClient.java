package net.ledok.spell_engine_expansion_ld.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.spell_engine_expansion_ld.charges.ChargeConfigs;
import net.ledok.spell_engine_expansion_ld.charges.ChargeTracker;
import net.ledok.spell_engine_expansion_ld.config.HudClientConfig;
import net.ledok.spell_engine_expansion_ld.network.ChargeConfigPayload;
import net.ledok.spell_engine_expansion_ld.network.ChargeStatePayload;
import net.ledok.spell_engine_expansion_ld.network.RecastStatePayload;
import net.ledok.spell_engine_expansion_ld.recast.RecastTracker;
import net.minecraft.client.Screenshot;
import net.spell_engine.internals.casting.SpellCaster;

import java.util.Map;

public class SpellEngineExpansionClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HudClientConfig.load();
        ClientPlayNetworking.registerGlobalReceiver(ChargeConfigPayload.TYPE, (payload, context) ->
                ChargeConfigs.acceptSync(payload.table()));
        ClientPlayNetworking.registerGlobalReceiver(ChargeStatePayload.TYPE, (payload, context) -> {
            var player = context.player();
            if (player != null) {
                ((ChargeTracker) ((SpellCaster.Player) player).getCooldownManager()).seeld$acceptSync(payload.entries());
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(RecastStatePayload.TYPE, (payload, context) -> {
            var player = context.player();
            if (player != null) {
                ((RecastTracker) ((SpellCaster.Player) player).getCooldownManager()).seeld$acceptRecastSync(payload.entries());
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ChargeConfigs.acceptSync(Map.of()));
        registerScreenshotAid();
    }

    /// Dev aid: with the environment variable SEELD_SCREENSHOT_AFTER=<ticks> set, the client saves one
    /// screenshot that many ticks after it has a player, then quits. Unset (the normal case): nothing.
    private static void registerScreenshotAid() {
        var raw = System.getenv("SEELD_SCREENSHOT_AFTER");
        if (raw == null || raw.isBlank()) {
            return;
        }
        int after;
        try {
            after = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return;
        }
        var ticks = new int[1];
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) {
                return;
            }
            ticks[0]++;
            if (ticks[0] == after) {
                Screenshot.grab(client.gameDirectory, "seeld-hud.png", client.getMainRenderTarget(), message -> { });
            } else if (ticks[0] == after + 60) {
                client.stop(); // three seconds later, once the file is on disk
            }
        });
    }
}
