package net.createplant.atomics.block.entity;

import java.util.List;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createplant.atomics.nuclear.ReactorCore;
import net.createplant.atomics.registry.CENuclear;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** The link from a coolant port in the shield to the core it belongs to, set by that core when it looks over its vault. */
public class ShieldPortBlockEntity extends SmartBlockEntity {
    private BlockPos controller;

    public ShieldPortBlockEntity(BlockPos pos, BlockState state) { super(CENuclear.SHIELD_PORT_ENTITY.get(), pos, state); }

    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) { }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CENuclear.SHIELD_PORT_ENTITY.get(), ShieldPortBlockEntity::port);
    }

    /** Joins the core led from {@code lead}, or leaves it with null; neighbouring pipes reconnect. */
    public void link(BlockPos lead) {
        if (java.util.Objects.equals(lead, controller)) return;
        controller = lead;
        level.invalidateCapabilities(worldPosition);
        getBlockState().updateNeighbourShapes(level, worldPosition, net.minecraft.world.level.block.Block.UPDATE_ALL);
        setChanged();
    }

    public void unlink(BlockPos lead) { if (lead.equals(controller)) link(null); }

    private ReactorCore core() {
        if (controller == null || level == null || !level.hasChunkAt(worldPosition) || !level.hasChunkAt(controller)) return null;
        return !isRemoved() && level.getBlockEntity(worldPosition) == this
            && level.getBlockEntity(controller) instanceof FuelChannelBlockEntity lead && lead.core() != null && lead.core().vaultContains(worldPosition) ? lead.core() : null;
    }

    private IFluidHandler port(Direction side) {
        var core = core();
        if (core == null) return null;
        return new IFluidHandler() {
            private boolean live() { return core() == core; }
            @Override public int getTanks() { return live() ? core.outlet().getTanks() : 0; }
            @Override public FluidStack getFluidInTank(int tank) { return live() ? core.outlet().getFluidInTank(tank) : FluidStack.EMPTY; }
            @Override public int getTankCapacity(int tank) { return live() ? core.outlet().getTankCapacity(tank) : 0; }
            @Override public boolean isFluidValid(int tank, FluidStack stack) { return live() && core.outlet().isFluidValid(tank, stack); }
            @Override public int fill(FluidStack stack, FluidAction action) { return 0; }
            @Override public FluidStack drain(FluidStack stack, FluidAction action) { return live() ? core.outlet().drain(stack, action) : FluidStack.EMPTY; }
            @Override public FluidStack drain(int amount, FluidAction action) { return live() ? core.outlet().drain(amount, action) : FluidStack.EMPTY; }
        };
    }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (controller != null) tag.putLong("Controller", controller.asLong());
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        controller = tag.contains("Controller") ? BlockPos.of(tag.getLong("Controller")) : null;
    }
}
