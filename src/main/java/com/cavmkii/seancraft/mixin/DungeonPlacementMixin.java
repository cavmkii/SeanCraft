package com.cavmkii.seancraft.mixin;

import com.cavmkii.seancraft.compat.Dungeons;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** When Dungeons Arise placement (18.1): none in the Heartland, and only some of the rest. */
@Mixin(ChunkGenerator.class)
abstract class DungeonPlacementMixin {
    @Inject(method = "tryGenerateStructure", at = @At("HEAD"), cancellable = true)
    private void seancraft$dungeons(StructureSet.StructureSelectionEntry entry, StructureManager structures, RegistryAccess registries,
            RandomState random, StructureTemplateManager templates, long seed, ChunkAccess chunk, ChunkPos pos, SectionPos section,
            CallbackInfoReturnable<Boolean> cir) {
        if (!Dungeons.allowed(entry.structure(), pos, seed)) {
            cir.setReturnValue(false);
        }
    }
}
