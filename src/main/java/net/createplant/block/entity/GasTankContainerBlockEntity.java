package net.createplant.block.entity;

import com.simibubi.create.foundation.fluid.SmartFluidTank;
import java.util.ArrayList;
import java.util.List;
import net.createplant.block.GasTankContainerBlock;
import net.createplant.block.TankCells;
import net.createplant.registry.CETransport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Create's horizontal tank held to the container sizes: gases only, rated like the gas receiver, and open to pipes only once the
 * blocks form a tank of a container size. Any other run keeps what it holds until it is completed again.
 */
public class GasTankContainerBlockEntity extends HorizontalTankBlockEntity {

    public GasTankContainerBlockEntity(BlockPos pos, BlockState state) { super(CETransport.GAS_TANK_CONTAINER_ENTITY.get(), pos, state); }

    @Override public int getMaxWidth() { return 3; }

    @Override public int getMaxLength(Direction.Axis axis, int width) { return axis == getMainConnectionAxis() ? GasTankContainerBlock.length(width) : getMaxWidth(); }


    @Override protected SmartFluidTank createInventory() {
        SmartFluidTank tank = new net.createplant.thermal.GradeFluidTank(getCapacityMultiplier(), this::onFluidStackChanged, this);
        tank.setValidator(CETransport::gas);
        return tank;
    }

    /**
     * Whether this block is part of a finished container tank. Read from the world: a block placed into a tank is reshaped while it is
     * still being placed, and the state this block entity keeps is then the placed one.
     */
    public boolean complete() {
        BlockState state = level == null ? getBlockState() : level.getBlockState(worldPosition);
        return state.hasProperty(GasTankContainerBlock.SIZE) && state.getValue(GasTankContainerBlock.SIZE) > 0;
    }

    /**
     * Each part carries its tank's width and length; a tank of a container size gives its parts that width and their place across it.
     * The model's across axis runs east for a tank along Z and north for one along X, as the block state turns it.
     */
    @Override protected BlockState shaped(BlockState state) {
        int size = height == GasTankContainerBlock.length(width) ? width : 0, cell = 0;
        if (size > 0) {
            BlockPos controller = getController();
            int across = getMainConnectionAxis() == Direction.Axis.Z ? worldPosition.getX() - controller.getX() : controller.getZ() + width - 1 - worldPosition.getZ();
            cell = across + width * (worldPosition.getY() - controller.getY());
        }
        return state.setValue(GasTankContainerBlock.SIZE, size).setValue(GasTankContainerBlock.CELL, cell);
    }

    @Override public void notifyMultiUpdated() {
        super.notifyMultiUpdated();
        level.invalidateCapabilities(worldPosition);
    }

    @Override public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        if (!complete()) {
            var controller = getControllerBE();
            int width = controller == null ? 1 : controller.getWidth();
            tooltip.add(Component.translatable("goggles.create_refinery_n_reactors.gas_tank_container.incomplete", width, width, GasTankContainerBlock.length(width)));
        }
        return super.addToGoggleTooltip(tooltip, sneaking) || !complete();
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CETransport.GAS_TANK_CONTAINER_ENTITY.get(), (be, side) -> be.complete() ? be.fluidCapability : null);
    }
}
