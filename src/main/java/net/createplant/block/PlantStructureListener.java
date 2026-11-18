package net.createplant.block;

import net.minecraft.core.BlockPos;

public interface PlantStructureListener {
    boolean containsStructure(BlockPos pos);
    void structureChanged();
}
