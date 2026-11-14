package com.example.serverstore;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class Gui {

    public interface Action { void run(ServerPlayer p, ClickType type); }

    /** A chest menu where every click is cancelled, except our button actions. */
    public static class GuiMenu extends ChestMenu {
        private final Map<Integer, Action> actions = new HashMap<>();
        private final int rows;
        private final SimpleContainer box;
        /** 0 = left click, 1 = right click (of the click that is being handled). */
        public int lastButton = 0;

        GuiMenu(int id, Inventory inv, int rows, SimpleContainer box) {
            super(rows == 3 ? MenuType.GENERIC_9x3 : MenuType.GENERIC_9x6, id, inv, box, rows);
            this.rows = rows;
            this.box = box;
        }

        static ItemStack fillerStack() {
            Item f = Util.item(Data.layout.filler);
            if (f == Items.AIR) f = Items.GRAY_STAINED_GLASS_PANE;
            ItemStack s = new ItemStack(f);
            s.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
            return s;
        }

        void set(int slot, ItemStack s, Action a) {
            box.setItem(slot, s);
            if (a != null) actions.put(slot, a);
            else actions.remove(slot);
        }

        void clear(int slot) {
            box.setItem(slot, fillerStack());
            actions.remove(slot);
        }

        void fillRest() {
            for (int i = 0; i < rows * 9; i++) {
                if (box.getItem(i).isEmpty()) box.setItem(i, fillerStack());
            }
        }

        @Override
        public void clicked(int slot, int button, ClickType type, Player player) {
            if (slot >= 0 && slot < rows * 9 && player instanceof ServerPlayer sp
                    && (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE)) {
                Action a = actions.get(slot);
                if (a != null) {
                    lastButton = button;
                    Util.click(sp);          // ding!
                    a.run(sp, type);
                }
            }
            if (player.containerMenu == this) this.sendAllDataToRemote();
        }

        @Override public ItemStack quickMoveStack(Player p, int i) { return ItemStack.EMPTY; }
        @Override public boolean stillValid(Player p) { return true; }
    }

    private static void open(ServerPlayer p, String title, int rows, Consumer<GuiMenu> builder) {
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> {
            GuiMenu m = new GuiMenu(id, inv, rows, new SimpleContainer(rows * 9));
            builder.accept(m);
            m.fillRest();
            return m;
        }, Component.literal(title)));
    }

    private static boolean isOwner(ServerPlayer p) {
        return ServerStore.isOp(p.createCommandSourceStack());
    }

    private static int delta(GuiMenu m, ClickType t, int small, int big) {
        int b = t == ClickType.QUICK_MOVE ? big : small;
        return m.lastButton == 1 ? -b : b;
    }

    private static String nice(String id) { return id.replace("minecraft:", "").replace('_', ' '); }

    private static List<String> balance(ServerPlayer p) {
        List<String> l = new ArrayList<>();
        for (Ore o : Ore.values()) l.add(o.label + ": " + Util.count(p, o.item));
        return l;
    }

    // ================================================================ main menu
    public static void main(ServerPlayer p) {
        open(p, Data.layout.mainTitle, 3, m -> {
            m.set(4, Util.icon(Items.PAPER, 1, "Your ores", ChatFormatting.WHITE, balance(p)), null);
            m.set(10, Util.icon(Items.CHEST, 1, "Shop", ChatFormatting.GREEN,
                    List.of("Buy items with ores.", "Right-click an item to sell it back.")), (pl, t) -> shop(pl));
            m.set(12, Util.icon(Items.GOLD_INGOT, 1, "Bids", ChatFormatting.GOLD,
                    List.of("See every open auction.", "Left click an item to bid.")), (pl, t) -> bids(pl, false));
            m.set(14, Util.icon(Items.WRITABLE_BOOK, 1, "My Auctions", ChatFormatting.AQUA,
                    List.of("Your listings.", "Create new auctions here.")), (pl, t) -> bids(pl, true));
            m.set(16, Util.icon(Items.BARREL, 1, "Player Market", ChatFormatting.LIGHT_PURPLE,
                    List.of("Buy items other players listed.", "List your own items here.")), (pl, t) -> market(pl));
            if (isOwner(p)) {
                m.set(22, Util.icon(Items.COMMAND_BLOCK, 1, "Edit Shop (Owner)", ChatFormatting.RED,
                        List.of("Only OPs can see this.")), (pl, t) -> ownerEdit(pl));
            }
        });
    }

    // ================================================================ shop
    public static void shop(ServerPlayer p) {
        open(p, Data.layout.shopTitle, 6, m -> {
            for (Data.Entry e : Data.layout.entries) {
                if (e.slot < 0 || e.slot >= 45) continue;
                Item it = Util.item(e.item);
                if (it == Items.AIR) continue;
                Ore ore = Ore.parse(e.ore);
                ItemStack s = new ItemStack(it, Math.max(1, e.count));
                s.set(DataComponents.LORE, Util.loreOf(List.of(
                        "Price: " + e.price + " " + ore.label,
                        "Left-click: buy",
                        "Right-click: sell back for " + Math.max(1, e.price / 2) + " " + ore.label)));
                m.set(e.slot, s, (pl, t) -> {
                    if (m.lastButton == 1) sell(pl, e); else buy(pl, e);
                });
            }
            m.set(47, Util.icon(Items.BARREL, 1, "Player Market", ChatFormatting.LIGHT_PURPLE,
                    List.of("Items listed by other players.")), (pl, t) -> market(pl));
            m.set(49, Util.icon(Items.PAPER, 1, "Your ores", ChatFormatting.WHITE, balance(p)), null);
            m.set(53, Util.icon(Items.ARROW, 1, "Back", ChatFormatting.RED, null), (pl, t) -> main(pl));
        });
    }

    private static void buy(ServerPlayer p, Data.Entry e) {
        Ore ore = Ore.parse(e.ore);
        Item it = Util.item(e.item);
        if (!Util.take(p, ore.item, e.price)) {
            Util.msg(p, "You need " + e.price + " " + ore.label + ".", ChatFormatting.RED);
            return;
        }
        Util.give(p, it, Math.max(1, e.count));
        Util.msg(p, "Purchased " + e.count + "x " + nice(e.item) + " for " + e.price + " " + ore.label, ChatFormatting.GREEN);
    }

    private static void sell(ServerPlayer p, Data.Entry e) {
        Ore ore = Ore.parse(e.ore);
        Item it = Util.item(e.item);
        int cnt = Math.max(1, e.count);
        int refund = Math.max(1, e.price / 2);
        if (!Util.take(p, it, cnt)) {
            Util.msg(p, "You need " + cnt + "x " + nice(e.item) + " to sell.", ChatFormatting.RED);
            return;
        }
        Util.give(p, ore.item, refund);
        Util.msg(p, "Sold " + cnt + "x " + nice(e.item) + " for " + refund + " " + ore.label, ChatFormatting.GREEN);
    }

    // ================================================================ bids / my auctions
    public static void bids(ServerPlayer p, boolean mine) {
        open(p, mine ? "My Auctions" : Data.layout.bidsTitle, 6, m -> {
            String me = p.getUUID().toString();
            boolean owner = isOwner(p);
            int slot = 0;
            for (Data.Auction a : new ArrayList<>(Data.store.auctions)) {
                if (mine && !me.equals(a.seller)) continue;
                if (slot >= 45) break;
                Item it = Util.item(a.item);
                if (it == Items.AIR) continue;
                Ore ore = Ore.parse(a.ore);
                ItemStack s = new ItemStack(it, Math.max(1, a.count));
                List<String> lore = new ArrayList<>();
                lore.add("Seller: " + a.sellerName);
                lore.add(a.bid == 0 ? "Starting bid: " + a.start + " " + ore.label
                                    : "Current bid: " + a.bid + " " + ore.label + " (" + a.bidderName + ")");
                lore.add("Time left: " + Util.timeLeft(a.end));
                if (mine) lore.add(a.bidder == null ? "Click to cancel" : "Has bids - can't cancel");
                else {
                    lore.add("Click: bid " + (a.bid == 0 ? a.start : a.bid + 1) + " " + ore.label);
                    lore.add("Shift-click: bid +5");
                    if (owner) lore.add("OWNER: right-click to remove");
                }
                s.set(DataComponents.LORE, Util.loreOf(lore));
                m.set(slot++, s, (pl, t) -> {
                    if (mine) Auctions.cancel(pl, a);
                    else if (m.lastButton == 1 && isOwner(pl)) Auctions.adminRemove(pl, a);
                    else Auctions.bid(pl, a, t == ClickType.QUICK_MOVE ? 5 : 1);
                    bids(pl, mine);
                });
            }
            m.set(45, Util.icon(Items.ARROW, 1, "Back", ChatFormatting.RED, null), (pl, t) -> main(pl));
            m.set(49, Util.icon(Items.EMERALD_BLOCK, 1, "Create Auction", ChatFormatting.GREEN,
                    List.of("Auction the item in your hand.")), (pl, t) -> createAuction(pl));
            m.set(53, Util.icon(Items.CLOCK, 1, "Refresh", ChatFormatting.YELLOW, null), (pl, t) -> bids(pl, mine));
        });
    }

    // ================================================================ create auction (no commands needed)
    private static class Draft {
        Ore ore = Ore.IRON;
        int start = 1;
        int minutes = 60;
    }

    private static final int[] DURATIONS = {5, 15, 30, 60, 180, 360, 720, 1440, 4320};

    private static String fmtMinutes(int mins) {
        if (mins < 60) return mins + " minutes";
        if (mins % 60 == 0) return (mins / 60) + (mins == 60 ? " hour" : " hours");
        return mins + " minutes";
    }

    public static void createAuction(ServerPlayer p) {
        Draft d = new Draft();
        open(p, "Create Auction", 3, m -> drawCreate(m, p, d));
    }

    private static void drawCreate(GuiMenu m, ServerPlayer p, Draft d) {
        ItemStack hand = p.getMainHandItem();
        if (hand.isEmpty()) {
            m.set(4, Util.icon(Items.BARRIER, 1, "Nothing in your hand!", ChatFormatting.RED,
                    List.of("Close this, hold the item you want", "to sell, then open Create Auction again.")), null);
        } else {
            ItemStack s = hand.copy();
            s.set(DataComponents.LORE, Util.loreOf(List.of("This is what gets auctioned.", "The whole stack is listed.")));
            m.set(4, s, null);
        }
        m.set(10, Util.icon(d.ore.item, 1, "Currency: " + d.ore.label, ChatFormatting.GOLD,
                List.of("Left-click: next ore", "Right-click: previous ore")), (pl, t) -> {
            int n = Ore.values().length;
            d.ore = Ore.values()[(d.ore.ordinal() + (m.lastButton == 1 ? n - 1 : 1)) % n];
            drawCreate(m, pl, d);
        });
        m.set(12, Util.icon(Items.GOLD_NUGGET, 1, "Starting bid: " + d.start + " " + d.ore.label, ChatFormatting.YELLOW,
                List.of("Left: +1   Right: -1", "Shift-left: +10   Shift-right: -10")), (pl, t) -> {
            d.start = Math.max(1, Math.min(100000, d.start + delta(m, t, 1, 10)));
            drawCreate(m, pl, d);
        });
        m.set(14, Util.icon(Items.CLOCK, 1, "Duration: " + fmtMinutes(d.minutes), ChatFormatting.AQUA,
                List.of("Left-click: longer", "Right-click: shorter")), (pl, t) -> {
            int idx = 0;
            for (int i = 0; i < DURATIONS.length; i++) if (DURATIONS[i] == d.minutes) idx = i;
            idx = Math.max(0, Math.min(DURATIONS.length - 1, idx + (m.lastButton == 1 ? -1 : 1)));
            d.minutes = DURATIONS[idx];
            drawCreate(m, pl, d);
        });
        m.set(16, Util.icon(Items.EMERALD_BLOCK, 1, "CONFIRM", ChatFormatting.GREEN,
                List.of("List it now!")), (pl, t) -> {
            if (Auctions.create(pl, d.ore.name(), d.start, d.minutes)) bids(pl, true);
        });
        m.set(22, Util.icon(Items.ARROW, 1, "Back", ChatFormatting.RED, null), (pl, t) -> bids(pl, true));
    }

    // ================================================================ player market (fixed price, players list items)
    public static void market(ServerPlayer p) {
        open(p, "Player Market", 6, m -> {
            String me = p.getUUID().toString();
            boolean owner = isOwner(p);
            int slot = 0;
            for (Data.Listing l : new ArrayList<>(Data.store.listings)) {
                if (slot >= 45) break;
                Item it = Util.item(l.item);
                if (it == Items.AIR) continue;
                Ore ore = Ore.parse(l.ore);
                ItemStack s = new ItemStack(it, Math.max(1, l.count));
                List<String> lore = new ArrayList<>();
                lore.add("Seller: " + l.sellerName);
                lore.add("Price: " + l.price + " " + ore.label);
                if (me.equals(l.seller)) lore.add("Your listing - right-click to take it back");
                else {
                    lore.add("Left-click: buy");
                    if (owner) lore.add("OWNER: right-click to remove");
                }
                s.set(DataComponents.LORE, Util.loreOf(lore));
                m.set(slot++, s, (pl, t) -> {
                    if (m.lastButton == 1) {
                        if (pl.getUUID().toString().equals(l.seller)) Market.cancel(pl, l);
                        else if (isOwner(pl)) Market.adminRemove(pl, l);
                    } else {
                        Market.buy(pl, l);
                    }
                    market(pl);
                });
            }
            m.set(45, Util.icon(Items.ARROW, 1, "Back", ChatFormatting.RED, null), (pl, t) -> main(pl));
            m.set(49, Util.icon(Items.EMERALD_BLOCK, 1, "List an Item", ChatFormatting.GREEN,
                    List.of("Sell the item in your hand", "for a fixed price.")), (pl, t) -> listItem(pl));
            m.set(53, Util.icon(Items.CLOCK, 1, "Refresh", ChatFormatting.YELLOW, null), (pl, t) -> market(pl));
        });
    }

    private static class ListDraft {
        Ore ore = Ore.IRON;
        int price = 1;
    }

    public static void listItem(ServerPlayer p) {
        ListDraft d = new ListDraft();
        open(p, "List an Item", 3, m -> drawList(m, p, d));
    }

    private static void drawList(GuiMenu m, ServerPlayer p, ListDraft d) {
        ItemStack hand = p.getMainHandItem();
        if (hand.isEmpty()) {
            m.set(4, Util.icon(Items.BARRIER, 1, "Nothing in your hand!", ChatFormatting.RED,
                    List.of("Close this, hold the item you want", "to sell, then click List an Item again.")), null);
        } else {
            ItemStack s = hand.copy();
            s.set(DataComponents.LORE, Util.loreOf(List.of("This is what gets listed.", "The whole stack is sold together.")));
            m.set(4, s, null);
        }
        m.set(11, Util.icon(d.ore.item, 1, "Currency: " + d.ore.label, ChatFormatting.GOLD,
                List.of("Left-click: next ore", "Right-click: previous ore")), (pl, t) -> {
            int n = Ore.values().length;
            d.ore = Ore.values()[(d.ore.ordinal() + (m.lastButton == 1 ? n - 1 : 1)) % n];
            drawList(m, pl, d);
        });
        m.set(13, Util.icon(Items.GOLD_NUGGET, 1, "Price: " + d.price + " " + d.ore.label, ChatFormatting.YELLOW,
                List.of("Left: +1   Right: -1", "Shift-left: +10   Shift-right: -10")), (pl, t) -> {
            d.price = Math.max(1, Math.min(100000, d.price + delta(m, t, 1, 10)));
            drawList(m, pl, d);
        });
        m.set(15, Util.icon(Items.EMERALD_BLOCK, 1, "CONFIRM", ChatFormatting.GREEN,
                List.of("Put it on the market!")), (pl, t) -> {
            if (Market.create(pl, d.ore.name(), d.price)) market(pl);
        });
        m.set(22, Util.icon(Items.ARROW, 1, "Back", ChatFormatting.RED, null), (pl, t) -> market(pl));
    }

    // ================================================================ owner: edit shop from the GUI
    public static void ownerEdit(ServerPlayer p) {
        open(p, "Edit Shop (click an item)", 6, m -> drawOwner(m, p));
    }

    private static void drawOwner(GuiMenu m, ServerPlayer p) {
        Map<Integer, Data.Entry> bySlot = new HashMap<>();
        for (Data.Entry e : Data.layout.entries) bySlot.put(e.slot, e);
        for (int i = 0; i < 45; i++) {
            Data.Entry e = bySlot.get(i);
            Item it = e == null ? Items.AIR : Util.item(e.item);
            if (e == null || it == Items.AIR) { m.clear(i); continue; }
            Ore ore = Ore.parse(e.ore);
            ItemStack s = new ItemStack(it, Math.max(1, e.count));
            s.set(DataComponents.LORE, Util.loreOf(List.of(
                    "Price: " + e.price + " " + ore.label, "Click to edit / remove")));
            m.set(i, s, (pl, t) -> editEntry(pl, e));
        }
        m.set(45, Util.icon(Items.EMERALD_BLOCK, 1, "Add item in your hand", ChatFormatting.GREEN,
                List.of("Adds the item you hold to the shop.", "Then click it to set the price.")), (pl, t) -> {
            addHeld(pl);
            drawOwner(m, pl);
        });
        m.set(47, Util.icon(Items.CHEST, 1, "Rearrange (drag mode)", ChatFormatting.AQUA,
                List.of("Drag items around freely.")), (pl, t) -> editor(pl));
        m.set(49, Util.icon(Items.TNT, 1, "Clear whole shop", ChatFormatting.RED,
                List.of("SHIFT-click to remove EVERY item.")), (pl, t) -> {
            if (t != ClickType.QUICK_MOVE) {
                Util.msg(pl, "Shift-click to confirm: this removes every shop item.", ChatFormatting.YELLOW);
                return;
            }
            Data.layout.entries.clear();
            Data.saveLayout();
            Util.msg(pl, "Shop cleared.", ChatFormatting.GREEN);
            drawOwner(m, pl);
        });
        m.set(53, Util.icon(Items.ARROW, 1, "Back", ChatFormatting.RED, null), (pl, t) -> main(pl));
    }

    private static void addHeld(ServerPlayer p) {
        ItemStack h = p.getMainHandItem();
        if (h.isEmpty()) { Util.msg(p, "Hold an item to add it.", ChatFormatting.RED); return; }
        boolean[] used = new boolean[45];
        for (Data.Entry e : Data.layout.entries) if (e.slot >= 0 && e.slot < 45) used[e.slot] = true;
        int f = 0;
        while (f < 45 && used[f]) f++;
        if (f >= 45) { Util.msg(p, "The shop is full.", ChatFormatting.RED); return; }
        Data.layout.entries.add(new Data.Entry(f, Util.id(h.getItem()), Math.max(1, h.getCount()), "IRON", 1));
        Data.saveLayout();
        Util.msg(p, "Added " + nice(Util.id(h.getItem())) + " - click it to set the price.", ChatFormatting.GREEN);
    }

    public static void editEntry(ServerPlayer p, Data.Entry e) {
        open(p, "Edit item", 3, m -> drawEntry(m, p, e));
    }

    private static void drawEntry(GuiMenu m, ServerPlayer p, Data.Entry e) {
        Item it = Util.item(e.item);
        Ore ore = Ore.parse(e.ore);
        if (it == Items.AIR) m.set(4, Util.icon(Items.BARRIER, 1, "Unknown item", ChatFormatting.RED, null), null);
        else m.set(4, new ItemStack(it, Math.max(1, e.count)), null);

        m.set(10, Util.icon(ore.item, 1, "Price: " + e.price + " " + ore.label, ChatFormatting.GOLD,
                List.of("Left: +1   Right: -1", "Shift-left: +10   Shift-right: -10")), (pl, t) -> {
            e.price = Math.max(1, Math.min(100000, e.price + delta(m, t, 1, 10)));
            Data.saveLayout();
            drawEntry(m, pl, e);
        });
        m.set(12, Util.icon(Items.GOLD_INGOT, 1, "Currency: " + ore.label, ChatFormatting.YELLOW,
                List.of("Left-click: next ore", "Right-click: previous ore")), (pl, t) -> {
            int n = Ore.values().length;
            Ore cur = Ore.parse(e.ore);
            e.ore = Ore.values()[(cur.ordinal() + (m.lastButton == 1 ? n - 1 : 1)) % n].name();
            Data.saveLayout();
            drawEntry(m, pl, e);
        });
        m.set(14, Util.icon(Items.CHEST, 1, "Amount per purchase: " + e.count, ChatFormatting.AQUA,
                List.of("Left: +1   Right: -1", "Shift-left: +8   Shift-right: -8")), (pl, t) -> {
            e.count = Math.max(1, Math.min(64, e.count + delta(m, t, 1, 8)));
            Data.saveLayout();
            drawEntry(m, pl, e);
        });
        m.set(16, Util.icon(Items.TNT, 1, "REMOVE from shop", ChatFormatting.RED,
                List.of("Click to delete this item.")), (pl, t) -> {
            Data.layout.entries.remove(e);
            Data.saveLayout();
            Util.msg(pl, "Removed from the shop.", ChatFormatting.GREEN);
            ownerEdit(pl);
        });
        m.set(22, Util.icon(Items.ARROW, 1, "Done", ChatFormatting.GREEN, null), (pl, t) -> ownerEdit(pl));
    }

    // ================================================================ owner: free drag editor
    /** Plain editable chest: move items around = change the shop. Saved when closed. */
    public static class EditorMenu extends ChestMenu {
        private final SimpleContainer box;
        EditorMenu(int id, Inventory inv, SimpleContainer box) {
            super(MenuType.GENERIC_9x6, id, inv, box, 6);
            this.box = box;
        }
        @Override
        public void removed(Player player) {
            super.removed(player);
            Map<String, Data.Entry> old = new HashMap<>();
            for (Data.Entry e : Data.layout.entries) old.putIfAbsent(e.item, e);
            List<Data.Entry> list = new ArrayList<>();
            for (int i = 0; i < 45; i++) {
                ItemStack s = box.getItem(i);
                if (s.isEmpty()) continue;
                String id = Util.id(s.getItem());
                Data.Entry prev = old.get(id);
                list.add(new Data.Entry(i, id, s.getCount(),
                        prev != null ? prev.ore : "IRON", prev != null ? prev.price : 1));
            }
            Data.layout.entries = list;
            Data.saveLayout();
            if (player instanceof ServerPlayer sp) Util.msg(sp, "Shop layout saved.", ChatFormatting.GREEN);
        }
    }

    public static void editor(ServerPlayer p) {
        SimpleContainer box = new SimpleContainer(54);
        for (Data.Entry e : Data.layout.entries) {
            Item it = Util.item(e.item);
            if (e.slot >= 0 && e.slot < 45 && it != Items.AIR) box.setItem(e.slot, new ItemStack(it, Math.max(1, e.count)));
        }
        for (int i = 45; i < 54; i++) {
            box.setItem(i, Util.icon(Items.BARRIER, 1, "Reserved (ignored)", ChatFormatting.DARK_GRAY, null));
        }
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new EditorMenu(id, inv, box),
                Component.literal("Drag items (bottom row reserved)")));
        Util.msg(p, "Drag items to rearrange. Close the chest to save. Use /owner shopedit again to set prices.", ChatFormatting.AQUA);
    }
}
