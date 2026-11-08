package net.ledok.spell_engine_expansion_ld;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.ledok.spell_engine_expansion_ld.charges.ChargeConfigs;
import net.ledok.spell_engine_expansion_ld.charges.ChargeTracker;
import net.ledok.spell_engine_expansion_ld.config.SeeldConfig;
import net.ledok.spell_engine_expansion_ld.network.ChargeConfigPayload;
import net.ledok.spell_engine_expansion_ld.network.ChargeStatePayload;
import net.ledok.spell_engine_expansion_ld.network.RecastStatePayload;
import net.ledok.spell_engine_expansion_ld.recast.RecastConfigs;
import net.ledok.spell_engine_expansion_ld.recast.RecastEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.spell_engine.internals.casting.SpellCaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Spell Engine additions for the Fallen Eden skill tree: spell charges (`data/<ns>/spell_charges/`),
/// recast chains (`data/<ns>/spell_recast/`), modifier fields Spell Engine lacks (`charges_add`,
/// `cast_duration_deduct`, `cast_duration_multiply`), and a scroll refund when a spell is unbound.
public class SpellEngineExpansionMod implements ModInitializer {
    public static final String ID = "spell_engine_expansion_ld";
    public static final Logger LOGGER = LoggerFactory.getLogger(ID);

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ID, path);
    }

    @Override
    public void onInitialize() {
        SeeldConfig.load();
        PayloadTypeRegistry.playS2C().register(ChargeConfigPayload.TYPE, ChargeConfigPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ChargeStatePayload.TYPE, ChargeStatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RecastStatePayload.TYPE, RecastStatePayload.CODEC);
        RecastEvents.register();

        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return id("spell_data");
            }

            @Override
            public void onResourceManagerReload(ResourceManager manager) {
                ChargeConfigs.load(manager);
                RecastConfigs.load(manager);
            }
        });

        // Join and /reload: the charge table, then this player's charge state (the client has a fresh
        // cooldown manager on join; Spell Engine re-pushes its cooldowns at the same moments).
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) -> {
            if (!ServerPlayNetworking.canSend(player, ChargeConfigPayload.TYPE)) {
                return; // a client without this mod: plain cooldowns for them
            }
            ServerPlayNetworking.send(player, ChargeConfigPayload.of(ChargeConfigs.serverTable()));
            ((ChargeTracker) ((SpellCaster.Player) player).getCooldownManager()).seeld$pushSync();
        });

        ServerLifecycleEvents.SERVER_STARTED.register(Diagnostics::logSummary);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, manager, success) -> Diagnostics.logSummary(server));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                Diagnostics.registerCommands(dispatcher, registryAccess));
    }
}
