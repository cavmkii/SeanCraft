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
- `client/tarkov/TarkovScreen.java`: the inventory screen (INVENTORY / STATUS / SKILLS).
  - INVENTORY is a 480x270 virtual canvas with three zones: equipment, carried and storage.
  - The CRAFT toggle (`craftMode`) swaps the equipment zone for crafting. `Open(3)` (`CRAFT_MODE`) opens it with crafting on.
  - The station row sits at top right inside a hideout.
- `tetris/Tetris.java`: the server side of the grids.
  - Server messages are `TetrisNet.Move`, `QuickMove`, `Action(n)` and `ContainerAction`.
  - `Loc.container(i, -2, 0)` puts something away into container i; i = -1 means route it by the chest filters.
- `client/ui/Kit.java`: the GUI kit (sprites under `assets/seancraft/textures/gui/sprites`, palette constants).
- `tarkov/Stations.java` and `client/tarkov/StationTabs.java`: hideout stations.
- UI spec: the user supplies it in zip packs (UI-SPEC.md plus mock-ups). It isn't in the repo.

## Building and testing
- To compile: `./gradlew compileJava -q`. To build the jar: `./gradlew build -q`.
- For an in-game test, run headless on Xvfb:
  ```
  LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -n 99 -s "-screen 0 1280x720x24" ./gradlew runClient --no-daemon --args="--quickPlaySingleplayer test"
  ```
  - Wait for "joined the game" in the log.
  - The game window sits at +213+120 and is 854x480. Take screenshots with `import -window root -crop 854x480+213+120 out.png`.
  - Drive it with xdotool (`DISPLAY=:99`, `XAUTHORITY` from `/tmp/xvfb-run.*/Xauthority`).
  - Chat commands need cheats: Esc, Open to LAN, Allow Cheats ON, Start.
  - Don't `pkill -f java`: it matches the shell running the command. Use `pkill -f "Knot[C]lient"`.
- Nine-slice `.mcmeta` borders must leave a centre slice (left + right < width).
