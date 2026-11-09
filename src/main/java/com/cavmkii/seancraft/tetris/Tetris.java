package com.cavmkii.seancraft.tetris;

import com.cavmkii.seancraft.Cfg;
import com.cavmkii.seancraft.SeanCraft;
import com.cavmkii.seancraft.tarkov.Hideouts;
import com.cavmkii.seancraft.tarkov.Stash;
import com.cavmkii.seancraft.tarkov.Tarkov;
import com.cavmkii.seancraft.tarkov.TarkovData;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;

/**
 * The INVENTORY screen, server side (design doc 3). The inventory itself is vanilla's (27 slots, hotbar, armor,
 * off-hand); this handles the screen's moves, chests and barrels opening in its storage column, the home stash (with a
 * Storehouse), crafting, auto-refill and the gather list. Creative players keep the vanilla inventory.
 */
public final class Tetris {
    public static final AttachmentType<InvData> DATA = AttachmentRegistry.<InvData>builder()
            .persistent(InvData.CODEC)
            .copyOnDeath()
            .initializer(InvData::new)
            .buildAndRegister(SeanCraft.id("inventory"));

    /** What the client last heard (refill toggles, gather list). */
    public static InvData clientView = new InvData();

    private static final double CONTAINER_REACH_SQR = 64;
    private static final int TABLE_RANGE = 6;

    static final class Runtime {
        boolean open;
        BlockPos container;
        boolean dirty = true;
        int hash;
        final net.minecraft.core.NonNullList<ItemStack> craft = net.minecraft.core.NonNullList.withSize(9, ItemStack.EMPTY);
        /** Hotbar as of last tick (item and count), to notice a slot's last item being used up. */
        final Item[] hotbarItem = new Item[9];
        final int[] hotbarCount = new int[9];
        String lastRecipe;
    }

    private static final Map<UUID, Runtime> RUNTIME = new HashMap<>();

    private Tetris() {}

    public static InvData data(Player player) {
        return player.level().isClientSide ? clientView : player.getAttachedOrCreate(DATA);
    }

    static Runtime rt(ServerPlayer player) {
        return RUNTIME.computeIfAbsent(player.getUUID(), id -> new Runtime());
    }

    public static void markDirty(Player player) {
        if (player instanceof ServerPlayer sp) {
            rt(sp).dirty = true;
        }
    }

    /** Opens the inventory screen on its CRAFTING tab, as a crafting table does. */
    public static void openCrafting(ServerPlayer player) {
        sendCraftList(player);
        // The INVENTORY tab with crafting mode on (TarkovScreen.CRAFT_MODE).
        ServerPlayNetworking.send(player, new TetrisNet.Open(3));
        com.cavmkii.seancraft.tarkov.Stations.send(player);
    }

    /** The Tetris inventory is in charge for survival and adventure players. */
    public static boolean active(Player player) {
        return !player.isCreative() && !player.isSpectator();
    }

    public static void init() {
        TetrisNet.register();
        ContainerGrid.init();
        FurnaceFeed.init();
        StashFeed.init();
        ServerTickEvents.END_SERVER_TICK.register(Tetris::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> rt(handler.player).dirty = true);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (RUNTIME.containsKey(handler.player.getUUID())) {
                CraftGrid.returnAll(handler.player);
            }
            RUNTIME.remove(handler.player.getUUID());
        });
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            Runtime rt = rt(newPlayer);
            rt.dirty = true;
            rt.container = null;
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!active(player) || player.isSecondaryUseActive() || com.cavmkii.seancraft.carry.Carry.carrying(player)) {
                return InteractionResult.PASS;
            }
            BlockState state = level.getBlockState(hit.getBlockPos());
            if (state.is(Blocks.CRAFTING_TABLE)) {
                if (player instanceof ServerPlayer sp) {
                    openCrafting(sp);
                }
                return InteractionResult.SUCCESS;
            }
            if (!(state.getBlock() instanceof ChestBlock) && !(state.getBlock() instanceof BarrelBlock)) {
                return InteractionResult.PASS;
            }
            if (player instanceof ServerPlayer sp) {
                openContainer(sp, hit.getBlockPos());
            }
            return InteractionResult.SUCCESS;
        });

        ServerPlayNetworking.registerGlobalReceiver(TetrisNet.Move.TYPE, (p, ctx) -> {
            move(ctx.player(), p.from(), p.to(), p.rotated(), p.count());
            sync(ctx.player());
            if (p.from().kind() == Loc.CRAFT || p.from().kind() == Loc.MATERIAL || p.to().kind() == Loc.CRAFT || p.to().kind() == Loc.MATERIAL) {
                // Changed by hand: "craft all" no longer refills with the last laid-out recipe.
                rt(ctx.player()).lastRecipe = null;
                sendCraftList(ctx.player());
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(TetrisNet.QuickMove.TYPE, (p, ctx) -> {
            quickMove(ctx.player(), p.from());
            sync(ctx.player());
            if (p.from().kind() == Loc.CRAFT) {
                // Shift-clicked out of the craft grid: the grid changed.
                rt(ctx.player()).lastRecipe = null;
                sendCraftList(ctx.player());
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(TetrisNet.Action.TYPE, (p, ctx) -> {
            action(ctx.player(), p.action());
            if (p.action() == 4) {
                // The inventory opened: the station row needs this hideout's stations.
                com.cavmkii.seancraft.tarkov.Stations.send(ctx.player());
            }
            sync(ctx.player());
        });
        ServerPlayNetworking.registerGlobalReceiver(TetrisNet.Track.TYPE, (p, ctx) -> {
            InvData d = data(ctx.player());
            d.tracked.clear();
            p.entries().stream().limit(6).forEach(d.tracked::add);
            sync(ctx.player());
        });
        ServerPlayNetworking.registerGlobalReceiver(TetrisNet.ContainerName.TYPE, (p, ctx) -> {
            rename(ctx.player(), p.index(), p.name());
            sync(ctx.player());
        });
        ServerPlayNetworking.registerGlobalReceiver(TetrisNet.ContainerAction.TYPE, (p, ctx) -> {
            containerAction(ctx.player(), p.index(), p.action(), p.value());
            sync(ctx.player());
        });
        // "" refreshes; "#grid" crafts from the grid (times); "#clear" empties the grid; a recipe id lays it out.
        ServerPlayNetworking.registerGlobalReceiver(TetrisNet.Craft.TYPE, (p, ctx) -> {
            ServerPlayer player = ctx.player();
            if (active(player) && player.isAlive()) {
                switch (p.id()) {
                    case "" -> { }
                    case "#grid" -> CraftGrid.craft(player, p.times());
                    case "#clear" -> {
                        CraftGrid.returnAll(player);
                        rt(player).lastRecipe = null;
                    }
                    default -> CraftGrid.fill(player, p.id(), true);
                }
                if (!p.id().isEmpty()) {
                    sync(player);
                }
            }
            sendCraftList(player);
        });
    }

    // ================================================================== tick

    private static void tick(MinecraftServer server) {
        boolean periodic = server.getTickCount() % 10 == 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isAlive() || !active(player)) {
                continue;
            }
            Runtime rt = rt(player);
            InvData data = data(player);
            refillHotbar(player, rt, data);
            if (server.getTickCount() % 100 == player.getId() % 100) {
                discoverRecipes(player);
            }
            if (rt.container != null && containerAt(player, rt.container) == null) {
                rt.container = null;
                rt.dirty = true;
            }
            if (rt.dirty || (rt.open && periodic)) {
                sync(player);
            }
        }
    }

    /**
     * Auto-refill: when a hotbar slot's last item is used up (placed, eaten, broken), the same item comes up from
     * the inventory, or the stash if that's turned on and you're home with a Storehouse (3.3). Moving a whole stack away doesn't count: only
     * a slot that went from one item to none.
     */
    private static void refillHotbar(ServerPlayer player, Runtime rt, InvData data) {
        Inventory inv = player.getInventory();
        // Only while playing: with a screen open, an emptied slot is the player moving things around.
        boolean playing = !rt.open && player.containerMenu == player.inventoryMenu;
        for (int i = 0; i < Inventory.getSelectionSize(); i++) {
            ItemStack now = inv.items.get(i);
            Item was = rt.hotbarItem[i];
            if (playing && data.autoRefill && now.isEmpty() && was != null && rt.hotbarCount[i] == 1) {
                ItemStack found = takeForRefill(player, data, was);
                if (!found.isEmpty()) {
                    inv.items.set(i, found);
                    rt.dirty = true;
                    now = found;
                }
            }
            rt.hotbarItem[i] = now.isEmpty() ? null : now.getItem();
            rt.hotbarCount[i] = now.getCount();
        }
    }

    private static ItemStack takeForRefill(ServerPlayer player, InvData data, Item item) {
        Inventory inv = player.getInventory();
        for (int i = Inventory.getSelectionSize(); i < Inventory.INVENTORY_SIZE; i++) {
            if (inv.items.get(i).is(item)) {
                ItemStack out = inv.items.get(i);
                inv.items.set(i, ItemStack.EMPTY);
                return out;
            }
        }
        Hideouts.Hideout home = data.refillFromStash ? Hideouts.at(player.serverLevel(), player.position()) : null;
        if (home != null) {
            for (Container c : Stash.containers(player.serverLevel(), home)) {
                for (int s = 0; s < c.getContainerSize(); s++) {
                    if (c.getItem(s).is(item)) {
                        ItemStack out = c.removeItemNoUpdate(s);
                        c.setChanged();
                        return out;
                    }
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Like vanilla, a recipe is discovered once you carry one of its ingredients. Vanilla only does this through
     * advancements for its own recipes; this covers every crafting recipe, including this mod's.
     */
    private static void discoverRecipes(ServerPlayer player) {
        List<ItemStack> carried = carried(player);
        List<RecipeHolder<?>> found = new ArrayList<>();
        for (RecipeHolder<CraftingRecipe> holder : player.server.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            if (player.getRecipeBook().contains(holder)
                    || !com.cavmkii.seancraft.rpg.Ladders.canCraft(player, holder.value().getResultItem(player.registryAccess()))) {
                continue;
            }
            outer:
            for (Ingredient ing : holder.value().getIngredients()) {
                if (ing.isEmpty()) {
                    continue;
                }
                for (ItemStack s : carried) {
                    if (!s.isEmpty() && ing.test(s)) {
                        found.add(holder);
                        break outer;
                    }
                }
            }
        }
        if (!found.isEmpty()) {
            player.awardRecipes(found);
        }
    }

    static void sync(ServerPlayer player) {
        Runtime rt = rt(player);
        rt.dirty = false;
        InvData d = data(player);
        List<TetrisNet.ContainerView> views = new ArrayList<>();
        Hideouts.Hideout home = Hideouts.at(player.serverLevel(), player.position());
        if (rt.open || rt.container != null) {
            for (Shown c : containers(player)) {
                ContainerGrid g = c.grid();
                List<ItemStack> overflow = new ArrayList<>();
                for (int slot : g.overflow) {
                    overflow.add(c.container.getItem(slot).copy());
                }
                ItemStack icon = new ItemStack(player.level().getBlockState(c.pos).getBlock().asItem());
                views.add(new TetrisNet.ContainerView(c.pos, c.name, g.width, g.height, g.view(), overflow, List.copyOf(g.overflow), g.filter(), icon));
            }
        }
        int storehouse = home == null ? 0 : home.level(com.cavmkii.seancraft.tarkov.Module.STOREHOUSE);
        // The first container is the chest you opened, if you opened one (the rest is the stash).
        ServerPlayNetworking.send(player, new TetrisNet.Sync(home != null && storehouse > 0, storehouse, views, d.autoRefill, d.refillFromStash,
                List.copyOf(d.tracked), rt.container != null && !views.isEmpty()));
    }

    /** The home's Storehouse level where the player stands, or 0. */
    static int storehouse(ServerPlayer player) {
        Hideouts.Hideout home = Hideouts.at(player.serverLevel(), player.position());
        return home == null ? 0 : home.level(com.cavmkii.seancraft.tarkov.Module.STOREHOUSE);
    }

    // ================================================================== storing

    /**
     * Puts a stack into the inventory (3.4): tops up matching stacks anywhere, then empty main slots, then the hotbar.
     * Leaves what didn't fit in the stack.
     */
    static void storeInInventory(Player player, ItemStack stack) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < Inventory.INVENTORY_SIZE && !stack.isEmpty(); i++) {
            ItemStack s = inv.items.get(i);
            if (ItemStack.isSameItemSameComponents(s, stack) && s.getCount() < s.getMaxStackSize()) {
                int n = Math.min(stack.getCount(), s.getMaxStackSize() - s.getCount());
                s.grow(n);
                stack.shrink(n);
            }
        }
        for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
            int from = pass == 0 ? Inventory.getSelectionSize() : 0;
            int to = pass == 0 ? Inventory.INVENTORY_SIZE : Inventory.getSelectionSize();
            for (int i = from; i < to && !stack.isEmpty(); i++) {
                if (inv.items.get(i).isEmpty()) {
                    inv.items.set(i, stack.split(Math.min(stack.getCount(), stack.getMaxStackSize())));
                }
            }
        }
    }

    // ================================================================== carried items, for other systems

    /** Live stacks the player carries: the vanilla inventory (36 slots) and the off-hand. Shrinking one uses it up. Armor is not included. */
    public static List<ItemStack> carried(Player player) {
        List<ItemStack> out = new ArrayList<>(player.getInventory().items);
        out.add(player.getOffhandItem());
        return out;
    }

    /** A random live stack from the main inventory (never the hotbar or worn gear), for Joe to steal from (7.2). Empty if none. */
    public static ItemStack stealable(ServerPlayer player, net.minecraft.util.RandomSource random) {
        List<ItemStack> all = new ArrayList<>();
        Inventory inv = player.getInventory();
        for (int i = Inventory.getSelectionSize(); i < Inventory.INVENTORY_SIZE; i++) {
            if (!inv.items.get(i).isEmpty()) {
                all.add(inv.items.get(i));
            }
        }
        return all.isEmpty() ? ItemStack.EMPTY : all.get(random.nextInt(all.size()));
    }

    // ================================================================== containers

    /** A container on the right of GEAR. {@code holder} keeps its grid layout (for a double chest, one fixed half). */
    record Shown(BlockPos pos, String name, Container container, BlockEntity holder) {
        ContainerGrid grid() {
            return ContainerGrid.of(container, holder);
        }
    }

    /** The opened container and, in a hideout, every stash chest. Same order as the client sees. */
    static List<Shown> containers(ServerPlayer player) {
        Runtime rt = rt(player);
        List<Shown> out = new ArrayList<>();
        Set<BlockPos> covered = new HashSet<>();
        ServerLevel level = player.serverLevel();
        if (rt.container != null) {
            Container c = containerAt(player, rt.container);
            if (c != null) {
                Shown shown = shown(level, rt.container, c, covered);
                if (shown != null) {
                    out.add(shown);
                }
            }
        }
        Hideouts.Hideout home = Hideouts.at(level, player.position());
        if (home != null) {
            for (Container c : Stash.containers(level, home)) {
                if (!(c instanceof BlockEntity be) || covered.contains(be.getBlockPos())) {
                    continue;
                }
                // A double chest's halves show as one Large Chest, the same as when you open it.
                BlockPos pos = be.getBlockPos();
                BlockState state = level.getBlockState(pos);
                Container whole = state.getBlock() instanceof ChestBlock chest ? ChestBlock.getContainer(chest, state, level, pos, true) : c;
                Shown shown = shown(level, pos, whole != null ? whole : c, covered);
                if (shown != null) {
                    out.add(shown);
                }
            }
        }
        return out;
    }

    /**
     * One container to show, marking its block (both halves for a double chest) as covered. The grid layout lives
     * on the half with the lower position, so it's the same whichever half you open or how you reach it.
     */
    private static Shown shown(ServerLevel level, BlockPos pos, Container c, Set<BlockPos> covered) {
        Set<BlockPos> halves = new HashSet<>();
        halves.add(pos);
        BlockPos holderPos = pos;
        BlockState state = level.getBlockState(pos);
        if (c instanceof CompoundContainer && state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
            halves.add(other);
            if (other.asLong() < holderPos.asLong()) {
                holderPos = other;
            }
        }
        covered.addAll(halves);
        BlockEntity holder = level.getBlockEntity(holderPos);
        return holder == null ? null : new Shown(pos, containerName(level, pos, halves), c, holder);
    }

    /** A container's custom name if either half has one, else its default ("Large Chest" for a double). */
    private static String containerName(ServerLevel level, BlockPos pos, Set<BlockPos> halves) {
        for (BlockPos p : halves) {
            if (level.getBlockEntity(p) instanceof BaseContainerBlockEntity be && be.getCustomName() != null) {
                return be.getCustomName().getString();
            }
        }
        if (halves.size() > 1) {
            return "Large Chest";
        }
        return level.getBlockEntity(pos) instanceof BaseContainerBlockEntity be ? be.getDisplayName().getString() : "Container";
    }

    /** Renames a container (both halves of a double chest). Blank goes back to the default name. */
    static void rename(ServerPlayer player, int index, String name) {
        List<Shown> shown = containers(player);
        if (index < 0 || index >= shown.size()) {
            return;
        }
        BlockPos pos = shown.get(index).pos;
        ServerLevel level = player.serverLevel();
        Set<BlockPos> halves = new HashSet<>();
        halves.add(pos);
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            halves.add(pos.relative(ChestBlock.getConnectedDirection(state)));
        }
        String clean = name.strip();
        for (BlockPos p : halves) {
            if (level.getBlockEntity(p) instanceof BaseContainerBlockEntity be) {
                ((com.cavmkii.seancraft.mixin.ContainerNameAccessor) be).seancraft$setName(clean.isEmpty() ? null : Component.literal(clean));
                be.setChanged();
            }
        }
        rt(player).dirty = true;
    }

    private static Container containerAt(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        if (player.distanceToSqr(Vec3.atCenterOf(pos)) > CONTAINER_REACH_SQR) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock chest) {
            return ChestBlock.getContainer(chest, state, level, pos, false);
        }
        if (state.getBlock() instanceof BarrelBlock && level.getBlockEntity(pos) instanceof Container c) {
            return c;
        }
        return null;
    }

    private static void openContainer(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        if (level.getBlockEntity(pos) instanceof BaseContainerBlockEntity be && !be.canOpen(player)) {
            return;
        }
        Container c = containerAt(player, pos);
        if (c == null) {
            return;
        }
        Runtime rt = rt(player);
        rt.container = pos;
        rt.open = true;
        BlockState state = level.getBlockState(pos);
        level.playSound(null, pos, state.getBlock() instanceof BarrelBlock ? SoundEvents.BARREL_OPEN : SoundEvents.CHEST_OPEN,
                SoundSource.BLOCKS, 0.5f, level.random.nextFloat() * 0.1f + 0.9f);
        player.awardStat(state.getBlock() instanceof BarrelBlock ? net.minecraft.stats.Stats.OPEN_BARREL : net.minecraft.stats.Stats.OPEN_CHEST);
        sync(player);
        ServerPlayNetworking.send(player, new TetrisNet.Open(0));
    }

    /**
     * Where things get put away: containers whose filter allows the item, narrowest filter first (a chest that only
     * takes weapons beats one that takes everything), like RimWorld stockpiles.
     */
    private static List<Shown> routeFor(List<Shown> shown, ItemStack stack) {
        List<Shown> out = new ArrayList<>();
        for (Shown c : shown) {
            if (StorageCategory.allows(c.grid().filter(), stack)) {
                out.add(c);
            }
        }
        out.sort((a, b) -> Integer.compare(Integer.bitCount(a.grid().filter()), Integer.bitCount(b.grid().filter())));
        return out;
    }

    /** Puts a stack away into the stash the way STASH ALL would (filters first). Leaves what didn't fit in the stack. */
    static void storeInStash(ServerPlayer player, ItemStack stack) {
        for (Shown c : routeFor(containers(player), stack)) {
            storeIn(c, stack);
            if (stack.isEmpty()) {
                return;
            }
        }
    }

    /** Puts a stack into a container's grid: top up first, then wherever it fits. Leaves the rest in the stack. */
    private static void storeIn(Shown c, ItemStack stack) {
        ContainerGrid g = c.grid();
        g.insertAuto(stack);
        g.save();
        c.container.setChanged();
    }

    // ================================================================== moving

    /** A place an item can be taken from. */
    private interface Ref {
        ItemStack get();

        void set(ItemStack stack);
    }

    private static Ref ref(ServerPlayer player, InvData data, Loc loc) {
        Inventory inv = player.getInventory();
        switch (loc.kind()) {
            case Loc.HAND -> {
                if (loc.a() < 0 || loc.a() >= Inventory.INVENTORY_SIZE) {
                    return null;
                }
                return new Ref() {
                    public ItemStack get() { return inv.items.get(loc.a()); }
                    public void set(ItemStack s) { inv.items.set(loc.a(), s); }
                };
            }
            case Loc.EQUIP -> {
                return switch (loc.a()) {
                    case 0, 1, 2, 3 -> new Ref() {
                        public ItemStack get() { return inv.armor.get(3 - loc.a()); }
                        public void set(ItemStack s) { inv.armor.set(3 - loc.a(), s); }
                    };
                    case 6 -> new Ref() {
                        public ItemStack get() { return inv.offhand.get(0); }
                        public void set(ItemStack s) { inv.offhand.set(0, s); }
                    };
                    default -> null;
                };
            }
            case Loc.CONTAINER -> {
                List<Shown> shown = containers(player);
                if (loc.a() < 0 || loc.a() >= shown.size()) {
                    return null;
                }
                Container c = shown.get(loc.a()).container;
                ContainerGrid g = shown.get(loc.a()).grid();
                // b = -1: an overflow item, by raw slot (c). Otherwise the grid cell (b, c).
                int slot = loc.b() == -1 ? loc.c() : g.slotAt(loc.b(), loc.c());
                if (slot < 0 || slot >= c.getContainerSize() || c.getItem(slot).isEmpty()) {
                    return null;
                }
                return new Ref() {
                    public ItemStack get() { return c.getItem(slot); }
                    public void set(ItemStack s) {
                        c.setItem(slot, s);
                        if (s.isEmpty()) {
                            g.forget(slot);
                        }
                        g.save();
                        c.setChanged();
                    }
                };
            }
            case Loc.CRAFT -> {
                if (loc.a() < 0 || loc.a() >= 9) {
                    return null;
                }
                var cells = rt(player).craft;
                return new Ref() {
                    public ItemStack get() { return cells.get(loc.a()); }
                    public void set(ItemStack s) { cells.set(loc.a(), s); }
                };
            }
            default -> {
                return null;
            }
        }
    }

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** Can this stack go into a single-item location at all? */
    private static boolean accepts(ServerPlayer player, Loc loc, ItemStack stack) {
        if (loc.kind() != Loc.EQUIP) {
            return true;
        }
        return switch (loc.a()) {
            case 0, 1, 2, 3 -> player.getEquipmentSlotForItem(stack) == ARMOR[loc.a()];
            default -> true;
        };
    }

    /** Can the item at this location be taken out? Cursed armor stays on. */
    private static String locked(ServerPlayer player, InvData data, Loc loc, ItemStack stack) {
        if (loc.kind() != Loc.EQUIP) {
            return null;
        }
        if (loc.a() <= 3 && EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) {
            return "It won't come off.";
        }
        return null;
    }

    private static int slotLimit(Loc loc, ItemStack stack, Container container) {
        if (loc.kind() == Loc.EQUIP && loc.a() != 6) {
            return 1;
        }
        int max = stack.getMaxStackSize();
        return container != null ? Math.min(max, container.getMaxStackSize()) : max;
    }

    /** Puts a stack at a location. Returns what's left. {@code self} is the container entry being moved, if any. */
    private static ItemStack insert(ServerPlayer player, InvData data, Loc to, ItemStack stack, boolean rotated, Placed self) {
        switch (to.kind()) {
            case Loc.DISCARD -> {
                player.drop(stack.copyAndClear(), false, true);
                return stack;
            }
            case Loc.CONTAINER -> {
                List<Shown> shown = containers(player);
                if (to.b() == -2) {
                    // Dropped on the storage list: into that chest, or (index -1) wherever the filters route it.
                    if (to.a() == -1) {
                        storeInStash(player, stack);
                    } else if (to.a() >= 0 && to.a() < shown.size()) {
                        storeIn(shown.get(to.a()), stack);
                    }
                    return stack;
                }
                if (to.a() < 0 || to.a() >= shown.size() || to.b() < 0) {
                    return stack;
                }
                ContainerGrid g = shown.get(to.a()).grid();
                g.insertAt(stack, to.b(), to.c(), rotated);
                g.save();
                g.container.setChanged();
                return stack;
            }
            case Loc.MATERIAL -> {
                // Back to your inventory.
                storeInInventory(player, stack);
                return stack;
            }
            case Loc.CRAFT, Loc.HAND, Loc.EQUIP -> {
                Container container = null;
                if (!accepts(player, to, stack)) {
                    return stack;
                }
                Ref r = ref(player, data, to);
                if (r == null) {
                    return stack;
                }
                ItemStack cur = r.get();
                int limit = slotLimit(to, stack, container);
                if (cur.isEmpty()) {
                    r.set(stack.split(Math.min(limit, stack.getCount())));
                } else if (ItemStack.isSameItemSameComponents(cur, stack)) {
                    int n = Math.min(stack.getCount(), limit - cur.getCount());
                    if (n > 0) {
                        cur.grow(n);
                        stack.shrink(n);
                        r.set(cur);
                    }
                }
                return stack;
            }
            default -> {
                return stack;
            }
        }
    }

    private static boolean slotKind(Loc loc) {
        return loc.kind() == Loc.HAND || loc.kind() == Loc.CRAFT || loc.kind() == Loc.EQUIP;
    }

    static void move(ServerPlayer player, Loc from, Loc to, boolean rotated, int count) {
        if (!player.isAlive() || !active(player)) {
            return;
        }
        InvData data = data(player);
        if (from.kind() == Loc.MATERIAL) {
            // Taken from wherever you have it (hotbar, grids, stash); what doesn't land goes back.
            ItemStack moving = CraftGrid.takeMaterial(player, from.a(), count);
            if (!moving.isEmpty()) {
                ItemStack left = insert(player, data, to, moving, rotated, null);
                if (!left.isEmpty()) {
                    give(player, left);
                }
            }
            rt(player).dirty = true;
            return;
        }
        Ref src = ref(player, data, from);
        if (src == null || src.get().isEmpty()) {
            return;
        }
        ItemStack source = src.get();
        String why = locked(player, data, from, source);
        if (why != null) {
            player.displayClientMessage(Component.literal(why).withStyle(ChatFormatting.RED), true);
            return;
        }
        if (from.kind() == Loc.CONTAINER && to.kind() == Loc.CONTAINER && to.b() == -2) {
            // Storage back onto storage: it's already put away.
            return;
        }
        int n = count <= 0 ? source.getCount() : Math.min(count, source.getCount());
        boolean whole = n == source.getCount();
        if (whole && from.kind() == Loc.CONTAINER && to.kind() == Loc.CONTAINER && from.a() == to.a() && from.b() >= 0 && to.b() >= 0) {
            // Rearranging inside one container: just move it in the grid.
            List<Shown> shown = containers(player);
            ContainerGrid g = shown.get(from.a()).grid();
            int slot = g.slotAt(from.b(), from.c());
            int there = g.slotAt(to.b(), to.c());
            if (slot >= 0 && (there < 0 || there == slot)) {
                if (g.relocate(slot, to.b(), to.c(), rotated)) {
                    g.save();
                }
                rt(player).dirty = true;
                return;
            }
        }
        ItemStack moving = source.copyWithCount(n);
        ItemStack left = insert(player, data, to, moving, rotated, null);
        int moved = n - left.getCount();
        if (moved > 0) {
            source.shrink(moved);
            src.set(source.isEmpty() ? ItemStack.EMPTY : source);
        } else if (whole && slotKind(from) && slotKind(to)) {
            // Swap two single-item places, if each can take the other's item.
            Ref dst = ref(player, data, to);
            if (dst != null && !dst.get().isEmpty() && locked(player, data, to, dst.get()) == null
                    && accepts(player, to, source) && accepts(player, from, dst.get())
                    && source.getCount() <= slotLimit(to, source, null) && dst.get().getCount() <= slotLimit(from, dst.get(), null)) {
                ItemStack other = dst.get();
                dst.set(source);
                src.set(other);
            }
        } else if (whole && (to.kind() == Loc.HAND || to.kind() == Loc.CRAFT || to.kind() == Loc.EQUIP) && from.kind() == Loc.CONTAINER) {
            // Dropped on an occupied hotbar, craft cell or equipment slot: swap, and the displaced item takes the moved one's place.
            Ref dst = ref(player, data, to);
            if (dst != null && !dst.get().isEmpty() && locked(player, data, to, dst.get()) == null
                    && accepts(player, to, source) && source.getCount() <= slotLimit(to, source, null)) {
                ItemStack other = dst.get();
                src.set(ItemStack.EMPTY);
                dst.set(source);
                ItemStack back = insert(player, data, from, other, false, null);
                if (!back.isEmpty()) {
                    back = insert(player, data, from, back, true, null);
                }
                if (!back.isEmpty()) {
                    give(player, back);
                }
            }
        }
        rt(player).dirty = true;
    }

    static void quickMove(ServerPlayer player, Loc from) {
        if (!player.isAlive() || !active(player)) {
            return;
        }
        InvData data = data(player);
        Ref src = ref(player, data, from);
        if (src == null || src.get().isEmpty() || locked(player, data, from, src.get()) != null) {
            return;
        }
        ItemStack stack = src.get().copy();
        if (from.kind() == Loc.CRAFT) {
            give(player, stack);
            src.set(ItemStack.EMPTY);
            rt(player).dirty = true;
            return;
        }
        if (from.kind() == Loc.CONTAINER || from.kind() == Loc.EQUIP) {
            storeInInventory(player, stack);
        } else {
            List<Shown> shown = containers(player);
            if (!shown.isEmpty()) {
                // An opened chest takes it as asked; otherwise the stash puts it where its filters say.
                List<Shown> targets = new ArrayList<>();
                if (rt(player).container != null) {
                    targets.add(shown.get(0));
                }
                targets.addAll(routeFor(rt(player).container != null ? shown.subList(1, shown.size()) : shown, stack));
                for (Shown c : targets) {
                    storeIn(c, stack);
                    if (stack.isEmpty()) {
                        break;
                    }
                }
            } else if (from.kind() == Loc.HAND && from.a() >= Inventory.getSelectionSize()) {
                // Main inventory, nothing open: wear it if it's armor and the slot is free, else to the hotbar (vanilla).
                for (int slot = 0; slot < 4 && !stack.isEmpty(); slot++) {
                    Loc eq = Loc.equip(slot);
                    if (accepts(player, eq, stack) && ref(player, data, eq).get().isEmpty()) {
                        stack = insert(player, data, eq, stack, false, null);
                    }
                }
                moveWithin(player.getInventory(), stack, 0, Inventory.getSelectionSize());
            } else if (from.kind() == Loc.HAND) {
                // Hotbar, nothing open: into the main inventory (vanilla).
                moveWithin(player.getInventory(), stack, Inventory.getSelectionSize(), Inventory.INVENTORY_SIZE);
            }
        }
        src.set(stack.isEmpty() ? ItemStack.EMPTY : stack);
        rt(player).dirty = true;
    }

    /** Tops up, then fills empty slots, within one range of the inventory. Leaves what didn't fit. */
    private static void moveWithin(Inventory inv, ItemStack stack, int from, int to) {
        for (int i = from; i < to && !stack.isEmpty(); i++) {
            ItemStack s = inv.items.get(i);
            if (ItemStack.isSameItemSameComponents(s, stack) && s.getCount() < s.getMaxStackSize()) {
                int n = Math.min(stack.getCount(), s.getMaxStackSize() - s.getCount());
                s.grow(n);
                stack.shrink(n);
            }
        }
        for (int i = from; i < to && !stack.isEmpty(); i++) {
            if (inv.items.get(i).isEmpty()) {
                inv.items.set(i, stack.split(stack.getCount()));
            }
        }
    }

    /**
     * REFILL STACKS (3.4): tops up the partial stacks you carry to full from the open container or, at home with
     * Storehouse 2, the whole stash. Never adds an item you aren't already carrying.
     */
    private static void refillStacks(ServerPlayer player, InvData data) {
        List<Shown> shown = containers(player);
        if (rt(player).container == null && storehouse(player) < 2) {
            player.displayClientMessage(Component.literal("Refilling from the stash needs Storehouse 2.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (rt(player).container != null && storehouse(player) < 2 && !shown.isEmpty()) {
            // Only the opened chest without Storehouse 2.
            shown = shown.subList(0, 1);
        }
        if (shown.isEmpty()) {
            return;
        }
        List<ItemStack> mine = new ArrayList<>(player.getInventory().items);
        for (ItemStack stack : mine) {
            if (stack.isEmpty() || !stack.isStackable() || stack.getCount() >= stack.getMaxStackSize()) {
                continue;
            }
            for (Shown c : shown) {
                Container box = c.container();
                for (int slot = 0; slot < box.getContainerSize() && stack.getCount() < stack.getMaxStackSize(); slot++) {
                    ItemStack there = box.getItem(slot);
                    if (there.isEmpty() || !ItemStack.isSameItemSameComponents(there, stack)) {
                        continue;
                    }
                    int n = Math.min(there.getCount(), stack.getMaxStackSize() - stack.getCount());
                    there.shrink(n);
                    stack.grow(n);
                    if (there.isEmpty()) {
                        box.setItem(slot, ItemStack.EMPTY);
                    }
                    box.setChanged();
                }
            }
        }
    }

    static void action(ServerPlayer player, int action) {
        Runtime rt = rt(player);
        InvData data = data(player);
        switch (action) {
            case 0 -> sortInventory(player.getInventory());
            case 1 -> {
                List<Shown> shown = containers(player);
                Inventory inv = player.getInventory();
                for (int i = Inventory.getSelectionSize(); i < Inventory.INVENTORY_SIZE; i++) {
                    ItemStack stack = inv.items.get(i);
                    for (Shown c : routeFor(shown, stack)) {
                        if (stack.isEmpty()) {
                            break;
                        }
                        storeIn(c, stack);
                    }
                    if (stack.isEmpty()) {
                        inv.items.set(i, ItemStack.EMPTY);
                    }
                }
            }
            case 3 -> rt.container = null;
            case 4 -> rt.open = true;
            case 8 -> {
                if (storehouse(player) >= 2) {
                    organize(player);
                }
            }
            case 9 -> refillStacks(player, data);
            case 6 -> data.autoRefill = !data.autoRefill;
            case 7 -> data.refillFromStash = !data.refillFromStash;
            case 5 -> {
                rt.open = false;
                CraftGrid.returnAll(player);
                rt.lastRecipe = null;
                if (rt.container != null) {
                    BlockState state = player.level().getBlockState(rt.container);
                    player.level().playSound(null, rt.container, state.getBlock() instanceof BarrelBlock ? SoundEvents.BARREL_CLOSE
                            : SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.5f, player.level().random.nextFloat() * 0.1f + 0.9f);
                }
                rt.container = null;
            }
            default -> { }
        }
        rt.dirty = true;
    }

    /** SORT on the player inventory: stacks merged, then ordered by kind and name, in the 27 main slots. */
    private static void sortInventory(Inventory inv) {
        List<ItemStack> all = new ArrayList<>();
        for (int i = Inventory.getSelectionSize(); i < Inventory.INVENTORY_SIZE; i++) {
            ItemStack s = inv.items.get(i);
            inv.items.set(i, ItemStack.EMPTY);
            if (s.isEmpty()) {
                continue;
            }
            for (ItemStack into : all) {
                if (ItemStack.isSameItemSameComponents(into, s) && into.getCount() < into.getMaxStackSize()) {
                    int n = Math.min(s.getCount(), into.getMaxStackSize() - into.getCount());
                    into.grow(n);
                    s.shrink(n);
                }
            }
            if (!s.isEmpty()) {
                all.add(s);
            }
        }
        all.sort((a, b) -> {
            int c = Integer.compare(StorageCategory.of(a).ordinal(), StorageCategory.of(b).ordinal());
            return c != 0 ? c : a.getHoverName().getString().compareToIgnoreCase(b.getHoverName().getString());
        });
        for (int i = 0; i < all.size(); i++) {
            inv.items.set(Inventory.getSelectionSize() + i, all.get(i));
        }
    }

    /** ORGANIZE STASH: anything sitting in a container that doesn't allow it moves to one that does. */
    static void organize(ServerPlayer player) {
        List<Shown> shown = containers(player);
        for (Shown c : shown) {
            ContainerGrid g = c.grid();
            int mask = g.filter();
            boolean moved = false;
            for (int slot = 0; slot < c.container.getContainerSize(); slot++) {
                ItemStack stack = c.container.getItem(slot);
                if (stack.isEmpty() || StorageCategory.allows(mask, stack)) {
                    continue;
                }
                for (Shown target : routeFor(shown, stack)) {
                    if (target != c) {
                        storeIn(target, stack);
                    }
                    if (stack.isEmpty()) {
                        break;
                    }
                }
                if (stack.isEmpty()) {
                    c.container.setItem(slot, ItemStack.EMPTY);
                    moved = true;
                }
            }
            if (moved) {
                ContainerGrid.of(c.container, c.holder).save();
                c.container.setChanged();
            }
        }
        mergeAcross(shown);
    }

    /**
     * Tops up part stacks across the stash: the same item spread over several chests in partial stacks gets poured
     * together, earliest chest first, so it ends up in as few stacks as it can.
     */
    private static void mergeAcross(List<Shown> shown) {
        List<int[]> partial = new ArrayList<>();
        for (int ci = 0; ci < shown.size(); ci++) {
            Container c = shown.get(ci).container;
            for (int slot = 0; slot < c.getContainerSize(); slot++) {
                ItemStack s = c.getItem(slot);
                if (!s.isEmpty() && s.getCount() < Math.min(s.getMaxStackSize(), c.getMaxStackSize())) {
                    partial.add(new int[] {ci, slot});
                }
            }
        }
        Set<Integer> changed = new HashSet<>();
        for (int i = 0; i < partial.size(); i++) {
            Container into = shown.get(partial.get(i)[0]).container;
            ItemStack target = into.getItem(partial.get(i)[1]);
            for (int j = partial.size() - 1; j > i && !target.isEmpty(); j--) {
                int room = Math.min(target.getMaxStackSize(), into.getMaxStackSize()) - target.getCount();
                if (room <= 0) {
                    break;
                }
                Container from = shown.get(partial.get(j)[0]).container;
                ItemStack source = from.getItem(partial.get(j)[1]);
                if (source.isEmpty() || !ItemStack.isSameItemSameComponents(source, target)
                        || !StorageCategory.allows(shown.get(partial.get(i)[0]).grid().filter(), source)) {
                    continue;
                }
                int n = Math.min(room, source.getCount());
                target.grow(n);
                source.shrink(n);
                if (source.isEmpty()) {
                    from.setItem(partial.get(j)[1], ItemStack.EMPTY);
                }
                changed.add(partial.get(i)[0]);
                changed.add(partial.get(j)[0]);
            }
        }
        for (int ci : changed) {
            Shown c = shown.get(ci);
            ContainerGrid.of(c.container, c.holder).save();
            c.container.setChanged();
        }
    }

    /** A container's SORT (0), TAKE ALL (1) and filter (2) buttons. */
    static void containerAction(ServerPlayer player, int index, int action, int value) {
        if (!player.isAlive() || !active(player)) {
            return;
        }
        List<Shown> shown = containers(player);
        if (index < 0 || index >= shown.size()) {
            return;
        }
        Shown c = shown.get(index);
        ContainerGrid g = c.grid();
        if (action == 3) {
            return;        } else if (action == 2) {
            // Filters come with Storehouse 2 (3.4.1).
            if (storehouse(player) < 2) {
                player.displayClientMessage(Component.literal("Chest filters need Storehouse 2.").withStyle(ChatFormatting.GRAY), true);
                return;
            }
            g.setFilter(value);
        } else if (action == 0) {
            g.sort();
        } else if (action == 1) {
            for (int slot = 0; slot < c.container.getContainerSize(); slot++) {
                ItemStack stack = c.container.getItem(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                storeInInventory(player, stack);
                if (stack.isEmpty()) {
                    c.container.setItem(slot, ItemStack.EMPTY);
                    g.forget(slot);
                }
            }
        }
        g.save();
        c.container.setChanged();
        rt(player).dirty = true;
    }

    private static boolean holds(Container container, ItemStack stack) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (ItemStack.isSameItemSameComponents(container.getItem(i), stack)) {
                return true;
            }
        }
        return false;
    }

    // ================================================================== crafting

    static boolean nearTable(ServerPlayer player) {
        if (Hideouts.at(player.serverLevel(), player.position()) != null) {
            return true;
        }
        BlockPos at = player.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-TABLE_RANGE, -3, -TABLE_RANGE), at.offset(TABLE_RANGE, 3, TABLE_RANGE))) {
            if (player.serverLevel().getBlockState(p).is(Blocks.CRAFTING_TABLE)) {
                return true;
            }
        }
        return false;
    }

    /** What crafting may draw from: the inventory, and the stash at home with Storehouse 3 (2.4.1). */
    static List<ItemStack> pool(ServerPlayer player, List<Container> touched) {
        List<ItemStack> out = new ArrayList<>(player.getInventory().items);
        Hideouts.Hideout home = Hideouts.at(player.serverLevel(), player.position());
        if (home != null && home.level(com.cavmkii.seancraft.tarkov.Module.STOREHOUSE) >= 3) {
            for (Container c : Stash.containers(player.serverLevel(), home)) {
                touched.add(c);
                for (int i = 0; i < c.getContainerSize(); i++) {
                    out.add(c.getItem(i));
                }
            }
        }
        return out;
    }

    private static boolean usable(CraftingRecipe recipe, boolean table) {
        if (recipe instanceof ShapedRecipe shaped) {
            return table || (shaped.getWidth() <= 2 && shaped.getHeight() <= 2);
        }
        if (recipe instanceof ShapelessRecipe) {
            return table || recipe.getIngredients().size() <= 4;
        }
        return false;
    }

    /** Picks stacks for one craft, adding to {@code used}. Null if the pool can't cover it. */
    private static Map<ItemStack, Integer> match(CraftingRecipe recipe, List<ItemStack> pool, Map<ItemStack, Integer> used) {
        List<Ingredient> ingredients = new ArrayList<>();
        for (Ingredient ing : recipe.getIngredients()) {
            if (!ing.isEmpty()) {
                ingredients.add(ing);
            }
        }
        // Narrow ingredients first, so a flexible one doesn't take what a strict one needs.
        ingredients.sort((a, b) -> Integer.compare(a.getItems().length, b.getItems().length));
        Map<ItemStack, Integer> take = new IdentityHashMap<>();
        for (Ingredient ing : ingredients) {
            boolean found = false;
            for (ItemStack s : pool) {
                int usedHere = used.getOrDefault(s, 0) + take.getOrDefault(s, 0);
                if (!s.isEmpty() && s.getCount() > usedHere && ing.test(s)) {
                    take.merge(s, 1, Integer::sum);
                    found = true;
                    break;
                }
            }
            if (!found) {
                return null;
            }
        }
        take.forEach((s, n) -> used.merge(s, n, Integer::sum));
        return take;
    }

    static void sendCraftList(ServerPlayer player) {
        CraftGrid.Preview preview = CraftGrid.preview(player);
        List<ItemStack> grid = new ArrayList<>();
        rt(player).craft.forEach(s -> grid.add(s.copy()));
        ServerPlayNetworking.send(player, new TetrisNet.CraftList(craftable(player), grid, preview.result().copy(), preview.needsTable(),
                CraftGrid.materials(player), Hideouts.at(player.serverLevel(), player.position()) != null, nearTable(player),
                rt(player).lastRecipe == null ? "" : rt(player).lastRecipe));
    }

    static List<TetrisNet.CraftEntry> craftable(ServerPlayer player) {
        List<TetrisNet.CraftEntry> out = new ArrayList<>();
        if (!active(player)) {
            return out;
        }
        boolean table = nearTable(player);
        List<ItemStack> pool = pool(player, new ArrayList<>());
        var registries = player.registryAccess();
        for (RecipeHolder<CraftingRecipe> holder : player.server.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            CraftingRecipe recipe = holder.value();
            if (!usable(recipe, table)) {
                continue;
            }
            ItemStack result = recipe.getResultItem(registries);
            if (result.isEmpty() || !com.cavmkii.seancraft.rpg.Ladders.canCraft(player, result)) {
                continue;
            }
            Map<ItemStack, Integer> used = new IdentityHashMap<>();
            int cap = Math.max(1, result.getMaxStackSize() / result.getCount());
            int times = 0;
            while (times < cap && match(recipe, pool, used) != null) {
                times++;
            }
            if (times > 0) {
                out.add(new TetrisNet.CraftEntry(holder.id().toString(), result.copy(), times));
            }
        }
        out.sort((a, b) -> a.result().getHoverName().getString().compareToIgnoreCase(b.result().getHoverName().getString()));
        if (Hideouts.benchesOf(player.server, player.getUUID()).isEmpty()) {
            // No Home Bench placed yet (4.1): put it first, even before you have the materials.
            String id = com.cavmkii.seancraft.SeanCraft.id("home_bench").toString();
            TetrisNet.CraftEntry bench = null;
            for (TetrisNet.CraftEntry e : out) {
                if (e.id().equals(id)) {
                    bench = e;
                }
            }
            if (bench != null) {
                out.remove(bench);
            } else {
                // Not craftable here; -1 means you have the materials but need a crafting table.
                var holder = player.server.getRecipeManager().byKey(ResourceLocation.parse(id)).orElse(null);
                boolean have = holder != null && holder.value() instanceof CraftingRecipe r
                        && match(r, pool(player, new ArrayList<>()), new IdentityHashMap<>()) != null;
                bench = new TetrisNet.CraftEntry(id, new ItemStack(com.cavmkii.seancraft.tarkov.TarkovContent.HIDEOUT_BENCH_ITEM), have ? -1 : 0);
            }
            out.add(0, bench);
        }
        return out;
    }

    static void craft(ServerPlayer player, String id, int times) {
        if (!player.isAlive() || !active(player)) {
            return;
        }
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) {
            return;
        }
        var holder = player.server.getRecipeManager().byKey(key).orElse(null);
        if (holder == null || !(holder.value() instanceof CraftingRecipe recipe) || !usable(recipe, nearTable(player))) {
            return;
        }
        List<Container> touched = new ArrayList<>();
        List<ItemStack> pool = pool(player, touched);
        ItemStack result = recipe.getResultItem(player.registryAccess());
        int made = 0;
        for (int t = 0; t < Math.max(1, Math.min(64, times)); t++) {
            Map<ItemStack, Integer> take = match(recipe, pool, new IdentityHashMap<>());
            if (take == null) {
                break;
            }
            List<ItemStack> remainders = new ArrayList<>();
            take.forEach((s, n) -> {
                if (s.getItem().hasCraftingRemainingItem()) {
                    for (int i = 0; i < n; i++) {
                        remainders.add(new ItemStack(s.getItem().getCraftingRemainingItem()));
                    }
                }
                s.shrink(n);
            });
            ItemStack out = result.copy();
            out.onCraftedBy(player.level(), player, out.getCount());
            give(player, out);
            remainders.forEach(r -> give(player, r));
            made++;
        }
        touched.forEach(Container::setChanged);
        if (made > 0) {
            player.awardStat(net.minecraft.stats.Stats.ITEM_CRAFTED.get(result.getItem()), made * result.getCount());
            player.level().playSound(null, player.blockPosition(), SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.PLAYERS, 0.4f, 1.2f);
        }
        rt(player).dirty = true;
    }

    /** Into the inventory; what doesn't fit drops at the player's feet. */
    public static void give(ServerPlayer player, ItemStack stack) {
        storeInInventory(player, stack);
        if (!stack.isEmpty()) {
            player.drop(stack.copyAndClear(), false);
        }
    }
}
