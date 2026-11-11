# SeanCraft

A Fabric mod for Minecraft Java 1.21.1. Survival gets more dangerous the further you go from spawn, and at night. You build up one home base, set camps on the way out, and when you die your stuff waits in a grave you have to go back for. It also adds a character system with a big passive tree, use-based skills, gear built from parts, body-part health, an ecology model for grassland, and two NPCs who hate each other.

Everyone needs the mod, the server and every player.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for **1.21.1**.
2. Put these in your `mods` folder, all the 1.21.1 Fabric builds:
   - [Fabric API](https://modrinth.com/mod/fabric-api)
   - [Better Combat](https://modrinth.com/mod/better-combat), which also needs [Player Animator](https://modrinth.com/mod/playeranimator) and [Cloth Config](https://modrinth.com/mod/cloth-config)
   - [Combat Roll](https://modrinth.com/mod/combat-roll)
   - [YAGM](https://modrinth.com/mod/yagm) (graves), which needs [OctoLib](https://modrinth.com/mod/shatterbyte-lib) and [Architectury API](https://modrinth.com/mod/architectury-api)
   - [Serene Seasons](https://modrinth.com/mod/serene-seasons), which needs [GlitchCore](https://modrinth.com/mod/glitchcore)
   - `seancraft-<version>.jar`
3. Start a **new world**. Worlds from the raid versions aren't supported: the old grid inventory and hideout data don't carry over.

To build the jar yourself you need JDK 21. Run `./gradlew build`, and the jar shows up in `build/libs/`.

## Keys

| Key | What it does |
|---|---|
| E or J | Inventory screen (INVENTORY tab) |
| H | Inventory screen, STATUS tab |
| K | Inventory screen, CHARACTER tab (passive web, level, attributes, skills) |
| Z, X, C | Your three active skills (vanilla's creative hotbar save/load keys sat on X and C and ate them; SeanCraft unbinds those now) |
| V | Ultimate (once you have a specialization) |
| R | Dodge roll (Combat Roll) |
| Y | Trophy power |
| T (over an item, in the inventory screen) | Adds it to your gather list, or takes it off if it's already there |
| Shift (over an item) | Full tooltip |

The inventory screen has three tabs: INVENTORY, STATUS and CHARACTER. INVENTORY has everything on one screen:
  - **Left (equipment):** your armor in boxes beside your actual character, who turns to follow the mouse. OFF is your off-hand, and MAIN shows whatever's in your selected hotbar slot. Under that, your load and class, health, stamina, and Rage or mana if your skills use them. Hover the load for what it's costing you.
  - **Middle top (crafting):** the crafting grid, always there. See Crafting below.
  - **Middle bottom (player inventory):** the normal 27 slots. The sort icon on the title merges stacks and sorts by kind, then name.
  - **Right (storage):** the chest you opened, or your stash at home once you've built a Storehouse. It has a search box, a tab for ALL and one per chest or barrel (right-click a tab to rename it), and REFILL STACKS, STASH ALL, TAKE ALL, sort and filter icons on its title. Some of those need a higher Storehouse; they're greyed out and the tooltip says which level.
  - **Bottom:** your hotbar in its own frame with number badges, the selected slot glowing, then the auto-refill toggles. Bottom left is the **GATHER LIST** button (see Gather list).
  - **Station row** (top right, at home only): one button per station in your home base. Click one to open it.
  - The vanilla hotbar and hearts hide while any SeanCraft screen is up. Every small icon has a tooltip. Item tooltips are short: the name and one line. Hold **Shift** for the full thing: use time, stack size, durability, part levels, weight, which module you need to craft it, and the key hints. Creative mode keeps the vanilla inventory.

## What's different from vanilla

### Danger, death and home

There's no raid timer any more. Danger comes from how far out you are, the time of day, and what's around.

- **Distance.** The further you get from world spawn, the tougher the monsters, and the more XP they give. The HUD in the bottom left shows which band you're in, and a banner pops up when you cross into a new one.

  | Band | Distance from spawn | Monster health | Monster damage | XP |
  |---|---|---|---|---|
  | Heartland | 0–1,000 | normal | normal | normal |
  | Wilds | 1,000–3,000 | +25% | +15% | +25% |
  | Frontier | 3,000–6,000 | +50% | +30% | +50% |
  | Far lands | 6,000+ | +100% | +50% | +100% |

  Nether distances count 8x, like the travel. A monster never gets past 4x health or 2.5x damage, elites included.
- **Elites.** Monsters can spawn with one or two stars (gold name). Each star is +100% health and +50% damage. None in the Heartland. One star: 5% in the Wilds, 15% in the Frontier, 25% in the Far lands; two stars: 5% in the Frontier, 10% in the Far lands. Those double at night in the overworld. The Nether always uses the Far lands rates.
- **Hunting parties.** At night in the Frontier or further, off safe ground, a party of 2–3 monsters can come for you, led by an elite. At most one a night. In the Nether they can come any time, about once every 10 minutes at most. Parties give up at dawn, and none come while you're in a boss fight. The mobs fit the biome (husks in the desert, strays in the snow, witches in swamps, and so on).
- **Safe ground.** No hostile mob spawns at your home base, near a spawn point, or within 8 blocks of a camp. Mobs can still walk in.
- **Dying.** You leave a grave (YAGM) with everything you had and half your XP. Anyone can open it, so a friend can bring your stuff home. There's no restore key: getting your gear back means going back for it. If you die in a boss arena, the grave goes just outside the ring.
- **Spawn points.** A new world gets 3 spawn points, one at world spawn and two about 400 blocks out on opposite sides. They always go on proper land, not out at sea or on a tiny island: each takes the nearest good land to its spot, and if world spawn itself is at sea, it moves to land too. It reads the world's own terrain generator, so it works with Terralith and other terrain mods. Each has a small shelter with 3 chests and a barrel of starting gear, and the empty ones restock every 30 minutes. New players start at a random one, and so does anyone who dies with no bed or camp.
- **Respawn.** Any bed sets your respawn, except within 100 blocks of a boss dungeon. So does a camp.
- **Camps.** Craft a camp kit at the Home Bench (3 planks on top, stick / campfire / stick, then 3 planks) and set it down out in the world. A camp is a tent and a fire:
  - it sets your respawn there, and using it sets it again;
  - it counts as a fire and a roof for resting;
  - you can change your skill loadout at one;
  - no hostile mobs spawn within 8 blocks.
  - One camp each. Setting down a new kit packs up the old one and gives it back. Sneak + use packs it up. Not within 100 blocks of a boss dungeon.
- **Home base.** Craft a Home Bench (3 cobblestone on top, then plank / crafting table / plank, then 3 planks) and place it. Everything within 30 blocks is your home base.
  - One Home Bench each. Place a second and your old one gets picked up and handed back, so moving house is easy. You can't put one inside someone else's base or within 100 blocks of a boss dungeon.
  - The Home Bench is a 3x3 grid with a recipe book on the left. Fire arrows, stations, modules, totems and the camp kit are made here.
  - **Module stations.** Each module is its own block. Craft it at the Home Bench, put it in your home base (one of each), then use it to build and upgrade it. Hit CONTRIBUTE to put in whatever you have from your inventory and stash; the level is done the moment it's all in, no waiting. TRACK puts what's still missing on your gather list. Break a module and it keeps its level, so you can move it. A built module also opens its own crafting grid; recipes it hasn't reached the level for show a lock.
  - **Station tabs.** At home, the Home Bench, Weapon Bench, crafting table and every module have tabs for each other, so you can hop between them without walking over.

  | Module | What it does |
  |---|---|
  | Storehouse | 1: every chest and barrel at home becomes one stash. 2: chest filters, ORGANIZE, and refill from the stash. 3: the stash at every station, so crafting and furnaces pull from it |
  | Workshop | Furnaces at home cook 25/50/75% faster; level 3 halves Weapon Bench repair costs |
  | Medstation | Unlocks meds; bleeds stop and fractures heal at home; level 3 gives regen at home |
  | Perimeter | Bigger home radius: 48, 72, 96 |
  | Map room | 2: a season and weather forecast when you get home, and everyone hears when a hunting party is after a friend. Level 1 is meant to share maps through JourneyMap; that part isn't built |

### Combat

Swings and rolls come from Better Combat and Combat Roll. SeanCraft puts a price on them.

- **Stamina.** Everyone has 100, plus whatever your food adds. The bar sits right of the hotbar.
  - Sprinting costs 8 a second.
  - A swing costs a quarter of the weapon's damage, times 5. An iron sword is 7.5 a swing.
  - A roll costs 20.
  - Stamina refills at 25 a second, but only after a second without spending any. Keep swinging and it never refills.
- **Running dry.** A swing you start with too little stamina does half damage. At 0 you can't sprint or roll until the bar is back to 25.
- **Rolling.** For the first 0.4 seconds of a roll, attacks (hits, arrows, explosions) can't touch you. Falls, fire, lava, bleeding and poison still can. You can't roll in the middle of a swing, and there's a 0.75 second wait between rolls. Rolls don't use hunger.
- **Equip load.** Your held weapons, shield and worn armor have a weight (light weapons 3, medium 5, heavy 9; armor weighs its armor points; netherite is 20% heavier, phantom membrane 15% lighter). Capacity is 40 plus your Strength. It shows under the hotbar on the INVENTORY tab.
  - Under 30%: no penalty. 30 to 70%: walk 5% slower. 70 to 100%: walk 15% slower and the roll only protects for 0.2 seconds. Over 100%: no rolling, no sprinting.
- **Heavy attacks.** Attack while sneaking. Twice the stamina, 75% more damage, twice the stagger. Swords and daggers lunge, maces and warhammers slam the ground and stagger everything close, axes and halberds spin, spears and pikes sweep the front. A staff fires a charged orb that bursts.
- **Poise.** Blunt hits build up on whatever they hit. A mob staggers after blunt damage worth 30% of its health, a player after 60. It drains after 3 seconds without a blunt hit. A mob that's close to staggering throws off stone chips and flinches on each hit, so you can see it coming and press.
- **Shields.** SeanCraft does its own blocking: a shield takes a share of a hit from the front and the rest gets through. Rim material sets it: copper 80%, iron 84%, gold 88%, diamond 92%, netherite 95%, plus 0.2% per Strength point over 10. Blocking costs half the blocked damage in stamina; a block that empties the bar breaks your guard (shield down for 3 seconds, staggered for 1).
  - **Parry:** raise the shield within 0.2 seconds of a hit (0.35 with a buckler). No damage, no stamina, the attacker staggers, and the Blademaster's riposte opens.
  - **Buckler** (Blademaster only): 60%, light. **Tower shield** (Knight only): blocks everything in front including arrows, can't parry, slows you 30% while raised, heavy.
- **Throwing.** Spears, throwing knives, javelins and boomerangs: hold use to wind up, let go to throw. Thrown at iron: knife 22, javelin 35, spear 32, boomerang 20. They stay where they land for 5 minutes, glowing. The boomerang comes back.
- **Wands and staves.** Attacking fires a bolt: 18 damage for 4 mana from a wand, 35 for 8 from a staff, times the crystal's factor. A wand can fire every 0.6 seconds, a staff every second (its charged orb every 1.5); swings in between fire nothing and cost no mana, and the hotbar shows the cooldown sweep. The crystal's catalyst sets the element. Holding one also makes your INT skills stronger by the same factor. The Arcanist now starts with a quartz wand.
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
- **Fire arrows.** Made at the Home Bench: an arrow in each corner, coal or charcoal top middle, string in the centre. Makes 4. They hit with pierce plus some fire, and set what they hit alight. Bows with Flame add a smaller fire share.
- **Other arrows** are at the crafting table now, 4 at a time, laid out like a vanilla arrow (tip over stick over feather): bodkin (iron nugget tip) ignores another 25% of armor; broadhead (iron ingot tip) makes bleeds half again as bad but gets through 10% less armor; blunt (leather tip) does blunt damage, so it breaks bones and staggers instead of bleeding.
- **Potions.** One of each type every 5 minutes (long and strong versions count as the same type). Fire Resistance gives 20 fire resistance points instead of making you immune to fire and lava. Resistance adds 10 armor a level to every body part. Strength is 30% more damage a level instead of a flat bonus.
- **The vanilla Mace.** Its smash bonus is capped at 50% and does nothing in a boss arena.

### Food and rest

- **No hunger.** You can't starve, and food doesn't heal you the vanilla way. Instead you have 3 food slots, shown in the top left next to the body figure, each with a bar for the time it has left.
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
  - Comfort is 1 for each kind of thing within 8 blocks (a fire, a bed, a carpet, a banner, a table, flowers or a flower pot), plus 1 for each module built at the home base you're in, plus 1 or 2 if the land around is Good or Remnant (see Ecology).
  - It works away from home too, and a camp counts as a fire and a roof, but there's usually less comfort out there.

### Inventory

- **It's the vanilla inventory:** 27 slots, the hotbar, armor and off-hand. No grids, rigs, backpacks or pouches any more.
- **Controls** work like vanilla: click, right-click for half or one, drag to split, **1-9** to put the hovered item on that hotbar slot, **Q** drops one, Ctrl+Q the stack.
- **Shift-click** moves things across:
  - from a chest or the stash into your inventory;
  - from your inventory into the open chest or stash;
  - with nothing open, from the main slots to the hotbar (armor goes on if it fits), and from the hotbar to the main slots.
- **Auto-refill.** When the last item in a hotbar slot gets used up (a block placed, food eaten, a tool broken), more of the same comes up from your main slots. The toggles next to the hotbar turn it off, or let it pull from the stash too (off by default; needs a Storehouse).
- **Ammo.** Bows and crossbows pull arrows like vanilla. The stack your bow will shoot next gets a green outline.
- **Chests and barrels** open on the INVENTORY tab as a list. Each one has a SORT button and a TAKE ALL button. REFILL STACKS tops up the part stacks you already carry (arrows, planks, bandages...) to full from the open chest, or from the stash at home with Storehouse 2. It never hands you anything you weren't already carrying. STASH ALL (at home) puts your 27 main slots into the stash and leaves the hotbar alone.
- **Carrying chests.** Shift + left-click a chest or barrel to pick it up with everything in it, and Shift + right-click to set it down again. While you carry it you walk slowly and can't sprint, switch hotbar slots, use items, fight, mine or open any menus. If you die carrying one, it and its contents drop where you fell. Spawn point chests can't be carried off.
- **Naming chests.** Right-click a chest's tab to rename it, then hit Enter.
- **Chest filters** (Storehouse 2) work like RimWorld stockpiles: allow or disallow weapons, ammo, armor & gear, tools, food, meds, parts, blocks and materials. STASH ALL and shift-click into the stash put things where the filters say, narrower filters first. ORGANIZE moves anything in the wrong chest to one that allows it and merges half-stacks. Filters only steer automatic moves.
- **Furnaces** (and smokers and blast furnaces) show your fuel and anything that furnace can cook beside it. Click one to put a stack in the right slot, or right-click for one. With Storehouse 3 there's a second row from the stash. Tools, weapons and enchanted stuff are left off the fuel list.
- **Crafting.** The grid sits at the top middle of the INVENTORY tab. It's 2x2 out in the world and 3x3 at a crafting table or at home (the title says which). RECIPES opens the recipe book over the equipment side: search, a CAN MAKE NOW box, folding categories, and every recipe, with ?????? for ones you haven't found. Right-clicking a crafting table opens the inventory with the book already open.
  - Pick a recipe and its pattern shows as faded icons in the grid. NEEDS beside the result lists each ingredient with have / need, green when you've got enough, red when you're short. You lay it out yourself and click the result for one, or Shift-click for as many as you can.
  - Have/need counts include your inventory, and the stash with Storehouse 3.
  - The pin button next to RECIPES puts the recipe's ingredients on your gather list.
  - Until you've placed a Home Bench, it's pinned to the top of the book.
- **Gather list.** A to-do list for materials. Pin a recipe, hit TRACK on a module, or hover an item and press T. Hit **GATHER LIST** at the bottom left of the inventory to change it: each line has - and + (Shift does steps of 10) and an X, and there's CLEAR ALL. Away from home it shows in the top left with how many you're carrying, green when you have enough. Up to 6 things.

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
  - Top left, a small body shows each part's health: green, then orange, then red, dark when destroyed. Problems show as tiny icons on the part itself: a blood drop for a bleed (the part also flashes), a bone for a fracture, a barrier for a destroyed part. The exact severities are on the STATUS tab.
  - Next to it, under your food slots, small lines say what the injuries are costing you right now, like `Walk -18%`, `Sprint -29%`, `Jump -4%`. They turn cyan while painkillers are taking the edge off.
  - A biome hazard gets its own line in the bottom left.
  - Every effect you have, good or bad, shows as a small icon with the time left. That covers poison, painkillers, and ability effects like going invisible. It blinks yellow for the last 5 seconds. This replaces the vanilla effect icons in the top right, which don't show a timer.
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
- **Sleeping** through the night heals every body part that isn't destroyed by 50% of its max (`Health.SleepHealPercent`). Destroyed ones still need surgery, and sleep can't get past lasting damage.

### Weapons and armor from parts

- Swords, axes, daggers, maces, warhammers, greatswords, halberds, spears, pikes, bows, crossbows, shields and metal armor are made from parts. The vanilla recipes for them are gone.
- Everything happens at the **Weapon Bench** (a smithing table and a crafting table, shapeless). The crafting table is vanilla only, except for the **wooden club**: 2 logs over a stick, so you've got something to hit with before you find a bench. It's blunt and has no parts.
- **Stash on station screens.** On the Weapon Bench, the Home Bench, a crafting table, anvil or any other screen that shows your inventory, the home **STASH** sits in a box on the left (at home, with Storehouse 3): click an item to take a stack onto your cursor, right-click for one, or drop whatever you're holding on the box to put it away (it goes where the chest filters say). The `>` cell flips pages when there's more than fits.
- **Names.** The BUILD slots say which part they take when the names differ, like "String (bowstring)", "Grip (handle)", "Stock (handle)" or "Rim (guard)", and the parts book lists them that way too ("Handle / grip").
- **Making parts.** Under BUILD (and REPAIR) there's always a 3x3 grid with the pattern book on its left, no tab to switch to. Parts are made by pattern: lay the material out in the part's shape and take the part from the result slot. The material you use decides the part's material, and every square with the same letter has to be the same material. Click a part in the book to see its pattern as a ghost.
  - The letters: M is metal (copper ingot, iron ingot, gold ingot, diamond), W is wood (planks, logs, bone, blaze rod, breeze rod), H is hide (string, leather, rabbit hide, phantom membrane, armadillo scute), T is bowstring material (string, leather, rabbit hide, cobweb, phantom membrane), G is a gem (quartz, lapis, amethyst shard, prismarine shard, echo shard), C a catalyst, S a stick.
  - Patterns, top row first: knife blade `M`; blade `M / M`; long blade `MM / MM`; axe head `MM / M.`; mace head `.M. / M.M`; spearhead `.M / M.`; halberd head `MM / M. / M.`; plate `MM`; guard `MSM`; focus `M.M`; limbs `W. / .W / W.`; handle `W / W`; haft `W / W / W`; crystal `G / C / G`; wrap `H. / .H`; bowstring `T / T / T`; padding `HHH`. They work anywhere in the grid and mirrored.
  - **Bow limbs are wood now** (planks, log, bone, blaze rod, breeze rod), so they take the wood traits.
  - **Wraps and bowstrings are different parts.** Wraps go on melee weapons, shields and armor; bows and crossbows take a bowstring: String, Rawhide (leather), Sinew (rabbit hide), Silk (cobweb) or Membrane (phantom membrane). A better string draws faster and throws a little harder. Bows made before this keep their old wrap as a string of the same tier.
  - **Wood species.** Plank and log parts remember their tree, and it's in the name ("Spruce Log Limbs"). All the wood squares have to be the same tree. On bow and crossbow limbs it matters a lot; on handles, hafts, stocks and other wood parts it's a third as strong (and on melee weapons the speed part becomes swing speed):

    | Tree | Damage | Draw / swing | Durability | Feel |
    |---|---|---|---|---|
    | Oak | 0 | 0 | +10% | all-round, sturdy |
    | Spruce | +10% | −5% | +5% | springy and strong |
    | Birch | −5% | +10% | 0 | light and quick |
    | Jungle | 0 | +5% | +15% | dense, lasts |
    | Acacia | +15% | −10% | −5% | hard and stiff |
    | Dark oak | +12% | −12% | +10% | heavy and strong |
    | Mangrove | +5% | 0 | +20% | won't rot |
    | Cherry | −5% | +15% | −10% | whippy |
    | Bamboo | −12% | +20% | −15% | very light, fragile |
    | Crimson | +8% | +5% | −5% | nether-grown, snappy |
    | Warped | +3% | +8% | +5% | nether-grown, even |
  - **Bows.** The vanilla bow is the **Shortbow**: quick to draw. The **Longbow** (built at the bench: limbs, grip, bowstring; on the STR and AGI ladders at rung 2, scales 50/50 STR/AGI) takes half again as long to draw, but its arrows fly 30% faster, so they hit harder and go further. The **Crossbow** loads at its parts' pace the same way. The bench shows a bow's Draw (or Reload) next to its damage.
  - **Netherite:** netherite scrap, a diamond part, netherite scrap in a row.
  - **Levelling:** the part in the centre, ringed by exactly the right number of its own material: level + 1 for metal and gems, twice that for wood and hide, never more than 8. Each level adds 6% to the head's damage, up to 4. The ring squares to fill light up.
  - **Infusions:** the wrap pattern plus one extra iron nugget (Heavy), flint (Keen) or lapis (Arcane) anywhere in the grid. Heavy moves all of the weapon's scaling to STR and its hits build 50% more poise; Keen moves all of it to AGI and its bleeds are 50% worse; Arcane moves half of it to INT and each hit gives back 2 mana.
  - **Crystals:** the catalyst in the middle sets the element: blaze powder fire, packed ice frost, a lightning rod lightning, a fermented spider eye poison, a golden carrot spirit.
  - Parts only locked weapons use show a lock in the book, and the grid won't make them.
- **BUILD tab.**
  1. Pick what to build. You get an outline of it, with each part's slot sitting where that part goes: blade on top, guard across, grip and pommel below, and so on.
  2. Empty slots show a faded example of the part that goes there. Hover one for its name, and the plate slot shows how many plates it takes.
  3. Drop the parts in and take the weapon from the result slot. The blade, axe head or plates decide what you get (copper makes the stone version); the other parts can be any material. If the result slot stays empty, hover it to see what's missing.
- **REPAIR tab.** Like an anvil: the item on the left, its striking part's material next to it, the repaired item on the right. 1 material per 25%, or per 50% on a bench at home with Workshop 3.
- The standard parts give you exactly the vanilla item. Other materials change damage, swing speed, durability, armor and weight.
- **Weapon families.** Each one has its own moveset (from Better Combat), damage type and base numbers. At iron:
  - Dagger (knife blade, handle): 20 damage, 2 swings a second, pierce and slash. Quick strikes leave enemies open again after 0.1 seconds, so every hit of a flurry lands, and hits from behind do 50% more.
  - Mace (mace head, handle, wrap): 35, blunt. Warhammer (mace head, haft, wrap): 45, slow, blunt, two-handed, ignores an extra 25% of armor. Both can knock a raised shield out of use, like the axe.
  - Greatsword (long blade, long grip, guard, wrap): 50, slash, wide two-handed sweeps.
  - Halberd (halberd head, haft, wrap): 45, slash and pierce, reach.
  - Spear (spearhead, haft, wrap): 32, pierce, reach, ignores an extra 25% of armor. Pike: 40, pierce, even longer reach.
  - Materials scale damage: copper ×0.85, iron ×1, gold ×1.1, diamond ×1.2, netherite ×1.35.
- **Material traits.** Every material also has one trait, shown in pink on the part and on the item's part list. Vanilla items come with the traits of their standard parts, so a plain iron sword already has 300 durability instead of 250.
  - Heads: copper +10% added lightning, iron +20% durability, gold +15% added spirit, diamond +5% crit chance, netherite +20% added fire and doesn't burn in lava. Iron plates get the +20% durability too.
  - Handles, hafts and bow limbs: planks +5% swing speed but −15% durability, log nothing, bone +15% durability, blaze rod +5% added fire, breeze rod +10% swing speed.
  - Wraps and padding: leather nothing, rabbit hide makes you harder for mobs to spot while sneaking (stacks per piece), phantom membrane is lighter, scute padding gives +2 armor.
  - The old "gold does more to undead, netherite sets things on fire" part bonuses are gone; the trait is the bonus now.
- **Recipe ladders.** You can use any weapon you find, but you can only make the ones you've unlocked. Each attribute has a ladder:
  - STR: wooden club → mace, axe, sword, longbow → warhammer, greatsword, halberd.
  - AGI: shortbow, dagger → sword, spear, crossbow, longbow, throwing knife → javelin, boomerang, pike.
  - INT: wand → staff.
  - You start knowing the first rung of your origin's ladder. 5,000 damage to monsters with rung 1 weapons opens rung 2, and 25,000 with rung 2 weapons opens rung 3. Swords are on both ladders.
  - Crossing over: do 1,000 damage with a found weapon from another ladder and you unlock that weapon, plus the first rung of its ladder, which you can then climb too.
  - Locked weapons are greyed out at the bench, and hovering one shows your progress. Heads that only locked weapons use can't be made. The wooden club recipe is locked the same way.
  - Shields and armor aren't on a ladder. The buckler is Blademaster only and the tower shield Knight only.
  - Throwing knife is AGI rung 2; javelin and boomerang AGI rung 3; wand INT rung 1, staff INT rung 2.
- Put an existing item in the left slot on BUILD and its parts show on its outline. You can swap them out; level a part in the parts grid underneath.
- **Requirements.** Rung 2 weapons want 15 in their scaling attribute and rung 3 want 30 (half of each for split weapons like the sword). Under that, they do 30% less damage and cost 30% more stamina. The tooltip shows it, red when you're short.
- **Wear.** Part-built gear never breaks. At zero durability it works at half strength (half damage, half armor, half block) until you repair it on the REPAIR tab.
- Vanilla swords, axes, bows, crossbows and shields you pick up turn into part-built items with their standard parts.
- Swords, axes, daggers, maces, warhammers, greatswords, halberds, spears, pikes, bucklers, tower shields, throwing knives, javelins, boomerangs, wands and staves are drawn from the parts actually in them, so a swapped blade shows.

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
- **Specialization.** Your starter web ends at three gates, one for each of your origin's specializations. Taking one needs level 20, any tier 1 boss trophy, and being at your home base. It gives you a title, opens the whole web (with a zoom-out the first time), opens your ultimate (V), and opens the first of your specialization's three signature nodes at the outer edge of its region. You get one specialization.
- **Milestones.** Mastery (level 40 and any tier 2 trophy) opens your second signature node and ranks your ultimate up; Capstone (level 60 and a tier 3 trophy) opens the third and ranks it up again. Tier 2 and 3 bosses aren't in yet, so for now only the first step is reachable.

  | Specialization | Signature nodes (specialization / mastery / capstone) | Ultimate (cost) | Mastery / capstone rank |
  |---|---|---|---|
  | Warrior | Can't be staggered while Rage is above half / +50% rage gain, +10% melee / +25% melee while Rage is above half | Rampage (50 Rage): 8 s of free Rage skills, no staggers, +20% damage | 10 s / each kill adds 1 s |
  | Knight | Players within 6 blocks take 15% less / blocking pulls the attacker and nearby monsters onto you / +10 armor, +50% knockback resistance | Bulwark (40 Rage): 6 s, players behind you take 50% less, you 30% less | 60% less / players behind you can't be staggered |
  | Paladin | Melee hits add 20% spirit / players within 8 blocks bleed 30% slower / another 20% spirit and a holy aura | Hallowed Ground (60 Rage): 8 s, 6-block circle, spirit damage to undead, 30% slower bleeding | 8-block circle / bleeds stop inside |
  | Archer | Arrows slow / +15% arrow damage, +5% crit / arrow crits pin | Deadeye (40 stamina): next three arrows always crit and pin | four arrows / they pierce |
  | Blademaster | +50% on your first hit after a block / sword hits leave enemies open fast, like a dagger / +15% swing speed, +10% crit | Thousand Cuts (50 stamina): dash through up to five enemies, three cuts each | seven targets / every cut bleeds |
  | Rogue | +25% sneak speed, +25% backstab / invisible while sneaking / +50% backstab, +25% crit damage | Vanish (40 stamina): 5 s invisible, things lose you, first hit is a backstab | 7 s / monsters can't pick you back up while it lasts |
  | Sorcerer | +30% skill area / +20% skill damage / skills cost 20% less mana | Cataclysm (70 mana): 5 s of lightning on monsters around you | 7 s / lightning and frost at once |
  | Warlock | Skill hits hex for +15% damage taken / melee hits poison / hex +15% more, +10% skill damage | Doom (60 mana): 8 s of double damage from its weaknesses; it bursts into poison if it dies | 10 s / the burst passes Doom to the nearest enemy |
  | Cleric | Meds apply and heal 30% better / surgical kits leave 15% lasting damage instead of 35% / you and players near you regenerate | Miracle (80 mana): revives every destroyed part on players near you, with lasting damage | lasting damage halved / also clears fractures |

  Ultimates recharge in 90 seconds, 75 at mastery and 60 at capstone.
- **Skills and loadout.** Learned skills go in three slots (Z, X, C); V is the ultimate. You can learn more than you can equip, but the loadout only changes at home or at a camp. Shift-click a learned skill on the web to rank it up, up to 4, for a point each. Each rank does one specific thing (the web tooltip says what the next one is); there's no flat bonus per rank any more.
- **Paying for skills.** Warrior skills cost Rage, Ranger stamina, Mage mana. The in-between regions pay from either of two pools, whichever is fuller right now: Duelist Rage or stamina, Shadow stamina or mana, Templar mana or Rage. Above each slot is a little plate with the cost: one icon and the number, or for those regions both icons with the one it'll use lit.
- **What hands you need.** Warrior and Duelist skills need a melee weapon in hand, Ranger skills a bow or crossbow, Mage skills a wand or staff. Shadow and Templar need nothing. No skill makes you invulnerable (that's the roll's job) and none heals.
- **Skill damage** is built on the gear in your hand. W is your weapon's damage, SP is spell power (a staff bolt's 35 times your crystal's factor; 0 without a wand or staff), and Shadow and Templar use whichever is higher ("power"). On top of that, every skill gets +1% per point of its region's attributes, spell-damage nodes and Casting.

  | Region | Skill (cost, cooldown) | What it does | Ranks 2 / 3 / 4 |
  |---|---|---|---|
  | Warrior | Leap Slam (25, 10 s) | Leap up to 8 blocks after a short crouch; landing does 150% W blunt in 3 blocks with double poise | 200% / radius 4 and a 2 s slow / 6 s cooldown, reset on a kill |
  | | War Cry (20, 20 s) | Players within 8 deal 15% more for 6 s; monsters within 6 turn on you and deal 10% less | 9 s / you can't be staggered for 3 s / +25% |
  | | Ground Slam (30, 12 s) | 0.6 s wind-up, then 200% W blunt in a 5-block cone; staggers anything but a boss | 7-block cone / slows 30% for 4 s / 250% |
  | | Berserk (40, 30 s) | 8 s of +25% attack speed, +20% damage dealt, +20% damage taken, and Rage doesn't drain | 10 s / your bleeds pause / +35% attack speed |
  | Duelist | Lunge (15, 6 s) | Dash 5 blocks into a target for 140% W pierce; interrupts a creeper's fuse, a drawn bow or an illager's spell | 170% / 7 blocks / 4 s cooldown |
  | | Riposte (10, 8 s) | 0.6 s stance: the next melee hit on you is parried, no shield needed, and countered with a 200% W crit | 0.8 s / the counter bleeds / refunded when it lands |
  | | Whirlwind (30, 14 s) | Spin 2 s: 60% W to everything within 2.5 blocks every 0.4 s, half speed, can't block | 2.5 s / pulls small mobs in / radius 3.5 |
  | | Blade Flurry (20, 10 s) | 5 strikes in a second at 50% W; each can crit and bleed | 6 strikes / +5% crit chance per strike / last strike 150% |
  | Ranger | Dash (20, 8 s) | Dash 6 blocks; your next shot within 2 s is fully drawn | 6 s cooldown / 2 charges / clears slows |
  | | Volley (25, 10 s) | 5 arrows in a 40° fan for one arrow, each 60% of a full draw | 7 arrows / fires your arrow type / 90% |
  | | Rain of Arrows (35, 18 s) | 1 s tell, then 8 volleys over 3 s on a 4-block circle up to 30 blocks away, 40% of a full draw each, and a slow | radius 5 / 4 s / burns if you carry fire arrows |
  | | Hunter's Mark (10, 15 s) | 12 s: the target takes 15% more from everyone and glows through walls | +20% / no invisibility, 20% less poise / cooldown resets if it dies |
  | Shadow | Smoke Bomb (20, 16 s) | 4-block cloud for 5 s: monsters inside lose their target, players inside count as sneaking | 6 s / slows monsters inside / radius 6 |
  | | Shadow Step (20, 10 s) | Teleport behind a target up to 10 blocks away; your next hit within 2 s is a backstab | 14 blocks / 7 s cooldown / that hit poisons |
  | | Poison Cloud (25, 14 s) | A 3-block cloud where you're aiming: 30% power poison a second for 6 s | 8 s / keeps poisoning 3 s after leaving / radius 4 |
  | | Blade Fan (20, 8 s) | 5 knives in a fan, 70% power slash each, can bleed | 7 knives / heavier bleeds / 90% |
  | Mage | Fireball (20, 4 s) | 0.5 s cast: 2.0×SP fire to what it hits and 1.0×SP in 2 blocks around; burns | 2.4× / radius 3 and burning ground 3 s / 0.3 s cast |
  | | Frost Nova (25, 12 s) | 1.2×SP frost in 5 blocks; roots anything but a boss 2 s, slows bosses 30% for 3 s | 3 s root / radius 6 / frozen targets take 25% more from the next hit |
  | | Blink (15, 8 s) | Teleport 8 blocks toward the crosshair, never through walls | 10 blocks / 2 charges / 5 s cooldown |
  | | Chain Lightning (30, 10 s) | 0.6 s cast: 1.6×SP to the target, then up to 3 more within 6 blocks, 25% less each jump, with a short stun | 4 jumps / no loss on the first jump / 1.9× |
  | Templar | Consecrate (25, 18 s) | 4-block circle for 8 s: players in it bleed 50% slower, undead take 40% power spirit a second | 10 s / fractures don't get worse in it / radius 5 |
  | | Holy Smite (15, 6 s) | Your next melee hit or wand bolt within 10 s adds 150% power spirit and staggers undead | 200% / hits everything within 2 blocks / 4 s cooldown |
  | | Divine Shield (30, 25 s) | Shield yourself or the player you're looking at (8 blocks) for 25% of max health for 6 s; no new bleeds or fractures while it holds | 30% / 8 s / knocks monsters back when it breaks |
  | | Judgment (35, 16 s) | 0.8 s cast, then a second later a pillar where you're aiming (up to 20 blocks): 2.5×power spirit in 3 blocks, double on anything under 25% health | 3× / radius 4 / kills anything but a boss under 15% |
- **Resources.** Rage (Strength skills) starts at 100, Strength raises it; +5 per hit you land and +10 per hit you take, and it drains 10 a second after 5 seconds out of combat. Mana (Intelligence skills) starts at 100, Intelligence raises it. Stamina is everyone's.
- **Crits.** Every melee hit and arrow has a chance to crit. You start at 5%, Agility adds to it, and it caps at 75%. A crit does +50% damage, plus 0.25% per point of Strength. Some web nodes add more of both. Your numbers are on the CHARACTER tab.
- **Use-skills** level up as you use them: swords, axes, blocking, mining, bows & crossbows, running, sneaking, swimming, enchanting, brewing, casting and farming. You lose 5% of your skill progress when you die. Firing a grappling hook (from the Grappling Hook Mod) also trains bows & crossbows.
- **Respec.** Use the Altar of Regret (2 gold ingots and a book over 3 crying obsidian) at your home base. It wipes your web and specialization, frees all your attribute points to place again (the [+] buttons on the web screen), and kills you. Below level 21 you get every web point back. From 21 you get 75% back, and from 41 you get 50% and it also needs an Orb of Regret. Your origin stays, and the whole web stays open.
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

### Ecology

The world keeps track of its wildlife, soil and water, even where nobody is. It's split into patches 64 blocks across, and each one holds a few numbers, only ones you can see the effect of within a session or two: how much small game there is, forage, fish, soil fertility and moisture, pests, and water pollution. A patch updates once an in-game day, and right away when you harvest, hunt, fertilize, spray or burn there. One that was unloaded jumps to where it was heading when you come back (up to 30 days). Phases 1 and 2 of the design are in. Monster pressure, villages, disease, deer and wolves, and beavers come later.

- **Rabbits and foxes.** Each patch has a small-game level that refills to a cap within about 3 in-game days. The cap comes from how much grass there is and the patch's quality. Hunting takes it down; hunt a patch out and it's thin for a few days. Foxes don't have their own number: they show up where the rabbits are, a few days behind. You'll see a handful of rabbits near you, more where there are more.
  - **Fox raids.** At night, where foxes are about, a crowded pen (6+ chickens, sheep, pigs or cows packed together) with no cover and no fence around it can lose a few animals. Fence it, give it room, or plant some cover.
- **Neighbours.** Once a day, a patch's game drifts toward its neighbours', so a hunted-out patch refills from around it. Walls, roads and open farmland close an edge, unless hedges or trees cross it. Good and Remnant patches refill their neighbours faster. A patch closed off on all sides holds less game, and its pickiest prairie plants slowly die out.
- **Soil.** Fields have a fertility number (50 on fresh ground). Every crop stage takes a little, so a field farmed hard yields less each harvest, and worn-out soil also slows growth. Ripe wheat, carrots, potatoes and beetroot drop more or less of their crop depending on the soil (seeds aren't affected). Bone meal, rotten flesh and bat guano on farmland add fertility over the next day or two. Leadplant adds a little on its own.
- **Pests.** Wheat gets aphids, worse on big wheat fields. A cave, bat roost, hedgerow or trees in the patch or next to it cut their growth by a third. You'll see specks over badly hit wheat.
  - **Insecticide** (Workshop 1: fermented spider eye, poisonous potato, glass bottle; makes 2) knocks the aphids down, but switches off that natural-enemy bonus for about a week, so they come back harder. Spray the same patch again and again and they build resistance.
  - **Bat guano** drops under roosting bats. It's a fast fertilizer.
- **Look at a crop** and the bottom of the screen says what's holding it back: worn-out soil, water or pests (and how resistant the pests are).
- **Runoff.** Past 80 fertility, the extra washes out to the nearest water downhill, even a few patches away. So the lake below a field you keep bone-mealing gets it.
  - **Green water and dead zones.** Past one level of pollution the water gets green scum; past a second the fish die (dead fish float). It turns within a few days of heavy fertilizing, and clears over a week or two once it stops. Big lakes take more to turn than ponds. Half of what clears flows on to the next water downhill.
  - **Prairie strips.** A strip of grass 4 or more blocks wide between a field and the water stops most of the runoff, if the patch is Fair or better.
- **Fish.** Each patch with water has a fish level. Every catch takes a little, and in thin water more bites get away ("It got away."). Fewer fish spawn where it's low. They come back over days unless the water's dead.
- **Soil kit** (Workshop 1: iron nugget over glass bottle over paper). Use it on the ground: fertility, moisture, the water (clear, runoff coming in, green, or dead zone, plus how the fish are doing), what's holding a crop back, how many fields by the water have a strip, and the patch's ecosystem quality with what's dragging it down.
- **Ecosystem quality** runs 0 to 100: Degraded, Weedy, Fair, Good, Remnant. It's plant variety (each kind of plant has a score: grass and dandelions 0, lily of the valley 6, the prairie plants 5 to 9; the scores of the different kinds add up, capped at 40), times how free it is of threats nearby (villages, cropland, crowded pens, roads, buildings), with a third off if it's over-fertilized or not burned or grazed on schedule.
  - **Walking into Good or Remnant ground** puts its name at the top of the screen, like a biome name. There are butterflies over the flowers there in the day.
  - **Prairie plants:** big bluestem, compass plant, butterfly milkweed, pale purple coneflower, rattlesnake master and leadplant. Remnant prairie patches of them generate in plains, savannas and meadows; that's where the seed comes from.
  - Prairie needs fire or grazing every 10 to 30 days. Go 40 days without either, or over-fertilize it, and the prairie plants die back to grass. Disturb it more often than every 5 days and they won't spread. In between, they slowly spread into the grass around them.
  - A Good patch nearby makes crops yield up to 15% more (Remnant: 25%), holds more rabbits, and adds 1 comfort to a home base beside it (Remnant: 2).
- **Seasons.** Serene Seasons drives the year: rabbits refill faster in spring and summer, aphids peak in summer and die back in winter, and prairie plants only spread in spring and summer.
- Inside a home base, nothing turns to dirt from overgrazing and the prairie plants don't die back, but the soil and animals still run.

### Bosses

- **Dungeons.** Each boss has one dungeon in its home biome, out in its tier's distance band (tier 1 is 500 to 2,000 blocks from spawn). It gets built the first time someone comes near it. The totem's entry at the Home Bench says roughly how far and which way, and crafting the totem gives you a map to it.
  - Mark: a sandy landing on a beach or river mouth. Daylan: a photo-shoot clearing in a cherry grove. Bo Daddy: a lit cavern dug in at the edge of the deep dark. It's walled in, so you tunnel to it.
  - No Home Bench within 100 blocks of a dungeon.
  - **Custom dungeons.** Build one in a creative world and save it with a structure block named `seancraft:dungeon/<boss>` (`mark`, `daylan` or `bo_daddy`). Copy the saved `.nbt` from the world's `generated` folder into `src/main/resources/data/seancraft/structure/dungeon/`. From then on that boss's dungeon is your build instead of the built-in one. The bottom layer is the floor, set at ground level (at the site's depth for Bo Daddy), and the Summoning Pedestal goes wherever you put one in it, or in the middle if you didn't. Keep the 40-block ring in mind.

- **Summoning.** Craft the boss's totem at the Home Bench and use it on a Summoning Pedestal. The boss rises after 3 seconds. The totem is used up. Beaten bosses can be summoned again.
- **The arena** is a ring 40 blocks across around the pedestal. Everyone inside sees the boss bar.
  - While the boss is alive, nobody can break or place blocks in the ring.
  - Hits from outside the ring do nothing.
  - The boss walks back if you pull it out, and it never chases you out.
  - It can't be put in a boat or minecart.
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
- **Trophy powers.** Craft a Trophy Rack at the Home Bench (6 planks, an item frame, 2 iron) and put it in your home base.
  - Use it with a trophy to put the trophy on the rack. Use it empty-handed to choose that trophy's power, and sneak to take a trophy back.
  - Press Y to use your power: 5 minutes on, then 20 minutes to recharge.
  - Old Salt (Mark): Tailwind, faster swimming.
  - Clout (Daylan): half again as much skill and class XP.
  - Too Much Soul (Bo Daddy): Bass Drop. You can't be staggered, and your heavy hits (40 damage or more) stagger mobs.

### Joe and Sean

- **Joe** is a small green goblin who wanders around looking for Sean, says stuff in chat, throws up, and steals from your main inventory (never your hotbar or what you're wearing). He leaves you alone unless you hit him, or unless someone says "baby wars" in chat. When he dies he drops everything he stole. He gets stronger as players progress.
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
  - `Danger.DistanceScaling`, `Danger.Elites` and `Danger.HuntingParties`: turn each of those off.
  - `Danger.DistanceBands` (`1000,3000,6000`) and `Danger.Caps` (`4.0,2.5`, the most distance and elite stars together can multiply a monster's health and damage).
  - `Home.Radius` (30, before Perimeter), `Home.MaxBenches` (1) and `Home.PedestalClearance` (100, no Home Bench, bed respawn or camp that close to a boss dungeon).
  - `Camp.SafeRadius` (8) and `Health.SleepHealPercent` (50).
  - `Spawn.*`: number of spawn points, distance (400), safe radius, restock time. A config file from before this update that still says 1000 (the old default) is moved to 400 once.
  - YAGM's own config is overridden on every start: graves open to anyone, 50% XP kept, no restore key.
  - `Combat.*`: `HealthBase` (300), `DamageScale` (5), `ArmorK` and `ResistK` (20), `StaminaRegen` (25 a second), `RollIframeTicks` (`8,4`: light or medium load, then heavy) and `RollRechargeSeconds` (0.75). Every skill and weapon number assumes `DamageScale` 5, so changing it throws the balance off.
  - `Potion.CooldownMinutes` (5), `Character.AttributePointsPerLevel` (2) and `Weapons.LadderDamage` (`1000,5000,25000`: crossover, rung 2, rung 3).
  - `Ecology.*`: `Enabled`, `PatchChunks` (4, patch size in chunks; changing it starts the model over), `TickBudgetMs` (2), `DayScale` (1; raise it to speed everything up), `CatchUpMaxDays` (30).
  - Every ecology number (breeding rates, fox hunting, soil, pests, water, fish, dispersal, plant scores) is in `data/seancraft/ecology/params.json`, and a datapack can override it.
  - `Boss.HealthPerPlayer` (85), `Boss.DamagePerPlayer` (10), `Boss.CooldownPerPlayer` (20), all percent per extra player, and `Boss.ScalingCap` (6 players).
- Damage and healing are 5x vanilla, and so is monster health. Sean's and Joe's health settings are multiplied too. Minecraft caps any mob's max health at 1024, so the Wither can't reach its full 1,500.
- **Commands:**
  - `/seancraft where` shows where Joe and Sean are.
  - `/seancraft spawnpoints respread` (op) rebuilds the spawn points other than world spawn at `Spawn.Distance`, on land. If world spawn's point is out at sea, it and the world spawn move to land too. Use it on a world whose spawn points ended up in the ocean. New shelters get built; the old ones stay as plain buildings and stop restocking.
  - `/seancraft summon joe|sean`, `/seancraft babywars start|stop`, `/seancraft pedestal` (puts a Summoning Pedestal under you), `/seancraft dungeons` (lists where the dungeons are; `/seancraft dungeons build <boss>` builds one now) and `/seancraft reload` need op.
  - `/character xp|skill|reset` is for testing the character system (op). `/character learn <player> <SKILL> <rank> <slot>` learns an active skill at a rank and puts it in slot 0-2, skipping the web. `/character ladder <player>` shows ladder progress, and `/character ladder <player> "<id>" <value>` sets it (ids like `STR1`, `AGI2`, `x:mace`, `u:mace`, `on:STR`).
  - `/seancraft eco patch` shows everything about the patch you're in; `/seancraft eco step <days>` runs it forward; `/seancraft eco set <field> <value>` sets one number (like `fertility`, `game`, `pest`, `pollution` or `fish`) for testing (all op).
  - `/seancraft injure <part> <bleed|fracture|scar|destroy|heal> [percent]` sets an injury on yourself, for testing (op).

## Works with

- **YAGM:** required, for graves (see Dying).
- **Serene Seasons:** required. The ecology and the Map room forecast follow its seasons.
- **FallingTree:** trees you chop go into your inventory, and axes made from parts work with it.
- **Better Combat and Combat Roll:** required (see Combat).
- **Grappling Hook Mod:** needs Forge Config API Port too. Hooks go to your hotbar, and using them trains bows & crossbows.

FallingTree and the Grappling Hook Mod were checked by reading their code, and neither has been played alongside this mod yet. Better Combat and Combat Roll were tested in game.

## Custom icons

Most items, parts and station blocks now use the SeanCraft asset pack (Appendix C palette). The part-built weapons are layered from the pack's part textures, so they change look when you swap parts. Station blocks face you when placed. The screens and HUD are drawn with the pack's GUI kit, following the UI spec (Appendix C, v16): forged-iron panels, soot slots, one gold accent, and colours that always mean the same thing (gold for selection, blood for danger, injuries and graves, moss for stamina and "enough", ember for rage, blue for mana, cyan for painkillers). Headings use Jacquard 12 (OFL, in `assets/seancraft/font/`). The pack's vanilla theme (buttons, hotbar, hearts, XP bar, boss bars) comes built in and is on by default; turn off "SeanCraft vanilla theme" under Options > Resource Packs if you don't want it.

A few items still borrow a vanilla texture (bow limbs, for one). To give one its own icon, drop a 16x16 PNG named after the item into `src/main/resources/assets/seancraft/textures/item/` and the build uses it. See [ICONS.md](ICONS.md) for the rules and the full list of item names.
