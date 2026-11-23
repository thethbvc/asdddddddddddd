package com.example.chunkmap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public final class ChunkMapScreen extends Screen {
    private static final int BACKGROUND = 0xFF0B0F14;
    private static final int COLOR_LOADED = 0xFF3FBF5F;    // loaded right now, seen before this session
    private static final int COLOR_NEW = 0xFFE0A030;       // first recorded during this game session
    private static final int COLOR_SEEN = 0xFF2A5A6A;      // loaded in the past, not loaded now
    private static final int COLOR_REGION_LINE = 0x30FFFFFF;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREY = 0xFFB0B8C0;

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private double centerX; // in chunk units
    private double centerZ;
    private double pixelsPerChunk = 8.0;

    public ChunkMapScreen() {
        super(Component.translatable("screen.chunkmap.title"));
        centerOnPlayer();
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(
                        Component.translatable("screen.chunkmap.center"), b -> centerOnPlayer())
                .bounds(8, 24, 90, 20)
                .build());
    }

    private void centerOnPlayer() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            centerX = player.getX() / 16.0;
            centerZ = player.getZ() / 16.0;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // Skip the blurred world background so the map is drawn on a plain surface
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, BACKGROUND);

        ChunkStore store = ChunkMapClient.currentStore();
        if (store == null) {
            g.drawString(font, "Not in a world", 8, 8, WHITE);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }

        int halfW = width / 2;
        int halfH = height / 2;
        double ppc = pixelsPerChunk;

        // Chunks
        for (Map.Entry<Long, ChunkStore.Entry> e : store.entries().entrySet()) {
            long k = e.getKey();
            int cx = ChunkStore.x(k);
            int cz = ChunkStore.z(k);
            int x0 = Mth.floor(halfW + (cx - centerX) * ppc);
            int y0 = Mth.floor(halfH + (cz - centerZ) * ppc);
            int x1 = Mth.floor(halfW + (cx + 1 - centerX) * ppc);
            int y1 = Mth.floor(halfH + (cz + 1 - centerZ) * ppc);
            if (x1 <= 0 || y1 <= 0 || x0 >= width || y0 >= height) {
                continue;
            }
            if (x1 <= x0) x1 = x0 + 1;
            if (y1 <= y0) y1 = y0 + 1;
            if (ppc >= 6) { // leave a 1px gap so individual chunks are visible
                x1--;
                y1--;
            }
            int color;
            if (store.isLoaded(k)) {
                color = store.isNewThisSession(e.getValue()) ? COLOR_NEW : COLOR_LOADED;
            } else {
                color = COLOR_SEEN;
            }
            g.fill(x0, y0, x1, y1, color);
        }

        // Region file boundaries (one .mca region = 32x32 chunks)
        double minX = centerX - halfW / ppc;
        double maxX = centerX + halfW / ppc;
        double minZ = centerZ - halfH / ppc;
        double maxZ = centerZ + halfH / ppc;
        for (int gx = (int) Math.floor(minX / 32.0) * 32; gx <= maxX; gx += 32) {
            int sx = Mth.floor(halfW + (gx - centerX) * ppc);
            g.fill(sx, 0, sx + 1, height, COLOR_REGION_LINE);
        }
        for (int gz = (int) Math.floor(minZ / 32.0) * 32; gz <= maxZ; gz += 32) {
            int sy = Mth.floor(halfH + (gz - centerZ) * ppc);
            g.fill(0, sy, width, sy + 1, COLOR_REGION_LINE);
        }

        // Player marker
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            int px = Mth.floor(halfW + (player.getX() / 16.0 - centerX) * ppc);
            int pz = Mth.floor(halfH + (player.getZ() / 16.0 - centerZ) * ppc);
            g.fill(px - 2, pz - 2, px + 3, pz + 3, WHITE);
        }

        super.render(g, mouseX, mouseY, partialTick); // button

        // Header
        g.drawString(font, "Chunks recorded: " + store.size() + "   Loaded now: " + store.loadedCount(), 8, 8, WHITE);

        // Legend
        int ly = height - 14;
        legendEntry(g, 8, ly, COLOR_NEW, "New this session");
        legendEntry(g, 120, ly, COLOR_LOADED, "Loaded now");
        legendEntry(g, 200, ly, COLOR_SEEN, "Seen before");
        g.drawString(font, "Faint lines = region (.mca) borders", 290, ly, GREY);

        // Hover info
        int hx = Mth.floor(centerX + (mouseX - halfW) / ppc);
        int hz = Mth.floor(centerZ + (mouseY - halfH) / ppc);
        long hk = ChunkStore.key(hx, hz);
        ChunkStore.Entry he = store.get(hk);
        String info = "Chunk " + hx + ", " + hz + "  (block " + (hx * 16) + ", " + (hz * 16) + ")  ";
        if (he == null) {
            info += "never loaded by you";
        } else {
            info += (store.isLoaded(hk) ? "loaded now" : "not loaded now")
                    + " | first seen " + TIME_FORMAT.format(Instant.ofEpochSecond(he.firstSeen));
        }
        g.drawString(font, info, 8, height - 28, WHITE);
    }

    private void legendEntry(GuiGraphics g, int x, int y, int color, String label) {
        g.fill(x, y, x + 8, y + 8, color);
        g.drawString(font, label, x + 12, y, GREY);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (super.mouseDragged(event, dragX, dragY)) {
            return true;
        }
        centerX -= dragX / pixelsPerChunk;
        centerZ -= dragY / pixelsPerChunk;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double halfW = width / 2.0;
        double halfH = height / 2.0;
        // keep the chunk under the cursor fixed while zooming
        double chunkX = centerX + (mouseX - halfW) / pixelsPerChunk;
        double chunkZ = centerZ + (mouseY - halfH) / pixelsPerChunk;
        pixelsPerChunk = Mth.clamp(pixelsPerChunk * Math.pow(1.25, scrollY), 0.5, 48.0);
        centerX = chunkX - (mouseX - halfW) / pixelsPerChunk;
        centerZ = chunkZ - (mouseY - halfH) / pixelsPerChunk;
        return true;
    }
}
