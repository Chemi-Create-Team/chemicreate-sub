package net.createplant.compat.display;

import java.util.List;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.createplant.block.entity.LiddedVesselBlockEntity;
import net.createplant.mixture.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.fluids.FluidStack;

/** Every constituent is available through Create's normal multi-line display target. */
public final class MixtureDisplaySource extends DisplaySource {
    public static FluidStack contents(net.minecraft.world.level.block.entity.BlockEntity be) {
        if (be instanceof LiddedVesselBlockEntity vessel && vessel.getControllerBE() != null) return vessel.getControllerBE().getTankInventory().getFluid();
        if (be instanceof com.simibubi.create.content.fluids.tank.FluidTankBlockEntity tank && tank.getControllerBE() != null) return tank.getControllerBE().getTankInventory().getFluid();
        return FluidStack.EMPTY;
    }
    @Override public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        return MixtureGoggleHelper.describe(contents(context.getSourceBlockEntity())).stream().map(Component::copy).toList();
    }
    @Override public int getPassiveRefreshTicks() { return 20; }
}
