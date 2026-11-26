package net.createplant.block;

import java.util.Locale;
import net.createplant.registry.CELargePress;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

/** The eight cells around either machine's centre belong to that centre alone. */
public final class LargePressFootprint {
    public enum Edge implements StringRepresentable {
        NORTH_WEST(-1, -1), NORTH(0, -1), NORTH_EAST(1, -1), WEST(-1, 0), EAST(1, 0),
        SOUTH_WEST(-1, 1), SOUTH(0, 1), SOUTH_EAST(1, 1);

        public final int x, z;

        Edge(int x, int z) { this.x = x; this.z = z; }
        @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
        public BlockPos at(BlockPos center) { return center.offset(x, 0, z); }
        public BlockPos center(BlockPos pos) { return pos.offset(-x, 0, -z); }

        static Edge atOffset(int x, int z) {
            for (Edge edge : EDGES) if (edge.x == x && edge.z == z) return edge;
            throw new IllegalArgumentException("Invalid press frame offset: " + x + ", " + z);
        }
    }

    private static final Edge[] EDGES = Edge.values();

    private LargePressFootprint() { }

    public static BlockState edge(Edge edge, boolean depot) {
        return CELargePress.FRAME.get().defaultBlockState().setValue(LargePressFrameBlock.EDGE, edge).setValue(LargePressFrameBlock.DEPOT, depot);
    }

    public static boolean isCenter(BlockState state, boolean depot) {
        return state.is(depot ? CELargePress.DEPOT.get() : CELargePress.PRESS.get());
    }

    static boolean canPlace(BlockPlaceContext context, boolean depot) {
        Level level = context.getLevel();
        BlockPos center = context.getClickedPos();
        var player = context.getPlayer();
        var collision = player == null ? CollisionContext.empty() : CollisionContext.of(player);
        for (Edge edge : EDGES) {
            BlockPos pos = edge.at(center);
            if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos) || !level.getBlockState(pos).isAir()
                || player != null && (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand()))
                || !level.isUnobstructed(edge(edge, depot), pos, collision)) return false;
        }
        return true;
    }

    static void place(Level level, BlockPos center, boolean depot) {
        if (!level.isClientSide()) for (Edge edge : EDGES) level.setBlock(edge.at(center), edge(edge, depot), Block.UPDATE_ALL);
    }

    static void remove(Level level, BlockPos center, boolean depot, boolean moving) {
        if (level.isClientSide() || moving || level.restoringBlockSnapshots) return;
        for (Edge edge : EDGES) {
            BlockPos pos = edge.at(center);
            if (level.hasChunkAt(pos) && level.getBlockState(pos) == edge(edge, depot))
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    public static boolean complete(Level level, BlockPos center, boolean depot) {
        if (!level.hasChunkAt(center) || !isCenter(level.getBlockState(center), depot)) return false;
        for (Edge edge : EDGES) {
            BlockPos pos = edge.at(center);
            if (!level.hasChunkAt(pos) || level.getBlockState(pos) != edge(edge, depot)) return false;
        }
        return true;
    }

    public static boolean openWorkingSpace(Level level, BlockPos center) {
        if (!level.getBlockState(center).isAir()) return false;
        for (Edge edge : EDGES) {
            BlockPos pos = edge.at(center);
            if (!level.hasChunkAt(pos) || !level.getBlockState(pos).isAir()) return false;
        }
        return true;
    }
}
