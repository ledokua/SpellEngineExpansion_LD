package net.ledok.spell_engine_expansion_ld.binding;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.item.ScrollItem;
import net.spell_engine.item.SpellEngineItems;

/// The scroll given back when a spell is unbound, and the mark on a book for spells that were bound
/// without a scroll (lapis `learn` offers), which get nothing back. The mark lives in the book's
/// `custom_data` as a list of spell ids under `seeld_no_refund`.
public final class ScrollRefund {
    public static final String NO_REFUND_KEY = "seeld_no_refund";

    private ScrollRefund() { }

    /// One scroll of `spell`, the same item a loot scroll is (single-spell container, rarity, pool model).
    public static ItemStack scrollOf(Level level, Holder<Spell> spell) {
        var scroll = new ItemStack(SpellEngineItems.SCROLL.get());
        ScrollItem.applySpell(scroll, spell, ScrollItem.resolveSpellPool(level, spell));
        return scroll;
    }

    public static void give(Player player, ItemStack scroll) {
        player.getInventory().placeItemBackInInventory(scroll);
    }

    public static boolean isMarkedNoRefund(ItemStack book, ResourceLocation spellId) {
        var data = book.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(NO_REFUND_KEY)) {
            return false;
        }
        var list = data.copyTag().getList(NO_REFUND_KEY, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            if (list.getString(i).equals(spellId.toString())) {
                return true;
            }
        }
        return false;
    }

    public static void markNoRefund(ItemStack book, ResourceLocation spellId) {
        if (isMarkedNoRefund(book, spellId)) {
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, book, tag -> {
            var list = tag.getList(NO_REFUND_KEY, Tag.TAG_STRING);
            list.add(StringTag.valueOf(spellId.toString()));
            tag.put(NO_REFUND_KEY, list);
        });
    }

    public static void unmark(ItemStack book, ResourceLocation spellId) {
        if (!isMarkedNoRefund(book, spellId)) {
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, book, tag -> {
            var list = tag.getList(NO_REFUND_KEY, Tag.TAG_STRING);
            list.removeIf(element -> element.getAsString().equals(spellId.toString()));
            if (list.isEmpty()) {
                tag.remove(NO_REFUND_KEY);
            } else {
                tag.put(NO_REFUND_KEY, list);
            }
        });
        var data = book.get(DataComponents.CUSTOM_DATA);
        if (data != null && data.isEmpty()) {
            book.remove(DataComponents.CUSTOM_DATA);
        }
    }
}
