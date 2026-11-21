package net.createplant.block.entity;

import java.util.List;
import java.util.Locale;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createplant.block.ReciprocatingCompressorBlock;
import net.createplant.registry.CEReciprocating;
import net.createplant.thermal.Compression;
import net.createplant.thermal.FluidMaterials;
import net.createplant.thermal.FluidThermal;
import net.createplant.thermal.ThermalFluidTank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** The suction and discharge chambers of a reciprocating compressor; the mounted engines move gas from one to the other. */
public class ReciprocatingCylinderBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    public static final int SUCTION_VOLUME = 1000, DISCHARGE_VOLUME = 250, RATING_BAR = 100;
    private final ThermalFluidTank inlet = new ThermalFluidTank(SUCTION_VOLUME, f -> changed()), outlet = new ThermalFluidTank(DISCHARGE_VOLUME, f -> changed());
    private final IFluidHandler suction = port(true), discharge = port(false);
    private boolean dirty;
    private long lastSync = -20;

    public ReciprocatingCylinderBlockEntity(BlockPos pos, BlockState state) { super(CEReciprocating.CYLINDER_ENTITY.get(), pos, state); }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CEReciprocating.CYLINDER_ENTITY.get(), (be, side) -> side == Direction.UP ? be.suction : side == Direction.DOWN || side == null ? be.discharge : null);
    }

    private void changed() {
        dirty = true;
        setChanged();
    }

    public ThermalFluidTank inlet() { return inlet; }

    public ThermalFluidTank outlet() { return outlet; }

    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) { }

    @Override public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        if (!isRemoved() && dirty && level.getGameTime() - lastSync >= 20) {
            sendData();
            lastSync = level.getGameTime();
            dirty = false;
        }
    }

    private IFluidHandler port(boolean in) {
        return LiveMachinePorts.fluid(this, new IFluidHandler() {
            private ThermalFluidTank tank() { return in ? inlet : outlet; }
            @Override public int getTanks() { return 1; }
            @Override public FluidStack getFluidInTank(int index) { return tank().getFluid(); }
            @Override public int getTankCapacity(int index) { return tank().getCapacity(); }
            @Override public boolean isFluidValid(int index, FluidStack stack) { return in && net.createplant.thermal.FluidState.gas(stack); }
            @Override public int fill(FluidStack stack, FluidAction action) {
                if (!in || !net.createplant.thermal.FluidState.gas(stack)) return 0;
                return inlet.fill(stack,action);
            }
            @Override public FluidStack drain(FluidStack stack, FluidAction action) { return in ? FluidStack.EMPTY : outlet.drain(stack, action); }
            @Override public FluidStack drain(int amount, FluidAction action) { return in ? FluidStack.EMPTY : outlet.drain(amount, action); }
        });
    }

    /** Gas still inside escapes when the cylinder is broken. */
    public void release() {
        if (level instanceof ServerLevel server && (!inlet.isEmpty() || !outlet.isEmpty()))
            server.sendParticles(ParticleTypes.CLOUD, worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5, 12, .3, .3, .3, .02);
        net.createplant.safety.ToxicGas.release(level, worldPosition, inlet.getFluid().copy());
        net.createplant.safety.ToxicGas.release(level, worldPosition, outlet.getFluid().copy());
        inlet.setFluid(FluidStack.EMPTY);
        outlet.setFluid(FluidStack.EMPTY);
    }

    @Override public boolean addToGoggleTooltip(List<Component> text, boolean sneaking) {
        String key = "reciprocating.create_refinery_n_reactors.";
        text.add(getBlockState().getBlock().getName());
        if(!outlet.isEmpty())text.add(FluidThermal.readout(outlet.getFluid()));
        text.add(Component.translatable(key + "contents", inlet.getFluidAmount(), outlet.getFluidAmount(), outlet.isEmpty() ? Component.translatable(key + "empty") : outlet.getFluid().getHoverName()));
        if (Compression.isAir(outlet.getFluid())) text.add(Compression.airVolumeLabel(outlet.getFluid()));
        else if (Compression.isAir(inlet.getFluid())) text.add(Compression.airVolumeLabel(inlet.getFluid()));
        return true;
    }

    private static String format(double value) { return String.format(Locale.ROOT, "%.2f", value); }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider lookup, boolean clientPacket) {
        super.write(tag, lookup, clientPacket);
        tag.put("Suction", inlet.writeToNBT(lookup, new CompoundTag()));
        tag.put("Discharge", outlet.writeToNBT(lookup, new CompoundTag()));
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider lookup, boolean clientPacket) {
        super.read(tag, lookup, clientPacket);
        inlet.readFromNBT(lookup, tag.getCompound("Suction"));
        outlet.readFromNBT(lookup, tag.getCompound("Discharge"));
    }
}
