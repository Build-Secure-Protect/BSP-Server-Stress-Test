# Testing

Headless checks run with `./gradlew runGameTestServer`. The sections below are the in-game checks, one per task, newest at the bottom.

**Use a test copy of a server.** Never put this mod on a live server's `mods/` folder.

## 2026-10-20: ST-0, the project

Headless: 4 GameTests pass: the mod loads beside BSP-Core and a mock player can be made; the settings file is `config/bsp_server_stress_test/settings.toml`, the folder does not overlap the world folder, and a file name leading outside the folder is refused; a message is plain text in English (not a translation key); `/stress` is refused to a player who is not an operator, allowed to a level 4 operator, and follows BSP-Core's admin check below level 4.

On a test server:
1. Put `bsp-server-stress-test-0.1.0.jar` in the test server's `mods` folder (on Mohist too) and start it. The log shows "BSP-Server Stress Test loaded; its files are in .../config/bsp_server_stress_test", and that folder holds `settings.toml`.
2. Join with a client that does **not** have the mod: you get in with no mod-mismatch screen.
3. As a level 4 operator, `/stress` shows four lines: "BSP-Server Stress Test 0.1.0", "Every file this mod writes is in config/bsp_server_stress_test/", "Runs will not start while a real player is online (safety.allowRunsWithPlayers is false).", and "BSP-Core: installed" (or "not installed"). The lines are plain English, not keys such as `message.bsp_server_stress_test...`.
4. A player who is not an operator (and not in BSP-Core's `admin.admins`) gets "Unknown or incomplete command" for `/stress`. Note: with BSP-Core installed and its `admin.admins` empty, BSP-Core counts every operator of level 2 or more as an admin, so they may use `/stress` too.
5. Stop the server, delete the jar and `config/bsp_server_stress_test/`, start it again: it starts, and nothing of the mod is left in the world folder.

## 2026-10-20: ST-1, the measuring core

Headless: 9 GameTests pass (twice running), five of them new: the average, worst and slow share of a set of tick times (50 ms exactly is not slow); a 40-tick measurement counts 37 to 41 ticks, gives the overworld its own tick times and shows its loaded chunks and the memory; a chunk generated far out is counted as new and a chunk already loaded is not; a timed measurement ends by itself after its seconds and its chat summary starts "Measured" and ends with "Whole server:"; the CSV goes to `config/bsp_server_stress_test/reports/` with the header, a row for the whole server and one per dimension, every row the same width.

On a test server (Mohist above all: the mod widens one vanilla field to count ticking block entities, and Forge applies that change when the server starts):
1. `/stress measure 10`. "Measuring the server for 10 seconds..." at once; about 10 seconds later, lines like "Measured 10.0 s: 200 ticks, 20.0 ticks a second. Average tick 4.2 ms, worst 31.0 ms, 0.0% of ticks over 50 ms.", up to five dimension lines (the busiest first), "Whole server: ... chunks loaded, ... entities, ... ticking block entities, ... players, ... new chunks generated, memory ... of ... MB." and "Figures written to config/bsp_server_stress_test/reports/<date> measure.csv".
2. Compare with `/spark tps` run at the same time: the average tick (MSPT) should be close to spark's figure for the same stretch.
3. Run `/stress measure 30` again while the first is running: "A measurement is already running; it ends in ... s."
4. Fly fast into land nobody has visited during a measurement: "new chunks generated" is above 0. Stand still in old land: it is 0.
5. Open the CSV in a spreadsheet: one "all" row and a row for every dimension; `over_50ms_percent` is filled on the "all" row only.
6. Nothing new in the world folder; the CSV is the only new file.

## 2026-10-20: ST-2, the run engine

Headless: 16 GameTests pass (twice running), seven of them new: patterns choose options and leaving out wins; steps and ceilings come from the profile and the settings cap them; a run of the test load (1 ms a step, ceiling 2, a step a second) steps 0, 1, 2 on its own, ends "finished: every option reached its ceiling", has measured three steps, writes its CSV and leaves the test load at 0; a run stops when the stop-window average is 50.5 ms against a 50 ms limit (not at 49.9), at once on a tick longer than the emergency time (not at exactly it), and on stop, releasing the load each time; stepping up and down by hand, holding (no step on its own however long) and stepping on its own again, ending past the ceiling; the real-player guard (refused, or confirmed only by the same start within 3 s; an operator at level 4 is not a real player); the starter profile files and a saved profile read back the same.

On a test server, as a level 4 operator:
1. After the first start, `config/bsp_server_stress_test/profiles/` holds `everything.json`, `pre_generated.json`, `players_only.json`, `machines_only.json`, `test_load.json`. `/stress profile list` names them; `/stress profile show test_load` shows "Includes test.*; leaves out nothing", the timing, and "test.spin: step 5, ceiling 60".
2. Make the test quick: `/stress profile timing test_load 15 3`. Then `/stress start test_load`. Chat: "Run 'test_load' started with 1 option(s)...", "Step 0: nothing added yet...". Every 15 s: "Step N measured: ... ticks a second, average tick ... ms ..." and "Step N+1: test load: 5N ms wasted every tick". `/spark tps` shows MSPT climbing by about 5 ms a step.
3. When the average tick passes 50 ms (around step 10, perhaps sooner on a busy server), within about 20 s: "Run 'test_load' stopped: the average tick over the last 20 s was ... ms, past the limit of 50 ms.", "Everything the run made has been released." and "Figures for every step written to config/bsp_server_stress_test/reports/<date> run test_load.csv". MSPT drops back at once.
4. Start it again; `/stress ramp hold`: the run stays on its step (`/stress status` says "held" and the seconds keep counting). `/stress ramp up` and `/stress ramp down` change the step at once. `/stress ramp auto`: it steps on its own again. `/stress stop`: "Run 'test_load' stopped by <your name>."
5. `/stress start everything`: "The profile 'everything' includes no option this server has." (true until the real options exist).
6. With a second, non-operator account online, `/stress start test_load`: "Not started: 1 real player(s) online (<name>)...". Set `allowRunsWithPlayers = true` in `settings.toml`, restart: the first start asks to repeat within 3 seconds, the second starts it.
7. Stop the server during a run: the log shows "Run 'test_load' stopped because the server is stopping."
8. `/stress profile new quick test_load`, `/stress profile limit quick 30`, `/stress profile step quick test.spin 10`: each says "Profile 'quick' saved." and shows the result; the file `profiles/quick.json` holds the changes.

## 2026-10-20: ST-3, the options catalogue

Headless: 23 GameTests pass (twice running), seven of them new, on a test server with Minecraft and BSP-Core only: hoppers, furnaces and beacons are ticking blocks, chests, barrels and stone are not, a wall sign is not listed apart from the sign, TNT is an explosive and not a ticking block; the moving piston is left out for having no item, the Wither for being in the leave-out list, and BSP-Core's Shatter Totem is never offered; zombies are mobs, items, arrows and players are not; the explosive guess takes tnt, deepslate_bomb, nuke, NukeBlock, BlockExplosive and ThermonuclearbombBlock, and not explosive_crafter, nuclear_reactor_core, battery_charger or bomb_shelter_door; every Minecraft and BSP-Core entry has an English name (Hopper, Zombie, Tetrium Crucible); `test.spin` is ready and `bots.stand` "comes with ST-9"; `pre_generated` covers held chunks and hoppers but not fresh chunks, flying bots or the test load, and `machines_only` only offered ticking blocks; `options.txt` is written in the mod's folder.

On a test server with the whole pack (the first real look at the catalogue):
1. Start the server: it starts as before (the catalogue reads every mod's language file once; note whether start-up takes noticeably longer). `config/bsp_server_stress_test/options.txt` appears.
2. `/stress options`: "This server can be tested with ... ticking blocks, ... mobs and ... explosives from ... mods, and 17 general options.", "Ready now: 1. Coming with a later task: .... Left out: ....", "Most options: ..." (expect Mekanism, Create, AE2 and Thermal near the top), and the file's place.
3. `/stress options mekanism` (and `create`, `ae2`, `bsp_core`): up to 40 lines like "machines.mekanism.digital_miner  Digital Miner (ticking block): comes with ST-11". Names should read in English; tell me any that show as `block.something...` or an id.
4. Open `options.txt` and read the EXPLOSIVE entries: expect Nuclear Bombs, Ballistix and Blastcraft blocks, and TNT. Anything wrongly taken for an explosive (or missing) can be fixed with `catalogue.leaveOut`, and tell me so the guess can improve.
5. Look over the "left out" lines; add anything you never want placed (for example a mod's own world-changing block) to `catalogue.leaveOut`, restart or run `/stress options`, and see it move to left out.
6. `/stress profile show machines_only`: "On this server it covers ... options, 0 of them ready now."

## 2026-10-20: ST-4, the timers

Headless: 26 GameTests pass (twice running), three of them new: a hopper and a zombie placed in a test area are timed by what they are (at least 15 ticks each over 20), the hopper's chunk is among the timed chunks, both hooks ran, the timers' own cost is counted, and the summary names the hopper and gives "Busiest chunks: the overworld at x ..."; a report puts the busiest first and adds up by mod; the timers are on only while a session is open. The jar's reference map names both hooked methods and the production field name.

On a test server (Mohist above all: the two hooks are mixins that inject at the start and end of `LevelChunk$BoundTickingBlockEntity.tick` and `ServerLevel.tickNonPassenger`; if Mohist or another mod changes either, the hook is skipped with a warning in the log rather than stopping the server):
1. Start the server and look in the log for mixin warnings naming `mixins.bsp_server_stress_test.json`: there should be none.
2. `/stress measure 20` somewhere with machines and mobs. After the usual lines: "Busiest: <name> <ms> ms a tick (<count> of them); ..." with English names, "Busiest chunks: the overworld at x ..., z ..., <ms> ms a tick, mostly <name>; ...", and "The timers themselves took about ... ms a tick (... blocks and entities timed each tick)." No line saying a timer "did not run on this server".
3. `/tp` to the first busy chunk's coordinates: what is there should match "mostly <name>".
4. Compare with `/spark profiler` over the same stretch: the busiest block entities and entities should be in roughly the same order.
5. Open the `... measure timings.csv` beside the figures file: rows of `block`, `entity`, `chunk`, `mod` and one `timers` row; the shares of the tick add up to less than 100 (the rest of the tick is chunk loading, networking, redstone and everything else not timed).
6. During `/stress start test_load` each "Step N measured" line is followed by a "Busiest: ..." line.

## 2026-10-20: ST-5, the plain-English report

Headless: 32 GameTests pass (twice running), six of them new, on made-up measurements whose numbers are known: a three-step run's headline names the biggest rise (step 2, from 6.0 to 20.0 ms, +14.0 ms), the costliest thing with its share and its reason (Hopper, 15 %, "Hoppers check every tick..."), the busiest place with coordinates, that the server kept up, and the 80 % of the tick the timers do not see; the sections and their lines read as expected; a run that crosses 50 ms says where it fell behind, and one that starts above it says so; a single measurement has no steps; new chunks and nearly full memory get their own lines only when they happen; each thing gets its own reason or its kind's; the file goes beside the CSVs. The real test-load run also writes its report and the chat shows the headline and where it is.

On a test server:
1. `/stress profile timing test_load 15 3`, then `/stress start test_load` and let it stop on the limit. In chat after "Everything the run made has been released.": lines starting "- " (the headline), then "Figures for every step written to ..." and "Plain-English report: config/bsp_server_stress_test/reports/<date> run test_load.txt".
2. Open the `.txt`. It should read as plain English throughout: no `message.bsp_server_stress_test...` keys, no `NaN`, no `%s`. WHAT HURT MOST should say the biggest rise came at a test-load step, that the server fell behind at the step that passed 50 ms, and that most of the tick went to work the timers do not see (the test load is not a block or an entity).
3. `/stress measure 20` in a busy base: a `measure.txt` beside its CSVs, whose WORST THINGS and WORST PLACES match what is there; check the reasons read sensibly for the pack's machines ("It is a ticking block from Mekanism: ...").
4. Tell me any thing in the pack that deserves its own reason (a machine that is known to be heavy and why), and it goes in the list.

## 2026-10-20: ST-6, the analyser and the watch

Headless: 37 GameTests pass (twice running), five of them new: 20 armour stands and 15 dropped stacks of 3 in one chunk are found as a crowd of at least 35 (mostly armour stands) and a pile of at least 45 items in 15 stacks; `/forceload` of one far chunk adds exactly one "forced" ticket, and a Forge chunk-loading ticket is traced to the mod that holds it (named in the report as "chunk loaders placed as blocks (BSP-Server Stress Test)"), the spawn area shows; the analysis report carries the crowd, pile and holder sentences and its sections before ABOUT THESE FIGURES; a 1-second analysis runs and writes its report and CSVs; the watch starts only when on, idle, watched for the whole window, past its quiet time and over the limit. (The test server itself force-loads 177 chunks for its test areas: the analyser reported them truthfully.)

On a test server (and, since it only reads, on a live server too if you like):
1. `/stress analyse 20`: "Analysing the server for 20 seconds...", then lines starting "- " (the headline): the costliest thing, the busiest place, keeping up, the time the timers do not see, "The biggest crowd is ... entities in the overworld at x ..., z ..., mostly ...", "The biggest pile of dropped items is ...", "Besides players, chunks are held loaded by ...", and "Plain-English report: config/bsp_server_stress_test/reports/<date> analyse.txt".
2. Open `analyse.txt`: CROWDS, ITEM PILES, WHAT KEEPS CHUNKS LOADED and DIMENSIONS read in plain English. `/tp` to the biggest crowd and pile: they are there.
3. Place a chunk loader from a mod in the pack (or BSP-Core's totem Anchor upgrade) and analyse again: WHAT KEEPS CHUNKS LOADED names it, for example "chunk loaders placed as blocks (Mekanism): 9 ticket(s); the first at ..." (on Mohist the mod's name may come out as "a mod" if the server keeps it hidden; tell me).
4. `/stress watch on`: "The watch is on: ...", and `settings.toml` now has `enabled = true` under `[watch]`. To make it trip without slowing the server, stop the server, set `limitMs = 5` under `[watch]` (a modded server's average tick is usually above 5 ms) and start it again. About a minute after the start, every admin online sees "Started by the watch: the average tick was ... Analysing now." and, 30 seconds later, the headline and the report's place. It does not start another for 30 minutes, and never while a run is going. Set `limitMs` back to 50 afterwards.
5. `/stress watch off`: "The watch is off."

## 2026-10-20: ST-7, held chunks

Headless: 44 GameTests pass (twice running), seven of them new: the square grows outward ring by ring and is shared evenly between places; the ceiling counts every chunk the mod holds; 9 real chunks 40 chunks from a test area load and tick with exactly one of the mod's tickets each, the bar shows, stepping down to 1 lets the outer ones go, and letting go removes every ticket; places are kept in `places.json`, spawn follows the world spawn and is the default; both options are ready in the catalogue and fresh land is at least 29,000 blocks out inside the border; the bar's text and the five clickable keys; a run raises `chunks.hold` 4 then 8 and its stop lets every chunk go.

On a test server, as a level 4 operator:
1. `/stress chunks`: "Chunks held: 0 of 4,096 at most, each one ticking, shared between spawn (the overworld at x ..., z ...). Loaded on the server in all: ...", then "Change it: [-100] [-10] [+10] [+100] [let go]".
2. Click [+100]: the line updates to 100 held, and a purple bar appears at the top: "Chunks held 100 / 4,096 · loaded on the server ...". `/spark tps` and `/stress measure 10` show the cost. Click [+100] a few times, then [let go]: the bar disappears and the loaded count drops back within a few seconds.
3. Stand somewhere else, `/stress chunks place add base`, then `/stress chunks 200`: "shared between spawn ... ; base ..." only if you also added spawn (`/stress chunks place add spawn`); otherwise just base. Fly to base: the chunks around it are loaded with no player near (F3 or the bar's count).
4. `/stress chunks mode loaded`: "Held chunks are now loaded, not ticking."; a farm or machine in a held chunk stops working while you are far away. `mode ticking` brings it back.
5. `/stress analyse 10` with chunks held: WHAT KEEPS CHUNKS LOADED lists "this mod's own held chunks (/stress chunks and runs): ... ticket(s)".
6. A run: `/stress profile new chunks_test`, `/stress profile include chunks_test chunks.hold`, `/stress profile timing chunks_test 20 5`, `/stress start chunks_test`. Each step adds 256 chunks; the bar follows; the report names the steps "step N (... chunks held at spawn (each one ticking))". `/stress chunks 50` during the run: "A run is setting the chunks now." Stop: every chunk is let go.
7. Fresh land (test copy only: it adds new land to the world for good): `/stress profile include chunks_test chunks.generate_fresh`, start again: "new chunks generated" in each step's figures is above 0, and the report names the world generation mods.
8. Restart the server with chunks held: none are held after the restart, and `places.json` is the only file the chunks left.

## 2026-10-20: ST-9, bots

Headless: 48 GameTests pass (twice running), four of them new: names `SSTBot_0001` and offline-mode UUIDs; two bots log in through the player list (found by UUID, in their level, the login event reached its last listener), are not real players to the guard, after 20 ticks are still online, stand in loaded chunks, and their queued packets are encoded bytes; logging them out leaves no player, stats or advancement file and no name cache line; a run raises `bots.stand` 1 then 2 and its stop logs them out; a bot the server kicks is logged out and its files deleted. (In the test world BSP-Core's first-join totem is turned off and mob spawning is paused while test bots are online, so the tests leave nothing behind.)

Before testing on a server: turn Server Utilities' rules off and BSP-Core's first-join totem off (see the README, "On the test server"), and use a test copy.

On a test server (Mohist above all: bots skip the login handshake and join through the player list; LuckPerms and other Bukkit plugins may treat them differently from a real login):
1. `/stress bots 5`: "Bots online: 5 of 200 at most. Everyone online, bots included: 6." The tab list shows SSTBot_0001 to SSTBot_0005 and chat says each joined. They stand at spawn; you can see them.
2. Watch the server log for errors as they join (from any mod or plugin). Tell me what appears; on Mohist look for LuckPerms complaints about users not loaded.
3. `/stress measure 20` with 20 bots online: compare with no bots. `/stress analyse 20`: WHAT KEEPS CHUNKS LOADED says "players: 21 online...".
4. Wait two minutes: the bots are still online (no time-out kick).
5. `/kick SSTBot_0003`: it leaves, and `/stress bots` shows one fewer.
6. `/stress bots release`: they all leave. In the world folder, `playerdata/`, `stats/` and `advancements/` hold no file named after a bot's UUID, and `usercache.json` has no SSTBot line.
7. A run: `/stress profile new bots_test`, `/stress profile include bots_test bots.stand`, `/stress profile timing bots_test 30 5`, `/stress start bots_test`. Each step adds 10 bots; the report names "step N (... bots standing where new players arrive)". Stop: every bot leaves.
8. With a real player (not an operator) online, `/stress start bots_test` is refused naming only the real player, never a bot.

## 2026-10-20: ST-10, moving bots

Headless: 52 GameTests pass (twice running), four of them new: the spread, steps, headings and the shared bot ceiling are right and the three bot options are ready; three standing bots stand at least 43 blocks apart with the first at the place; two wandering bots move between 1 block and their walking distance in 40 ticks, stay inside their square and stay on the ground; a flying bot covers 36 to 44 blocks in 40 ticks at y 160, flying, over a loaded chunk.

On a test server (turn the rules and the first-join totem off first, as for ST-9):
1. `/stress chunks place add spawn` and stand somewhere else for `/stress chunks place add base`. `/stress bots 8 stand`: 4 bots at spawn and 4 at base, 48 blocks apart, on the ground. `/stress bots` shows "standing 8".
2. `/stress bots 6 wander`: six bots walk about at the places (follow one in spectator mode): they stay within about 100 blocks of the place and walk on the ground, like players exploring.
3. `/stress bots 3 fly`: three bots rise to y 160 and fly off in different directions at about 20 blocks a second. `/stress measure 30`: "new chunks generated" is well above 0 and climbs while they fly (test copy only: that land stays). `/stress analyse 10` shows them in WHAT KEEPS CHUNKS LOADED as players.
4. `/stress bots release`: all leave; their files are deleted.
5. A run with movers: `/stress profile new move_test`, `include move_test bots.wander`, `include move_test bots.fly_out`, `timing move_test 30 5`, `/stress start move_test`; each step adds 10 wandering and 5 flying bots; the report names both.

## 2026-10-20: ST-11, idle machines and cleanup

Headless: 56 GameTests pass (twice running), four of them new: the grid fills rows then layers with no shared cell; hoppers, furnaces and BSP-Core's Tetrium Crucible have options, the Shatter Totem and the moving piston do not; 5 real hoppers are placed with their block entities over held chunks and listed in `placed.json`, stepping down to 2 removes the last 3, letting go removes all, the record and the held chunks; a stone put in the first cell is never replaced; 3 hoppers left by a "stopped server" (memory forgotten, record kept) are found and removed by cleanup.

Before testing on a server: in the world's `serverconfig/forge-server.toml` set `removeErroringBlockEntities = true` (a machine placed on its own may crash when it ticks if its mod expects it inside a bigger structure; with this, Forge removes that block instead of stopping the server, and the log names it: tell me which ones).

On a test server:
1. `/stress options mekanism`: the machine lines now say "ready".
2. A small run: `/stress profile new mach_test`, `include mach_test machines.minecraft.*`, `timing mach_test 20 5`, `/stress start mach_test`. Fly up 20 blocks above spawn: a grid of furnaces, hoppers, beacons, signs and the rest, 2 blocks apart, growing by 5 of each per step. The report's WORST THINGS names the costliest.
3. Then a mod: `/stress profile new mek_test`, `include mek_test machines.mekanism.*`, `timing mek_test 20 5`, start. Watch the log for errors from Mekanism blocks that do not like standing alone.
4. During a run, `placed.json` grows in `config/bsp_server_stress_test/`; `/stress stop`: every placed block is gone and the file with it.
5. Start a run, wait a step, then stop the server from the console (not `/stress stop`). Start it again: the log says "... blocks placed by an earlier run are still in the world ...", and `/stress` says the same. `/stress cleanup`: "Cleaned up: N placed blocks removed, ...". The grid is gone.
6. Put a block of your own into the grid's space before a run (20 above spawn): the run places around it and never removes it.

## 2026-10-21: ST-12, crowds of mobs, items and orbs

Headless: 62 GameTests pass (twice running), six of them new: zombie, cow, item and orb options are ready and the Wither and the Ender Dragon have none; 3 real zombies are spawned alive, tagged, persistent and over held chunks, stepping down to 1 removes the last two, letting go removes all and the held chunks; 10 items and 10 orbs are still 10 each after 25 ticks (none merged); a zombie carrying the tag but not spawned by this server is refused entry; cleanup takes the spawned zombies and a loose tagged one; a bot cannot be hurt.

On a test server (not on peaceful):
1. `/stress profile new crowd_test`, `include crowd_test entities.minecraft.cow`, `include crowd_test entities.items`, `timing crowd_test 20 5`, `/stress start crowd_test`. At spawn: a growing herd of cows packed one per block, and a carpet of cobblestone items that nobody can pick up. The report's WORST THINGS names Cow and Item with their reasons ("Mobs think every tick...", "Dropped items look every tick...").
2. A hostile run with bots: include `entities.minecraft.zombie` and `bots.wander`. Zombies chase the bots; the bots never die.
3. `/stress analyse 10` during a crowd run: CROWDS and ITEM PILES point at spawn.
4. Stop the run: every cow, item and zombie is gone.
5. Start a crowd run, then stop the server from the console mid-run. Start it again and go to spawn: none of the run's mobs or items come back (they are refused as their chunks load). `/stress cleanup` reports any still loaded.

## 2026-10-21: ST-13, explosions

Headless: 67 GameTests pass (twice running), five of them new: the vanilla, TNT-chain and TNT-block options are ready and the blast timing is right; a kept explosion leaves a dirt block next to it and a breaking one does not; a cluster of 8 lit TNT with fuses of 40 to 59 ticks, close together; a TNT block placed with its redstone block goes off at once and the redstone block is gone after 4 ticks, while a bone block (no answer to redstone) is taken away after ten seconds and counted as a dud; switching vanilla explosions on holds the blast area and switching off frees it.

On a test copy (the blast area is 300 blocks east of spawn, or of each place: the land there is blown apart for good):
1. `/stress profile new boom_test`, `include boom_test explosions.vanilla`, `timing boom_test 30 5`, `/stress start boom_test`. Fly 300 blocks east of spawn: every 5 seconds an explosion, every other one leaving the land whole (it still costs the work of finding what would break), the others making craters, growing by 8 power each step. Expect the run to stop on the tick limit somewhere past power 64; the report shows each step's tick figures.
2. `include boom_test explosions.tnt_chain`: clusters of lit TNT going off together.
3. The pack's explosives: `/stress options ballistix` (and `nukes`, `blastcraft`) lists them; include one at a time (`include boom_test explosions.ballistix.*` for example). Watch the blast area: which go off with a redstone block on top and which are taken away as duds; the server log says "N explosives did not go off with a redstone block on them..." at the end. Tell me which mods' explosives need another way to be set off.
4. Nuclear Bombs' bigger bombs may be very heavy: try one with `ceiling boom_test explosions.nukes.nuke 1` first.
5. `/stress stop` mid-run: no more blasts, the blast area is let go, and any redstone block or waiting explosive is taken away.

## 2026-10-21: ST-14, the world options

Headless: 72 GameTests pass (twice running), five of them new and one changed: the seven world options are ready (every general option is now), the contraptions have the right number of blocks, every fourth basin is lava, saves a minute turn into the right ticks; a redstone clock's lamp switches on and off and a hopper ring passes its items round (still 256), and removing both leaves no block and no dropped item; a water basin's source goes and comes back, water reaches the basin's corner, the lava basin holds lava, a light swaps glowstone for stone, the sand lands, and removing them leaves no block, fluid, falling block or item; a clock and a basin left by a "stopped server" (memory forgotten, record kept) are removed by cleanup, and a random tick speed left raised by it is put back from `random_ticks.json`; held chunks are marked as changed and a save comes within 130 ticks at 12 a minute.

On a test server (the contraptions are 60 blocks above the ground at spawn, or each place: 20 for the machine grid plus 40):
1. `/stress options`: "Coming with a later task: 0"; the general options are all ready.
2. `/stress profile new world_test`, `include world_test world.redstone_clocks`, `include world_test world.hoppers`, `timing world_test 20 5`, `/stress start world_test`. Fly up 60 blocks above spawn: rows of flickering lamps with dust beside them, and squares of four hoppers; open one: cobblestone passing through. The report's WORST THINGS names Hopper; the clocks' cost is in "the time the timers do not see".
3. `world.fluids`: glass basins filling and draining every 3 seconds, every fourth one lava. Check nothing near them catches fire.
4. `world.lighting` and `world.falling_blocks`: blocks blinking between glowstone and stone; sand falling 10 blocks every 2 seconds.
5. `/stress stop` mid-run: every contraption is gone, with no water, lava, sand, items or falling sand left in the air, and `placed.json` is gone.
6. `world.random_ticks`: `/gamerule randomTickSpeed` during the run shows the step's value (3, 6, 9...); after `/stress stop` it is back to what it was. Then start the run again and stop the server from the console mid-run: `random_ticks.json` is in the mod's folder; at the next start the log says "The randomTickSpeed game rule is back to ..." and the file is gone.
7. `world.save`: with chunks held (`include world_test chunks.hold`), the run's later steps save up to 12 times a minute; the tick figures show the cost, and spark's profiler shows the saving. A world with `/save-off` is not saved by it.
8. Put a block of your own where a contraption would go (60 above spawn): that cell is skipped.

## 2026-10-21: ST-15, Create's working setups

Headless, with Create 6.0.8 (and its Flywheel, Ponder, Registrate and MixinExtras) loaded from `libs/` (`python3 tools/prepare_libs.py`): 77 GameTests pass (twice running), five of them new: the four Create setups are ready options of the kind "working setup" and `setups_only` covers them; a press line makes iron sheets, millstones make gravel, a fan through a campfire makes cooked beef on its depots, and a belt carries cobblestone to its far end, each within 400 ticks of being placed; removing each leaves no block and no dropped item.

On a test server with the full pack (the setups are 100 blocks above the ground at spawn, or each place: 20 for the machine grid, 40 more for the contraptions, 40 more for the setups):
1. `/stress options create`: four "working setup" lines, ready.
2. `/stress start setups_only` (or a profile of your own with `include <name> setups.create.press_line` and `timing <name> 20 5`). Fly up 100 blocks above spawn: rows of creative motors turning presses over depots, millstones, fans blowing through campfires over depots, and belts. Watch: iron sheets on the depots, beef turning cooked, cobblestone riding the belts.
3. The report's WORST THINGS should name Create's block entities (press, millstone, fan, belt, depot); the rest of their cost is in "the time the timers do not see".
4. `/stress stop`: every setup is gone, with no items on the ground under them and no belt pieces or shafts left.
5. Stop the server from the console mid-run, start it, `/stress cleanup`: the setups go too. Tell me if a belt left shafts or items behind.
6. Watch the server log for errors from Create while setups are placed and removed (above all on Mohist), and tell me what appears.

## 2026-10-21: ST-16, Create's moving contraptions

Headless, with Create loaded from `libs/`: 79 GameTests pass (twice running), two of them new: a bearing rotor's plate becomes one moving piece carrying the mod's tag and owned by this server, and removing the setup leaves no block, no moving piece and no dropped item; a piston line pushes its face to the far end and pulls it back (seen over 500 ticks), and removing it leaves nothing either. The first run showed that Create's plain mechanical piston pushes but does not pull back: the setup uses the sticky one.

On a test server with the full pack (100 blocks above the ground at spawn or each place):
1. `/stress options create`: six "working setup" lines, ready, among them Bearing rotor and Piston line.
2. A profile with `include <name> setups.create.bearing_rotor` and `include <name> setups.create.piston_line`, `timing <name> 20 5`, then start it. Above spawn: green plates spinning on bearings, and slime faces pushed out 4 blocks and back every 4 seconds.
3. The report's WORST THINGS should name Create's contraption entity; a moving piece's cost is mostly its collision checks.
4. `/stress stop` mid-movement: every rotor, piston, pole, head and slime block is gone, nothing is left on the ground, and no stray slime blocks stand anywhere around.
5. Stop the server from the console while rotors spin. Start it again and look: the plates do not come back (their moving pieces are refused); `/stress cleanup` removes the bearings, motors and the rest. Tell me if any slime blocks were left standing.
6. Bigger pieces: set `setups.rotorSide = 9` and `setups.pistonSide = 5` in `settings.toml`, restart, and run again.

## 2026-10-21: the remaining families (Mekanism, Refined Storage, AE2, Thermal and Powah, EnderIO, Industrial Foregoing, Botania) and Create trains

Headless, with every family loaded from `libs/` (`python3 tools/prepare_libs.py`): 87 GameTests pass (twice running), eight of them new: each setup makes its product within 400 ticks of being placed (Mekanism: iron dust, gravel and cooked beef; Refined Storage: cobblestone reaching the far chest through the network; AE2: the ME chest's cell holding items; Thermal: gravel, cooked beef and planks; EnderIO: gravel and cooked beef; Industrial Foregoing: cooked beef; Botania: cooked beef from furnaces the Exoflame heats), and removing it leaves no block and no dropped item; a train line's train reaches station B and comes back to A within 1,200 ticks, and removing it leaves no block, no carriage and no conductor. What the runs showed and the setups now do: a creative energy cube placed as a block starts empty (charged from its saved data); Mekanism machines take items only from some sides; an EnderIO machine takes no power without a capacitor in its own slot; Titanium's creative generator exists only in development; a Botania flower placed as a block binds to no pool; a Create bogey stands on the track one step from the station marker, a station must be in assembly mode before it, and out of it after, and a train runs its schedule only with a conductor seated between controls that face it.

On a test server with the full pack (setups 100 blocks above the ground at spawn or each place):
1. `/stress options mekanism` (and `refinedstorage`, `ae2`, `thermal`, `enderio`, `industrialforegoing`, `botania`, `create`): a "working setup" line each, ready (Create has seven).
2. `/stress start setups_only` with `timing setups_only 30 5`: every family's setups at once, five more each step. Fly up and look: each machine working (Mekanism and Thermal machines lit, AE2 and Refined Storage networks powered, the Exoflame's furnaces lit, trains shuttling).
3. The report's WORST THINGS: which family's block entities cost the most. Send me the report.
4. `/stress stop`: every setup gone, nothing on the ground, no trains left (`/c trains` or Create's train map shows none named "SST ...").
5. Stop the server from the console mid-run, start it, `/stress cleanup`: blocks gone and "N trains left by a Create train line were taken out of Create's records" if a train line was running.
6. Known world records the mods themselves keep (D4 cannot cover them): AE2 and Refined Storage keep network data, Create keeps its track graph and trains in the world's saved data. The setups leave nothing in AE2's cells or Refined Storage's storage, and remove their tracks and trains, but some empty records may stay in those mods' files on the throw-away world.

## 2026-10-21: bots leaving straight after joining (first trial on the test server)

Seen on the test server: every bot joined and left within a second ("lost connection: The bot's connection was closed."). Reproduced headless: a packet that fails while it is encoded for a bot closes the bot's connection, exactly as a real connection's would, and the mod finished the logout with its own generic line, hiding the cause. Now: such a packet is dropped for the bot and the log warns once per kind, naming the packet and the error; bots' connections give a loopback address; a bot disconnected all the same is logged with the reason the server or mod gave. 88 GameTests pass (twice running), one new: a packet broken while encoding (a custom payload over a released buffer) leaves the bot online and is counted as dropped.

On the test server:
1. Replace the jar, restart, and start the same run (`/stress start players30`).
2. The bots should stay on. In the log, look for `could not be encoded for bot`: each line names a packet some mod sends that a bot cannot take. Send me those lines (they show which mod expects a real client).
3. If bots still leave, look for `Stress test bot SSTBot_... was disconnected by the server or a mod:` followed by the reason, and send me those lines and any error just above them.

## 2026-10-21: bots leaving with "NoSuchElementException: splitter" (second trial)

Seen on the test server after the first fix: every bot left at once with "Internal Exception: java.util.NoSuchElementException: splitter". Code that hooks its own handler into every joining player's connection by name (plugins on Mohist, mods) did not find the names a real connection has: a bot's in-memory connection had them in another order, with the connection handler unnamed, and no `timeout` or `legacy_query`. Laying it out as a real connection showed the next layer headless: Forge's own join hook (`NetworkFilters`) then ran for bots and failed, because bots skip the login handshake that records what kind of client a player is. Now a bot's connection is laid out as a real server connection's and carries what a Forge client with every mod and channel of the server hands over at login (Forge's network version and connection data), and any error in it is logged once per kind and ignored for the bot. 89 GameTests pass (twice running), one new: a bot's connection has the real names in the real order, reads as a modded Forge client with the server's mod list, takes handlers hooked in before `packet_handler` and after `splitter`, and stays online through a `NoSuchElementException: splitter` fired into it.

On the test server:
1. Replace the jar, restart, and start the run again (`/stress start players30`).
2. The bots should stay on. In the log, look for `is ignored for bots` and `could not be encoded for bot`: each line names something that would have thrown a bot off, with its stack trace (the plugin or mod is in it). Send me those.
3. If bots still leave: the `was disconnected by the server or a mod:` lines and anything just above them.

## 2026-10-21: memory with the mod installed and idle

Reported: about 18 GB used with the mod installed and no run going, against 8.15 GB without. spark's health reports showed the same heap in use with and without the mod (3.3 to 3.5 GB of 27.7 GB); what differed was the young generation the garbage collector had reserved (Eden 6.2 GB, survivors full) and CPU, and a report without the mod taken just after a restart showed the same (Eden 6.3 GB, survivors full, 23% CPU, a 169 ms tick): Java's collector grows its reserved space during a 300-mod server's startup and is slow to hand it back, whatever mods are installed. Not the mod.

Checked headless all the same: everything the mod does while idle returns at once or works in fixed-size buffers, the catalogue built at start keeps about 1 MB (17 mods, 123 ms), and one new GameTest measures what an idle block-entity tick allocates with the timing hooks in place: 1.4 and -2.2 bytes per tick on two runs (noise around zero; the hook's callback object is optimised away by the compiler), failing above 8. 90 GameTests pass.

To compare memory fairly on the server: take both readings at the same time after a restart (10 minutes in), with `/spark health --memory`, and read "Memory usage" (the heap in use), not the host's figure (what Java has reserved). For a steady figure on the host, give Java the same `-Xms` as `-Xmx`, so it reserves all of it from the start.

## 2026-10-21: bots spread far apart, on generated land only

Owner feedback from the third trial (bots now stay on): the bots stood close together; real players may be thousands of blocks apart. Now each standing or wandering bot has its own spot, `bots.spacing` apart (1,000 by default, up to 100,000) on a square spiral out from each place, and only spots whose land the bot would see is already generated are used (read from the chunks' saved status, loading nothing), so no new land is made. A wandering bot wanders the square around its own spot, on generated land only. 92 GameTests pass (twice running), three new or rewritten: standing bots each on their own spot 32 apart, every one on generated land; the spot search skips land that is not generated, gives each spot once and goes on from where it stopped; at a spacing of 100,000 bots share the generated spot near the place and no land 100,000 blocks out is made or even loaded.

Note: the log line "logged in with entity id ... at (...)" is where a bot joins (the world spawn); it is moved to its spot right after.

On the test server:
1. In `config/bsp_server_stress_test/settings.toml`, `[bots]`, set `spacing` to how far apart you want them (an existing file keeps its old value, 48 or 208). For 30 bots: at 1,000 they reach about 3,000 blocks out from the place; at 2,000, about 6,000.
2. Restart, start the run, and watch the log: "Place 'spawn' has only N spots ... whose land is already generated" means the pre-generated land holds N spots at that spacing and the other bots share them. Use a smaller spacing, pre-generate more land, or add places (`/stress chunks place add <name>` where you stand) in other generated areas: bots are shared between the places in turn.
3. Check where they are: `/tp SSTBot_0005` and the like, or spark's player list. The report's "new chunks generated" should read 0.

## 2026-10-21: /stress set, one option by hand in a running test

`/stress set <option> <amount>` (D53 to D57). 93 GameTests pass (twice running), one new: with made-up options in a run, setting one by hand ends the step and begins one marked by hand; one set above its ceiling holds there at the next step while the others step on; an option not in the run is added and steps on; 0 takes one out and lets go of it; an amount over the hard limit is cut to it; ending the run lets go of the added one too.

On the test server, during a run:
1. `/stress set setups.create.millstones 10`: chat shows a new step "Step N (by hand: setups.create.millstones to 10)", and ten millstone setups appear in the setups grid. Chat also shows the profile commands to keep it.
2. `/stress set setups.mekanism.machine_line 50` (above its ceiling of 30): the next steps leave it at 50.
3. `/stress set setups.create.millstones 0`: they are removed.
4. `/stress stop`: everything goes, the added ones too. The report lists the by-hand steps with their figures.
5. With no run going, `/stress set ...` says it works only during a test.

## 2026-10-21: the report counts ticks a second, not only tick length

From the owner's measurement report of 14:32: "The server kept up with 20 ticks a second: average tick 16.0 ms, 11.7 ticks a second". The report judged by the average tick only. Now a server is behind below 19 measured ticks a second too, and a short-tick, few-ticks server is reported with the time that went between ticks (1,000 ms less ticks a second times the average tick) and the new land made. 94 GameTests pass (twice running), one new: that exact measurement (16 ms, 11.7 ticks a second, 645 new chunks) reads "behind ... About 813 ms of each second went to work done between ticks ... (645 new chunks were generated)"; a run falling behind on ticks a second at step 1, and one behind from the start, say so the same way.

On the test server: `/stress measure 60` while flying bots or a player make new land: the report's line 3 should say "behind" with the milliseconds between ticks and the new chunks; with nothing moving, "kept up".

## 2026-10-23: BSP-Core forgets bots' totems (ST-C1, BSP-Core 1.4.5)

Built against BSP-Core 1.4.5. A bot leaving has BSP-Core forget its totems after BSP-Core's own logout (which places carried totems); `/stress cleanup` has it forget every bot name up to `limits.maxBots`. 95 GameTests pass (twice running), one new: with BSP-Core's first-join grant on for the test, a bot joins with a totem and, once it has left, BSP-Core was asked to forget it, it has no placed totem and no totem lies dropped near it; the grant is put back after.

On the test server (BSP-Core 1.4.5 or newer installed):
1. Set `first_join.grantTotemOnFirstJoin = true` again in BSP-Core's server config if you had turned it off for bots.
2. `/stress bots 3`: each bot gets a totem. `/stress bots release`: no totems are left placed where they stood, and `/bsp totem` lookups show none for `SSTBot_0001` to `0003`.
3. Stop the server from the console while bots are on, start it, `/stress cleanup`: "BSP-Core forgot the totems of the bot names SSTBot_0001 to SSTBot_0200: N totems taken ...".
