package com.example.serverstore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Everything saved to disk: config/serverstore/layout.json and store.json */
public final class Data {
    public static class Entry {
        public int slot;
        public String item = "minecraft:stone";
        public int count = 1;
        public String ore = "IRON";
        public int price = 1;
        public Entry() {}
        public Entry(int slot, String item, int count, String ore, int price) {
            this.slot = slot; this.item = item; this.count = count; this.ore = ore; this.price = price;
        }
    }

    public static class Layout {
        public String mainTitle = "Server Menu";
        public String shopTitle = "Shop";
        public String bidsTitle = "Auctions";
        public String filler = "minecraft:gray_stained_glass_pane";
        public List<Entry> entries = new ArrayList<>();
    }

    public static class Auction {
        public int id;
        public String seller, sellerName;
        public String item;
        public int count;
        public String ore;
        public int start;
        public int bid;            // 0 = no bids yet
        public String bidder, bidderName;
        public long end;
    }

    /** A fixed-price item that a player put up in the player market. */
    public static class Listing {
        public int id;
        public String seller, sellerName;
        public String item;
        public int count;
        public String ore;
        public int price;
    }

    public static class Pending {
        public String item;
        public int count;
        public Pending() {}
        public Pending(String item, int count) { this.item = item; this.count = count; }
    }

    public static class Store {
        public int nextId = 1;
        public List<Auction> auctions = new ArrayList<>();
        public List<Listing> listings = new ArrayList<>();
        public Map<String, List<Pending>> pending = new HashMap<>();
    }

    public static Layout layout = new Layout();
    public static Store store = new Store();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path dir() {
        Path d = FMLPaths.CONFIGDIR.get().resolve("serverstore");
        try { Files.createDirectories(d); } catch (Exception ignored) {}
        return d;
    }

    public static void load() {
        try {
            Path l = dir().resolve("layout.json");
            if (Files.exists(l)) layout = GSON.fromJson(Files.readString(l), Layout.class);
            if (layout == null) layout = new Layout();
            if (layout.entries.isEmpty() && !Files.exists(l)) defaults();
            normalize();
            Path s = dir().resolve("store.json");
            if (Files.exists(s)) store = GSON.fromJson(Files.readString(s), Store.class);
            if (store == null) store = new Store();
        } catch (Exception e) {
            e.printStackTrace();
        }
        saveLayout();
    }

    /** Shop items live in slots 0-44; the bottom row is for buttons. */
    private static void normalize() {
        boolean[] used = new boolean[45];
        List<Entry> bad = new ArrayList<>();
        for (Entry e : layout.entries) {
            if (e.slot >= 0 && e.slot < 45 && !used[e.slot]) used[e.slot] = true;
            else bad.add(e);
        }
        for (Entry e : bad) {
            int f = 0;
            while (f < 45 && used[f]) f++;
            if (f < 45) { e.slot = f; used[f] = true; }
            else layout.entries.remove(e);
        }
    }

    private static void defaults() {
        layout.entries.add(new Entry(10, "minecraft:bread", 16, "IRON", 4));
        layout.entries.add(new Entry(11, "minecraft:golden_apple", 1, "GOLD", 3));
        layout.entries.add(new Entry(12, "minecraft:iron_pickaxe", 1, "IRON", 12));
        layout.entries.add(new Entry(13, "minecraft:diamond_sword", 1, "DIAMOND", 2));
        layout.entries.add(new Entry(14, "minecraft:ender_pearl", 4, "EMERALD", 1));
        layout.entries.add(new Entry(15, "minecraft:elytra", 1, "NETHERITE", 2));
    }

    public static void saveLayout() {
        try { Files.writeString(dir().resolve("layout.json"), GSON.toJson(layout)); } catch (Exception e) { e.printStackTrace(); }
    }

    public static void save() {
        try { Files.writeString(dir().resolve("store.json"), GSON.toJson(store)); } catch (Exception e) { e.printStackTrace(); }
    }
}
