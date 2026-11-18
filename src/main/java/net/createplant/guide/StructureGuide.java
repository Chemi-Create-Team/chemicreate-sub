package net.createplant.guide;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** The candidate layouts of one kind of multiblock, built from the same rules and limits its structure check uses. */
public interface StructureGuide {
    /** The anchor of an unformed structure that this block entity starts, or null. */
    BlockPos anchor(BlockEntity be);

    /** Like {@link #anchor(BlockEntity)}, for guides that need to see the block entities around it, for example to skip a formed structure. */
    default BlockPos anchor(BlockEntity be, List<BlockEntity> area) { return anchor(be); }

    /** The anchors to guide around the player; by default one per unformed block entity, but a structure without a block entity can use the player instead. */
    default List<BlockPos> anchors(Level level, net.minecraft.world.entity.player.Player player, List<BlockEntity> area) {
        List<BlockPos> result = new java.util.ArrayList<>();
        for (BlockEntity be : area) {
            BlockPos anchor = anchor(be, area);
            if (anchor != null) result.add(anchor);
        }
        return result;
    }

    /** How many structures of this kind are guided at once, nearest first. */
    default int limit() { return 6; }

    List<GhostLayout> layouts(Level level, BlockPos anchor);
}
