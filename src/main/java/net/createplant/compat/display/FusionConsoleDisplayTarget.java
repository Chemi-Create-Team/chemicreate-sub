package net.createplant.compat.display;

import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import java.util.List;
import net.createplant.block.entity.FusionControlConsoleBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;

/** Lets a standard Create Display Link act as the hard-wired instrument cable to the six-section fusion desk. */
public class FusionConsoleDisplayTarget extends DisplayTarget {
    @Override public void acceptText(int line, List<MutableComponent> text, DisplayLinkContext context) {
        BlockPos master = FusionControlConsoleBlockEntity.masterAt(context.level(), context.getTargetPos());
        if (master != null && context.level().getBlockEntity(master) instanceof FusionControlConsoleBlockEntity console)
            console.receiveDisplayLink(context.blockEntity());
    }
    @Override public DisplayTargetStats provideStats(DisplayLinkContext context) { return new DisplayTargetStats(1, 16, this); }
    @Override public AABB getMultiblockBounds(LevelAccessor level, BlockPos pos) {
        if (level instanceof net.minecraft.world.level.Level actual) {
            BlockPos master = FusionControlConsoleBlockEntity.masterAt(actual, pos);
            if (master != null && actual.getBlockEntity(master) instanceof FusionControlConsoleBlockEntity console)
                return AABB.encapsulatingFullBlocks(console.origin(), console.panel(FusionControlConsoleBlockEntity.WIDTH - 1,
                    FusionControlConsoleBlockEntity.DEPTH - 1));
        }
        return super.getMultiblockBounds(level, pos);
    }
}
