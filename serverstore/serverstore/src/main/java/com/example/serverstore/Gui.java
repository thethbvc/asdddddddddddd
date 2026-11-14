package com.example.serverstore;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
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

        GuiMenu(int id, Inventory inv, int rows, SimpleContainer box) {
            super(rows == 3 ? MenuType.GENERIC_9x3 : MenuType.GENERIC_9x6, id, inv, box, rows);
            this.rows = rows;
            this.box = box;
        }

        void set(int slot, ItemStack s, Action a) {
            box.setItem(slot, s);
            if (a != null) actions.put(slot, a);
        }

        void fillRest() {
            Item f = Util.item(Data.layout.filler);
            if (f == Items.AIR) f = Items.GRAY_STAINED_GLASS_PANE;
            for (int i = 0; i < rows * 9; i++) {
                if (box.getItem(i).isEmpty()) {
                    ItemStack s = new ItemStack(f);
                    s.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(" "));
                    box.setItem(i, s);
                }
            }
        }

        @Override
        public void clicked(int slot, int button, ClickType type, Player player) {
            if (slot >= 0 && slot < rows * 9 && player instanceof ServerPlayer sp
                    && (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE)) {
                Action a = actions.get(slot);
                if (a != null) a.run(sp, type);
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

    // ---------------------------------------------------------------- main menu
    public static void main(ServerPlayer p) {
        open(p, Data.layout.mainTitle, 3, m -> {
            m.set(11, Util.icon(Items.CHEST, 1, "Shop", ChatFormatting.GREEN,
                    List.of("Buy items with ores.")), (pl, t) -> shop(pl));
            m.set(13, Util.icon(Items.GOLD_INGOT, 1, "Bids", ChatFormatting.GOLD,
                    List.of("See every open auction.", "Left click an item to bid.")), (pl, t) -> bids(pl, false));
            m.set(15, Util.icon(Items.WRITABLE_BOOK, 1, "My Auctions", ChatFormatting.AQUA,
                    List.of("Your own listings.", "Create one with:", "/auction create <ore> <start> <minutes>")),
                    (pl, t) -> bids(pl, true));
        });
    }

    // ---------------------------------------------------------------- shop
    public static void shop(ServerPlayer p) {
        open(p, Data.layout.shopTitle, 6, m -> {
            for (Data.Entry e : Data.layout.entries) {
                if (e.slot < 0 || e.slot >= 53) continue;
                Item it = Util.item(e.item);
                if (it == Items.AIR) continue;
                Ore ore = Ore.parse(e.ore);
                ItemStack s = new ItemStack(it, Math.max(1, e.count));
                s.set(net.minecraft.core.component.DataComponents.LORE, Util.loreOf(List.of(
                        "Price: " + e.price + " " + ore.label, "Click to buy")));
                m.set(e.slot, s, (pl, t) -> buy(pl, e));
            }
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
        Util.msg(p, "Purchased " + e.count + "x " + e.item.replace("minecraft:", "") + " for " + e.price + " " + ore.label, ChatFormatting.GREEN);
    }

    // ---------------------------------------------------------------- bids / my auctions
    public static void bids(ServerPlayer p, boolean mine) {
        open(p, mine ? "My Auctions" : Data.layout.bidsTitle, 6, m -> {
            String me = p.getUUID().toString();
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
                else { lore.add("Click: bid " + (a.bid == 0 ? a.start : a.bid + 1) + " " + ore.label);
                       lore.add("Shift-click: bid +5"); }
                s.set(net.minecraft.core.component.DataComponents.LORE, Util.loreOf(lore));
                m.set(slot++, s, (pl, t) -> {
                    if (mine) Auctions.cancel(pl, a);
                    else Auctions.bid(pl, a, t == ClickType.QUICK_MOVE ? 5 : 1);
                    bids(pl, mine);
                });
            }
            m.set(45, Util.icon(Items.ARROW, 1, "Back", ChatFormatting.RED, null), (pl, t) -> main(pl));
            m.set(53, Util.icon(Items.CLOCK, 1, "Refresh", ChatFormatting.YELLOW, null), (pl, t) -> bids(pl, mine));
        });
    }

    // ---------------------------------------------------------------- owner editor
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
            for (int i = 0; i < 53; i++) {
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
            if (e.slot >= 0 && e.slot < 53 && it != Items.AIR) box.setItem(e.slot, new ItemStack(it, Math.max(1, e.count)));
        }
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new EditorMenu(id, inv, box),
                Component.literal("Shop Editor (slot 54 is reserved)")));
        Util.msg(p, "Move items to rearrange. Put items in to add, take out to remove. Count = amount per purchase. "
                + "Set prices with /owner price <ore> <amount> while holding the item.", ChatFormatting.AQUA);
    }
}
