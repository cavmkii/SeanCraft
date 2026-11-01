# SeanCraft

A Fabric mod for Minecraft Java 1.21.1. It turns survival into a raid game: you head out from a safe spot, loot what you can, and get back before things get bad. If you die out there, you lose your kit. It also adds a character system with a big passive tree, use-based skills, gear built from parts, body-part health, a Tetris-style inventory, and two NPCs who hate each other.

Everyone needs the mod, the server and every player.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for **1.21.1**.
2. Put these in your `mods` folder, all the 1.21.1 Fabric builds:
   - [Fabric API](https://modrinth.com/mod/fabric-api)
   - [Better Combat](https://modrinth.com/mod/better-combat), which also needs [Player Animator](https://modrinth.com/mod/playeranimator) and [Cloth Config](https://modrinth.com/mod/cloth-config)
   - [Combat Roll](https://modrinth.com/mod/combat-roll)
   - `seancraft-<version>.jar`
3. Start a **new world**. Spawn points are only built in new worlds.

To build the jar yourself you need JDK 21. Run `./gradlew build`, and the jar shows up in `build/libs/`.

## Keys

| Key | What it does |
|---|---|
| E or J | Inventory screen (GEAR tab) |
| H | Inventory screen, STATUS tab |
| K | Inventory screen, WEB tab (passive tree) |
| Z, X, C, V, B | Active skills in your 5 ability slots |
| R | Dodge roll (Combat Roll) |
| Y | Trophy power |
| T (over an item, in the inventory screen) | Adds it to your gather list |

The inventory screen has five tabs: GEAR, CRAFTING, SKILLS, STATUS and WEB. Creative mode keeps the vanilla inventory.

## What's different from vanilla

### Raids, spawn and hideout

- **Spawn points.** A new world gets 3 fixed spawn points, one at world spawn and two about 400 blocks out. Each has a small shelter with 3 chests and a barrel of starting gear, and the empty ones restock every 30 minutes. New players start at a random one, and so does anyone who dies without a bed.
- **Safe zones.** The area around a spawn point is safe, and so is your hideout. Anywhere else, you're **in raid**. The HUD in the bottom left says which one you're in, and a short banner at the top of the screen tells you when a raid starts or you extract.
- **Threat.** The longer a raid goes, the worse it gets. Threat 1 hits at 5 minutes, 2 at 20, 3 at 40 and 4 at 60. Mobs start spawning as elites with more health and damage, and hunting parties come after you.
- **Distance.** The further you get from world spawn, the tougher the monsters, and the more XP they give. The HUD shows which band you're in, and a banner pops up when you cross into a new one.

  | Band | Distance from spawn | Monster health | Monster damage | XP |
  |---|---|---|---|---|
  | Heartland | 0–1,000 | normal | normal | normal |
  | Wilds | 1,000–3,000 | +25% | +15% | +25% |
  | Frontier | 3,000–6,000 | +50% | +30% | +50% |
  | Far lands | 6,000+ | +100% | +50% | +100% |

  Elites add on top of that, but a monster never gets past 4x health or 2.5x damage. In the Frontier and the Far lands, raids start at threat 2, and so does any raid in the Nether. Nether distances count 8x, like the travel.
- **FOB tables.** Made at the Hideout Bench from 8 planks, 4 sticks and a campfire. While you're in the same chunk as one, the raid timer stops. Each raid only gets 15 minutes of that, shared between every table, and it fills back up when you extract. It doesn't count while a monster is within 16 blocks or for 10 seconds after you get hit. One per chunk, and not in a safe zone. It's not a safe zone itself, and it has no stash.
- **Dying in raid.** By default you lose everything except what's in your secure pouch, and nothing drops. Dying in a safe zone is a normal death.
- **Hideout.** Craft a Hideout Bench (3 cobblestone on top, then plank / crafting table / plank, then 3 planks) and place it. Everything within 30 blocks is your hideout. Chests and barrels inside it are your stash.
  - Hideouts have to be at least 500 blocks from each other and from spawn points.
  - You can have 3 Hideout Benches placed at once. Break one to move it. Anyone can use anyone's hideout as a safe zone.
  - Only a bed inside a hideout sets your respawn point. You can still sleep in other beds.
- **Hideout modules.** The bench builds modules, and each one takes real time. You don't need everything at once: hit CONTRIBUTE to put in whatever you have from your inventory and stash. Once it's all in, it starts building. TRACK puts what's still missing on your gather list (see below).
- **Station tabs.** In your hideout, the Hideout Bench, Weapon Bench and crafting table screens all have tabs for each other, so you can hop between them from anywhere in the hideout without walking over. Each station shows up once it's within the hideout.

  | Module | What it does |
  |---|---|
  | Workshop | Faster furnaces in the hideout; unlocks bigger pouches, rigs and backpacks |
  | Medstation | Unlocks meds; bleeds stop and fractures heal in the hideout |
  | Perimeter | Bigger hideout radius |
  | Intel center | More calm time per raid; warns you about hunting parties |

### Combat

Swings and rolls come from Better Combat and Combat Roll. SeanCraft puts a price on them.

- **Stamina.** Everyone has 100, plus whatever your food adds. The bar sits right of the hotbar.
  - Sprinting costs 8 a second.
  - A swing costs a quarter of the weapon's damage, times 5. An iron sword is 7.5 a swing.
  - A roll costs 20.
  - Stamina refills at 25 a second, but only after a second without spending any. Keep swinging and it never refills.
- **Running dry.** A swing you start with too little stamina does half damage. At 0 you can't sprint or roll until the bar is back to 25.
- **Rolling.** For the first 0.4 seconds of a roll, attacks (hits, arrows, explosions) can't touch you. Falls, fire, lava, bleeding and poison still can. You can't roll in the middle of a swing, and there's a 0.75 second wait between rolls. Rolls don't use hunger.
- **Swords** made at the Weapon Bench swing three times in a combo: two side slashes and a harder third slash.
- **Damage types.** Every hit is split into types:
  - physical: slash, pierce, blunt;
  - elemental: fire, frost, lightning, poison;
  - spirit.

  Swords are mostly slash, axes slash plus chop, maces and fists blunt, arrows and tridents pierce. Gold weapons add 15% as spirit and netherite 20% as fire, on top. Chop only hurts wood, so it does nothing to monsters.
- **Resistances.** Skeletons take 50% from pierce and 150% from blunt. Iron golems shrug off slash and pierce. Fire does nothing to fire-immune mobs. Spirit only hurts the undead, at 150%.
- **Armor** works per body part: your helmet only protects your head, your chestplate your body and arms, and leggings and boots your legs.
  - A hit is multiplied by 20 / (armor + 20). Leather is 5, chainmail and gold 8, iron 12, diamond 20, netherite 25. So iron takes a third off, and diamond takes half.
  - Each level of Protection adds 2 armor on the parts that piece covers.
  - Pierce ignores a quarter of armor.
  - Armor doesn't stop elemental damage. Each level of Fire, Blast or Projectile Protection gives 4 resistance points against its kind, through the same formula.
  - Monsters' armor uses the same formula.
- **Fire arrows.** Made at the Hideout Bench: 4 arrows, a coal or charcoal and a string make 4. They hit with pierce plus some fire, and set what they hit alight. Bows with Flame add a smaller fire share.

### Food and rest

- **No hunger.** You can't starve, and food doesn't heal you the vanilla way. Instead you have 3 food slots, shown above the injuries in the bottom left, each with a bar for the time it has left.
- **Meals.** Eating something puts it in a slot for a while, adding max health, max stamina and slow health regen. Hover a food to see what it gives.
  - Meat and stews are health food: lots of health, a little stamina.
  - Bread, berries, fruit and honey are stamina food.
  - Fish, pumpkin pie and golden food are balanced.
  - Better food lasts longer (up to about 20 minutes).
  - The bonus fades over the last quarter, and the bar turns amber, so you know it's time to eat again.
- **Eating rules.** You can't have two of the same food at once. You can eat it again once it's half gone, which starts it over. With all 3 slots full, you wait for one to run out. You lose your meals when you die.
- **Rested.** Stay within 4 blocks of a lit campfire, fire or furnace, under a roof, for 20 seconds without getting hurt.
  - A Resting chip counts down while you wait.
  - Rested gives +50% health regen, +100% stamina regen and +50% skill XP.
  - It lasts 8 minutes, plus 1 minute for each point of comfort.
  - Comfort is 1 for each kind of thing within 8 blocks (a fire, a bed, a carpet, a banner, a table, flowers or a flower pot), plus 1 for each module built in the hideout you're in.
  - It works away from a hideout too, like at a FOB table, but there's usually less comfort out there.

### Inventory

- **Grids.** Your inventory is a set of grids: pockets (5x2), a rig, a backpack, and a secure pouch. Items take up real space, so a sword is 1x3 and a chestplate is 3x3. The hotbar, armor and off-hand still work like vanilla.
- **Controls** work like vanilla:
  - click to pick up and click to put down;
  - right-click picks up half, or puts down one while you keep holding the rest;
  - **Shift**+click (or Ctrl) sends an item across;
  - **1-9** puts the item you're hovering on that hotbar slot;
  - **Q** drops one, Ctrl+Q drops the stack;
  - **R** rotates;
  - Esc lets go.
- **Swapping.** Dropping something on a hotbar or armor slot that's already full swaps them, and you end up holding the item you bumped, like vanilla. Put it wherever you want, or let go and it stays where the new one came from.
- **Auto-refill.** When the last item in a hotbar slot gets used up (a block placed, food eaten, a tool broken), more of the same comes up from your grids. The toggles next to HOTBAR turn it off, or let it pull from the stash too (off by default).
- **Pickups** fill pockets, then rig, then backpack. Weapons and tools go to the hotbar first. If nothing has room, the item stays on the ground.
- **Rigs and backpacks** have to be empty before you can take them off. You start with a Scav vest (4x2), a Sling bag (3x3) and a 2x2 pouch. Bigger ones are made at the Hideout Bench.
- **Chests and barrels** open on the GEAR tab as grids too. Where you put things stays put, while hoppers still work like normal. Each one has a SORT button (merges stacks and packs it tight) and a TAKE ALL button. In your hideout, every stash chest shows up there, and STASH ALL dumps your pockets, rig and backpack into them.
- **Carrying chests.** Shift + left-click a chest or barrel to pick it up with everything in it, and Shift + right-click to set it down again. While you carry it you walk slowly and can't sprint, switch hotbar slots, use items, fight, mine or open any menus. Picking up half of a double chest leaves the other half as a single chest. If you die carrying one, it and its contents drop where you fell. Spawn point chests can't be carried off.
- **Large chests** show up as one Large Chest everywhere, whether you open it or see it from the stash.
- **Naming chests.** Click a chest's name to rename it, then hit Enter. The name shows up next to a picture of the block, so you can tell a chest from a barrel at a glance.
- **Chest filters** work like RimWorld stockpiles. FILTER on a chest lets you allow or disallow kinds of things: weapons, ammo, armor & gear, tools, food, meds, parts, blocks and materials.
  - STASH ALL, and Shift-click with no chest open, put things where the filters say. Chests with narrower filters get first pick, so a weapons-only chest fills before a take-anything chest.
  - ORGANIZE STASH moves anything sitting in the wrong chest to one that allows it, then tops up half-stacks across chests so the same item ends up in as few stacks as it can.
  - Filters only steer automatic moves. You can always put anything anywhere by hand. Sneak + use with an empty hand opens the old chest screen.
- **Vanilla screens** like the crafting table, furnace and anvil show your grids beside them. Click a grid item to grab it, then drop it in a slot, or the other way round.
- **Furnaces** (and smokers and blast furnaces) list your fuel and anything that furnace can cook under the backpack, with a second row for each from the stash when you're in the hideout. Click one to put a stack in the right slot, or right-click for one. Tools, weapons and enchanted stuff are left off the fuel list so you don't burn your bow by accident.
- **Crafting.** The CRAFTING tab has a recipe list on the left, grouped by category, and a crafting grid on the right. Right-clicking a crafting table opens the same tab.
  - Click a category heading to fold it shut, or click it again to open it.
  - Click a recipe and it gets laid out in the grid with what you have. Your hotbar, grids and stash all count.
  - Or lay it out yourself: click something in MATERIALS, then click a grid cell. Right-click puts down just one.
  - Click the result to craft, or Shift-click to craft as many as you can. The recipe you picked stays selected and lays itself out again after each craft, so you can keep clicking.
  - PIN TO HUD under the result puts the selected recipe's ingredients on your gather list.
  - Anything left in the grid goes back when you close the screen.
  - Your hotbar shows under MATERIALS, so you can grab from it or drop things into it without switching tabs.
  - The RECIPES sub-tab shows every recipe, sorted by category. The ones you haven't found yet show up as ??????. You find a recipe by picking up one of its ingredients. Click a recipe to see what it needs and how much of it you have, then hit LAY OUT to put it in the grid, or PIN TO HUD to track the ingredients.
  - 3x3 recipes need a crafting table within 6 blocks, or you have to be in the hideout. Otherwise you get 2x2.
  - Outside a hideout, the tab tells you how to make a Hideout Bench and pins it to the top of the list.
- **Gather list.** It's a to-do list for materials. Pin a recipe from CRAFT or RECIPES, hit TRACK on a hideout module (it adds whatever hasn't been put in yet), or hover an item and press T, and set how many you want with the - and + buttons (Shift does steps of 10). While you're out of the hideout, the list shows in the top left with how many you're carrying, and each line turns green when you have enough. It holds up to 6 things.
- **Ammo.** Bows and crossbows pull arrows from your rig, pockets or backpack.

### Health

- **Health is 300.** Everything is on a bigger scale than vanilla: you have 300 health, monsters have 5x their vanilla health, and all damage and healing is 5x. A bar with numbers replaces the hearts.
- **Body parts.** Your health is split across 7 parts: head (24), thorax (54), stomach (48), two arms (42 each) and two legs (45 each). Hits land on a part depending on where they came from. Falls hit both legs, half each.
- **Injuries.** Parts can bleed, fracture, or get destroyed. A destroyed head or thorax kills you. Bleeds and fractures have a severity from 1 to 100%: the damage the part took over its max, so a hit for a third of your thorax is a 33% injury. Small grazes don't injure. Another one on the same part stacks with it. A bleed at 50% or more is a heavy bleed. Bleeding drains the part, faster the worse it is, and stops once the part is destroyed. Hits on a destroyed arm, leg or stomach go to the thorax at 70%.
- **What injuries do.** It's all on a sliding scale, not on or off. A limb counts as its fracture severity, or 100% if it's destroyed, and two bad legs (or arms) count for more than one.
  - **Legs:** up to 40% slower walking, slower sprinting on top of that, and up to 10% lower jumps. At 100% you can't sprint at all.
  - **Arms:** up to 40% weaker swings, 30% slower swings, and arrows or tridents fly up to 10° off line. A drawn bow also wobbles on screen.
  - **Mining** gets slower from bad arms, bad legs and bleeding, up to 70% slower.
  - **Limp.** With a bad leg, every other step lands hard: your view dips and tips toward the bad side. Bad legs, bad arms and blood loss also make your view sway a little while you stand still.
- **Painkillers** let you push through it for 5 minutes. Leg penalties drop to 30% and arm penalties to 50%, and you can always sprint. They don't stop bleeding.
- **Surgery.**
  - A **surgical kit** brings a destroyed part back, but it leaves lasting damage: that part only heals to 65% from then on, and it acts like a mild fracture for its penalties.
  - A **surgery case** (2x2, Medstation level 3, rare in chests) brings a part back to new, lasting damage and all.
- **Locked health.** A destroyed part locks away its share of your health, and lasting damage locks part of a part's share. Food, regen and potions can't fill it. It shows as a dark, striped end on the health bar.
- **On screen.**
  - Bottom left lists your injuries with their severity and what fixes them.
  - Above that, a small line shows what they're doing to you right now, like `Walk -24%  Sprint -38%  Jump -6%`. It turns cyan while painkillers are taking the edge off.
  - Above that, every effect you have, good or bad, shows as a small icon with the time left. That covers poison, painkillers, and ability effects like going invisible. It blinks yellow for the last 5 seconds. This replaces the vanilla effect icons in the top right, which don't show a timer.
  - The edges of the screen glow while you're hurt: pulsing red while you bleed, white for a fracture, grey for a destroyed part. They get stronger the worse it is.
  - The STATUS tab shows every part's severities and lasting damage.
- **Meds:** bandage (light bleeds), tourniquet (any bleed), splint (fractures), painkillers, field kit, surgical kit and surgery case.
  - Use one from the hotbar and it treats whatever needs it most.
  - Or pick the body part yourself on the STATUS tab.
  - Meds show up in chests too.
- **Status effects** like poison and wither are listed on the STATUS tab, with a button for each cure you're carrying:
  - milk clears everything;
  - honey cures poison;
  - an antidote (made at the Medstation) cures every bad effect.
- **Sleeping** through the night heals every body part that isn't destroyed by 50% of its max (`Raid.SleepHealPercent`). Destroyed ones still need surgery, and sleep can't get past lasting damage.

### Weapons and armor from parts

- Swords, axes, crossbows, shields and metal armor are made from parts: blades, axe heads, limbs, handles, guards, wraps, plates and padding. The vanilla recipes for them are gone. Bows keep their vanilla recipe.
- Everything happens at the **Weapon Bench** (a smithing table and a crafting table, shapeless). The crafting table itself is vanilla only.
- **PARTS tab.** Every part is laid out by kind and material, and the ones you can make right now are green. Hover one to see what it costs. Click to make one, or Shift-click to make 5. It takes materials from what you carry and the stash, and the parts go into your grids.
  - Most parts cost 2 of their material. Axe heads, limbs and padding cost 3, and a guard needs a stick as well.
  - Netherite parts are the diamond part plus 2 netherite scrap.
- **BUILD tab.**
  1. With the left slot empty, pick what to build. You get an outline of it, with each part's slot sitting where that part goes: blade on top, guard across, grip and pommel below, and so on.
  2. Empty slots show a faded example of the part that goes there. Hover one for its name, and the plate slot shows how many plates it takes.
  3. Drop the parts in and hit ASSEMBLE. The blade, axe head or plates decide what you get (copper makes the stone version); the other parts can be any material. If ASSEMBLE is grey, hover it to see what's missing.
- The standard parts give you exactly the vanilla item. Other materials change damage, swing speed, durability, armor and weight.
- Put an existing item in the left slot and its parts show on its outline. You can swap them out, or level a part up to 4 with its own material using the ▲ next to it.

### Character

- **Levels.** You get class XP from killing mobs and mining ores, up to level 60. Each level gives a point for the passive web.
- **The web (K)** has about 120 nodes and no fixed classes. Go straight down one branch and you're a pure warrior, ranger or caster, or mix them however you want. While you have points to spend, a small yellow "Unspent web points" note sits next to your ability bar. Some nodes unlock active skills, which go in your 5 ability slots. Each archetype has its own resource: rage, stamina or mana.
- **Attributes.** Strength, Agility and Intelligence come from your skills and the web. Your highest one is your primary attribute, and it adds damage.
- **Crits.** Every melee hit and arrow has a chance to crit. You start at 5%, Agility adds to it, and it caps at 75%. A crit does +50% damage, and Strength adds to that. Some web nodes add more of both. Your numbers are on the SKILLS tab.
- **Skills** level up as you use them: swords, axes, blocking, mining, bows & crossbows, running, sneaking, swimming, enchanting, brewing, casting and farming. You lose 5% of your skill progress when you die. Firing a grappling hook (from the Grappling Hook Mod) also trains bows & crossbows.
- **Respec.** Use the Altar of Regret (2 gold ingots and a book over 3 crying obsidian). It wipes your web and kills you on the spot. Below level 21 you get every point back. From 21 you get 75% back, and from 41 you get 50% and it also needs an Orb of Regret.

### World

- Villages are about twice as common.
- Village chests almost always have meds and some supplies. Other vanilla chests have meds about half the time.
- Small supply caches (a barrel and some mossy slabs) are scattered around the overworld.
- Portals work like normal.

### Bosses

The first boss is in. Boss dungeons come later, so for now an op places the arena's Summoning Pedestal with `/seancraft pedestal` (it's also in the creative tab).

- **Summoning.** Craft the boss's totem at the Hideout Bench and use it on a Summoning Pedestal. The boss rises after 3 seconds. The totem is used up. Beaten bosses can be summoned again.
- **The arena** is a ring 40 blocks across around the pedestal. Everyone inside sees the boss bar.
  - While the boss is alive, nobody can break or place blocks in the ring.
  - Hits from outside the ring do nothing.
  - The boss walks back if you pull it out, and it never chases you out.
  - It can't be put in a boat or minecart.
  - Raid threat stops rising while you're in the ring.
- **How bosses fight.** A boss moves into position, picks an attack, winds it up (arm raised, sounds, particles), strikes, then stays open for a moment. Touching it doesn't hurt; only its attacks do. At set health it changes phase: a few seconds where it can't be hurt, then new attacks. Hits it's weak to burst with crit sparks; resisted ones spark dully.
- **Groups.** Each extra player who has been in the ring adds 85% boss health and 10% damage, and the boss attacks more often. It counts up to 6 players.
- **Dying in a boss fight** costs a random third of what you carry; the rest drops where you fell, so you can run back. The fight keeps going while anyone is in the ring. If the ring is empty for 30 seconds, the boss leaves and the totem is gone.
- **Rewards** go in a chest on the pedestal, one share per player in the ring at the kill. Your first kill of a boss also gives its trophy and class XP (about a level per tier).
- **Mark / The Old Guard** (tier 1). Totem: 4 copper ingots, a fishing rod, a cod and a book and quill.
  - He keeps his distance, pulls you in with his reel (roll sideways when it clicks), and throws a net that roots you for 2 seconds. Cut it with any sword or axe swing.
  - At half health he snaps his rod and becomes the Old Guard. He walks you down, swings hard up close, and throws a ring of hooks and paperwork.
  - While he braces between attacks, hits from the front do half damage, so go around him.
  - He takes 150% from slash.
  - Drops: Mark's Reel (a grapple rod), the Old Salt trophy and a Stale Logbook on your first kill, and copper, rabbit hide and bones.
- **Trophy powers.** Craft a Trophy Rack at the Hideout Bench (6 planks, an item frame, 2 iron) and put it in a hideout.
  - Use it with a trophy to put the trophy on the rack. Use it empty-handed to choose that trophy's power, and sneak to take a trophy back.
  - Press Y to use your power: 5 minutes on, then 20 minutes to recharge.
  - Old Salt's power is Tailwind: faster swimming.

### Joe and Sean

- **Joe** is a small green goblin who wanders around looking for Sean, says stuff in chat, throws up, and steals from your pockets, rig or backpack (never your hotbar, gear or pouch). He leaves you alone unless you hit him, or unless someone says "baby wars" in chat. When he dies he drops everything he stole. He gets stronger as players progress.
- **Sean** is a travelling merchant. He sells Soda, Merch, a Glock, a Restraining Order (keeps Joe away), Diddy Oil and the 67 sword, all for emeralds.
- **Fights.** When Joe and Sean get close, they fight. There are spells everywhere, and anyone nearby gets hit too.
- **The Baby Wars.** Say "baby wars" 3 times in an hour and they start:
  - Joe sets up a camp and sends waves of babies at everyone.
  - Sean shows up with his security guards.
  - A Baby Bonnet puts you on Joe's side, and Merch puts you on Sean's.
  - Throw stuff in Joe's cauldron to curse the next wave.
- **Snow.** When it starts snowing on you, you say "awe man the sky is cumming!" in chat.

There's only ever one Joe and one Sean, and they're not always around. Each visit lasts 20 to 40 minutes, then they wander off for 30 to 60 minutes (`General.StayMinutes*` and `General.AwayMinutes*`). They won't leave in the middle of a fight or the Baby Wars, or while you're watching. If they die, they come back later.

## Config and commands

- **Config file:** `config/seancraft/seancraft.properties`. It's created on first run, and every setting has a comment. Run `/seancraft reload` after changing it.
- **Common changes:**
  - `Raid.DeathRule`: `LOSE_EVERYTHING`, `KEEP_SECURE` (normal drops, but you keep the pouch), or `VANILLA`.
  - `Raid.DistanceScaling`: turn the distance bands off.
  - `Raid.HideoutSpacing` (500), `Raid.MaxHideouts` (3) and `Raid.FobPauseMinutes` (15).
  - `Raid.ThreatMinutes`: when each threat level starts (default `5,20,40,60`).
  - `Spawn.*`: number of spawn points, distance, safe radius, restock time.
- Damage and healing are 5x vanilla, and so is monster health. Sean's and Joe's health settings are multiplied too. Minecraft caps any mob's max health at 1024, so the Wither can't reach its full 1,500.
- **Commands:**
  - `/seancraft where` shows where Joe and Sean are.
  - `/seancraft summon joe|sean`, `/seancraft babywars start|stop`, `/seancraft pedestal` (puts a Summoning Pedestal under you) and `/seancraft reload` need op.
  - `/character xp|skill|reset` is for testing the character system (op).
  - `/seancraft injure <part> <bleed|fracture|scar|destroy|heal> [percent]` sets an injury on yourself, for testing (op).

## Works with

- **FallingTree:** trees you chop go into your grids, and axes made from parts work with it.
- **Better Combat and Combat Roll:** required (see Combat).
- **Grappling Hook Mod:** needs Forge Config API Port too. Hooks go to your hotbar, and using them trains bows & crossbows.

FallingTree and the Grappling Hook Mod were checked by reading their code, and neither has been played alongside this mod yet. Better Combat and Combat Roll were tested in game.

## Custom icons

Lots of items borrow a vanilla texture for now. To give one its own icon, drop a 16x16 PNG named after the item into `src/main/resources/assets/seancraft/textures/item/` and the build uses it. See [ICONS.md](ICONS.md) for the rules and the full list of item names.
