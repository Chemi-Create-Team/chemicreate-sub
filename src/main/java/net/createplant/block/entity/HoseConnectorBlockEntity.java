package net.createplant.block.entity;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.List;
import net.createplant.block.HoseConnectorBlock;
import net.createplant.registry.CEPortableTools;
import net.createplant.registry.CEPressureInfrastructure;
import net.createplant.transport.HoseSocket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public final class HoseConnectorBlockEntity extends SmartBlockEntity implements HoseSocket.Endpoint,
        com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {
    private final HoseSocket socket = new HoseSocket(this);
    private final FluidTank buffer = new FluidTank(1000) {
        @Override protected void onContentsChanged() { setChanged(); }
    };

    public HoseConnectorBlockEntity(BlockPos pos, BlockState state) {
        super(CEPressureInfrastructure.CONNECTOR_ENTITY.get(), pos, state);
    }

    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) { }
    @Override public HoseSocket hose() { return socket; }
    @Override public Direction hoseFace() { return getBlockState().getValue(HoseConnectorBlock.FACING); }

    public IFluidHandler port(Direction face) {
        return face == hoseFace().getOpposite() ? new RearPort(face) : null;
    }

    private final class RearPort implements IFluidHandler {
        private final Direction face;
        RearPort(Direction face) { this.face = face; }

        private IFluidHandler target() {
            if (isRemoved() || level == null || face != hoseFace().getOpposite()) return null;
            if (!socket.connected()) return buffer;
            var remote = socket.peer();
            if (remote instanceof HoseConnectorBlockEntity connector) return connector.hoseFluid();
            return remote == null ? null : buffer;
        }

        @Override public int getTanks() { var tank = target(); return tank == null ? 0 : tank.getTanks(); }
        @Override public FluidStack getFluidInTank(int tank) { var to = target(); return to == null ? FluidStack.EMPTY : to.getFluidInTank(tank); }
        @Override public int getTankCapacity(int tank) { var to = target(); return to == null ? 0 : to.getTankCapacity(tank); }
        @Override public boolean isFluidValid(int tank, FluidStack stack) {
            var to = target();
            return to != null && to.isFluidValid(tank, stack);
        }
        @Override public int fill(FluidStack stack, FluidAction action) {
            var to = target();
            int moved = to == null ? 0 : to.fill(stack, action);
            if (action.execute() && to != buffer) socket.transferred(stack, moved);
            return moved;
        }
        @Override public FluidStack drain(FluidStack stack, FluidAction action) {
            var from = target();
            return measured(from, from == null ? FluidStack.EMPTY : from.drain(stack, action), action);
        }
        @Override public FluidStack drain(int amount, FluidAction action) {
            var from = target();
            return measured(from, from == null ? FluidStack.EMPTY : from.drain(amount, action), action);
        }
        private FluidStack measured(IFluidHandler from, FluidStack moved, FluidAction action) {
            if (action.execute() && from != buffer) socket.transferred(moved, moved.getAmount());
            return moved;
        }
    }

    @Override public IFluidHandler hoseFluid() {
        var back = getBlockPos().relative(hoseFace().getOpposite());
        if (level != null && level.hasChunkAt(back) && !(level.getBlockState(back).getBlock() instanceof HoseConnectorBlock)) {
            var tank = level.getCapability(Capabilities.FluidHandler.BLOCK, back, hoseFace());
            if (tank != null && buffer.isEmpty()) return tank;
        }
        return buffer;
    }

    @Override public void lazyTick() {
        super.lazyTick();
        if (level != null && !level.isClientSide && !isVirtual()) socket.validate();
    }

    @Override public void tick() {
        super.tick();
        if (level != null && !level.isClientSide) socket.tickReadout();
    }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        super.write(tag, lookup, packet);
        socket.write(tag);
        if (packet) socket.writeReadout(tag, lookup);
        if (!buffer.isEmpty()) tag.put("Buffer", buffer.writeToNBT(lookup, new CompoundTag()));
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        super.read(tag, lookup, packet);
        socket.read(tag);
        if (packet) socket.readReadout(tag, lookup);
        buffer.readFromNBT(lookup, tag.getCompound("Buffer"));
    }

    @Override protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!buffer.isEmpty()) components.set(CEPortableTools.CONTENTS.get(), SimpleFluidContent.copyOf(buffer.getFluid()));
    }

    @Override protected void applyImplicitComponents(DataComponentInput components) {
        super.applyImplicitComponents(components);
        var contents = components.get(CEPortableTools.CONTENTS.get());
        if (contents != null) buffer.setFluid(contents.copy());
    }
}
