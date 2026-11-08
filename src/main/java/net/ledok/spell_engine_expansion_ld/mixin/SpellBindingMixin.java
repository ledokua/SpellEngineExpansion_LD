package net.ledok.spell_engine_expansion_ld.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.ledok.spell_engine_expansion_ld.config.SeeldConfig;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.container.SpellContainerHelper;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.spellbinding.SpellBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.HashMap;

/// The binding table lists the spells of the pool that have a `learn` block, or the spells of the scroll
/// in the slot. With `learn` removed everywhere, a bound spell shows up only while a second scroll of it
/// is in the slot, so it can't be unbound without one. This adds the book's own spells to the list (at
/// no cost; the client renders them as bound and offers the unbind dialog on click).
@Mixin(SpellBinding.class)
public abstract class SpellBindingMixin {
    @ModifyReturnValue(method = "offersFor", at = @At("RETURN"))
    private static SpellBinding.OfferResult seeld$offerBoundSpells(SpellBinding.OfferResult original, Level world, boolean creative,
                                                                   ItemStack itemStack, ItemStack consumableStack, int libraryPower) {
        if (!SeeldConfig.get().unbind_refunds_scroll || original.mode() != SpellBinding.Mode.SPELL) {
            return original;
        }
        var container = SpellContainerHelper.containerFromItemStack(itemStack);
        if (container == null || container.spell_ids().isEmpty()) {
            return original;
        }
        var registry = SpellRegistry.from(world);
        var byEntry = new HashMap<Holder<Spell>, SpellBinding.Offer>();
        for (var offer : original.offers()) {
            registry.getHolder(offer.id()).ifPresent(entry -> byEntry.put(entry, offer));
        }
        boolean added = false;
        for (var idString : container.spell_ids()) {
            var id = ResourceLocation.tryParse(idString);
            if (id == null) {
                continue;
            }
            var entry = registry.getHolder(id).orElse(null);
            if (entry == null || byEntry.containsKey(entry)) {
                continue;
            }
            byEntry.put(entry, new SpellBinding.Offer(registry.getId(entry.value()), 0, 0, 0, true));
            added = true;
        }
        if (!added) {
            return original;
        }
        // Same catalog order the table lays its tier rows out in
        var entries = new ArrayList<>(byEntry.keySet());
        entries.sort(SpellContainerHelper.catalogEntrySorter);
        var offers = new ArrayList<SpellBinding.Offer>(entries.size());
        for (var entry : entries) {
            offers.add(byEntry.get(entry));
        }
        return new SpellBinding.OfferResult(original.mode(), offers);
    }
}
