# Chunk Map (Fabric, client-side, Minecraft 1.21.11)

Press **M** in a world to open a map of every chunk your client has received.
The data is saved per server and dimension in `.minecraft/config/chunkmap/`.

- Amber: first recorded during this game session
- Green: loaded right now
- Teal: loaded in the past, not loaded now
- Faint lines: region (`.mca`) borders, 32x32 chunks each
- Drag to pan, scroll to zoom, "Center on me" to jump back to your position
- Hover a chunk to see its coordinates and when you first loaded it

## Limits

A client can't ask a server which chunks it has generated. The server only sends
the chunks around you, so this map shows what *your game* has loaded, not what
other players have explored.

## Build

Needs JDK 21 and Gradle (the Gradle wrapper jar can't be generated offline).

    gradle wrapper      # once, creates ./gradlew
    ./gradlew build     # jar ends up in build/libs/chunkmap-1.0.0.jar

Easier alternative: generate a blank 1.21.11 project at https://fabricmc.net/develop/template
(pick Mojang mappings), then copy this project's `src/` folder over the generated one.

Put the jar (not the -sources jar) in your `mods` folder next to Fabric API.
