package com.cavmkii.seancraft.tetris;

import com.cavmkii.seancraft.tarkov.Hideouts;
import com.cavmkii.seancraft.tarkov.Stash;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

/**
 * Feeding a furnace from the grids and the stash. While a furnace (or smoker, or blast furnace) is open, the client
 * is told what fuel and cookable items you have, and clicking one puts it straight into the right slot.
 */
public final class FurnaceFeed {
    /** Last stock sent, per player, so it's only resent when something changed. */
    private static final Map<UUID, Integer> SENT = new java.util.HashMap<>();

    private FurnaceFeed() {}

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(FurnaceFeed::tick);
        ServerPlayNetworking.registerGlobalReceiver(TetrisNet.FurnaceFill.TYPE, (p, ctx) -> fill(ctx.player(), p));
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof AbstractFurnaceMenu menu && Tetris.active(player)) {
                send(player, menu, false);
            } else {
                SENT.remove(player.getUUID());
            }
        }
    }

    private static RecipeType<?> type(AbstractFurnaceMenu menu) {
        return menu instanceof BlastFurnaceMenu ? RecipeType.BLASTING : menu instanceof SmokerMenu ? RecipeType.SMOKING : RecipeType.SMELTING;
    }

    @SuppressWarnings("unchecked")
    private static boolean cookable(ServerPlayer player, AbstractFurnaceMenu menu, ItemStack stack) {
        var type = (RecipeType<net.minecraft.world.item.crafting.AbstractCookingRecipe>) type(menu);
        return player.server.getRecipeManager().getRecipeFor(type, new SingleRecipeInput(stack), player.level()).isPresent();
    }

    /** Live stacks from what you carry (hotbar, off-hand, grids). */
    private static List<ItemStack> yours(ServerPlayer player) {
        return Tetris.carried(player);
    }

    /** Live stacks from the stash, if you're in your hideout. */
    private static List<ItemStack> stash(ServerPlayer player, List<Container> touched) {
        List<ItemStack> out = new ArrayList<>();
        Hideouts.Hideout home = Hideouts.at(player.serverLevel(), player.position());
        // The stash at every station comes with Storehouse 3 (2.4.1).
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

    private static void merge(List<ItemStack> into, ItemStack stack) {
        for (ItemStack m : into) {
            if (ItemStack.isSameItemSameComponents(m, stack)) {
                m.grow(stack.getCount());
                return;
            }
        }
        into.add(stack.copy());
    }

    private static void send(ServerPlayer player, AbstractFurnaceMenu menu, boolean force) {
        List<ItemStack> fuelYou = new ArrayList<>();
        List<ItemStack> smeltYou = new ArrayList<>();
        List<ItemStack> fuelStash = new ArrayList<>();
        List<ItemStack> smeltStash = new ArrayList<>();
        for (ItemStack s : yours(player)) {
            sort(player, menu, s, fuelYou, smeltYou);
        }
        for (ItemStack s : stash(player, new ArrayList<>())) {
            sort(player, menu, s, fuelStash, smeltStash);
        }
        boolean home = Tetris.storehouse(player) >= 3;
        int hash = java.util.Objects.hash(home, hash(fuelYou), hash(fuelStash), hash(smeltYou), hash(smeltStash));
        Integer last = SENT.get(player.getUUID());
        if (!force && last != null && last == hash) {
            return;
        }
        SENT.put(player.getUUID(), hash);
        ServerPlayNetworking.send(player, new TetrisNet.FurnaceStock(home, fuelYou, fuelStash, smeltYou, smeltStash));
    }

    private static void sort(ServerPlayer player, AbstractFurnaceMenu menu, ItemStack s, List<ItemStack> fuel, List<ItemStack> smelt) {
        if (s.isEmpty()) {
            return;
        }
        // Tools, weapons and enchanted things burn too, but nobody means to feed their bow to a furnace.
        if (AbstractFurnaceBlockEntity.isFuel(s) && !s.isDamageableItem() && !s.isEnchanted()) {
            merge(fuel, s);
        }
        if (cookable(player, menu, s)) {
            merge(smelt, s);
        }
    }

    private static int hash(List<ItemStack> list) {
        int h = 1;
        for (ItemStack s : list) {
            h = 31 * h + ItemStack.hashItemAndComponents(s) * 7 + s.getCount();
        }
        return h;
    }

    private static void fill(ServerPlayer player, TetrisNet.FurnaceFill p) {
        if (!(player.containerMenu instanceof AbstractFurnaceMenu menu) || !Tetris.active(player) || !player.isAlive()) {
            return;
        }
        // Furnace menus: slot 0 is the input, 1 the fuel.
        Slot slot = menu.getSlot(p.fuel() ? 1 : 0);
        ItemStack in = slot.getItem();
        if (!in.isEmpty() && !ItemStack.isSameItemSameComponents(in, p.item())) {
            return;
        }
        if (!slot.mayPlace(p.item())) {
            return;
        }
        int room = slot.getMaxStackSize(p.item()) - in.getCount();
        int want = p.count() <= 0 ? room : Math.min(room, p.count());
        if (want <= 0) {
            return;
        }
        List<Container> touched = new ArrayList<>();
        List<ItemStack> source = p.stash() ? stash(player, touched) : yours(player);
        ItemStack taken = p.item().copyWithCount(0);
        for (ItemStack s : source) {
            if (want <= 0) {
                break;
            }
            if (ItemStack.isSameItemSameComponents(s, p.item())) {
                int n = Math.min(want, s.getCount());
                s.shrink(n);
                taken.grow(n);
                want -= n;
            }
        }
        touched.forEach(Container::setChanged);
        if (taken.isEmpty()) {
            return;
        }
        ItemStack left = slot.safeInsert(taken);
        if (!left.isEmpty()) {
            Tetris.give(player, left);
        }
        menu.broadcastChanges();
        Tetris.rt(player).dirty = true;
        send(player, menu, true);
    }
}
