package com.example.serverstore;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Saves and loads the full data of an item (enchantments, custom name, durability, lore, container
 * contents...) so listings, auctions and the mailbox keep it. Plain items need no saved data (null).
 * The count is never part of the saved data, it is stored next to it.
 */
public final class Stacks {
    private Stacks() {}

    private static DynamicOps<JsonElement> ops() {
        return Auctions.server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
    }

    /** True if nothing beyond the item type needs saving. */
    public static boolean isPlain(ItemStack s) {
        return ItemStack.isSameItemSameComponents(s, new ItemStack(s.getItem()));
    }

    /** The item's full data as JSON text, or null if it could not be saved. */
    public static String encode(ItemStack s) {
        try {
            if (Auctions.server == null) return null;
            ItemStack one = s.copy();
            one.setCount(1);
            Optional<JsonElement> r = ItemStack.CODEC.encodeStart(ops(), one).result();
            return r.map(JsonElement::toString).orElse(null);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /** Loads saved item data, or null if it is missing or broken. */
    public static ItemStack parse(String json) {
        try {
            if (json == null || Auctions.server == null) return null;
            JsonElement el = JsonParser.parseString(json);
            Optional<ItemStack> r = ItemStack.CODEC.parse(ops(), el).result();
            return r.orElse(null);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /** The stack for a listing/auction/mailbox entry. Falls back to the plain item if the saved data is broken. */
    public static ItemStack decode(String json, String itemId, int count) {
        ItemStack s = parse(json);
        if (s == null || s.isEmpty()) s = new ItemStack(Util.item(itemId));
        s.setCount(Math.max(1, count));
        return s;
    }
}
