package com.example.serverstore;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The mailbox. Every purchase, payout and refund goes through here.
 * Items are handed over right away if they fit. Anything that does not fit (full inventory) or whose
 * owner is offline is held in store.json until the player collects it from /mailbox.
 * Nothing is ever dropped on the floor and nothing is handed over automatically later.
 */
public final class Mail {
    private static final long REMIND_EVERY_MS = 30_000;
    private static final Map<UUID, Long> LAST_REMINDER = new HashMap<>();

    private Mail() {}

    private static String nice(String id) { return id.replace("minecraft:", "").replace('_', ' '); }

    /** Adds to the player's waiting items (merged per item type). Caller saves. */
    private static void hold(String uuid, String itemId, int count, String stack) {
        if (count <= 0) return;
        List<Data.Pending> list = Data.store.pending.computeIfAbsent(uuid, k -> new ArrayList<>());
        for (Data.Pending d : list) {
            if (d.item.equals(itemId) && Objects.equals(d.stack, stack)) { d.count += count; return; }
        }
        list.add(new Data.Pending(itemId, count, stack));
    }

    /** Items waiting for a player (a copy, safe to loop over). */
    public static List<Data.Pending> waiting(String uuid) {
        List<Data.Pending> l = Data.store.pending.get(uuid);
        return l == null ? new ArrayList<>() : new ArrayList<>(l);
    }

    /** Total number of waiting items that can actually be delivered (unknown items are ignored). */
    public static int waitingCount(String uuid) {
        int c = 0;
        for (Data.Pending d : waiting(uuid)) if (Util.item(d.item) != Items.AIR) c += Math.max(0, d.count);
        return c;
    }

    // ---------------------------------------------------------------- sending

    /** Send items to a player, online or not, and tell them about it. */
    public static void send(String uuid, String itemId, int count, String from) {
        send(uuid, itemId, count, from, null);
    }

    /** stack = saved full item data (enchants, name...), or null for a plain item. */
    public static void send(String uuid, String itemId, int count, String from, String stack) {
        handOver(uuid, Auctions.online(uuid), itemId, count, from, stack);
    }

    /** Give items to an online player when the caller already reports the action (buy, cancel...). */
    public static void give(ServerPlayer p, String itemId, int count) {
        give(p, itemId, count, null);
    }

    public static void give(ServerPlayer p, String itemId, int count, String stack) {
        handOver(p.getUUID().toString(), p, itemId, count, null, stack);
    }

    private static void handOver(String uuid, ServerPlayer p, String itemId, int count, String from, String stack) {
        if (count <= 0) return;
        Item item = Util.item(itemId);
        int left = count;
        if (p != null && item != Items.AIR) {
            try {
                left = Util.giveFit(p, Stacks.decode(stack, itemId, 1), count);
            } catch (Exception e) {
                e.printStackTrace();
                left = count;                 // something went wrong: keep it safe in the mailbox
            }
            int given = count - left;
            if (given > 0 && from != null) {
                Util.msg(p, "You received " + given + "x " + nice(itemId) + " from " + from + ".", ChatFormatting.GREEN);
            }
        }
        if (left > 0) {
            hold(uuid, itemId, left, stack);
            Data.save();
            if (p != null && item != Items.AIR) {
                Util.msg(p, "Your inventory is full! " + left + "x " + nice(itemId) + " was sent to your mailbox - check your /mailbox!", ChatFormatting.RED);
                LAST_REMINDER.put(p.getUUID(), System.currentTimeMillis());
            }
        }
    }

    // ---------------------------------------------------------------- delivering what is waiting

    /** Hands over everything that fits right now. Returns true if anything changed. */
    private static boolean drain(ServerPlayer p) {
        String uuid = p.getUUID().toString();
        List<Data.Pending> list = Data.store.pending.get(uuid);
        if (list == null) return false;
        boolean changed = false;
        for (Data.Pending d : new ArrayList<>(list)) {
            if (d.count <= 0) { list.remove(d); changed = true; continue; }
            Item item = Util.item(d.item);
            if (item == Items.AIR) continue;
            int left;
            try {
                left = Util.giveFit(p, Stacks.decode(d.stack, d.item, 1), d.count);
            } catch (Exception e) {
                e.printStackTrace();
                continue;
            }
            int given = d.count - left;
            if (given <= 0) continue;
            changed = true;
            d.count = left;
            if (left <= 0) list.remove(d);
            Util.msg(p, "Delivered " + given + "x " + nice(d.item) + " from your mailbox.", ChatFormatting.GREEN);
        }
        if (list.isEmpty()) { Data.store.pending.remove(uuid); changed = true; }
        return changed;
    }

    /** Runs once a second: reminds players who have items waiting. Nothing is handed over automatically. */
    public static void tick() {
        if (Data.store.pending.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (String uuid : new ArrayList<>(Data.store.pending.keySet())) {
            ServerPlayer p = Auctions.online(uuid);
            if (p == null) continue;
            int left = waitingCount(uuid);
            if (left <= 0) { LAST_REMINDER.remove(p.getUUID()); continue; }
            Long last = LAST_REMINDER.get(p.getUUID());
            if (last == null || now - last >= REMIND_EVERY_MS) {
                Util.msg(p, "You have " + left + " item(s) waiting - check your /mailbox!", ChatFormatting.YELLOW);
                LAST_REMINDER.put(p.getUUID(), now);
            }
        }
    }

    /** When a player joins: tell them what piled up while they were away. */
    public static void onLogin(ServerPlayer p) {
        int left = waitingCount(p.getUUID().toString());
        if (left <= 0) return;
        Util.msg(p, "You have " + left + " item(s) waiting in your mailbox - check your /mailbox!", ChatFormatting.GOLD);
        LAST_REMINDER.put(p.getUUID(), System.currentTimeMillis());
    }

    // ---------------------------------------------------------------- manual collecting (mailbox menu)

    public static void collect(ServerPlayer p, Data.Pending d) {
        String uuid = p.getUUID().toString();
        List<Data.Pending> list = Data.store.pending.get(uuid);
        if (list == null || !list.contains(d)) return;
        if (Util.item(d.item) == Items.AIR) return;
        int left = Util.giveFit(p, Stacks.decode(d.stack, d.item, 1), d.count);
        int given = d.count - left;
        if (given <= 0) {
            Util.msg(p, "Your inventory is full - clear some space first.", ChatFormatting.RED);
            return;
        }
        d.count = left;
        if (left <= 0) list.remove(d);
        if (list.isEmpty()) Data.store.pending.remove(uuid);
        Data.save();
        Util.msg(p, "Collected " + given + "x " + nice(d.item) + ".", ChatFormatting.GREEN);
    }

    public static void collectAll(ServerPlayer p) {
        boolean changed = drain(p);
        if (changed) Data.save();
        else if (waitingCount(p.getUUID().toString()) > 0) {
            Util.msg(p, "Your inventory is full - clear some space first.", ChatFormatting.RED);
        }
    }
}
