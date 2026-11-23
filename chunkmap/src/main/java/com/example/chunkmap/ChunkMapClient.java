package com.example.chunkmap;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class ChunkMapClient implements ClientModInitializer {
    public static final String MOD_ID = "chunkmap";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final long SESSION_START = System.currentTimeMillis() / 1000L;
    private static final int AUTOSAVE_TICKS = 20 * 60 * 5; // every 5 minutes

    private static final Map<String, ChunkStore> STORES = new HashMap<>();
    private static KeyMapping openKey;
    private static int tickCounter;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.chunkmap.open", InputConstants.Type.KEYSYM, InputConstants.KEY_M, category));

        // A chunk packet arrived and the chunk now exists on the client
        ClientChunkEvents.CHUNK_LOAD.register((level, chunk) ->
                storeFor(level).markLoaded(chunk.getPos().toLong(), System.currentTimeMillis() / 1000L));

        ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) ->
                storeFor(level).markUnloaded(chunk.getPos().toLong()));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.consumeClick()) {
                if (client.level != null && client.screen == null) {
                    client.setScreen(new ChunkMapScreen());
                }
            }
            if (++tickCounter >= AUTOSAVE_TICKS) {
                tickCounter = 0;
                saveAll();
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            for (ChunkStore s : STORES.values()) {
                s.clearLoaded();
            }
            saveAll();
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> saveAll());
    }

    /** The store for the level the player is currently in, or null when not in a world. */
    public static ChunkStore currentStore() {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? null : storeFor(level);
    }

    private static ChunkStore storeFor(ClientLevel level) {
        String name = sanitize(serverId()) + "__" + sanitize(level.dimension().toString());
        if (name.length() > 150) {
            name = name.substring(0, 150);
        }
        String fileName = name + ".cmap";
        return STORES.computeIfAbsent(fileName, f -> {
            Path dir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
            return ChunkStore.load(dir.resolve(f), SESSION_START);
        });
    }

    private static String serverId() {
        Minecraft mc = Minecraft.getInstance();
        ServerData server = mc.getCurrentServer();
        if (server != null && server.ip != null) {
            return server.ip;
        }
        if (mc.getSingleplayerServer() != null) {
            return "singleplayer_" + mc.getSingleplayerServer().getWorldData().getLevelName();
        }
        return "unknown";
    }

    private static String sanitize(String s) {
        return s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static void saveAll() {
        for (ChunkStore s : STORES.values()) {
            s.save();
        }
    }
}
