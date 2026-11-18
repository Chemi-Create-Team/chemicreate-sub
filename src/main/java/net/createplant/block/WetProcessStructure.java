package net.createplant.block;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import net.createplant.registry.CEWetProcessing;
import net.minecraft.core.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import static net.createplant.block.WetProcessPartBlock.Role.*;

/** One block-grid circle shared by validation, construction and Ponder. */
public final class WetProcessStructure {
    public static final List<Integer> CLARIFIER_WIDTHS = IntStream.rangeClosed(7, 30).boxed().toList();
    public static final List<Integer> CLARIFIER_HEIGHTS = List.of(3, 4);
    public record Cell(BlockPos pos, BlockState state) { }
    private WetProcessStructure() { }
    public static int minimum(int width) { return -(width - 1) / 2; }
    public static boolean inside(int x, int z, int width) {
        int offset = width % 2 == 0 ? 1 : 0;
        int dx = 2 * x - offset, dz = 2 * z - offset;
        return dx * dx + dz * dz <= width * width;
    }
    public static boolean interior(int x, int z, int width) {
        return inside(x, z, width) && inside(x + 1, z, width) && inside(x - 1, z, width)
            && inside(x, z + 1, width) && inside(x, z - 1, width);
    }
    public static List<Cell> cells(BlockPos drive, Direction front, int width, int height) {
        if (!CLARIFIER_WIDTHS.contains(width) || !CLARIFIER_HEIGHTS.contains(height)) throw new IllegalArgumentException("Unsupported clarifier dimensions");
        var result = new ArrayList<Cell>();
        int radius = width / 2, minimum = minimum(width), bottom = 1 - height;
        Direction right = front.getCounterClockWise();
        for (int x = minimum; x <= radius; x++) for (int z = minimum; z <= radius; z++) {
            if (!inside(x, z, width)) continue;
            boolean wall = !interior(x, z, width);
            for (int y = bottom; y <= 0; y++) {
                BlockState state;
                if (x == 0 && z == 0 && y == 0) state = CEWetProcessing.CLARIFIER.get().defaultBlockState().setValue(WetProcessDriveBlock.FACING, front);
                else {
                    WetProcessPartBlock.Role role = y == bottom || wall ? BASIN : null;
                    Direction face = y == bottom ? Direction.DOWN : Direction.UP;
                    if (x == 0 && z == 0 && y == bottom) { role = FEED;face = Direction.DOWN; }
                    if (x == 0 && z == radius && y == 0) { role = OVERFLOW;face = front; }
                    if (x == 0 && z == minimum && y == bottom) { role = UNDERFLOW;face = front.getOpposite(); }
                    state = role == null ? Blocks.AIR.defaultBlockState() : CEWetProcessing.PARTS.get(role).get().defaultBlockState().setValue(WetProcessPartBlock.FACING, face);
                }
                result.add(new Cell(drive.relative(right, x).relative(front, z).above(y), state));
            }
        }
        return result;
    }
}
