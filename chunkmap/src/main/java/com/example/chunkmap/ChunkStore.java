package com.example.chunkmap;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Every chunk the client has received for one server + dimension.
 * Keys use the same bit layout as ChunkPos#toLong (x in the low 32 bits, z in the high 32 bits).
 */
public final class ChunkStore {
    private static final int MAGIC = 0x434D4150; // "CMAP"
    private static final int VERSION = 1;

    public static final class Entry {
        public final long firstSeen; // epoch seconds
        public long lastSeen;

        Entry(long firstSeen, long lastSeen) {
            this.firstSeen = firstSeen;
            this.lastSeen = lastSeen;
        }
    }

    private final Path file;
    private final long sessionStart;
    private final Map<Long, Entry> chunks = new HashMap<>();
    private final Set<Long> loadedNow = new HashSet<>();
    private boolean dirty;

    private ChunkStore(Path file, long sessionStart) {
        this.file = file;
        this.sessionStart = sessionStart;
    }

    public static long key(int x, int z) {
        return (x & 0xFFFFFFFFL) | ((z & 0xFFFFFFFFL) << 32);
    }

    public static int x(long key) {
        return (int) key;
    }

    public static int z(long key) {
        return (int) (key >> 32);
    }

    public static ChunkStore load(Path file, long sessionStart) {
        ChunkStore store = new ChunkStore(file, sessionStart);
        if (!Files.exists(file)) {
            return store;
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION) {
                return store;
            }
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                long k = in.readLong();
                long first = in.readLong();
                long last = in.readLong();
                store.chunks.put(k, new Entry(first, last));
            }
        } catch (IOException e) {
            ChunkMapClient.LOGGER.warn("Could not read chunk map file {}: {}", file, e.toString());
        }
        return store;
    }

    public void markLoaded(long key, long nowSeconds) {
        loadedNow.add(key);
        Entry e = chunks.get(key);
        if (e == null) {
            chunks.put(key, new Entry(nowSeconds, nowSeconds));
        } else {
            e.lastSeen = nowSeconds;
        }
        dirty = true;
    }

    public void markUnloaded(long key) {
        loadedNow.remove(key);
    }

    public void clearLoaded() {
        loadedNow.clear();
    }

    public boolean isLoaded(long key) {
        return loadedNow.contains(key);
    }

    /** True when this chunk was first recorded during the current game session. */
    public boolean isNewThisSession(Entry e) {
        return e.firstSeen >= sessionStart;
    }

    public Entry get(long key) {
        return chunks.get(key);
    }

    public Map<Long, Entry> entries() {
        return Collections.unmodifiableMap(chunks);
    }

    public int size() {
        return chunks.size();
    }

    public int loadedCount() {
        return loadedNow.size();
    }

    public boolean isDirty() {
        return dirty;
    }

    public void save() {
        if (!dirty) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp)))) {
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                out.writeInt(chunks.size());
                for (Map.Entry<Long, Entry> e : chunks.entrySet()) {
                    out.writeLong(e.getKey());
                    out.writeLong(e.getValue().firstSeen);
                    out.writeLong(e.getValue().lastSeen);
                }
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            dirty = false;
        } catch (IOException e) {
            ChunkMapClient.LOGGER.warn("Could not save chunk map file {}: {}", file, e.toString());
        }
    }
}
