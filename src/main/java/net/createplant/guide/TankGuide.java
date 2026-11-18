package net.createplant.guide;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.createplant.block.TankFootprint;
import net.createplant.block.TankStructure;
import net.createplant.block.entity.TankCasingBlockEntity;
import net.createplant.registry.CEBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Tank of casings: a round floor, a wall ring, an empty interior and an open top. The tank may sit anywhere around a casing, so the
 * candidates are laid out from the first floor casing in x then z order, which is the same cell for every diameter of the footprint.
 */
public final class TankGuide implements StructureGuide {
    private static final int REACH = TankFootprint.MAX_DIAMETER - 1;

    @Override public BlockPos anchor(BlockEntity be) {
        return be instanceof TankCasingBlockEntity casing && !casing.isFormed() ? be.getBlockPos() : null;
    }

    /** A casing inside a formed tank belongs to it, so only the others start a guide. */
    @Override public BlockPos anchor(BlockEntity be, List<BlockEntity> area) {
        BlockPos pos = anchor(be);
        if (pos == null) return null;
        for (BlockEntity other : area) {
            if (!(other instanceof TankCasingBlockEntity tank) || !tank.isFormed()) continue;
            var shape = tank.getShape();
            BlockPos o = shape.origin();
            if (pos.getX() >= o.getX() && pos.getX() < o.getX() + shape.diameter() && pos.getZ() >= o.getZ() && pos.getZ() < o.getZ() + shape.diameter()
                && pos.getY() >= o.getY() && pos.getY() <= o.getY() + shape.height()) return null;
        }
        return pos;
    }

    /** Guides the casing nearest to the player; builders work on one tank at a time. */
    @Override public int limit() { return 1; }

    @Override public List<GhostLayout> layouts(Level level, BlockPos anchor) {
        int floor = anchor.getY();
        for (int steps = 0; steps < TankStructure.MAX_HEIGHT && isCasing(level, anchor.atY(floor - 1)); steps++) floor--;
        BlockPos first = null;
        int top = 0;
        for (int x = -REACH; x <= REACH; x++) for (int z = -REACH; z <= REACH; z++) {
            BlockPos at = new BlockPos(anchor.getX() + x, floor, anchor.getZ() + z);
            if (!isCasing(level, at)) continue;
            if (first == null || at.getX() < first.getX() || at.getX() == first.getX() && at.getZ() < first.getZ()) first = at;
            int layer = 0;
            while (layer < TankStructure.MAX_HEIGHT && isCasing(level, at.above(layer + 1))) layer++;
            top = Math.max(top, layer);
        }
        if (first == null) return List.of();
        Set<Integer> heights = new LinkedHashSet<>(List.of(TankStructure.MIN_HEIGHT, Math.clamp(top, TankStructure.MIN_HEIGHT, TankStructure.MAX_HEIGHT)));
        List<GhostLayout> result = new ArrayList<>();
        var casing = CEBlocks.TANK_CASING.get().defaultBlockState();
        for (int diameter = TankFootprint.MIN_DIAMETER; diameter <= TankFootprint.MAX_DIAMETER; diameter++) {
            int x0 = 0, z0 = 0;
            while (!TankFootprint.contains(x0, z0, diameter)) z0++;
            BlockPos origin = first.offset(-x0, 0, -z0);
            for (int height : heights) {
                List<GhostLayout.Cell> cells = new ArrayList<>();
                for (int x = 0; x < diameter; x++) for (int z = 0; z < diameter; z++) {
                    if (!TankFootprint.contains(x, z, diameter)) continue;
                    boolean wall = TankFootprint.isWall(x, z, diameter);
                    cells.add(GhostLayout.Cell.of(origin.offset(x, 0, z), casing));
                    for (int dy = 1; dy <= height; dy++) {
                        if (wall) cells.add(GhostLayout.Cell.of(origin.offset(x, dy, z), casing));
                        else cells.add(GhostLayout.Cell.free(origin.offset(x, dy, z), (l, at) -> l.getBlockState(at).isAir()));
                    }
                    if (wall) cells.add(GhostLayout.Cell.free(origin.offset(x, height + 1, z), (l, at) -> !l.getBlockState(at).is(CEBlocks.TANK_CASING.get())));
                }
                result.add(new GhostLayout(diameter + " wide, " + height + " high", cells));
            }
        }
        return result;
    }

    private static boolean isCasing(Level level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.getBlockState(pos).is(CEBlocks.TANK_CASING.get());
    }
}
