SERVER STORE - NeoForge 1.21.11 (Java 21), server-side only

BUILD THE JAR (no setup on your PC):
1. Make a free account at github.com, create a new empty repo.
2. Upload ALL files from this zip (keep the folders, incl. the hidden .github folder).
3. Open the repo > Actions tab > "Build jar" runs automatically (or press Run workflow).
4. When it's green, click the run > Artifacts > download "serverstore-jar" > unzip.
5. Put serverstore-1.0.0.jar in your server's  mods  folder and restart.

IF IT FAILS: open build.gradle and set neoForge version to the newest 21.11.x from
https://projects.neoforged.net/neoforged/neoforge . Paste any other error to Claude.

COMMANDS
/shop                              main menu: Shop | Bids | My Auctions
/auction create <ore> <start> <minutes>   auction the item in your hand
/owner shopedit                    (OP) drag items around to design the shop (slot 54 reserved)
/owner price <ore> <amount>        (OP) price of the item in your hand
/owner title <main|shop|bids> <text>
/owner filler <item>               background item, e.g. black_stained_glass_pane
/mailbox                           items waiting for you (also in /shop > Mailbox)
/owner reload

MAILBOX
Everything you buy, win or get paid goes straight into your inventory. If it doesn't fit (inventory full)
or you're offline, it waits in your Mailbox (/mailbox) until you collect it. You get a reminder every
30 seconds and when you log in. Nothing is ever dropped on the floor.

SHOP LISTINGS
Enchanted, damaged, renamed items (and anything else with extra data) can be listed and auctioned: the item is
saved exactly as it is and the buyer gets it back with all its enchants.
Price = ores and/or ONE optional extra item. In "List an Item", click "Extra item price" to open a
paged menu of every item in the game (search with the sign, Back arrow to cancel); right-click it to clear.
Extra items are only taken from plain stacks (no enchants/names/contents), so nobody loses a filled shulker box.
While a /shop menu is open you can rearrange your own inventory; the chest part can't be touched.

Ores (low to high): copper, iron, gold, diamond, netherite. Files: config/serverstore/
