package com.example.serverstore;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public final class Market {
    public static final int MAX_LISTINGS = 45;

    private static String nice(String id) { return id.replace("minecraft:", "").replace('_', ' '); }

    /** Takes the item in the player's hand and lists it at a fixed price. */
    public static boolean create(ServerPlayer p, String oreName, int price) {
        ItemStack hand = p.getMainHandItem();
        if (hand.isEmpty()) { Util.msg(p, "Hold the item you want to sell.", ChatFormatting.RED); return false; }
        if (hand.isDamaged() || hand.isEnchanted()) {
            Util.msg(p, "Damaged or enchanted items are not supported (their data would be lost).", ChatFormatting.RED);
            return false;
        }
        if (Data.store.listings.size() >= MAX_LISTINGS) { Util.msg(p, "The player market is full.", ChatFormatting.RED); return false; }
        Ore ore = Ore.parse(oreName);
        Data.Listing l = new Data.Listing();
        l.id = Data.store.nextId++;
        l.seller = p.getUUID().toString();
        l.sellerName = p.getName().getString();
        l.item = Util.id(hand.getItem());
        l.count = hand.getCount();
        l.ore = ore.name();
        l.price = price;
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        Data.store.listings.add(l);
        Data.save();
        Util.msg(p, "Listed " + l.count + "x " + nice(l.item) + " for " + price + " " + ore.label, ChatFormatting.GREEN);
        return true;
    }

    public static void buy(ServerPlayer p, Data.Listing l) {
        if (!Data.store.listings.contains(l)) { Util.msg(p, "That listing is gone.", ChatFormatting.RED); return; }
        if (p.getUUID().toString().equals(l.seller)) {
            Util.msg(p, "That's your own listing (right-click to take it back).", ChatFormatting.YELLOW);
            return;
        }
        Ore ore = Ore.parse(l.ore);
        if (!Util.take(p, ore.item, l.price)) {
            Util.msg(p, "You need " + l.price + " " + ore.label + ".", ChatFormatting.RED);
            return;
        }
        Data.store.listings.remove(l);
        Util.give(p, Util.item(l.item), l.count);
        ServerPlayer seller = Auctions.online(l.seller);
        if (seller != null) {
            Util.msg(seller, p.getName().getString() + " bought your " + l.count + "x " + nice(l.item) + "!", ChatFormatting.YELLOW);
            Util.click(seller);
        }
        Auctions.deliver(l.seller, Util.id(ore.item), l.price, "the player market");
        Data.save();
        Util.msg(p, "Bought " + l.count + "x " + nice(l.item) + " for " + l.price + " " + ore.label, ChatFormatting.GREEN);
    }

    /** Seller takes their own item back. */
    public static void cancel(ServerPlayer p, Data.Listing l) {
        if (!Data.store.listings.remove(l)) return;
        Util.give(p, Util.item(l.item), l.count);
        Data.save();
        Util.msg(p, "Listing removed, item returned.", ChatFormatting.YELLOW);
    }

    /** OP only: remove any listing, item goes back to the seller. */
    public static void adminRemove(ServerPlayer p, Data.Listing l) {
        if (!Data.store.listings.remove(l)) return;
        Auctions.deliver(l.seller, l.item, l.count, "the player market");
        Data.save();
        Util.msg(p, "Listing removed (item returned to the seller).", ChatFormatting.YELLOW);
    }
}
