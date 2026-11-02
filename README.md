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
| Z, X, C | Your three active skills |
| V | Ultimate (once you have a specialization) |
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
- **Hideout modules.** Each module is its own block. Craft it at the Hideout Bench, put it anywhere inside the hideout (one of each per hideout), then use it to build and upgrade it. Each level takes real time. You don't need everything at once: hit CONTRIBUTE to put in whatever you have from your inventory and stash, and once it's all in it starts building. TRACK puts what's still missing on your gather list (see below). Break a module and it keeps its level, so you can move it to another hideout.
  - A module only works while its block is in the hideout. Hideouts from before module blocks get their old levels back when you place each block.
- **Station tabs.** In your hideout, the Hideout Bench, Weapon Bench, crafting table and every module have tabs for each other, so you can hop between them from anywhere in the hideout without walking over. Each station shows up once it's within the hideout.

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

  Swords are mostly slash, axes slash plus chop, maces and fists blunt, arrows and tridents pierce. Some materials add an element on top (see material traits under Weapons and armor). Chop only hurts wood, so it does nothing to monsters.
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
- **Rigs and backpacks** have to be empty before you can take them off. You start with a Scav vest (4x2), a Sling bag (3x3) and a 2x2 pouch. Bigger ones are made at the Hideout Bench:
  - Rigs: Chest rig (4x3), Carrier rig (5x3, Workshop 1), Assault rig (6x3, Workshop 2).
  - Backpacks: Scav backpack (4x5), Pilgrim backpack (5x7, Workshop 1), Expedition pack (6x8, Workshop 3).
  - Or upgrade: trade in the empty one below it in the line and pay half the materials.
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

- Swords, axes, daggers, maces, warhammers, greatswords, halberds, spears, pikes, bows, crossbows, shields and metal armor are made from parts. The vanilla recipes for them are gone.
- Everything happens at the **Weapon Bench** (a smithing table and a crafting table, shapeless). The crafting table is vanilla only, except for the **wooden club**: 2 logs over a stick, so you've got something to hit with before you find a bench. It's blunt and has no parts.
- **PARTS tab.** Every part is laid out by kind and material, and the ones you can make right now are green. Heads are on the first page and everything else on the second (click the arrow under the grid). Hover one to see what it costs. Click to make one, or Shift-click to make 5. It takes materials from what you carry and the stash, and the parts go into your grids.
  - Most parts cost 2 of their material. A knife blade costs 1. Axe heads, mace heads, limbs, hafts and padding cost 3. Long blades and halberd heads cost 4. A guard needs a stick as well.
  - Netherite parts are the diamond part plus 2 netherite scrap.
- **BUILD tab.**
  1. With the left slot empty, pick what to build. You get an outline of it, with each part's slot sitting where that part goes: blade on top, guard across, grip and pommel below, and so on.
  2. Empty slots show a faded example of the part that goes there. Hover one for its name, and the plate slot shows how many plates it takes.
  3. Drop the parts in and hit ASSEMBLE. The blade, axe head or plates decide what you get (copper makes the stone version); the other parts can be any material. If ASSEMBLE is grey, hover it to see what's missing.
- The standard parts give you exactly the vanilla item. Other materials change damage, swing speed, durability, armor and weight.
- **Weapon families.** Each one has its own moveset (from Better Combat), damage type and base numbers. At iron:
  - Dagger (knife blade, handle): 20 damage, 2 swings a second, pierce and slash. Quick strikes leave enemies open again fast, and hits from behind do 50% more.
  - Mace (mace head, handle, wrap): 35, blunt. Warhammer (mace head, haft, wrap): 45, slow, blunt, two-handed, ignores an extra 25% of armor. Both can knock a raised shield out of use, like the axe.
  - Greatsword (long blade, long grip, guard, wrap): 50, slash, wide two-handed sweeps.
  - Halberd (halberd head, haft, wrap): 45, slash and pierce, reach.
  - Spear (spearhead, haft, wrap): 32, pierce, reach, ignores an extra 25% of armor. Pike: 40, pierce, even longer reach.
  - Materials scale damage: copper ×0.85, iron ×1, gold ×1.1, diamond ×1.2, netherite ×1.35.
- **Material traits.** Every material also has one trait, shown in pink on the part and on the item's part list. Vanilla items come with the traits of their standard parts, so a plain iron sword already has 300 durability instead of 250.
  - Heads (and bow limbs): copper +10% added lightning, iron +20% durability, gold +15% added spirit, diamond +5% crit chance, netherite +20% added fire and doesn't burn in lava. Iron plates get the +20% durability too.
  - Handles and hafts: planks +5% swing speed but −15% durability, log nothing, bone +15% durability, blaze rod +5% added fire, breeze rod +10% swing speed.
  - Wraps and padding: leather nothing, rabbit hide makes you harder for mobs to spot while sneaking (stacks per piece), phantom membrane is lighter, scute padding gives +2 armor.
  - The old "gold does more to undead, netherite sets things on fire" part bonuses are gone; the trait is the bonus now.
- **Recipe ladders.** You can use any weapon you find, but you can only make the ones you've unlocked. Each attribute has a ladder:
  - STR: wooden club → mace, axe, sword → warhammer, greatsword, halberd.
  - AGI: bow, dagger → sword, spear, crossbow → pike.
  - INT: wands and staves, once they're in. Until then an Arcanist can make the club.
  - You start knowing the first rung of your origin's ladder. 5,000 damage to monsters with rung 1 weapons opens rung 2, and 25,000 with rung 2 weapons opens rung 3. Swords are on both ladders.
  - Crossing over: do 1,000 damage with a found weapon from another ladder and you unlock that weapon, plus the first rung of its ladder, which you can then climb too.
  - Locked weapons are greyed out at the bench, and hovering one shows your progress. Heads that only locked weapons use can't be made. The wooden club recipe is locked the same way.
  - Shields and armor aren't on a ladder.
- Put an existing item in the left slot and its parts show on its outline. You can swap them out, or level a part up to 4 with its own material using the ▲ next to it.

### Character

- **Origin.** The first time you open the web (K) you pick an origin, and it never changes:
  - **Vanguard** (Strength): starts in the Warrior region with a wooden club. Can become a Warrior, Knight or Paladin.
  - **Scout** (Agility): starts in the Ranger region with a bow. Can become an Archer, Blademaster or Rogue.
  - **Arcanist** (Intelligence): starts in the Mage region. Can become a Sorcerer, Warlock or Cleric. (The apprentice wand comes with wands.)
- **Levels.** You get class XP from killing mobs, mining ores and first boss kills, up to level 60. Each level gives a web point and 2 attribute points.
- **Attributes.** You start with 10 in your origin's attribute. Each level's 2 points split by what you trained during that level: swords, axes, mining and blocking feed Strength; bows, running and sneaking feed Agility; casting, brewing and enchanting feed Intelligence. Swimming and farming feed nothing. The web never gives attributes. There's no "primary" attribute:
  - Each weapon scales with its own: +1% damage per point. Axes, maces, warhammers, greatswords, halberds and the club use Strength. Daggers, bows and crossbows use Agility. Swords, spears and pikes use the average of Strength and Agility. Skills scale with their region's attribute.
  - Strength: more rage, bigger crits, a little knockback, a little max health.
  - Agility: crit chance, swing speed, faster meds, and Agility weapons and skills cost up to 50% less stamina.
  - Intelligence: more mana and faster regen, shorter cooldowns, faster skill XP, and longer food, Rested and painkillers.
- **The web (K)** has 120 nodes in six regions around a ring. At first you only see your starter web, your origin's home region, with a faint outline of the rest. Every node does something on its own: damage, health, stamina, elemental resistance, bonuses that scale with an attribute, keystones with a real drawback, and active skills.
- **Specialization.** Your starter web ends at three gates, one for each of your origin's specializations. Taking one needs level 20, any tier 1 boss trophy, and being in a hideout. It gives you a title, opens the whole web (with a zoom-out the first time), opens your ultimate (V), and opens the first of your specialization's three signature nodes at the outer edge of its region. You get one specialization.
- **Milestones.** Mastery (level 40 and any tier 2 trophy) opens your second signature node and ranks your ultimate up; Capstone (level 60 and a tier 3 trophy) opens the third and ranks it up again. Tier 2 and 3 bosses aren't in yet, so for now only the first step is reachable.

  | Specialization | Signature nodes (specialization / mastery / capstone) | Ultimate |
  |---|---|---|
  | Warrior | Can't be staggered while Rage is above half / +50% rage gain, +10% melee / +25% melee while Rage is above half | Rampage: 8s of free Rage skills, no staggers, +20% damage |
  | Knight | Players within 6 blocks take 15% less / blocking pulls the attacker and nearby monsters onto you / +10 armor, +50% knockback resistance | Bulwark: 6s, players behind you take half, you 30% less |
  | Paladin | Melee hits add 20% spirit / players within 8 blocks bleed 30% slower / another 20% spirit and a holy aura | Hallowed Ground: 8s, spirit damage to undead in it, slower bleeding |
  | Archer | Arrows slow / +15% arrow damage, +5% crit / arrow crits pin | Deadeye: next three arrows always crit and pin |
  | Blademaster | +50% on your first hit after a block / sword hits leave enemies open fast, like a dagger / +15% swing speed, +10% crit | Thousand Cuts: dash through up to five enemies, three cuts each |
  | Rogue | +25% sneak speed, +25% backstab / invisible while sneaking / +50% backstab, +25% crit damage | Vanish: 5s invisible, things lose you, first hit is a backstab |
  | Sorcerer | +30% skill area / +20% skill damage / skills cost 20% less mana | Cataclysm: 5s of lightning on monsters around you |
  | Warlock | Skill hits hex for +15% damage taken / melee hits poison / hex +15% more, +10% skill damage | Doom: 8s of double damage from its weaknesses; it bursts into poison if it dies |
  | Cleric | Meds apply and heal 30% better / surgical kits leave 15% lasting damage instead of 35% / you and players near you regenerate | Miracle: revives every destroyed part on players near you |

  Ultimates cost 40 of the specialization's resource (rage, stamina or mana) and recharge in 120 seconds, 100 at mastery and 80 at capstone; each rank also makes them last or hit 25% more.
- **Skills and loadout.** Learned skills go in three slots (Z, X, C); V is the ultimate. You can learn more than you can equip, but the loadout only changes in a hideout. Shift-click a learned skill on the web to rank it up, up to 4, for a point each (+15% damage and -10% cost per rank).
- **Resources.** Rage (Strength skills) starts at 100, Strength raises it; +5 per hit you land and +10 per hit you take, and it drains 10 a second after 5 seconds out of combat. Mana (Intelligence skills) starts at 100, Intelligence raises it. Stamina is everyone's.
- **Crits.** Every melee hit and arrow has a chance to crit. You start at 5%, Agility adds to it, and it caps at 75%. A crit does +50% damage, plus 0.25% per point of Strength. Some web nodes add more of both. Your numbers are on the SKILLS tab.
- **Use-skills** level up as you use them: swords, axes, blocking, mining, bows & crossbows, running, sneaking, swimming, enchanting, brewing, casting and farming. You lose 5% of your skill progress when you die. Firing a grappling hook (from the Grappling Hook Mod) also trains bows & crossbows.
- **Respec.** Use the Altar of Regret (2 gold ingots and a book over 3 crying obsidian) inside a hideout. It wipes your web and specialization, frees all your attribute points to place again (the [+] buttons on the web screen), and kills you. Below level 21 you get every web point back. From 21 you get 75% back, and from 41 you get 50% and it also needs an Orb of Regret. Your origin stays, and the whole web stays open.
- **Old characters** get their web points back once, since the web's layout changed, and their attribute points from the levels they already have. They pick an origin like everyone else.

### World

- Villages are about twice as common.
- Village chests almost always have meds and some supplies. Other vanilla chests have meds about half the time.
- Small supply caches (a barrel and some mossy slabs) are scattered around the overworld.
- Portals work like normal.
- **Hazards.** Some places hurt to be in. You can go in early, but it drains about 1% of your health a second (a minute and a half from full) until you have the right key or drink the right potion. A potion covers you for 3 minutes, however long the effect itself lasts. While it's draining you, the edges of your screen glow and the effect list says what fixes it.
  - **Darkness:** the deep dark, and anywhere below Y 0 that isn't lit (a lit tunnel is fine). Key: Daylan's Follower Lantern. Potion: Night Vision.
  - **Heat:** deserts. Potion: Fire Resistance. The key comes from a tier 2 boss that isn't in yet.
  - **Cold:** frozen biomes out in the Far lands (not the snowy taiga), and the frozen and jagged peaks anywhere. Potion: Warmth, brewed from an awkward potion and a magma block. Its key is also from a tier 2 boss.
  - Boss arenas never have a hazard, so every dungeon can be reached.

### Bosses

- **Dungeons.** Each boss has one dungeon in its home biome, out in its tier's distance band (tier 1 is 500 to 2,000 blocks from spawn). It gets built the first time someone comes near it. The totem's entry at the Hideout Bench says roughly how far and which way, and crafting the totem gives you a map to it.
  - Mark: a sandy landing on a beach or river mouth. Daylan: a photo-shoot clearing in a cherry grove. Bo Daddy: a lit cavern dug in at the edge of the deep dark. It's walled in, so you tunnel to it.
  - No Hideout Bench within 100 blocks of a dungeon.
  - **Custom dungeons.** Build one in a creative world and save it with a structure block named `seancraft:dungeon/<boss>` (`mark`, `daylan` or `bo_daddy`). Copy the saved `.nbt` from the world's `generated` folder into `src/main/resources/data/seancraft/structure/dungeon/`. From then on that boss's dungeon is your build instead of the built-in one. The bottom layer is the floor, set at ground level (at the site's depth for Bo Daddy), and the Summoning Pedestal goes wherever you put one in it, or in the middle if you didn't. Keep the 40-block ring in mind.

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
- **Daylan Little** (tier 1). Totem: 4 copper ingots, an emerald, a glow ink sac and a cherry sapling.
  - Influencer: he hangs back behind six bought followers. Each one cuts his damage taken by 10%, they dive at you now and then, and any hit pops one. The boss bar shows his follower count dropping. He heals off them with Humble Brag, so pop them first.
  - He only types a post when nobody's within 6 blocks. That's 2 seconds of free hits, then 5 text bubbles fly out in a fan.
  - Ring Light Flash blinds you if you're looking at him. Turn away.
  - At 67% he soft launches a political career: a speech that pulls everyone in. He can't be hurt during it.
  - Firefighter: he hoses you back 8 blocks (it also puts out fire and fire resistance), chops hard up close, and cones off a zone that gets blasted with water. He takes half damage from fire in this phase.
  - At 33% the hard launch pulls twice as hard, then knocks everyone outward.
  - Policeman: he whistles "Freeze!", and anyone who moves more than half a block during the check gets a ticket (hurt and slowed). He also tases, cuffs you in place (a sword or axe swing breaks the cuffs), swings a nightstick, and puts up a riot shield that blocks arrows from the front, so flank him.
  - You can only be stunned, cuffed or slowed once at a time, plus a second after it ends.
  - He takes 150% from slash.
  - Drops: the Follower Lantern (carry it and Darkness can't touch you), and on your first kill the Clout trophy and a Blue Check (use it to put a ✔ after your name in chat). Also copper, rabbit hide and sometimes emeralds.
- **Bo Daddy Harris** (tier 1). Totem: 4 copper ingots, a note block, a leather and a poppy.
  - Outlaw Country: he walks you down with a guitar swing, lassos you back if you run, and does a line dance with three stomps. The stomps land off the beat, so watch his feet, not the music.
  - Before every Baritone Roar he takes a big breath in. Get out of the ring of sound, then punish him: right after a roar he's out of breath and takes 150% from everything for a second and a half. Fire and frost hits keep him complaining (and out of breath) a bit longer.
  - At half health he switches it up to Silky R&B: a croon that slows you, Slow Jam zones on the ground, roses thrown at anyone hanging back, and the Drop, a huge ring that leaves him out of breath the longest.
  - His heavy hits stagger you for a moment.
  - Three sculk shriekers sit around the stage. Every roar and Drop fills them up, and once they're full the whole arena pulses with Darkness every 5 seconds until someone breaks a shrieker. The Follower Lantern blocks it.
  - Drops: the Subwoofer (shows every mob within 32 blocks through walls for 5 seconds, 30 second cooldown), and on your first kill the Too Much Soul trophy and the "Twinkle (Bo Daddy Cover)" music disc. Also copper, leather and sometimes amethyst.
- **Trophy powers.** Craft a Trophy Rack at the Hideout Bench (6 planks, an item frame, 2 iron) and put it in a hideout.
  - Use it with a trophy to put the trophy on the rack. Use it empty-handed to choose that trophy's power, and sneak to take a trophy back.
  - Press Y to use your power: 5 minutes on, then 20 minutes to recharge.
  - Old Salt (Mark): Tailwind, faster swimming.
  - Clout (Daylan): half again as much skill and class XP.
  - Too Much Soul (Bo Daddy): Bass Drop. You can't be staggered, and your heavy hits (40 damage or more) stagger mobs.

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
  - `/seancraft summon joe|sean`, `/seancraft babywars start|stop`, `/seancraft pedestal` (puts a Summoning Pedestal under you), `/seancraft dungeons` (lists where the dungeons are; `/seancraft dungeons build <boss>` builds one now) and `/seancraft reload` need op.
  - `/character xp|skill|reset` is for testing the character system (op). `/character ladder <player>` shows ladder progress, and `/character ladder <player> "<id>" <value>` sets it (ids like `STR1`, `AGI2`, `x:mace`, `u:mace`, `on:STR`).
  - `/seancraft injure <part> <bleed|fracture|scar|destroy|heal> [percent]` sets an injury on yourself, for testing (op).

## Works with

- **FallingTree:** trees you chop go into your grids, and axes made from parts work with it.
- **Better Combat and Combat Roll:** required (see Combat).
- **Grappling Hook Mod:** needs Forge Config API Port too. Hooks go to your hotbar, and using them trains bows & crossbows.

FallingTree and the Grappling Hook Mod were checked by reading their code, and neither has been played alongside this mod yet. Better Combat and Combat Roll were tested in game.

## Custom icons

Most items, parts and station blocks now use the SeanCraft asset pack (Appendix C palette). The part-built weapons show their standard build, layered from the pack's part textures; they don't change look when you swap parts yet. Station blocks face you when placed. The screens and HUD are drawn with the pack's GUI kit, following the UI spec (Appendix C, v16): forged-iron panels, soot slots, one gold accent, and colours that always mean the same thing (gold for selection and anything secure, blood for danger and the raid, moss for stamina and "enough", ember for rage, blue for mana, cyan for painkillers). Headings use Jacquard 12 (OFL, in `assets/seancraft/font/`). Vanilla's own screens (pause menu, Mod Menu, other mods' configs) still look stock.

A few items still borrow a vanilla texture (bow limbs, for one). To give one its own icon, drop a 16x16 PNG named after the item into `src/main/resources/assets/seancraft/textures/item/` and the build uses it. See [ICONS.md](ICONS.md) for the rules and the full list of item names.
