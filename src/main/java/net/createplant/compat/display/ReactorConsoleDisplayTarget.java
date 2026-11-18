package net.createplant.compat.display;

import java.util.List;
import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.createplant.block.entity.ReactorControlConsoleBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;

/** Lets a standard Create Display Link use any unit of the reactor desk, or of its linked display board, as its target. */
public class ReactorConsoleDisplayTarget extends DisplayTarget {
    @Override public void acceptText(int line, List<MutableComponent> text, DisplayLinkContext context) {
        var console = ReactorControlConsoleBlockEntity.forTarget(context.level(), context.getTargetPos());
        if (console != null)
            console.receiveDisplayLink(context.blockEntity());
    }

    @Override public DisplayTargetStats provideStats(DisplayLinkContext context) { return new DisplayTargetStats(1, 32, this); }

    @Override public AABB getMultiblockBounds(LevelAccessor level, BlockPos pos) {
        if (level instanceof net.minecraft.world.level.Level actual) {
            BlockPos master = ReactorControlConsoleBlockEntity.masterAt(actual, pos);
            if (master != null && actual.getBlockEntity(master) instanceof ReactorControlConsoleBlockEntity console)
                return AABB.encapsulatingFullBlocks(console.getBlockPos(), console.deskPos(ReactorControlConsoleBlockEntity.DESK_WIDTH - 1));
        }
        return super.getMultiblockBounds(level, pos);
    }
}
