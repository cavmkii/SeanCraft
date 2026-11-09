package com.cavmkii.seancraft.tarkov;

import com.cavmkii.seancraft.pattern.Pattern;
import com.cavmkii.seancraft.pattern.Patterns;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The Hideout Bench (design doc 4.8): a 3x3 grid with its own recipe book, like the crafting table. Rigs, backpacks
 * and pouches after the first in a line take the empty previous item in the centre. Recipes gated by a module level
 * only work in a hideout whose module has reached it. Module levels themselves are built with CONTRIBUTE at the module.
 */
public class HideoutCraftMenu extends AbstractContainerMenu {
    public static final int GRID_X = 70;
    public static final int GRID_Y = 30;
    public static final int RESULT_X = 160;
    public static final int RESULT_Y = 48;

    private final ContainerLevelAccess access;
    private final ServerPlayer owner;
    private final SimpleContainer grid = new SimpleContainer(9);
    private final SimpleContainer out = new SimpleContainer(1);
    private Pattern matched;
    private char[] matchedAt;
    /** The hideout's module levels, for the book's locks. */
    private final DataSlot[] levels = new DataSlot[Module.values().length];
    /** 1 while the grid matches a recipe whose module level isn't met. */
    private final DataSlot gated = DataSlot.standalone();
    /** Whose recipes this grid makes: 0 the Hideout Bench (ungated ones), else module ordinal + 1 (that module's). */
    private final DataSlot station = DataSlot.standalone();

    public HideoutCraftMenu(int id, Inventory inventory) {
        this(id, inventory, ContainerLevelAccess.NULL);
    }

    public HideoutCraftMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        this(id, inventory, access, null);
    }

    public HideoutCraftMenu(int id, Inventory inventory, ContainerLevelAccess access, Module stationModule) {
        super(TarkovContent.HIDEOUT_CRAFT_MENU, id);
        station.set(stationModule == null ? 0 : stationModule.ordinal() + 1);
        this.access = access;
        this.owner = inventory.player instanceof ServerPlayer sp ? sp : null;
        for (int i = 0; i < 9; i++) {
            addSlot(new Slot(grid, i, GRID_X + (i % 3) * 18, GRID_Y + (i / 3) * 18));
        }
        addSlot(new Slot(out, 0, RESULT_X, RESULT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public void onTake(Player player, ItemStack stack) {
                consume(player);
                super.onTake(player, stack);
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, 9 + row * 9 + col, 8 + col * 18, 130 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 188));
        }
        for (int m = 0; m < levels.length; m++) {
            levels[m] = DataSlot.standalone();
            addDataSlot(levels[m]);
        }
        addDataSlot(gated);
        addDataSlot(station);
        access.execute((level, pos) -> {
            Hideouts.Hideout h = Hideouts.byPos((net.minecraft.server.level.ServerLevel) level, pos);
            for (Module m : Module.values()) {
                levels[m.ordinal()].set(h == null ? 0 : h.level(m));
            }
        });
        grid.addListener(c -> refresh());
    }

    public int level(Module m) {
        return levels[m.ordinal()].get();
    }

    /** The module whose station this is, or null for the Hideout Bench. */
    public Module station() {
        int s = station.get();
        return s <= 0 || s > Module.values().length ? null : Module.values()[s - 1];
    }

    /** Recipes are made where they belong: a module's at its block, the rest at the Hideout Bench. */
    public static boolean belongs(Pattern p, Module station) {
        if (station == null) {
            return p.gateModule == null;
        }
        return station.name().equals(p.gateModule);
    }

    /** True if any recipe is made at this module's block. */
    public static boolean crafts(Module module) {
        for (Pattern p : Patterns.station("hideout")) {
            if (module.name().equals(p.gateModule)) {
                return true;
            }
        }
        return false;
    }

    public boolean gated() {
        return gated.get() == 1;
    }

    /** True if this hideout's modules allow the recipe. */
    public boolean allowed(Pattern p) {
        if (p.gateModule == null) {
            return true;
        }
        try {
            return level(Module.valueOf(p.gateModule)) >= p.gateLevel;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private void refresh() {
        if (owner == null) {
            return;
        }
        ItemStack[] cells = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            cells[i] = grid.getItem(i);
        }
        matched = null;
        matchedAt = null;
        boolean blocked = false;
        for (Pattern p : Patterns.station("hideout")) {
            if (!belongs(p, station())) {
                continue;
            }
            char[] at = p.place(cells, p::accepts);
            if (at != null) {
                if (!allowed(p)) {
                    blocked = true;
                    continue;
                }
                matched = p;
                matchedAt = at;
                break;
            }
        }
        gated.set(blocked && matched == null ? 1 : 0);
        out.setItem(0, matched == null ? ItemStack.EMPTY : matched.resultStack());
    }

    private void consume(Player player) {
        if (owner == null || matched == null) {
            return;
        }
        ItemStack result = matched.resultStack();
        for (int i = 0; i < 9; i++) {
            if (matchedAt[i] != 0) {
                ItemStack s = grid.getItem(i);
                net.minecraft.world.item.Item remainder = s.getItem().getCraftingRemainingItem();
                s.shrink(1);
                if (remainder != null) {
                    com.cavmkii.seancraft.tetris.Tetris.give(owner, new ItemStack(remainder));
                }
            }
        }
        if (result.getItem() instanceof com.cavmkii.seancraft.boss.BossContent.TotemItem totem) {
            // A map to the boss's dungeon comes with its totem (8.4.1).
            ItemStack map = com.cavmkii.seancraft.boss.BossSites.map(owner.server.overworld(), totem);
            if (!map.isEmpty()) {
                com.cavmkii.seancraft.tetris.Tetris.give(owner, map);
            }
        }
        player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.VILLAGER_WORK_LEATHERWORKER,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.7f, 1f);
        grid.setChanged();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (index == 9 && slot.hasItem() && player instanceof ServerPlayer sp) {
            // Shift-click the result: as many as the grid allows.
            for (int n = 0; n < 64 && slot.hasItem(); n++) {
                ItemStack made = slot.getItem().copy();
                slot.set(ItemStack.EMPTY);
                slot.onTake(player, made);
                com.cavmkii.seancraft.tetris.Tetris.give(sp, made);
            }
            return ItemStack.EMPTY;
        }
        if (index < 9 && slot.hasItem()) {
            ItemStack s = slot.getItem();
            if (player instanceof ServerPlayer sp) {
                com.cavmkii.seancraft.tetris.Tetris.give(sp, s.copy());
            }
            slot.set(ItemStack.EMPTY);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        access.execute((level, pos) -> clearContainer(player, grid));
    }

    @Override
    public boolean stillValid(Player player) {
        // A module's station only needs you in its hideout; the bench's own grid needs the bench.
        return access.evaluate((level, pos) -> (station() != null || level.getBlockState(pos).is(TarkovContent.HIDEOUT_BENCH))
                && (player.distanceToSqr(Vec3.atCenterOf(pos)) <= 64
                || player instanceof ServerPlayer sp && Hideouts.at(sp.serverLevel(), sp.position()) instanceof Hideouts.Hideout h
                && h.contains(Vec3.atCenterOf(pos))), true);
    }

    public static void open(ServerPlayer player, BlockPos pos) {
        open(player, pos, null);
    }

    /** A crafting grid at the Hideout Bench ({@code station} null) or at a module's block; {@code pos} is the hideout's bench. */
    public static void open(ServerPlayer player, BlockPos pos, Module station) {
        player.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, p) -> new HideoutCraftMenu(id, inv,
                ContainerLevelAccess.create(player.level(), pos), station),
                net.minecraft.network.chat.Component.literal(station == null ? "Home Bench" : station.label)));
    }
}
