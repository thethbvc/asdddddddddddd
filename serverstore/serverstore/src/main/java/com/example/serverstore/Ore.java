package com.example.serverstore;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Currency, cheapest to most expensive. */
public enum Ore {
    IRON("Iron", Items.IRON_INGOT),
    GOLD("Gold", Items.GOLD_INGOT),
    DIAMOND("Diamond", Items.DIAMOND),
    EMERALD("Emerald", Items.EMERALD),
    NETHERITE("Netherite", Items.NETHERITE_INGOT);

    public final String label;
    public final Item item;

    Ore(String label, Item item) { this.label = label; this.item = item; }

    public static Ore parse(String s) {
        try { return valueOf(s.toUpperCase()); } catch (Exception e) { return IRON; }
    }
}
