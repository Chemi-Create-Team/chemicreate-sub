package net.createplant.block;

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import net.createplant.registry.CETransport;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** The gas tank container's frame and shell, built into a high-pressure gas-liquid separator: it forms at the same container sizes. */
public class HighPressureSeparatorBlock extends GasTankContainerBlock {
    public HighPressureSeparatorBlock(Properties properties) { super(properties); }

    @Override public BlockEntityType<? extends FluidTankBlockEntity> getBlockEntityType() { return CETransport.HIGH_PRESSURE_SEPARATOR_ENTITY.get(); }
}
