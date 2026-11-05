package com.cavmkii.seancraft.ecology;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Patch records for one dimension (17.2). The doc suggests chunk data attachments owned by each patch's top-left
 * chunk; one saved file per dimension does the same job without that chunk having to be loaded.
 */
public final class EcoData extends SavedData {
    public final Map<Long, Patch> patches = new HashMap<>();
    /** Patch size the records were made with; a change starts the model over. */
    public int patchChunks;

    static EcoData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(EcoData::new, EcoData::load, null), "seancraft_ecology");
    }

    private static EcoData load(CompoundTag tag, HolderLookup.Provider registries) {
        EcoData d = new EcoData();
        d.patchChunks = tag.getInt("patchChunks");
        ListTag list = tag.getList("patches", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            d.patches.put(t.getLong("key"), Patch.load(t.getCompound("state")));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("patchChunks", patchChunks);
        ListTag list = new ListTag();
        patches.forEach((key, p) -> {
            CompoundTag t = new CompoundTag();
            t.putLong("key", key);
            t.put("state", p.save());
            list.add(t);
        });
        tag.put("patches", list);
        return tag;
    }
}
