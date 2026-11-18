package net.createplant.guide;

import java.util.List;
import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** One way to complete a structure: every block it needs, where, and what counts as already correct. */
public record GhostLayout(String name, List<Cell> cells) {
    /** @param ghost the block to draw, or null for a cell the structure needs left free; @param accepts whether the world already satisfies the cell */
    public record Cell(BlockPos pos, BlockState ghost, BiPredicate<Level, BlockPos> accepts) {
        public static Cell of(BlockPos pos, BlockState state) { return new Cell(pos, state, (level, at) -> level.getBlockState(at).is(state.getBlock())); }
        public static Cell free(BlockPos pos, BiPredicate<Level, BlockPos> accepts) { return new Cell(pos, null, accepts); }
    }
}
