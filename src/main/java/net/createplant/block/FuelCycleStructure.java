package net.createplant.block;

import java.util.ArrayList;
import java.util.List;
import net.createplant.registry.CEFuelCycle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class FuelCycleStructure {
    public record Cell(BlockPos pos, BlockState state) { }
    /** Storeys of the column that carry the uranium, plutonium, waste and solvent outlets, from the bottom. */
    public static final int[] OUTLETS = {2, 4, 7, 9};
    private FuelCycleStructure() { }
    public static List<Cell> cells(BlockPos controller, Direction facing, boolean melter) {
        List<Cell> cells = new ArrayList<>();
        Direction across = facing.getClockWise();
        if (melter) {
            for (int d = 0; d < 7; d++) for (int x = -1; x <= 2; x++) for (int y = 0; y < 4; y++) {
                BlockPos pos = controller.relative(facing.getOpposite(), d).relative(across, x).above(y);
                boolean core = d >= 2 && d <= 4 && x >= 0 && x <= 1;
                boolean edgeX = x == -1 || x == 2, edgeZ = d == 0 || d == 6;
                if (d == 0 && x == 0 && y == 0) add(cells, pos, CEFuelCycle.MELTER.get(), facing);
                else if (core && y >= 1 && y <= 2) add(cells, pos, CEFuelCycle.MELTER_ELECTRODE.get(), facing);
                else {
                    int part = core ? (y == 3 ? 5 : 3) : edgeX && edgeZ ? 0 : edgeX && (y == 0 || y == 3) ? 1
                        : edgeZ && (y == 0 || y == 3) ? 2 : edgeX && y == 2 ? 6 : -1;
                    if (part < 0) add(cells, pos, net.minecraft.world.level.block.Blocks.AIR, facing);
                    else cells.add(new Cell(pos, CEFuelCycle.MELTER_SHELL.get().defaultBlockState().setValue(ProcessFrameBlock.PART, part)
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, facing)));
                }
            }
        } else {
            // Twelve octagonal storeys stand behind the drive: settlers at both ends, pulse storeys between, four of them drawing products.
            BlockPos base = controller.relative(facing.getOpposite(), 2);
            add(cells, controller, CEFuelCycle.EXTRACTION_COLUMN.get(), facing);
            for (int y = 0; y < 12; y++) {
                BlockPos centre = base.above(y);
                int outlet = java.util.Arrays.binarySearch(OUTLETS, y);
                if (outlet >= 0) cells.add(new Cell(centre, CEFuelCycle.EXTRACTION_OUTLET.get().defaultBlockState().setValue(FuelCycleOutletBlock.TANK, outlet + 2)));
                else add(cells, centre, y > 1 && y < 10 ? CEFuelCycle.PULSE_PLATE.get() : CEFuelCycle.COLUMN_SHELL.get(), facing);
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
                    if (x != 0 || z != 0) cells.add(new Cell(centre.offset(x, 0, z), ExtractionColumnSectionBlock.ring(x, z)));
            }
        }
        return cells;
    }
    private static void add(List<Cell> cells, BlockPos pos, Block block, Direction facing) {
        BlockState state = block.defaultBlockState();
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
        cells.add(new Cell(pos, state));
    }
}
