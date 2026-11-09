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
- `ecology/`: design doc 17, phase 1 (the grassland loop). `Ecology` holds patch storage (`EcoData`, one saved file per dimension), the tick budget, sampling, crop hooks and the wildlife sampler; `Model` is the population and soil equations; `Quality` the 0-100 score; `Params` the tuning, overridable from `data/seancraft/ecology/params.json`. Debug with `/seancraft eco patch|step|set`.
  - Serene Seasons is a hard dependency but read by reflection (`Seasons`); it and GlitchCore are `modLocalRuntime` in dev.
- `compat/GraveCompat.java`: YAGM (required) handles death. We set its config on server start and move graves out of boss rings. In dev, OctoLib's nested snakeyaml is added as `localRuntime` by hand.
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
- Nine-slice `.mcmeta` borders must leave a centre slice (left + right < width).
