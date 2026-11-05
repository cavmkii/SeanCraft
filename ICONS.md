# Custom icons

Most SeanCraft items borrow a vanilla texture for now, so padding looks like wool, a surgical kit looks like an iron ingot, and so on. To give an item its own icon, add a PNG to the repo. Nothing else needs changing: the build picks it up and points that item at it.

## How to add one

1. Make a **16x16 PNG** with a transparent background (RGBA).
2. Name it after the item ID below, like `padding_leather.png`.
3. Put it in `src/main/resources/assets/seancraft/textures/item/`. You'll need to create the `textures/item` folders the first time.
4. Commit it, or upload it on GitHub with "Add file → Upload files" into that folder.

Items without a PNG keep their stand-in, so you can add them one at a time.

## Style tips

- Draw at 16x16 and don't scale up a bigger drawing. Hard pixel edges, with no blur or anti-aliasing.
- Keep a 1px margin so the icon doesn't touch the edges, and give it a darker outline so it reads on both dark and light slots.
- Light from the top-left, like vanilla items.
- Material colors help: copper is orange, iron light grey, gold yellow, diamond cyan, netherite dark grey/purple.
- Items that are bigger in the grid (the 2x2 Surgery Case, the 2x1 Surgical Kit, rigs, backpacks) still use a 16x16 icon. It just gets drawn bigger.
- Animated textures (a tall PNG plus a `.png.mcmeta` file) work too.

## Item IDs

**Weapon and armor parts** come in five materials each:

| Part | IDs |
|---|---|
| Blade | `blade_copper`, `blade_iron`, `blade_gold`, `blade_diamond`, `blade_netherite` |
| Axe head | `axe_head_copper`, `axe_head_iron`, `axe_head_gold`, `axe_head_diamond`, `axe_head_netherite` |
| Limbs (bows, crossbows) | `limbs_copper`, `limbs_iron`, `limbs_gold`, `limbs_diamond`, `limbs_netherite` |
| Guard | `guard_copper`, `guard_iron`, `guard_gold`, `guard_diamond`, `guard_netherite` |
| Plates | `plate_copper`, `plate_iron`, `plate_gold`, `plate_diamond`, `plate_netherite` |
| Handle | `handle_planks`, `handle_log`, `handle_bone`, `handle_blaze`, `handle_breeze` |
| Wrap | `wrap_string`, `wrap_leather`, `wrap_rabbit`, `wrap_membrane`, `wrap_scute` |
| Padding | `padding_string`, `padding_leather`, `padding_rabbit`, `padding_membrane`, `padding_scute` |
| Long blade, knife blade, mace head, spearhead, halberd head | `long_blade_<metal>`, `knife_blade_<metal>`, `mace_head_<metal>`, `spearhead_<metal>`, `halberd_head_<metal>` (metal: copper, iron, gold, diamond, netherite) |
| Haft | `haft_planks`, `haft_log`, `haft_bone`, `haft_blaze`, `haft_breeze` |

**Weapons:** `<metal>_dagger`, `<metal>_mace`, `<metal>_warhammer`, `<metal>_greatsword`, `<metal>_halberd`, `<metal>_spear`, `<metal>_pike` (metal: copper, iron, gold, diamond, netherite), and `wooden_club`. These and the new parts above already have simple generated icons; drop in your own PNG with the same name to replace one.

**Meds:** `bandage`, `tourniquet`, `splint`, `painkillers`, `field_kit`, `surgical_kit`, `surgery_case`, `antidote`

**Gear:**
- **Rigs:** `rig_scav`, `rig_chest`, `rig_carrier`, `rig_assault`
- **Backpacks:** `pack_sling`, `pack_scav`, `pack_pilgrim`, `pack_expedition`
- **Secure pouches:** `secure_beta`, `secure_gamma`, `secure_kappa`

**Joe and Sean:** `soda`, `merch`, `glock`, `restraining_order`, `diddy_oil`, `sixty_seven`, `baby_bonnet`

**Character:** `orb_of_regret`

**Raid:** `fire_arrow` (blocks: `fob_table`)

**Bosses:** `mark_totem`, `trophy_old_salt`, `marks_reel` (blocks: `summoning_pedestal`, `trophy_rack`)

**Blocks** (as items): `hideout_bench`, `weapon_bench`, `altar_of_regret`, `workshop`, `medstation`, `perimeter`, `intel`. These show the block model in your inventory now, and a PNG replaces that with a flat icon.

**Ecology:** `soil_kit`, `insecticide`, `bat_guano`

**Prairie plants** are blocks drawn as crosses, like flowers, so their art goes in `src/main/resources/assets/seancraft/textures/block/<id>.png` (16x16, transparent background): `big_bluestem`, `compass_plant`, `butterfly_milkweed`, `pale_purple_coneflower`, `rattlesnake_master`, `leadplant`. The ones there now are rough stand-ins; replace them with the same names.
