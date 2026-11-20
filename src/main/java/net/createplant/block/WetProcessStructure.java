package net.createplant.block;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import net.createplant.registry.CEBlocks;
import net.createplant.registry.CEWetProcessing;
import net.minecraft.core.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** The open tank and clarifier share the tank casing footprint. Heights include the floor. */
public final class WetProcessStructure {
    public static final List<Integer> CLARIFIER_WIDTHS = IntStream.rangeClosed(7, 29).filter(width -> width % 2 == 1).boxed().toList();
    public static final List<Integer> CLARIFIER_HEIGHTS = List.of(3, 4);
    public record Cell(BlockPos pos, BlockState state) { }
    private WetProcessStructure() { }

    public static int minimum(int width) { return -width / 2; }
    public static boolean interior(int x, int z, int width) {
        int radius = width / 2;
        return TankFootprint.contains(x + radius, z + radius, width) && !TankFootprint.isWall(x + radius, z + radius, width);
    }

    public static List<Cell> tankCells(BlockPos floor, int width, int height) {
        return layout(floor, Direction.NORTH, width, height, false);
    }

    public static List<Cell> cells(BlockPos drive, Direction front, int width, int height) {
        return layout(drive.below(height - 1), front, width, height, true);
    }

    private static List<Cell> layout(BlockPos floor, Direction front, int width, int height, boolean clarifier) {
        if (!CLARIFIER_WIDTHS.contains(width) || !CLARIFIER_HEIGHTS.contains(height)) throw new IllegalArgumentException("Unsupported tank dimensions");
        var result = new ArrayList<Cell>();
        int radius = width / 2;
        Direction right = front.getCounterClockWise();
        BlockState casing = CEBlocks.TANK_CASING.get().defaultBlockState();
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            if (!TankFootprint.contains(x + radius, z + radius, width)) continue;
            boolean wall = !interior(x, z, width);
            for (int y = 0; y < height; y++) {
                BlockState state = y == 0 || wall ? casing : Blocks.AIR.defaultBlockState();
                if (clarifier && x == 0 && z == 0) {
                    if (y == 0) state = CEWetProcessing.PARTS.get(WetProcessPartBlock.Role.FEED).get().defaultBlockState().setValue(WetProcessPartBlock.FACING, Direction.DOWN);
                    if (y == height - 1) state = CEWetProcessing.CLARIFIER.get().defaultBlockState().setValue(WetProcessDriveBlock.FACING, front);
                }
                result.add(new Cell(floor.relative(right, x).relative(front, z).above(y), state));
            }
        }
        return result;
    }

    public static WetProcessPartBlock.Role rimRole(BlockPos drive, Direction front, int width, int height, BlockPos pos) {
        if (pos.equals(drive.relative(front, width / 2))) return WetProcessPartBlock.Role.OVERFLOW;
        if (pos.equals(drive.relative(front.getOpposite(), width / 2).below(height - 1))) return WetProcessPartBlock.Role.UNDERFLOW;
        return null;
    }
}
