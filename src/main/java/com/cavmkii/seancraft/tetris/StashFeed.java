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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The hideout stash on station screens (Weapon Bench, Hideout Bench, crafting table, anvil...): while one is open in a
 * hideout, the client gets the stash's contents, merged by item, and can take a stack onto the cursor or put the
 * cursor's stack away.
 */
public final class StashFeed {
    private static final Map<UUID, Integer> SENT = new java.util.HashMap<>();

    private StashFeed() {}

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(StashFeed::tick);
        ServerPlayNetworking.registerGlobalReceiver(TetrisNet.StashTake.TYPE, (p, ctx) -> take(ctx.player(), p));
    }

    /** Screens that get the grids beside them: the ones whose menu shows your main inventory. */
    static boolean applies(AbstractContainerMenu menu) {
        for (Slot slot : menu.slots) {
            if (slot.container instanceof Inventory && slot.getContainerSlot() >= Inventory.getSelectionSize()
                    && slot.getContainerSlot() < Inventory.INVENTORY_SIZE) {
                return true;
            }
        }
        return false;
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.containerMenu != player.inventoryMenu && Tetris.active(player) && applies(player.containerMenu)) {
                send(player, false);
            } else {
                SENT.remove(player.getUUID());
            }
        }
    }

    /** Live stacks from the stash containers, if you're in a hideout. */
    private static List<ItemStack> stash(ServerPlayer player, List<Container> touched) {
        List<ItemStack> out = new ArrayList<>();
        Hideouts.Hideout home = Hideouts.at(player.serverLevel(), player.position());
        if (home != null) {
            for (Container c : Stash.containers(player.serverLevel(), home)) {
                touched.add(c);
                for (int i = 0; i < c.getContainerSize(); i++) {
                    if (!c.getItem(i).isEmpty()) {
                        out.add(c.getItem(i));
                    }
                }
            }
        }
        return out;
    }

    private static void send(ServerPlayer player, boolean force) {
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack s : stash(player, new ArrayList<>())) {
            boolean found = false;
            for (ItemStack m : merged) {
                if (ItemStack.isSameItemSameComponents(m, s)) {
                    m.grow(s.getCount());
                    found = true;
                    break;
                }
            }
            if (!found) {
                merged.add(s.copy());
            }
        }
        boolean home = Hideouts.at(player.serverLevel(), player.position()) != null;
        int h = home ? 1 : 0;
        for (ItemStack s : merged) {
            h = 31 * h + ItemStack.hashItemAndComponents(s) * 7 + s.getCount();
        }
        Integer last = SENT.get(player.getUUID());
        if (!force && last != null && last == h) {
            return;
        }
        SENT.put(player.getUUID(), h);
        ServerPlayNetworking.send(player, new TetrisNet.StashStock(home, merged));
    }

    private static void take(ServerPlayer player, TetrisNet.StashTake p) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == player.inventoryMenu || !Tetris.active(player) || !player.isAlive() || !applies(menu)
                || Hideouts.at(player.serverLevel(), player.position()) == null) {
            return;
        }
        ItemStack carried = menu.getCarried();
        if (p.item().isEmpty()) {
            // Put the cursor's stack away.
            if (!carried.isEmpty()) {
                Tetris.storeInStash(player, carried);
                menu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
            }
        } else {
            if (!carried.isEmpty() && !ItemStack.isSameItemSameComponents(carried, p.item())) {
                return;
            }
            int room = p.item().getMaxStackSize() - carried.getCount();
            int want = p.count() <= 0 ? room : Math.min(room, p.count());
            List<Container> touched = new ArrayList<>();
            ItemStack taken = p.item().copyWithCount(0);
            for (ItemStack s : stash(player, touched)) {
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
            if (carried.isEmpty()) {
                menu.setCarried(taken);
            } else {
                carried.grow(taken.getCount());
            }
        }
        menu.broadcastChanges();
        Tetris.rt(player).dirty = true;
        send(player, true);
    }
}
