# BSP-Server Stress Test

A test tool for server admins on the Build Secure Protect (BSP) network: Minecraft 1.20.1, Forge 47.4.x (Mohist on the servers). MIT.

It will fake load on one server (players, loaded chunks, machines from the pack's mods doing real work, chunk generation, large explosions and more), raise it step by step, and write in plain English what cost the server the most and why. It can also look at a running world, with no test going, and point at the chunks, blocks and entities that cost the most.

**Use it on a test copy of a server only.** It is not part of the player modpack. Players can join a server that has it without installing it.

The mod is in development: it measures and analyses a server, runs step-by-step tests with profiles, and can so far load chunks (held, or freshly generated), log in bots that stand, wander or fly out, place every mod's ticking blocks idle, spawn crowds of any mob, dropped items and orbs, set off vanilla and modded explosions, run small redstone clocks, hopper rings, fluid basins, switching lights and falling sand, raise the random tick speed, save the world over and over, run the pack's machines for real (Create's presses, millstones, fan, belt, rotor, piston and a shuttling train; Mekanism, Thermal, EnderIO and Industrial Foregoing machines; Refined Storage and AE2 networks moving items; a Botania flower heating furnaces), and make a made-up test load.

## What it measures

Each measurement (a step of a run, or `/stress measure`) records the server's own tick times, overall and per dimension, the counts (chunks, entities, ticking block entities, players, new chunks, memory), and how long every block entity and entity took to tick, added up by block or entity type, by chunk and by mod. The timers cost two clock reads per tick of each block and entity while measuring, and nothing otherwise; each summary says how much they took.

## The report

Every run and every `/stress measure` writes three files in `config/bsp_server_stress_test/reports/` with the same name: the plain-English report (`.txt`), the tick figures and counts (`.csv`), and where the tick time went (`timings.csv`). The report opens with what hurt the server most and why, then goes step by step, lists the worst places and things with their reasons, and each mod's share.

## Everything in one folder

Every file the mod writes is in `config/bsp_server_stress_test/` beside the server's other configs: its settings (`settings.toml`), the list of options (`options.txt`), the places for held chunks (`places.json`), the record of blocks a run placed (`placed.json`, gone when they are removed), the world's own random tick speed while a run has changed it (`random_ticks.json`), the profiles (`profiles/`) and the reports (`reports/`). Held chunks are never saved: a restart lets them go. Nothing goes in the world folder, with one exception: a run that includes `world.random_ticks` changes the `randomTickSpeed` game rule, which the game keeps in the world's `level.dat`; it is put back when the run ends, at the next start after a server stopped mid-run, or by `/stress cleanup`. To remove the mod, delete its jar from `mods/` and that folder.

It uses no database and no Redis, and talks to no other server.

## Commands

- `/stress`: what the mod is set to and where its files are (operators at level 4 and BSP-Core admins).
- `/stress measure [seconds]`: measures the server as it is for 5 to 600 seconds (30 by default), then gives the tick figures, the busiest dimensions and the counts in chat, and writes them all to a CSV file in `config/bsp_server_stress_test/reports/`.
- `/stress options [mod]`: what this server can be tested with, built from the mods it loaded: ticking blocks, mobs, explosives and the general options, with how many are ready now. With a mod id, that mod's options and their ids. The full list is in `config/bsp_server_stress_test/options.txt`.
- `/stress analyse [seconds]`: looks at the server as it is for 5 to 600 seconds (30 by default) without adding anything, then reports what costs most and where: the busiest blocks, entities and chunks, crowds of entities, piles of dropped items, what keeps chunks loaded, and each dimension. Safe on any server: it only reads.
- `/stress watch [on | off]`: when on, an analysis starts by itself whenever the average tick stays above 50 ms for a minute (then it waits 30 minutes).
- `/stress bots [number] [stand | wander | fly]`: logs in that many bots of a kind (`SSTBot_0001` and on) as real players, 200 at most in all: standing spread out at the chunk places (or spawn), wandering inside a 200-block square there, or flying out in straight lines through new land. `/stress bots release` logs them all out. Their player files are deleted when they leave.
- `/stress chunks [number]`: holds that many chunks loaded at the saved places, or at spawn (4,096 at most), with clickable keys to step it and a bar at the top of the screen with the live count. `/stress chunks add <change>`, `release`, `mode ticking | loaded`, `place add | remove <name>`, `place list`.
- `/stress cleanup`: removes every block a run placed (even after a server stopped mid-run) and every mob, item and orb it spawned, logs every bot out, lets every held chunk go and puts back a random tick speed a stopped run left changed.
- `/stress start <profile>`: starts a run. Step 0 measures the server as it is; each step after adds more of every option the profile includes, until every option reaches its ceiling or the average tick passes the profile's limit. Every step's figures go to a CSV in `reports/` when it ends.
- `/stress stop`: ends the run and removes everything it made. `/stress status`: where the run is.
- `/stress ramp up | down | hold | auto`: one step up or down now, stay on this step, or step on its own again.
- `/stress set <option> <amount>`: one option of the running test to that amount now (added if the test does not have it, taken out at 0). The change starts a step of its own, marked "by hand" in the report; for this test only.
- `/stress profile list | show <name> | new <name> [copy of]`, and to edit one: `include | exclude | forget <name> <pattern>`, `step | ceiling <name> <option> <amount>`, `timing <name> <stepSeconds> <settleSeconds>`, `limit <name> <ms>`.

## Profiles

A profile is a file in `config/bsp_server_stress_test/profiles/`, editable by hand or with `/stress profile`:

- `include` and `exclude`: patterns of options. `*` is every option, `bots.*` every option in a category, and anything else one option. Leaving out wins over including.
- `stepSeconds` (60): how long each step lasts. `settleSeconds` (10): how long a step runs before it is measured.
- `stopAverageMs` (50): the run stops when the average tick passes this.
- `options`: an option's own `step` and `ceiling` where the defaults are not wanted.

Starters: `everything`, `pre_generated` (everything but new land), `players_only`, `machines_only`, `setups_only` (the working setups), `test_load` (the made-up test load, to try runs). They are written when missing and never over an edited file.

## On the test server

Bots are real players to every mod. Before a run with bots:

- BSP-Server Utilities' rules popup holds every new player until they agree, so bots would stand frozen: turn it off with `rules.enabled = false` (a request to that mod is open).
- BSP-Core gives every new player a Shatter Totem and places carried totems in the world when they leave. With BSP-Core 1.4.5 or newer the mod has BSP-Core forget a bot's totems when it leaves and in `/stress cleanup`, so nothing stays; with an older BSP-Core, set `first_join.grantTotemOnFirstJoin = false` in its server config.
- Set the BSP mods to local storage (not MySQL) on the test server.
- Hostile mobs need a difficulty other than peaceful.
- Machines placed on their own (not built into a working setup) can crash the server when they tick if a mod expects them to be part of something bigger. Set `removeErroringBlockEntities = true` in the world's `serverconfig/forge-server.toml` on the test server, so Forge removes such a block instead of stopping the server.

## Requirements

- Minecraft 1.20.1 with Forge 47.4.10 or newer, or Mohist 1.20.1.
- Optional: BSP-Core 1.4.0 or newer (its admins may use `/stress`); 1.4.5 or newer to forget bots' totems.

## Settings

`config/bsp_server_stress_test/settings.toml`:

- `safety.allowRunsWithPlayers` (false): when false, a run will not start while a real player is online (bots, and people allowed to use `/stress`, do not count). When true, `/stress start` must be given twice within 3 seconds while real players are on.
- `safety.emergencyTickSeconds` (10): one tick longer than this stops the run at once.
- `safety.stopWindowSeconds` (20): the stretch the average tick is taken over for a profile's limit.
- `catalogue.leaveOut`: blocks and mobs never offered as options, by id (`minecraft:wither`) or a whole mod (`somemod:*`). By default: command and structure blocks, BSP-Core's totems and ghost block, the Ender Dragon and the Wither.
- `bots.*`: `spacing` (1,000 blocks between bots' spots, up to 100,000; only spots on already generated land are used, so standing and wandering bots never make new land), `wanderSquare` (200, around each bot's own spot), `walkBlocksPerSecond` (4.3), `flyBlocksPerSecond` (20), `flyY` (160).
- `explosions.*`: `everySeconds` (5), `distance` (300 blocks east of each place: the blast area), `spacing` (64).
- `limits.maxEntities` (2,000 per option) and `limits.maxSpawnedTotal` (10,000): mobs, items and orbs.
- `machines.*`: `gridSide` (64), `spacing` (2), `heightAboveGround` (20); `limits.maxPlacedTotal` (20,000).
- `world.*`: where the world options' contraptions go: `gridSide` (16), `spacing` (8), `aboveMachines` (40 blocks above the machine grid).
- `setups.*`: where the working setups go: `gridSide` (8), `spacing` (16), `aboveWorld` (40 blocks above the contraptions); the size of Create's moving pieces: `rotorSide` (5) and `pistonSide` (3), read at server start.
- `chunks.ticking` (true): held chunks tick, as if a player stood there; false: only loaded.
- `watch.*`: `enabled` (false; `/stress watch on|off` sets it), `limitMs` (50), `overSeconds` (60), `analyseSeconds` (30), `quietMinutes` (30).
- `limits.*`: hard ceilings no profile goes past: `maxBots` 200, `maxChunks` 4096, `maxExplosionPower` 128, `maxMachines` 2000 (also contraptions per world option), `maxTestMilliseconds` 200, `maxRandomTickSpeed` 30, `maxSavesPerMinute` 12.

## Building

```
./gradlew build
```

The jar is written to `build/libs/`. BSP-Core is compiled against from its sibling checkout (`../BSP-Core/build/libs`): build BSP-Core first.

The working setups are tested against the pack's own mods: Create, Mekanism, Refined Storage, AE2, Thermal with Powah, EnderIO, Industrial Foregoing and Botania, and the mods they need. Copy them into `libs/` (never committed; the built mod does not depend on them) before the tests:

```
python3 tools/prepare_libs.py
```

By default it reads `../BSP-Servers/BSP-Spawn (Current)/mods`; give another mods folder as its argument.

Headless tests:

```
./gradlew runGameTestServer
```

## Documentation

- `docs/API.md`: what other mods may call.
- `docs/TESTING.md`: in-game checks, a section per task.
- `CHANGELOG.md`: changes per version.
