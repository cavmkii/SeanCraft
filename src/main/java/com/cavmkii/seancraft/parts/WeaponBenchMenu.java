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
 * Weapon Bench (design doc 4.5). Modify: put a weapon or armor piece in the left slot and its parts appear on its outline; take a part
 * out to remove it, put one in to fit it. Build: with the left slot empty, pick what to make and drop the parts into
 * its outline, then ASSEMBLE. The arrow buttons level a part up with its own material. The PARTS tab makes the parts
 * themselves from materials you carry and the stash.
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
                return !partsTab;
            }
        });
        // One set of part slots per family, each on that family's outline; only the current family's set is live.
        for (Family layoutFamily : Family.values()) {
            for (int i = 0; i < layoutFamily.slots.size(); i++) {
                addPartSlot(layoutFamily, i);
            }
        }
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
        parts.addListener(c -> saveParts());
        building.set(-1);
        addDataSlot(building);
        addDataSlot(infusion);
        addDataSlot(catalyst);
        for (net.minecraft.world.inventory.DataSlot d : affordable) {
            addDataSlot(d);
        }
    }

    /** Client: the screen is on its PARTS tab, so the item and part slots are hidden. */
    public boolean partsTab;

    /** Index of the first player inventory slot (after the item slot and every family's part slots). */
    private int firstInventorySlot;

    /** Which parts the PARTS tab can make right now, as bits over {@link PartDef#ALL}; 16 per data slot. */
    private final net.minecraft.world.inventory.DataSlot[] affordable = affordableSlots();

    private static net.minecraft.world.inventory.DataSlot[] affordableSlots() {
        net.minecraft.world.inventory.DataSlot[] slots = new net.minecraft.world.inventory.DataSlot[(PartDef.ALL.size() + 15) / 16];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = net.minecraft.world.inventory.DataSlot.standalone();
        }
        return slots;
    }
    private int affordTick;

    public boolean canMake(int partIndex) {
        return (affordable[partIndex / 16].get() >> (partIndex % 16) & 1) != 0;
    }

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
                    return !partsTab && family() == layoutFamily;
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

    public static final int MAKE_BUTTON = 300;
    /** Cycles the infusion for wraps made on the PARTS tab (9.4.3). */
    public static final int INFUSE_BUTTON = 500;
    /** Repairs the item in the slot by 25% (9.4.8); REPAIR_ALL repeats while you can pay. */
    public static final int REPAIR_BUTTON = 501;
    public static final int REPAIR_ALL_BUTTON = 502;
    public static final String[] INFUSIONS = {null, "heavy", "keen", "arcane"};
    public static final String[] INFUSION_LABELS = {"Plain", "Heavy", "Keen", "Arcane"};
    private final net.minecraft.world.inventory.DataSlot infusion = net.minecraft.world.inventory.DataSlot.standalone();
    /** The element for crystals made here (9.4.5); CATALYST_BUTTON cycles it. */
    private final net.minecraft.world.inventory.DataSlot catalyst = net.minecraft.world.inventory.DataSlot.standalone();
    public static final int CATALYST_BUTTON = 503;

    public int catalystIndex() {
        return Math.floorMod(catalyst.get(), com.cavmkii.seancraft.rpg.Magic.ELEMENTS.length);
    }

    /** Blaze powder for fire, packed ice for frost, a lightning rod, a fermented spider eye for poison, a golden carrot for spirit. */
    public static List<Cost> catalystCost(int index) {
        net.minecraft.world.item.Item item = switch (index) {
            case 1 -> net.minecraft.world.item.Items.PACKED_ICE;
            case 2 -> net.minecraft.world.item.Items.LIGHTNING_ROD;
            case 3 -> net.minecraft.world.item.Items.FERMENTED_SPIDER_EYE;
            case 4 -> net.minecraft.world.item.Items.GOLDEN_CARROT;
            default -> net.minecraft.world.item.Items.BLAZE_POWDER;
        };
        return List.of(Cost.of(item, 1));
    }

    public int infusionIndex() {
        return Math.floorMod(infusion.get(), INFUSIONS.length);
    }

    /** What an infused wrap costs on top of its material: iron nuggets for Heavy, flint for Keen, lapis for Arcane. */
    public static List<Cost> infusionCost(int index) {
        return switch (index) {
            case 1 -> List.of(Cost.of(net.minecraft.world.item.Items.IRON_NUGGET, 2));
            case 2 -> List.of(Cost.of(net.minecraft.world.item.Items.FLINT, 1));
            case 3 -> List.of(Cost.of(net.minecraft.world.item.Items.LAPIS_LAZULI, 1));
            default -> List.of();
        };
    }

    private List<Cost> costsFor(PartDef def) {
        if (def.kind() == PartKind.CRYSTAL) {
            List<Cost> all = new java.util.ArrayList<>(def.costs());
            all.addAll(catalystCost(catalystIndex()));
            return all;
        }
        if (def.kind() != PartKind.WRAP || infusionIndex() == 0) {
            return def.costs();
        }
        List<Cost> all = new java.util.ArrayList<>(def.costs());
        all.addAll(infusionCost(infusionIndex()));
        return all;
    }
    public static final int MAKE_FIVE_BUTTON = 400;

    /** Server: refresh which parts can be made, twice a second. */
    @Override
    public void broadcastChanges() {
        if (server && access != ContainerLevelAccess.NULL && affordTick++ % 10 == 0) {
            access.execute((level, pos) -> refreshAffordable());
        }
        super.broadcastChanges();
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

    private void refreshAffordable() {
        if (owner == null) {
            return;
        }
        Hideouts.Hideout hideout = Hideouts.at(owner.serverLevel(), owner.position());
        int[] bits = new int[affordable.length];
        for (int i = 0; i < PartDef.ALL.size(); i++) {
            if (!headLocked(PartDef.ALL.get(i)) && Stash.canAfford(owner, hideout, costsFor(PartDef.ALL.get(i)))) {
                bits[i / 16] |= 1 << (i % 16);
            }
        }
        for (int k = 0; k < affordable.length; k++) {
            affordable[k].set(bits[k]);
        }
    }

    /** Makes up to {@code times} of a part from what you carry and the stash; they go into your grids. */
    private boolean makePart(ServerPlayer player, int partIndex, int times) {
        if (partIndex < 0 || partIndex >= PartDef.ALL.size()) {
            return false;
        }
        PartDef def = PartDef.ALL.get(partIndex);
        if (headLocked(def)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("You haven't unlocked a weapon that uses a "
                    + def.kind().label.toLowerCase() + " yet.").withStyle(net.minecraft.ChatFormatting.RED), true);
            return false;
        }
        Hideouts.Hideout hideout = Hideouts.at(player.serverLevel(), player.position());
        int made = 0;
        List<Cost> costs = costsFor(def);
        for (int t = 0; t < times && Stash.canAfford(player, hideout, costs); t++) {
            Stash.consume(player, hideout, costs);
            ItemStack made1 = new ItemStack(PartItems.get(def));
            if (def.kind() == PartKind.WRAP && infusionIndex() > 0) {
                made1.set(Parts.INFUSION, INFUSIONS[infusionIndex()]);
            }
            if (def.kind() == PartKind.CRYSTAL) {
                made1.set(Parts.INFUSION, com.cavmkii.seancraft.rpg.Magic.ELEMENTS[catalystIndex()]);
            }
            com.cavmkii.seancraft.tetris.Tetris.give(player, made1);
            made++;
        }
        if (made > 0) {
            player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.SMITHING_TABLE_USE,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.6f, 1.2f);
            refreshAffordable();
        }
        return made > 0;
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

    /** Button i levels up the part in slot i; PICK_BUTTON + n picks what to build; ASSEMBLE_BUTTON builds it. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if ((id == REPAIR_BUTTON || id == REPAIR_ALL_BUTTON) && player instanceof ServerPlayer sp && !weapon().isEmpty()) {
            ItemStack w = weapon();
            net.minecraft.world.item.Item mat = Wear.material(w);
            if (mat == null || Wear.stepsToFull(w) == 0) {
                return false;
            }
            Hideouts.Hideout hideout = Hideouts.at(sp.serverLevel(), sp.position());
            List<Cost> cost = List.of(Cost.of(mat, 1));
            int done = 0;
            while (Wear.stepsToFull(w) > 0 && Stash.canAfford(sp, hideout, cost) && (done == 0 || id == REPAIR_ALL_BUTTON)) {
                Stash.consume(sp, hideout, cost);
                Wear.repairStep(w);
                done++;
            }
            if (done > 0) {
                item.setChanged();
                sp.level().playSound(null, sp.blockPosition(), net.minecraft.sounds.SoundEvents.ANVIL_USE, net.minecraft.sounds.SoundSource.BLOCKS, 0.6f, 1.2f);
            }
            return done > 0;
        }
        if (id == CATALYST_BUTTON) {
            catalyst.set((catalystIndex() + 1) % com.cavmkii.seancraft.rpg.Magic.ELEMENTS.length);
            refreshAffordable();
            return true;
        }
        if (id == INFUSE_BUTTON) {
            infusion.set((infusionIndex() + 1) % INFUSIONS.length);
            refreshAffordable();
            return true;
        }
        if (id >= MAKE_BUTTON && id < MAKE_BUTTON + PartDef.ALL.size() && player instanceof ServerPlayer sp) {
            return makePart(sp, id - MAKE_BUTTON, 1);
        }
        if (id >= MAKE_FIVE_BUTTON && id < MAKE_FIVE_BUTTON + PartDef.ALL.size() && player instanceof ServerPlayer sp) {
            return makePart(sp, id - MAKE_FIVE_BUTTON, 5);
        }
        if (id >= PICK_BUTTON && id < PICK_BUTTON + BUILDABLE.length && buildMode()) {
            if (building.get() != id - PICK_BUTTON) {
                clearContainer(player, parts); // hand back parts meant for something else
                building.set(id - PICK_BUTTON);
            }
            return true;
        }
        if (id == ASSEMBLE_BUTTON) {
            ItemStack result = assemblyResult();
            if (result.isEmpty()) {
                return false;
            }
            loading = true;
            for (int i = 0; i < PARTS; i++) {
                parts.setItem(i, ItemStack.EMPTY);
            }
            loading = false;
            item.setItem(0, result);
            player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.SMITHING_TABLE_USE,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 1f);
            return true;
        }
        if (id < 0 || id >= PARTS || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        ItemStack stack = parts.getItem(id);
        if (!(stack.getItem() instanceof PartItem part)) {
            return false;
        }
        int level = Parts.level(stack);
        if (level >= PartDef.MAX_LEVEL) {
            return false;
        }
        List<Cost> cost = List.of(Cost.of(part.def.material(), part.def.upgradeCost(level)));
        Hideouts.Hideout hideout = Hideouts.at(serverPlayer.serverLevel(), serverPlayer.position());
        if (!Stash.canAfford(serverPlayer, hideout, cost)) {
            return false;
        }
        Stash.consume(serverPlayer, hideout, cost);
        ItemStack upgraded = stack.copy();
        upgraded.set(Parts.LEVEL, level + 1);
        parts.setItem(id, upgraded);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
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
