package net.createplant.nuclear;

import java.util.function.Supplier;
import net.createplant.block.ReactorConsoleDisplayBlock;
import net.createplant.block.ReactorControlConsoleBlock;
import net.createplant.registry.CENuclear;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * A grid of identical units that acquire their position as a section: the four-unit operator desk, or the 7 x 4 display board
 * stacked from the floor up. Sections run along the operator's right first, then upwards.
 */
public final class ReactorConsoleStructure {
    public static final ReactorConsoleStructure DESK = new ReactorConsoleStructure(CENuclear.REACTOR_CONTROL_CONSOLE, ReactorControlConsoleBlock.SECTION, 4, 1);
    public static final ReactorConsoleStructure DISPLAY = new ReactorConsoleStructure(CENuclear.REACTOR_CONSOLE_DISPLAY, ReactorConsoleDisplayBlock.SECTION, 7, 4);
    public static final int MASTER = 0;
    public final int width, height, cells, unformed;
    private final Supplier<? extends Block> block;
    private final IntegerProperty section;
    private final int[] corners;

    private ReactorConsoleStructure(Supplier<? extends Block> block, IntegerProperty section, int width, int height) {
        this.block = block;
        this.section = section;
        this.width = width;
        this.height = height;
        this.cells = width * height;
        this.unformed = cells;
        this.corners = new int[] {0, width - 1, cells - width, cells - 1};
    }

    /** The section's offset from section 0, which is the station's master. */
    public BlockPos offset(int part, Direction facing) {
        return BlockPos.ZERO.relative(facing.getCounterClockWise(), part % width).above(part / width);
    }
    /** Places a full set of raw units facing the operator, for tests, previews and play-test worlds. */
    public void place(java.util.function.BiConsumer<BlockPos, BlockState> set, BlockPos root, Direction facing) {
        for (int part = 0; part < cells; part++)
            set.accept(root.offset(offset(part, facing)), block.get().defaultBlockState().setValue(ReactorControlConsoleBlock.FACING, facing));
    }
    /** The usual board position for a desk: one block behind and above it, overhanging it by one block on the operator's left. */
    public static BlockPos boardBehind(BlockPos desk, Direction facing) {
        return desk.relative(facing.getOpposite()).above().relative(facing.getCounterClockWise(), -1);
    }
    public int section(BlockState state) {
        return state.getValue(section);
    }
    public BlockPos master(BlockPos pos, BlockState state) {
        if (!state.is(block.get()))
            return null;
        int part = state.getValue(section);
        return part == unformed ? null : pos.subtract(offset(part, state.getValue(ReactorControlConsoleBlock.FACING)));
    }
    public boolean loaded(Level level, BlockPos root, Direction facing) {
        for (int part : corners)
            if (!level.hasChunkAt(root.offset(offset(part, facing))))
                return false;
        return true;
    }
    public boolean complete(Level level, BlockPos root, Direction facing) {
        return loaded(level, root, facing) && matches(level, root, facing, false);
    }
    private boolean matches(Level level, BlockPos root, Direction facing, boolean raw) {
        for (int part = 0; part < cells; part++) {
            var state = level.getBlockState(root.offset(offset(part, facing)));
            if (!state.is(block.get()) || state.getValue(ReactorControlConsoleBlock.FACING) != facing)
                return false;
            int actual = state.getValue(section);
            if (actual != part && !(raw && actual == unformed))
                return false;
        }
        return true;
    }
    public boolean tryForm(ServerLevel level, BlockPos placed) {
        var state = level.getBlockState(placed);
        if (!state.is(block.get()))
            return false;
        Direction facing = state.getValue(ReactorControlConsoleBlock.FACING);
        var known = master(placed, state);
        if (known != null)
            return form(level, known, facing);
        for (int part = 0; part < cells; part++)
            if (form(level, placed.subtract(offset(part, facing)), facing))
                return true;
        return false;
    }
    private boolean form(ServerLevel level, BlockPos root, Direction facing) {
        if (!loaded(level, root, facing) || !matches(level, root, facing, true))
            return false;
        boolean changed = false;
        for (int part = 0; part < cells; part++) {
            var pos = root.offset(offset(part, facing));
            var before = level.getBlockState(pos);
            var next = before.setValue(section, part);
            if (before != next) {
                level.setBlock(pos, next, Block.UPDATE_CLIENTS);
                changed = true;
            }
        }
        if (level.getBlockEntity(root) instanceof ReactorConsoleStation station && (changed || !station.assembled()))
            station.stationChanged();
        return true;
    }
    public void changed(Level level, BlockPos pos, BlockState previous) {
        if (level.isClientSide)
            return;
        var root = master(pos, previous);
        if (root != null && level.hasChunkAt(root) && level.getBlockEntity(root) instanceof ReactorConsoleStation station)
            station.invalidateStation();
    }
}
