package com.example.serverstore;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Arrays;

@Mod("serverstore")
@EventBusSubscriber(modid = "serverstore")
public class ServerStore {
    private static int ticks = 0;

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent e) {
        Auctions.server = e.getServer();
        Data.load();
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent e) {
        Data.save();
        Data.saveLayout();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) Auctions.claim(sp);
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post e) {
        if (++ticks % 20 == 0) Auctions.tick();
    }

    // If this line fails to compile, replace the lambda body with:  src.hasPermission(2)
    private static boolean isOp(CommandSourceStack src) {
        return src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();

        d.register(Commands.literal("shop").executes(c -> {
            Gui.main(c.getSource().getPlayerOrException());
            return 1;
        }));

        d.register(Commands.literal("auction")
                .then(Commands.literal("create")
                        .then(Commands.argument("ore", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(Ore.values()).map(o -> o.name().toLowerCase()), b))
                                .then(Commands.argument("start", IntegerArgumentType.integer(1, 100000))
                                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 4320))
                                                .executes(c -> {
                                                    Auctions.create(c.getSource().getPlayerOrException(),
                                                            StringArgumentType.getString(c, "ore"),
                                                            IntegerArgumentType.getInteger(c, "start"),
                                                            IntegerArgumentType.getInteger(c, "minutes"));
                                                    return 1;
                                                }))))));

        d.register(Commands.literal("owner")
                .requires(ServerStore::isOp)
                .then(Commands.literal("shopedit").executes(c -> {
                    Gui.editor(c.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("price")
                        .then(Commands.argument("ore", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(Ore.values()).map(o -> o.name().toLowerCase()), b))
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 100000))
                                        .executes(c -> {
                                            ServerPlayer p = c.getSource().getPlayerOrException();
                                            String id = Util.id(p.getMainHandItem().getItem());
                                            Ore ore = Ore.parse(StringArgumentType.getString(c, "ore"));
                                            int amt = IntegerArgumentType.getInteger(c, "amount");
                                            int n = 0;
                                            for (Data.Entry en : Data.layout.entries)
                                                if (en.item.equals(id)) { en.ore = ore.name(); en.price = amt; n++; }
                                            Data.saveLayout();
                                            Util.msg(p, n == 0 ? "That item isn't in the shop."
                                                    : "Price set to " + amt + " " + ore.label + " (" + n + " entries).",
                                                    n == 0 ? ChatFormatting.RED : ChatFormatting.GREEN);
                                            return 1;
                                        })))))
                .then(Commands.literal("title")
                        .then(Commands.argument("which", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"main", "shop", "bids"}, b))
                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                        .executes(c -> {
                                            String w = StringArgumentType.getString(c, "which");
                                            String t = StringArgumentType.getString(c, "text");
                                            switch (w) {
                                                case "main" -> Data.layout.mainTitle = t;
                                                case "shop" -> Data.layout.shopTitle = t;
                                                case "bids" -> Data.layout.bidsTitle = t;
                                                default -> { c.getSource().sendFailure(net.minecraft.network.chat.Component.literal("Use main, shop or bids")); return 0; }
                                            }
                                            Data.saveLayout();
                                            c.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("Title updated."), false);
                                            return 1;
                                        }))))
                .then(Commands.literal("filler")
                        .then(Commands.argument("item", StringArgumentType.word())
                                .executes(c -> {
                                    String id = StringArgumentType.getString(c, "item");
                                    if (!id.contains(":")) id = "minecraft:" + id;
                                    Item it = Util.item(id);
                                    if (it == Items.AIR) {
                                        c.getSource().sendFailure(net.minecraft.network.chat.Component.literal("Unknown item."));
                                        return 0;
                                    }
                                    Data.layout.filler = id;
                                    Data.saveLayout();
                                    c.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("Filler updated."), false);
                                    return 1;
                                })))
                .then(Commands.literal("reload").executes(c -> {
                    Data.load();
                    c.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("Reloaded."), false);
                    return 1;
                })));
    }
}
