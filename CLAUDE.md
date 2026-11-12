# SeanCraft

Fabric 1.21.1 mod. Package `com.cavmkii.seancraft`, mod id `seancraft`. The GitHub repo is cavmkii/GoblinCraft (it redirects to SeanCraft).

## Rules
- The user sets versions in `gradle.properties` (`mod_version`). Don't bump it yourself.
- "merge" means: open a PR from the working branch to main, merge it with the merge method "merge", then run `git fetch origin main && git merge --ff-only origin/main` and push. Force pushes are blocked.
- Don't put model names in commits, code or PRs.
- After a build, hand over `build/libs/seancraft-<version>.jar`.
- The README is in the user's casual voice. Update it with each feature, and fix any stale lines you notice.
- The user is a skeptical expert. Say what you tested in game, what you didn't, and where you deviated from a spec. Keep it short and plain.

## Layout
- `client/tarkov/TarkovScreen.java`: the inventory screen (INVENTORY / STATUS / CHARACTER).
  - INVENTORY is a 480x270 virtual canvas laid out to UI-SPEC inventory v5: equipment (EQ), crafting (CR), player inventory (PI), stash (ST) and the hotbar frame (HB). The zone rectangles are constants at the top of the INVENTORY layout section.
  - Crafting is always on screen. RECIPES (`bookOpen`) draws the recipe book over the equipment zone. `Open(3)` (`CRAFT_MODE`) opens it with the book open, for a crafting table.
  - The station row sits at top right at a home base.
  - The player inventory is vanilla (no grids since v23): `Loc.hand(0-35)` is the inventory, hotbar 0-8.
- Names: the code still says hideout/Tarkov/Tetris; in game it's home base / Home Bench (`home_bench`). `Hideouts` is the home bases, `Camps` the camps, `Tarkov` the danger layer (distance bands, elites, hunting parties, safe ground via `Tarkov.safeGround`).
- `tetris/Tetris.java`: the server side of the inventory screen, stash and chests. Stash features are gated by Storehouse level (`Tetris.storehouse`, `Stash.containers`).
  - Server messages are `TetrisNet.Move`, `QuickMove`, `Action(n)` and `ContainerAction`. `Action(9)` is REFILL STACKS.
  - `Loc.container(i, -2, 0)` puts something away into container i; i = -1 means route it by the chest filters.
- `client/ui/Kit.java`: the GUI kit (sprites under `assets/seancraft/textures/gui/sprites`, palette constants).
- `tarkov/Stations.java` and `client/tarkov/StationTabs.java`: home base stations.
- `ecology/`: design doc 17, phases 1 and 2 in the simplified v23 form: only numbers players can see (abundance 0-1, fertility, pests, water pollution). `Model.step` eases each value toward its target in closed form; patches update daily and on player actions (`Ecology.update`); runoff goes to the stored downhill water patch; fishing via `EcoFishingMixin`. `Ecology` holds patch storage (`EcoData`, one saved file per dimension), the tick budget, sampling, crop hooks and the wildlife sampler; `Model` is the population and soil equations; `Quality` the 0-100 score; `Params` the tuning, overridable from `data/seancraft/ecology/params.json`. Debug with `/seancraft eco patch|step|set`.
  - Serene Seasons is a hard dependency but read by reflection (`Seasons`); it and GlitchCore are `modLocalRuntime` in dev.
- `food/Kitchen.java`: doc 11 cooking. Climate crops (`ClimateCrop`, gated by biome base temperature; `fertileFarmland` hook for mulch), forage on block break (scaled by patch forage), goat/hoglin/snow hare/ice fish drops, dish tiers (`DISHES`, read by `Food.def`), the fermenter crock (`FERMENT` component, converts in inventory) and the Travel Grill. Recipes are hideout patterns under `seancraft_patterns/kitchen/`, gated on `KITCHEN`; `"ingredients": [...]` makes a pattern any-order, `"ferment": seconds` wraps the result in a crock, `"grill": true` puts it on the Travel Grill.
- `food/Mulch.java` (fertile farmland, a per-level saved set) and `hazard/Mobility.java` (climbing stamina in jungles and above `Cfg.peakY`, fall fracture factor read by `Health.fall`). `/seancraft module <module> <level>` sets module levels for tests.
- `compat/Celestials.java` reads Enhanced Celestials 2's moon by reflection (blood/harvest moon hooks in `Tarkov.rollStars`, `huntingParty`, `EcoCropMixin`). `compat/Dungeons.java` + `DungeonPlacementMixin` keep When Dungeons Arise out of the Heartland, thin it (`Cfg.dungeonsKeep`) and add band loot (`BandAtLeast` loot condition). `compat/Markers.java` sends home/camp/grave markers; `client/compat/JourneyMapMarkers.java` is the JourneyMap plugin (entrypoint `journeymap`).
- EC2, its event pack, CorgiLib, Data Anchor, WDA and JourneyMap are built with newer Loom than ours, so Loom won't take them as dependencies. Dev runs load them from `run/mods` (Loader remaps them at launch): run `scripts/devmods.sh` once. The JourneyMap API in `libs/` is its nested jar with the `Fabric-Loom-Version` manifest line removed, compile only. Moving to Loom 1.19 would need Gradle 9.8+ and Java 25.
- EC2 commands: `/enhancedcelestials2core setLunarEvent enhancedcelestials2defaultlunarevents:blood_moon` (at night).
- `compat/GraveCompat.java`: YAGM (required) handles death. We set its config on server start and move graves out of boss rings. In dev, OctoLib's nested snakeyaml is added as `localRuntime` by hand.
- Dev runs also load Terralith 2.5.8 (`modLocalRuntime`, test only) to match players' terrain. Its world-gen datapack shows an experimental-settings warning on create and load; click through. The current Terralith and Tectonic need Fabric Loader 0.17+/0.18 and newer Loom (their `.ct` class tweakers), which this build doesn't have.
- `Cfg` has a `ConfigVersion` line; bump it and add a step in `Cfg.load` when a default changes and old files should move.
- Dev runs pick a random player name, so a reloaded world has a new player.
- UI spec: the user supplies it in zip packs (UI-SPEC.md plus mock-ups). It isn't in the repo.

## Building and testing
- To compile: `./gradlew compileJava -q`. To build the jar: `./gradlew build -q`.
- For an in-game test, run headless on Xvfb:
  ```
  LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -n 99 -s "-screen 0 1280x720x24" ./gradlew runClient --no-daemon --args="--quickPlaySingleplayer test"
  ```
  - Wait for "joined the game" in the log. Quick Play can't create a world; make a new one through the menus (Create New World).
  - The game window sits at +213+120 and is 854x480. Take screenshots with `import -window root -crop 854x480+213+120 out.png`.
  - Drive it with xdotool (`DISPLAY=:99`, `XAUTHORITY` from `/tmp/xvfb-run.*/Xauthority`).
  - Chat commands need cheats: Esc, Open to LAN, Allow Cheats ON, Start.
  - Don't `pkill -f java`: it matches the shell running the command. Use `pkill -f "Knot[C]lient"`.
- Server code that reads blocks away from a player must check `level.getChunkSource().getChunkNow(cx, cz) != null` first. `hasChunk`/`isLoaded` only say a chunk is on its way, and reading it makes the server thread finish or generate it on the spot (that was the ecology lag spike).
- To profile: `jcmd <pid> JFR.start settings=profile filename=x.jfr`, then `jfr print --stack-depth 64 --events jdk.ExecutionSample x.jfr` (the default depth of 5 hides the callers).
- Nine-slice `.mcmeta` borders must leave a centre slice (left + right < width).
