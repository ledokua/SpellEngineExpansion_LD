package net.ledok.spell_engine_expansion_ld.mixin.client;

import net.ledok.spell_engine_expansion_ld.charges.ChargeTracker;
import net.ledok.spell_engine_expansion_ld.charges.SpellCharges;
import net.ledok.spell_engine_expansion_ld.config.HudClientConfig;
import net.ledok.spell_engine_expansion_ld.hud.CooldownView;
import net.ledok.spell_engine_expansion_ld.recast.RecastTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.spell_engine.client.gui.HudRenderHelper;
import net.spell_engine.client.input.SpellHotbar;
import net.spell_engine.internals.casting.SpellCaster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;

/// Draws on top of Spell Engine's spell hotbar, after its own render pass:
/// - the time left while a spell is on cooldown, centred on the icon ("13" / "4.2"),
/// - a coloured frame around a ready spell's icon,
/// - the charge count of a charged spell, where an item stack shows its count,
/// - a draining line under a recast stage's icon for the time left to press again.
/// The hotbar widget is the same in every Spell Engine input mode (use key, number keys, scroll),
/// so this covers all of them.
@Mixin(HudRenderHelper.SpellHotBarWidget.class)
public abstract class SpellHotBarWidgetMixin {
    private static final int SLOT_WIDTH = 20;   // HudRenderHelper.SpellHotBarWidget.slotWidth
    private static final int ICON_OFFSET = 3;   // iconsOffset
    private static final int ICON_SIZE = 16;
    private static final int OVERLAY_Z = 200;   // above the cooldown sweep, like an item count

    @Inject(method = "render", at = @At("TAIL"))
    private static void seeld$drawOverlay(GuiGraphics context, int screenWidth, int screenHeight,
                                          HudRenderHelper.SpellHotBarWidget.ViewModel viewModel, CallbackInfo ci) {
        var client = Minecraft.getInstance();
        var player = client.player;
        var rect = HudRenderHelper.SpellHotBarWidget.lastRendered;
        if (player == null || rect == null || viewModel == null) {
            return;
        }
        var slots = SpellHotbar.INSTANCE.slots;
        if (slots.size() != viewModel.spells().size()) {
            return; // the HUD config screen renders a mock view model
        }
        var hud = HudClientConfig.get();
        var manager = ((SpellCaster.Player) player).getCooldownManager();
        var tracker = (ChargeTracker) manager;
        var recasts = (RecastTracker) manager;
        var cooldowns = (CooldownView) manager;
        var font = client.font;
        float tickDelta = client.getTimer().getGameTimeDeltaPartialTick(false);
        context.pose().pushPose();
        context.pose().translate(0, 0, OVERLAY_Z);
        for (int i = 0; i < slots.size(); i++) {
            var spell = slots.get(i).spell();
            if (spell == null) {
                continue; // an item-use slot: vanilla look
            }
            int x = (int) rect.topLeft().x + ICON_OFFSET + SLOT_WIDTH * i;
            int y = (int) rect.topLeft().y + ICON_OFFSET;
            boolean charged = SpellCharges.isCharged(player, spell);
            float sweep = viewModel.spells().get(i).cooldown();

            // Recast window
            var root = recasts.seeld$rootOfStage(SpellCharges.id(spell));
            if (root != null) {
                var window = recasts.seeld$recasts().get(root);
                int width = Math.round(ICON_SIZE * window.remaining(recasts.seeld$currentTick() + tickDelta));
                if (width > 0) {
                    context.fill(x, y + ICON_SIZE - 1, x + width, y + ICON_SIZE, 0xFFFFD75F);
                }
            }

            // Cooldown time left, or the ready frame
            if (sweep > 0) {
                float ticksLeft = cooldowns.seeld$ticksLeft(spell, tickDelta);
                if (hud.cooldown_numbers && ticksLeft > 0) {
                    var text = seeld$format(ticksLeft / 20F, hud.cooldown_decimals_below);
                    int textY = charged ? y + 2 : y + (ICON_SIZE - font.lineHeight) / 2 + 1;
                    context.drawString(font, text, x + ICON_SIZE / 2 - font.width(text) / 2, textY, 0xFFFFFF, true);
                }
            } else if (hud.ready_border) {
                context.renderOutline(x - 1, y - 1, ICON_SIZE + 2, ICON_SIZE + 2, HudClientConfig.borderColor());
            }

            // Charges
            if (charged) {
                int left = tracker.seeld$chargesLeft(spell);
                var text = String.valueOf(left);
                context.drawString(font, text, x + ICON_SIZE + 1 - font.width(text), y + ICON_SIZE - 7,
                        left > 0 ? 0xFFFFFF : 0xFF5555, true);
            }
        }
        context.pose().popPose();
    }

    /// Never shows less than what is left: whole seconds are rounded up, decimals too.
    private static String seeld$format(float seconds, float decimalsBelow) {
        float tenths = (float) Math.ceil(seconds * 10F - 1e-4F) / 10F;
        if (tenths < decimalsBelow) {
            return String.format(Locale.ROOT, "%.1f", tenths);
        }
        return String.valueOf((int) Math.ceil(seconds - 1e-4F));
    }
}
