package net.createplant.compat.display;

import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import java.util.List;
import net.createplant.block.entity.ProcessControlBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.MutableComponent;

public class PidDisplayTarget extends DisplayTarget {
    @Override public void acceptText(int line, List<MutableComponent> text, DisplayLinkContext context) {
        if (!(context.getSourceBlockEntity() instanceof ProcessControlBlockEntity instrument) || !instrument.valid()) return;
        BlockPos target = context.getTargetPos();
        if (!(context.level().getBlockEntity(target) instanceof ProcessControlBlockEntity)) target = target.below();
        if (context.level().getBlockEntity(target) instanceof ProcessControlBlockEntity pid) pid.receive(instrument.normalized());
    }
    @Override public DisplayTargetStats provideStats(DisplayLinkContext context) { return new DisplayTargetStats(1, 16, this); }
}
