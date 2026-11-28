package net.createplant.atomics.block;

import net.createplant.block.ColumnSectionBlock;
import net.createplant.atomics.registry.CEFuelCycle;
import net.minecraft.world.level.block.state.BlockState;

/** One storey of the pulse extraction column: placed as a whole 3 × 3 octagon, with structure blocks filling the ring. */
public class ExtractionColumnSectionBlock extends ColumnSectionBlock {
    public ExtractionColumnSectionBlock(Properties properties) { super(properties); }

    @Override protected BlockState ringCell(int x, int z) { return ring(x, z); }

    public static BlockState ring(int x, int z) {
        return CEFuelCycle.COLUMN_STRUCTURE.get().defaultBlockState().setValue(ExtractionColumnStructureBlock.FACING, ringFacing(x, z));
    }
}
