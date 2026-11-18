package net.createplant.block.entity;

import java.util.List;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.createplant.block.PressureValveBlock;
import net.createplant.registry.CEPressure;
import net.createplant.thermal.Compression;
import net.createplant.thermal.FluidThermal;
import net.createplant.transport.FluidHazards;
import net.createplant.transport.PipeMaterial;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

public class PressureValveBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    private final FluidTank input = new FluidTank(4000, stack -> PipeMaterial.VALVE.carries(stack));
    private final FluidTank output = new FluidTank(4000);
    /** Set when the outlet took nothing; any port transfer clears it. */
    private boolean waiting;

    public PressureValveBlockEntity(BlockPos pos, BlockState state) { super(CEPressure.VALVE_ENTITY.get(), pos, state); }
    @Override public void addBehaviours(List<BlockEntityBehaviour> list) { }

    public IFluidHandler port(Direction side) {
        Direction facing = getBlockState().getValue(PressureValveBlock.FACING);
        if (side != null && side.getAxis() != facing.getAxis()) return null;
        boolean inlet = side == facing.getOpposite();
        return new IFluidHandler() {
            private FluidTank tank() { return inlet ? input : output; }
            public int getTanks() { return 1; }
            public FluidStack getFluidInTank(int index) { return tank().getFluid(); }
            public int getTankCapacity(int index) { return tank().getCapacity(); }
            public boolean isFluidValid(int index, FluidStack stack) { return inlet && input.isFluidValid(stack); }
            public int fill(FluidStack stack, FluidAction action) {
                int n = inlet ? input.fill(stack, action) : 0;
                if (action.execute() && n > 0) changed();
                return n;
            }
            public FluidStack drain(int amount, FluidAction action) {
                var stack = inlet ? FluidStack.EMPTY : output.drain(amount, action);
                if (action.execute() && !stack.isEmpty()) changed();
                return stack;
            }
            public FluidStack drain(FluidStack stack, FluidAction action) {
                return !inlet && net.createplant.mixture.MixtureTransfer.matches(output.getFluid(), stack) ? drain(stack.getAmount(), action) : FluidStack.EMPTY;
            }
        };
    }

    private void changed() {
        waiting = false;
        setChanged();
        if (level != null && !level.isClientSide) sendData();
    }

    @Override public void tick() {
        super.tick();
        if (level == null || level.isClientSide || waiting || input.isEmpty()) return;
        int accepted = output.fill(Compression.convert(input.getFluid(), false), EXECUTE);
        if (accepted <= 0) { waiting = true; return; }
        input.drain(accepted, EXECUTE);
        changed();
    }

    public boolean waiting() { return waiting; }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        super.write(tag, lookup, packet);
        tag.put("Input", input.writeToNBT(lookup, new CompoundTag()));
        tag.put("Output", output.writeToNBT(lookup, new CompoundTag()));
    }
    @Override protected void read(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        super.read(tag, lookup, packet);
        input.readFromNBT(lookup, tag.getCompound("Input"));
        output.readFromNBT(lookup, tag.getCompound("Output"));
    }

    @Override public boolean addToGoggleTooltip(List<Component> text, boolean sneaking) {
        text.add(getBlockState().getBlock().getName());
        text.add(Component.translatable("grade.create_refinery_n_reactors.reducer"));
        if (!output.isEmpty()) text.add(FluidThermal.readout(output.getFluid()));
        if (Compression.isAir(output.getFluid())) text.add(Compression.airVolumeLabel(output.getFluid()));
        return true;
    }
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CEPressure.VALVE_ENTITY.get(), (be, side) -> be.port(side));
    }
}
