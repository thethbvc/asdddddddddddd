package com.example.serverstore;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** The shop: players list items at a fixed ore price. */
public final class Market {
    public static final int MAX_LISTINGS = 200;

    /** OPs in test mode may buy their own listings (they pay themselves, so nothing is lost). */
    public static final Set<UUID> TEST = new HashSet<>();

    /** Retained as an empty array for compatibility with the current GUI; block prices are disabled. */
    public static final String[] COMMON_BLOCKS = {};

    public static String nice(String id) { return id.replace("minecraft:", "").replace('_', ' '); }

    /** Listings now have a single ore-currency price. */
    public static String priceText(Data.Listing l) {
        return l.price <= 0 ? "free" : l.price + " " + Ore.parse(l.ore).label;
    }

    public static boolean isTest(ServerPlayer p) {
        return TEST.contains(p.getUUID()) && ServerStore.isOp(p.createCommandSourceStack());
    }

    /** Takes the item in the player's hand and lists it. Legacy extra-price arguments are ignored. */
    public static boolean create(ServerPlayer p, String oreName, int price, String extraItem, int extraCount) {
        ItemStack hand = p.getMainHandItem();
        if (hand.isEmpty()) { Util.msg(p, "Hold the item you want to sell.", ChatFormatting.RED); return false; }
        if (hand.isDamaged() || hand.isEnchanted()) {
            Util.msg(p, "Damaged or enchanted items are not supported (their data would be lost).", ChatFormatting.RED);
            return false;
        }
        if (Data.store.listings.size() >= MAX_LISTINGS) { Util.msg(p, "The shop is full.", ChatFormatting.RED); return false; }
        if (price <= 0) { Util.msg(p, "Set an ore price greater than 0.", ChatFormatting.RED); return false; }
        Ore ore = Ore.parse(oreName);
        Data.Listing l = new Data.Listing();
        l.id = Data.store.nextId++;
        l.seller = p.getUUID().toString();
        l.sellerName = p.getName().getString();
        l.item = Util.id(hand.getItem());
        l.count = hand.getCount();
        l.ore = ore.name();
        l.price = price;
        // Clear legacy fields so future saves no longer retain block-price data.
        l.extraItem = null;
        l.extraCount = 0;
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        Data.store.listings.add(l);
        Data.save();
        Util.msg(p, "Listed " + l.count + "x " + nice(l.item) + " for " + priceText(l), ChatFormatting.GREEN);
        return true;
    }

    public static void buy(ServerPlayer p, Data.Listing l) {
        if (!Data.store.listings.contains(l)) { Util.msg(p, "That listing is gone.", ChatFormatting.RED); return; }
        boolean test = isTest(p);
        if (p.getUUID().toString().equals(l.seller) && !test) {
            Util.msg(p, "That's your own listing (right-click to take it back).", ChatFormatting.YELLOW);
            return;
        }
        Ore ore = Ore.parse(l.ore);
        if (l.price > 0 && Util.count(p, ore.item) < l.price) {
            Util.msg(p, "You need " + priceText(l) + ".", ChatFormatting.RED);
            return;
        }
        if (l.price > 0) Util.take(p, ore.item, l.price);
        Data.store.listings.remove(l);
        Util.give(p, Util.item(l.item), l.count);
        ServerPlayer seller = Auctions.online(l.seller);
        if (seller != null && seller != p) {
            Util.msg(seller, p.getName().getString() + " bought your " + l.count + "x " + nice(l.item) + "!", ChatFormatting.YELLOW);
            Util.click(seller);
        }
        if (l.price > 0) Auctions.deliver(l.seller, Util.id(ore.item), l.price, "the shop");
        Data.save();
        Util.msg(p, "Bought " + l.count + "x " + nice(l.item) + " for " + priceText(l) + (test ? "  [TEST MODE]" : ""), ChatFormatting.GREEN);
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
        Auctions.deliver(l.seller, l.item, l.count, "the shop (removed by an admin)");
        Data.save();
        Util.msg(p, "Listing removed (item returned to " + l.sellerName + ").", ChatFormatting.YELLOW);
    }
}
