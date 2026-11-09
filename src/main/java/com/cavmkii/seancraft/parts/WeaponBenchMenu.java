package com.cavmkii.seancraft.parts;

import com.cavmkii.seancraft.tarkov.Cost;
import com.cavmkii.seancraft.tarkov.Hideouts;
import com.cavmkii.seancraft.tarkov.Stash;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Weapon Bench (design doc 4.5, 4.6, 4.8). BUILD: with the left slot empty, pick what to make, drop its parts into the
 * outline and take the weapon from the result slot. MODIFY: put a weapon or armor piece in the left slot and its parts
 * appear on its outline; swap them. PARTS: a 3x3 grid that makes parts by pattern and levels them (part in the centre,
 * its material around it). REPAIR: the item in one slot, its striking part's material in the other.
 */
public class WeaponBenchMenu extends AbstractContainerMenu {
    public static final int PARTS = 4;
    public static final int ITEM_X = 8;
    public static final int ITEM_Y = 20;

    /**
     * Where each part slot sits on a family's outline (top-left of the slot), in the order of {@link Family#slots}.
     * The outline area is x 62-172, y 16-116.
     */
    public static int[] layout(Family family, int index) {
        int[][] pos = switch (family) {
            // Blade on top, guard across, grip below, wrap at the pommel.
            case SWORD -> new int[][] {{109, 24}, {109, 77}, {109, 57}, {109, 98}};
            // Head off the top of the haft, wrap low on it.
            case AXE -> new int[][] {{86, 26}, {109, 62}, {109, 94}};
            // Knife blade on top, short grip under the little guard.
            case DAGGER -> new int[][] {{109, 34}, {109, 80}};
            // Head at the top of the handle (mace) or haft (warhammer), wrap low down.
            case MACE -> new int[][] {{109, 26}, {109, 64}, {109, 94}};
            case WARHAMMER -> new int[][] {{109, 25}, {109, 62}, {109, 96}};
            // Like the sword, longer.
            case GREATSWORD -> new int[][] {{109, 26}, {109, 80}, {109, 60}, {109, 99}};
            // The axe blade off the side of the haft.
            case HALBERD -> new int[][] {{128, 28}, {109, 64}, {109, 96}};
            case SPEAR, PIKE -> new int[][] {{109, 20}, {109, 60}, {109, 94}};
            // Limbs arc on the right, grip in the middle, string on the left.
            case BOW -> new int[][] {{124, 22}, {128, 58}, {92, 58}};
            // Limbs across the top, stock down the middle, string between the limb tips.
            case CROSSBOW -> new int[][] {{109, 22}, {109, 78}, {109, 46}};
            // Handle in the middle, rim at the top edge, straps below.
            case SHIELD, TOWER_SHIELD -> new int[][] {{109, 58}, {109, 24}, {109, 88}};
            case BUCKLER -> new int[][] {{109, 58}, {109, 30}};
            case THROWING_KNIFE -> new int[][] {{109, 34}, {109, 76}};
            case JAVELIN -> new int[][] {{109, 20}, {109, 70}};
            case BOOMERANG -> new int[][] {{96, 50}, {124, 64}};
            // Crystal on top, held by the focus, grip below.
            case WAND -> new int[][] {{109, 26}, {109, 46}, {109, 80}};
            case STAFF -> new int[][] {{109, 18}, {109, 38}, {109, 82}};
            case HELMET -> new int[][] {{109, 30}, {109, 54}, {109, 92}};
            case CHEST -> new int[][] {{109, 34}, {109, 60}, {109, 88}};
            // Greaves down one leg, padding down the other, straps at the waist.
            case LEGS -> new int[][] {{90, 56}, {128, 56}, {109, 20}};
            case BOOTS -> new int[][] {{84, 74}, {134, 74}, {109, 36}};
        };
        return index < pos.length ? pos[index] : null;
    }

    private final ContainerLevelAccess access;
    private final SimpleContainer item = new SimpleContainer(1);
    private final SimpleContainer parts = new SimpleContainer(PARTS);
    private final boolean server;
    private boolean loading;
    /** The part slots show an item's installed parts (modify mode) rather than loose parts for a build. */
    private boolean mirroring;
    /** Family picked to build, as an index into {@link #BUILDABLE}, or -1. Synced to the client. */
    private final net.minecraft.world.inventory.DataSlot building = net.minecraft.world.inventory.DataSlot.standalone();

    /** What the bench can build from scratch: every part-built family (4.4). */
    public static final Family[] BUILDABLE = {Family.SWORD, Family.AXE, Family.DAGGER, Family.MACE, Family.WARHAMMER,
            Family.GREATSWORD, Family.HALBERD, Family.SPEAR, Family.PIKE, Family.BOW, Family.CROSSBOW, Family.SHIELD, Family.BUCKLER, Family.TOWER_SHIELD, Family.THROWING_KNIFE, Family.JAVELIN, Family.BOOMERANG, Family.WAND, Family.STAFF,
            Family.HELMET, Family.CHEST, Family.LEGS, Family.BOOTS};
    public static final int ASSEMBLE_BUTTON = 200;
    public static final int PICK_BUTTON = 100;

    public WeaponBenchMenu(int id, Inventory inventory) {
        this(id, inventory, ContainerLevelAccess.NULL);
    }

    public WeaponBenchMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        super(PartItems.BENCH_MENU, id);
        this.access = access;
        this.server = !inventory.player.level().isClientSide;
        this.owner = inventory.player instanceof ServerPlayer sp ? sp : null;
        if (owner != null) {
            viewer = com.cavmkii.seancraft.rpg.Rpg.data(owner);
        }
        addSlot(new Slot(item, 0, ITEM_X, ITEM_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                // Not while loose parts for a build sit in the part slots: they'd be replaced by the item's own.
                return Parts.modular(stack) && parts.isEmpty();
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }

            @Override
            public boolean isActive() {
                return tab == TAB_BUILD;
            }
        });
        // One set of part slots per family, each on that family's outline; only the current family's set is live.
        for (Family layoutFamily : Family.values()) {
            for (int i = 0; i < layoutFamily.slots.size(); i++) {
                addPartSlot(layoutFamily, i);
            }
        }
        addStationSlots();
        firstInventorySlot = slots.size();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, 9 + row * 9 + col, 8 + col * 18, 130 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 188));
        }
        item.addListener(c -> loadParts());
        parts.addListener(c -> {
            saveParts();
            refreshBuild();
        });
        grid.addListener(c -> refreshGrid());
        repair.addListener(c -> refreshRepair());
        building.set(-1);
        addDataSlot(building);
        addDataSlot(gridLocked);
    }

    public static final int TAB_BUILD = 0;
    public static final int TAB_PARTS = 1;
    public static final int TAB_REPAIR = 2;
    /** Which tab is showing; set on both sides (the screen sends TAB_BUTTON + n). Slots of other tabs are inactive. */
    public int tab;
    public static final int TAB_BUTTON = 600;

    // PARTS: the pattern grid and its result (4.8).
    public static final int GRID_X = 61;
    public static final int GRID_Y = 30;
    public static final int GRID_RESULT_X = 151;
    public static final int GRID_RESULT_Y = 48;
    private final SimpleContainer grid = new SimpleContainer(9);
    private final SimpleContainer gridOut = new SimpleContainer(1);
    private PartCrafting.Result gridResult = PartCrafting.Result.NONE;
    /** 1 while the grid matches a head that no unlocked weapon uses (4.7), so the screen can say why. */
    private final net.minecraft.world.inventory.DataSlot gridLocked = net.minecraft.world.inventory.DataSlot.standalone();

    public boolean gridLocked() {
        return gridLocked.get() == 1;
    }
    // REPAIR: the item, its material, the repaired item (9.4.8).
    public static final int REPAIR_ITEM_X = 48;
    public static final int REPAIR_MAT_X = 84;
    public static final int REPAIR_Y = 48;
    public static final int REPAIR_RESULT_X = 148;
    private final SimpleContainer repair = new SimpleContainer(2);
    private final SimpleContainer repairOut = new SimpleContainer(1);
    // BUILD: the finished weapon (replaces ASSEMBLE).
    public static final int BUILD_RESULT_X = 38;
    public static final int BUILD_RESULT_Y = 20;
    private final SimpleContainer buildOut = new SimpleContainer(1);

    /** Index of the first player inventory slot (after the item slot and every family's part slots). */
    private int firstInventorySlot;

    private void addPartSlot(Family layoutFamily, int partIndex) {
        int[] at = layout(layoutFamily, partIndex);
        addSlot(new Slot(parts, partIndex, at[0], at[1]) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    PartSlot slot = slotFor(partIndex);
                    return slot != null && stack.getItem() instanceof PartItem part
                            && family().fits(part.def) && part.def.kind().slot == slot;
                }

                @Override
                public boolean isActive() {
                    return tab == TAB_BUILD && family() == layoutFamily;
                }

                @Override
                public int getMaxStackSize() {
                    return weapon().isEmpty() && slotFor(partIndex) == PartSlot.PLATE ? platesFor(family()) : 1;
                }

                @Override
                public int getMaxStackSize(ItemStack stack) {
                    return getMaxStackSize();
                }
            });
    }

    /** The live slot for part {@code index} of the current family, for the screen. */
    public Slot partSlot(int index) {
        Family family = family();
        if (family == null) {
            return null;
        }
        int at = 1;
        for (Family f : Family.values()) {
            if (f == family) {
                return index < f.slots.size() ? slots.get(at + index) : null;
            }
            at += f.slots.size();
        }
        return null;
    }

    private ServerPlayer owner;
    /** The player's character, for recipe ladders (4.7). The screen sets the client's copy. */
    public com.cavmkii.seancraft.rpg.PlayerData viewer;

    /** Why a family can't be built from scratch yet, or null. */
    public String lockReason(Family family) {
        // Specialization-only families (4.7): the buckler is the Blademaster's, the tower shield the Knight's.
        if (viewer != null && family == Family.BUCKLER && viewer.spec != com.cavmkii.seancraft.rpg.Specialization.BLADEMASTER) {
            return "Blademaster only";
        }
        if (viewer != null && family == Family.TOWER_SHIELD && viewer.spec != com.cavmkii.seancraft.rpg.Specialization.KNIGHT) {
            return "Knight only";
        }
        return viewer == null ? null : com.cavmkii.seancraft.rpg.Ladders.lockReason(viewer, com.cavmkii.seancraft.rpg.Ladders.key(family));
    }

    /** A striking part only the locked families use (4.7): it can't be made yet. */
    public boolean headLocked(PartDef def) {
        if (def.kind().slot != PartSlot.HEAD || viewer == null) {
            return false;
        }
        for (Family f : BUILDABLE) {
            if (f.head == def.kind() && lockReason(f) == null) {
                return false;
            }
        }
        return true;
    }

    public static int platesFor(Family family) {
        return switch (family) {
            case HELMET -> 2;
            case CHEST -> 4;
            case LEGS -> 3;
            case BOOTS -> 1;
            default -> 1;
        };
    }

    public boolean buildMode() {
        return weapon().isEmpty();
    }

    public int buildingIndex() {
        return building.get();
    }

    /** What ASSEMBLE would make from the loose parts, or empty. Works on both sides, for the preview. */
    public ItemStack assemblyResult() {
        Family family = family();
        if (!buildMode() || family == null) {
            return ItemStack.EMPTY;
        }
        if (assemblyProblem() != null) {
            return ItemStack.EMPTY;
        }
        Map<PartSlot, Parts.Installed> map = new EnumMap<>(PartSlot.class);
        int metal = -1;
        for (int i = 0; i < family.slots.size(); i++) {
            PartSlot slot = family.slots.get(i);
            ItemStack stack = parts.getItem(i);
            PartItem part = (PartItem) stack.getItem();
            if (slot == PartSlot.HEAD || slot == PartSlot.PLATE) {
                // The blade, head or plates pick the item; the other parts can be any material.
                metal = part.def.tier();
            }
            map.put(slot, new Parts.Installed(part.def, Parts.level(stack), Parts.infusion(stack)));
        }
        net.minecraft.world.item.Item result = switch (family) {
            case BOW -> net.minecraft.world.item.Items.BOW;
            case CROSSBOW -> net.minecraft.world.item.Items.CROSSBOW;
            case SHIELD -> net.minecraft.world.item.Items.SHIELD;
            case BUCKLER -> WeaponItems.BUCKLER;
            case TOWER_SHIELD -> WeaponItems.TOWER_SHIELD;
            case BOOMERANG -> WeaponItems.BOOMERANG;
            default -> Family.itemFor(family, metal);
        };
        if (result == null) {
            return ItemStack.EMPTY;
        }
        ItemStack out = new ItemStack(result);
        Parts.setInstalled(out, map);
        return out;
    }

    /** Why ASSEMBLE can't run yet, for the screen, or null when it can. */
    public String assemblyProblem() {
        Family family = family();
        if (!buildMode() || family == null) {
            return "Pick what to build";
        }
        String locked = lockReason(family);
        if (locked != null) {
            return "Locked: " + locked;
        }
        for (int i = 0; i < family.slots.size(); i++) {
            PartSlot slot = family.slots.get(i);
            ItemStack stack = parts.getItem(i);
            if (!(stack.getItem() instanceof PartItem)) {
                return "Needs a " + family.slotName(slot).toLowerCase();
            }
            if (slot == PartSlot.PLATE && stack.getCount() < platesFor(family)) {
                return "Needs " + platesFor(family) + " " + family.slotName(slot).toLowerCase();
            }
        }
        return null;
    }

    public ItemStack weapon() {
        return item.getItem(0);
    }

    public Family family() {
        if (weapon().isEmpty()) {
            int b = building.get();
            return b >= 0 && b < BUILDABLE.length ? BUILDABLE[b] : null;
        }
        Family.Stock stock = Family.stock(weapon());
        return stock != null ? stock.family() : null;
    }

    public PartSlot slotFor(int index) {
        Family family = family();
        return family != null && index < family.slots.size() ? family.slots.get(index) : null;
    }

    private void loadParts() {
        if (!server) {
            return;
        }
        loading = true;
        try {
            ItemStack w = weapon();
            if (w.isEmpty() && !mirroring) {
                // Build mode: the part slots hold loose parts, so a click on the empty item slot leaves them alone.
                return;
            }
            mirroring = !w.isEmpty();
            Map<PartSlot, Parts.Installed> installed = Parts.installed(w);
            if (!w.isEmpty() && w.get(Parts.INSTALLED) == null && Parts.modular(w)) {
                // Loot and old items get their standard parts fitted the first time.
                Parts.setInstalled(w, installed);
            }
            for (int i = 0; i < PARTS; i++) {
                PartSlot slot = slotFor(i);
                Parts.Installed inst = slot != null ? installed.get(slot) : null;
                parts.setItem(i, inst != null ? Parts.partStack(inst) : ItemStack.EMPTY);
            }
        } finally {
            loading = false;
        }
    }

    private void saveParts() {
        if (!server || loading || weapon().isEmpty()) {
            return;
        }
        Map<PartSlot, Parts.Installed> map = new EnumMap<>(PartSlot.class);
        for (int i = 0; i < PARTS; i++) {
            PartSlot slot = slotFor(i);
            ItemStack stack = parts.getItem(i);
            if (slot != null && stack.getItem() instanceof PartItem part) {
                map.put(slot, new Parts.Installed(part.def, Parts.level(stack), Parts.infusion(stack)));
            }
        }
        Parts.setInstalled(weapon(), map);
    }

    /** PICK_BUTTON + n picks what to build; TAB_BUTTON + n switches tab. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= TAB_BUTTON && id < TAB_BUTTON + 3) {
            setTab(player, id - TAB_BUTTON);
            return true;
        }
        if (id >= PICK_BUTTON && id < PICK_BUTTON + BUILDABLE.length && buildMode()) {
            if (building.get() != id - PICK_BUTTON) {
                clearContainer(player, parts); // hand back parts meant for something else
                building.set(id - PICK_BUTTON);
                refreshBuild();
            }
            return true;
        }
        return false;
    }

    /** Leaving a tab hands back what was left in its slots. */
    public void setTab(Player player, int next) {
        if (tab == next) {
            return;
        }
        if (server) {
            if (tab == TAB_PARTS) {
                clearContainer(player, grid);
            } else if (tab == TAB_REPAIR) {
                clearContainer(player, repair);
            }
        }
        tab = next;
    }

    private void addStationSlots() {
        for (int i = 0; i < 9; i++) {
            addSlot(new Slot(grid, i, GRID_X + (i % 3) * 18, GRID_Y + (i / 3) * 18) {
                @Override
                public boolean isActive() {
                    return tab == TAB_PARTS;
                }
            });
        }
        addSlot(new ResultSlot(gridOut, GRID_RESULT_X, GRID_RESULT_Y, TAB_PARTS) {
            @Override
            void taken(Player player) {
                consumeGrid(player, gridResult.use());
            }
        });
        addSlot(new Slot(repair, 0, REPAIR_ITEM_X, REPAIR_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.isDamageableItem() && Wear.material(stack) != null;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }

            @Override
            public boolean isActive() {
                return tab == TAB_REPAIR;
            }
        });
        addSlot(new Slot(repair, 1, REPAIR_MAT_X, REPAIR_Y) {
            @Override
            public boolean isActive() {
                return tab == TAB_REPAIR;
            }
        });
        addSlot(new ResultSlot(repairOut, REPAIR_RESULT_X, REPAIR_Y, TAB_REPAIR) {
            @Override
            void taken(Player player) {
                int steps = repairSteps();
                repair.setItem(0, ItemStack.EMPTY);
                int per = stepsPerMaterial();
                repair.getItem(1).shrink((steps + per - 1) / per);
                repair.setChanged();
                player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.ANVIL_USE,
                        net.minecraft.sounds.SoundSource.BLOCKS, 0.6f, 1.2f);
            }
        });
        addSlot(new ResultSlot(buildOut, BUILD_RESULT_X, BUILD_RESULT_Y, TAB_BUILD) {
            @Override
            public boolean isActive() {
                return tab == TAB_BUILD && buildMode() && family() != null;
            }

            @Override
            void taken(Player player) {
                loading = true;
                for (int i = 0; i < PARTS; i++) {
                    parts.setItem(i, ItemStack.EMPTY);
                }
                loading = false;
                player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.SMITHING_TABLE_USE,
                        net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 1f);
            }
        });
    }

    /** A take-only slot; what taking costs is in {@link #taken}. */
    abstract class ResultSlot extends Slot {
        private final int onTab;

        ResultSlot(SimpleContainer container, int x, int y, int onTab) {
            super(container, 0, x, y);
            this.onTab = onTab;
        }

        abstract void taken(Player player);

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean isActive() {
            return tab == onTab;
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            if (server) {
                taken(player);
            }
            super.onTake(player, stack);
        }
    }

    private void consumeGrid(Player player, int[] use) {
        for (int i = 0; i < 9; i++) {
            if (use[i] > 0) {
                ItemStack s = grid.getItem(i);
                net.minecraft.world.item.Item remainder = s.getItem().getCraftingRemainingItem();
                s.shrink(use[i]);
                if (remainder != null && server) {
                    com.cavmkii.seancraft.tetris.Tetris.give((ServerPlayer) player, new ItemStack(remainder));
                }
            }
        }
        grid.setChanged();
        player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.SMITHING_TABLE_USE,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.6f, 1.2f);
    }

    /** The grid's result: a part (or a levelled one), unless it's a head no unlocked weapon uses (4.7). */
    private void refreshGrid() {
        if (!server) {
            return;
        }
        ItemStack[] cells = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            cells[i] = grid.getItem(i);
        }
        gridResult = PartCrafting.match(cells);
        ItemStack out = gridResult.output();
        boolean locked = out.getItem() instanceof PartItem p && headLocked(p.def) && !(grid.getItem(4).getItem() instanceof PartItem);
        gridLocked.set(locked ? 1 : 0);
        if (locked) {
            out = ItemStack.EMPTY;
        }
        gridOut.setItem(0, out.copy());
    }

    private int repairSteps() {
        ItemStack w = repair.getItem(0);
        ItemStack m = repair.getItem(1);
        if (w.isEmpty() || m.isEmpty() || Wear.material(w) == null || !m.is(Wear.material(w))) {
            return 0;
        }
        return Math.min(m.getCount() * stepsPerMaterial(), Wear.stepsToFull(w));
    }

    /** A bench in a home base with Workshop level 3 repairs at half cost: each material does two steps (v23 2.4.1). */
    private int stepsPerMaterial() {
        return access.evaluate((level, pos) -> level instanceof net.minecraft.server.level.ServerLevel sl
                && com.cavmkii.seancraft.tarkov.Hideouts.at(sl, net.minecraft.world.phys.Vec3.atCenterOf(pos)) instanceof
                        com.cavmkii.seancraft.tarkov.Hideouts.Hideout h
                && h.level(com.cavmkii.seancraft.tarkov.Module.WORKSHOP) >= 3 ? 2 : 1, 1);
    }

    private void refreshRepair() {
        if (!server) {
            return;
        }
        int steps = repairSteps();
        ItemStack out = ItemStack.EMPTY;
        if (steps > 0) {
            out = repair.getItem(0).copy();
            for (int i = 0; i < steps; i++) {
                Wear.repairStep(out);
            }
        }
        repairOut.setItem(0, out);
    }

    private void refreshBuild() {
        if (!server || loading) {
            return;
        }
        buildOut.setItem(0, assemblyResult());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (slot instanceof ResultSlot result && player instanceof ServerPlayer sp) {
            // Shift-click: as many as the grid allows, straight into the player's grids.
            int made = 0;
            while (slot.hasItem() && made < 64) {
                ItemStack out = slot.getItem().copy();
                slot.set(ItemStack.EMPTY);
                result.onTake(player, out);
                com.cavmkii.seancraft.tetris.Tetris.give(sp, out);
                made++;
                if (!(result.container == gridOut)) {
                    break;
                }
            }
            return ItemStack.EMPTY;
        }
        if (index < firstInventorySlot) {
            if (!moveItemStackTo(stack, firstInventorySlot, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (Parts.modular(stack) && weapon().isEmpty()) {
            if (!moveItemStackTo(stack, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean moved = false;
            for (int i = 0; i < PARTS && !stack.isEmpty(); i++) {
                Slot target = partSlot(i);
                if (target != null && target.mayPlace(stack)) {
                    moved |= moveItemStackTo(stack, target.index, target.index + 1, false);
                }
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        clearContainer(player, grid);
        clearContainer(player, repair);
        if (buildMode()) {
            // Loose parts for a build that wasn't assembled go back to the player.
            clearContainer(player, parts);
            return;
        }
        // The parts live inside the item; only the item itself goes back to the player.
        loading = true;
        for (int i = 0; i < PARTS; i++) {
            parts.setItem(i, ItemStack.EMPTY);
        }
        loading = false;
        access.execute((level, pos) -> clearContainer(player, item));
    }

    /** In reach, or opened from the hideout screen while you're in the same hideout as the bench. */
    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, PartItems.WEAPON_BENCH) || access.evaluate((level, pos) -> level.getBlockState(pos).is(PartItems.WEAPON_BENCH)
                && player instanceof ServerPlayer sp && Hideouts.at(sp.serverLevel(), sp.position()) instanceof Hideouts.Hideout h
                && h.contains(net.minecraft.world.phys.Vec3.atCenterOf(pos)), true);
    }
}
