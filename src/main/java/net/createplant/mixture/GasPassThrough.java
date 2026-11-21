package net.createplant.mixture;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

/** The gases a machine let through unchanged: the components of a mixed feed it does not use, waiting at its gas outlet. */
public final class GasPassThrough extends BlockEntityBehaviour {
    public static final BehaviourType<GasPassThrough> TYPE = new BehaviourType<>();
    public static final int CAPACITY = 64000;
    private FluidStack gas = FluidStack.EMPTY;

    GasPassThrough(SmartBlockEntity machine) { super(machine); }

    public static GasPassThrough of(BlockEntity machine) { return machine == null ? null : get(machine, TYPE); }

    /** Every machine that takes a mixed gas feed and has a gas outlet for what it does not use, on the block that owns the process. */
    public static void attach(com.simibubi.create.api.event.BlockEntityBehaviourEvent event) {
        for (var type : java.util.List.<net.minecraft.world.level.block.entity.BlockEntityType<? extends SmartBlockEntity>>of(
                net.createplant.registry.CEReactor.ENTITY.get(), net.createplant.registry.CEReactor.ABSORPTION_ENTITY.get(),
                net.createplant.registry.CETransport.LIDDED_VESSEL_ENTITY.get(), net.createplant.registry.CETransport.REACTION_TOWER_ENTITY.get(),
                net.createplant.registry.CETransport.AMMONIA_SEPARATOR_ENTITY.get(), net.createplant.registry.CEDistillation.COLUMN_ENTITY.get(),
                net.createplant.registry.CEConverter.TRUNNION_ENTITY.get(), net.createplant.registry.CEElectrolysis.CONTROLLER_ENTITY.get(),
                net.createplant.registry.CEFuelCell.FUEL_CELL_ENTITY.get(), net.createplant.registry.CEFusion.CONTROLLER_ENTITY.get(),
                net.createplant.registry.CEFusionFuel.COLD_BOX_ENTITY.get(), net.createplant.registry.CECompressor.RELIEF_SCRUBBER_ENTITY.get(),
                net.createplant.registry.CEProcessBlocks.COOLER_RADIATOR_DRIVE.get(), net.createplant.registry.CEReciprocating.CYLINDER_ENTITY.get(),
                net.createplant.registry.CEGasifier.PART.get(), net.createplant.registry.CEFuelCycle.MACHINE_ENTITY.get(),
                net.createplant.registry.CEWetProcessing.DRIVE_ENTITY.get(), net.createplant.registry.CEHighStressMachines.CENTRIFUGE_ENTITY.get()))
            event.forType(type, machine -> event.attach(new GasPassThrough(machine)));
    }

    @Override public BehaviourType<?> getType() { return TYPE; }
    public FluidStack gas() { return gas; }
    public int room() { return Math.max(0, CAPACITY - gas.getAmount()); }

    void add(FluidStack component) {
        if (component.isEmpty()) return;
        gas = gas.isEmpty() ? component.copy() : MixtureHelper.merge(gas, component, true);
        blockEntity.setChanged();
    }

    FluidStack take(int amount, FluidAction action) {
        int taken = Math.min(Math.max(0, amount), gas.getAmount());
        if (taken == 0) return FluidStack.EMPTY;
        var result = MixtureHelper.portion(gas, taken);
        if (action.execute()) { gas = MixtureHelper.remainder(gas, taken);blockEntity.setChanged(); }
        return result;
    }

    /** Breaking the machine lets the waiting gas out. */
    @Override public void destroy() {
        if (!gas.isEmpty() && getWorld() != null && !getWorld().isClientSide()) net.createplant.safety.ToxicGas.release(getWorld(), getPos(), gas);
        gas = FluidStack.EMPTY;
    }

    @Override public void write(CompoundTag tag, HolderLookup.Provider lookup, boolean clientPacket) {
        if (!clientPacket && !gas.isEmpty()) tag.put("PassThroughGas", gas.save(lookup));
    }

    @Override public void read(CompoundTag tag, HolderLookup.Provider lookup, boolean clientPacket) {
        if (!clientPacket) gas = tag.contains("PassThroughGas") ? FluidStack.parseOptional(lookup, tag.getCompound("PassThroughGas")) : FluidStack.EMPTY;
    }
}
