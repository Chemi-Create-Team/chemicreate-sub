package net.createplant.block.entity;

import java.util.List;
import net.createplant.block.GasTankContainerBlock;
import net.createplant.registry.CETransport;
import net.createplant.thermal.FluidState;
import net.createplant.thermal.FluidThermal;
import net.createplant.thermal.Grade;
import net.createplant.thermal.ThermalFluidTank;
import net.createplant.thermal.TwoPhase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * The gas tank container's tank, rated for high pressure and turned into a gas-liquid separator. A stream that is part liquid and
 * part gas, such as the RBMK's wet steam, goes in at a side face, the liquid is kept in the bottom row and the gas in the top row. Open to pipes
 * only once the blocks form a tank of a container size.
 */
public class HighPressureSeparatorBlockEntity extends GasTankContainerBlockEntity {
    private static final int PER_BLOCK = 16000;
    private final ThermalFluidTank liquid = buffer(), gas = buffer();

    public HighPressureSeparatorBlockEntity(BlockPos pos, BlockState state) { super(CETransport.HIGH_PRESSURE_SEPARATOR_ENTITY.get(), pos, state); }

    private ThermalFluidTank buffer() {
        return new ThermalFluidTank(PER_BLOCK, fluid -> changed()) { @Override protected int storageRatio() { return Grade.Pressure.HIGH.storageRatio(); } };
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) sendData();
    }

    /** The separator keeps its contents in its own two buffers; Create's single tank stays shut. */
    @Override protected com.simibubi.create.foundation.fluid.SmartFluidTank createInventory() {
        var tank = super.createInventory();
        tank.setValidator(stack -> false);
        return tank;
    }

    private HighPressureSeparatorBlockEntity root() {
        return complete() && !isRemoved() && getControllerBE() instanceof HighPressureSeparatorBlockEntity root ? root : null;
    }

    private int volume() { return Math.max(1, getWidth() * getWidth() * getHeight()) * PER_BLOCK; }

    private void resize() {
        for (var tank : List.of(liquid, gas)) { var kept = tank.getFluid().copy(); tank.setCapacity(volume()); tank.setFluid(kept); }
    }

    @Override public void setWidth(int width) { super.setWidth(width); resize(); }
    @Override public void setHeight(int height) { super.setHeight(height); resize(); }

    public FluidStack liquid() { return liquid.getFluid().copy(); }
    public FluidStack gas() { return gas.getFluid().copy(); }

    /** Takes a stream whole or in part: a two-phase one is divided between the buffers, a single phase goes to its own buffer. */
    private int admit(FluidStack stream, IFluidHandler.FluidAction action) {
        if (stream.isEmpty()) return 0;
        int taken = TwoPhase.is(stream) ? TwoPhase.divide(stream, liquid, gas, action) : (FluidState.gas(stream) ? gas : liquid).fill(stream, action);
        if (taken > 0 && action.execute()) changed();
        return taken;
    }

    private enum Mode { INLET, GAS, LIQUID }

    private IFluidHandler port(Direction side) {
        var root = root();
        if (root == null) return null;
        int layer = worldPosition.getY() - root.worldPosition.getY();
        if (side == Direction.UP) return layer == root.getWidth() - 1 ? new Port(root, Mode.GAS) : null;
        if (side == Direction.DOWN) return layer == 0 ? new Port(root, Mode.LIQUID) : null;
        return new Port(root, Mode.INLET);
    }

    private final class Port implements IFluidHandler {
        private final HighPressureSeparatorBlockEntity root;
        private final Mode mode;

        Port(HighPressureSeparatorBlockEntity root, Mode mode) { this.root = root; this.mode = mode; }

        private boolean live() { return !isRemoved() && level != null && level.getBlockEntity(worldPosition) == HighPressureSeparatorBlockEntity.this && root() == root; }
        private ThermalFluidTank outlet() { return mode == Mode.GAS ? root.gas : root.liquid; }
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int tank) { return !live() || tank != 0 || mode == Mode.INLET ? FluidStack.EMPTY : outlet().getFluidInTank(0).copy(); }
        @Override public int getTankCapacity(int tank) { return !live() || tank != 0 ? 0 : mode == Mode.INLET ? root.volume() : outlet().getCapacity(); }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return live() && tank == 0 && mode == Mode.INLET && root.admit(stack, FluidAction.SIMULATE) > 0; }
        @Override public int fill(FluidStack stack, FluidAction action) { return live() && mode == Mode.INLET ? root.admit(stack, action) : 0; }
        @Override public FluidStack drain(int amount, FluidAction action) { return !live() || mode == Mode.INLET ? FluidStack.EMPTY : outlet().drain(amount, action); }
        @Override public FluidStack drain(FluidStack stack, FluidAction action) { return FluidThermal.compatible(getFluidInTank(0), stack) ? drain(stack.getAmount(), action) : FluidStack.EMPTY; }
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CETransport.HIGH_PRESSURE_SEPARATOR_ENTITY.get(), (be, side) -> be.port(side));
    }

    @Override public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        var root = root();
        if (root == null) return super.addToGoggleTooltip(tooltip, sneaking);
        tooltip.add(getBlockState().getBlock().getName());
        var source = root.gas.isEmpty() ? root.liquid.getFluid() : root.gas.getFluid();
        if (!source.isEmpty()) tooltip.add(FluidThermal.readout(source));
        tooltip.add(Component.translatable("grade.create_refinery_n_reactors.high_pressure_separator", root.liquid.getFluidAmount(), root.gas.getFluidAmount()));
        return true;
    }

    @Override public void write(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        super.write(tag, lookup, packet);
        tag.put("SeparatorLiquid", liquid.writeToNBT(lookup, new CompoundTag()));
        tag.put("SeparatorGas", gas.writeToNBT(lookup, new CompoundTag()));
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        super.read(tag, lookup, packet);
        resize();
        liquid.readFromNBT(lookup, tag.getCompound("SeparatorLiquid"));
        gas.readFromNBT(lookup, tag.getCompound("SeparatorGas"));
    }
}
