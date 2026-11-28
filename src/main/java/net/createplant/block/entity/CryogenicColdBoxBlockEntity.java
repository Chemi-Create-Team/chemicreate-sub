package net.createplant.block.entity;

import java.util.List;
import java.util.Locale;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createplant.block.CryogenicColdBoxBlock;
import net.createplant.registry.CEIsotopeGases;
import net.createplant.registry.CEMetals;
import net.createplant.thermal.FluidThermal;
import net.createplant.thermal.PhaseChange;
import net.createplant.thermal.ThermalFluidTank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

/** Shaft-powered liquefaction of nitrogen, oxygen and helium, with finite cooling-water and product buffers. */
public class CryogenicColdBoxBlockEntity extends KineticBlockEntity implements IHaveGoggleInformation {
    public static final double WATTS_PER_RPM = 4000;
    /** The cooling water leaves just below its boiling point at most. */
    public static final double WATER_LIMIT_KELVIN = 372;
    private final ThermalFluidTank gas = new ThermalFluidTank(128_000, f -> setChanged());
    private final ThermalFluidTank liquid = new ThermalFluidTank(16_000, f -> setChanged());
    private final ThermalFluidTank water = new ThermalFluidTank(32_000, f -> setChanged());
    private final ThermalFluidTank warm = new ThermalFluidTank(32_000, f -> setChanged());
    private double watts;
    private int flow,portEpoch;
    private boolean formed;
    private String status = "structure";

    public CryogenicColdBoxBlockEntity(BlockPos pos, BlockState state) { super(CEIsotopeGases.COLD_BOX_ENTITY.get(), pos, state); }
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) { }
    public Direction front() { return getBlockState().getValue(CryogenicColdBoxBlock.HORIZONTAL_FACING); }
    public ThermalFluidTank gas() { return gas; }
    public ThermalFluidTank liquid() { return liquid; }
    public ThermalFluidTank water() { return water; }
    public ThermalFluidTank warm() { return warm; }

    public double watts() { return watts; }
    public int flow() { return flow; }
    public boolean formed() { return formed; }
    public String status() { return status; }

    public BlockPos body(int x, int y, int z) { return worldPosition.relative(front().getClockWise(), x).above(y).relative(front().getOpposite(), z); }

    private boolean structure() {
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = 1; z <= 3; z++) {
            BlockPos pos = body(x, y, z);
            if (!level.hasChunkAt(pos)) { status = "unloaded";return false; }
            boolean centre = x == 0 && y == 0 && z == 2;
            if (!level.getBlockState(pos).is(centre ? CEIsotopeGases.REGENERATOR.get() : CEMetals.STEEL_BLOCK.get().getBlock())) { status = "structure";return false; }
        }
        return true;
    }

    @Override public void tick() {
        super.tick();
        if (level == null || level.isClientSide || level.getGameTime() % 5 != 0) return;
        step();
    }

    public void step() {
        if (level == null || level.isClientSide) return;
        String previous = status;
        boolean wasFormed = formed;
        watts = 0;flow = 0;
        formed = structure();
        if(wasFormed!=formed){portEpoch++;level.invalidateCapabilities(worldPosition);}
        if (formed) runCycle();
        if (!formed) ProductionMonitor.limitedBy(this, "structure");
        else if (!status.equals("running")) ProductionMonitor.limitedBy(this, status);
        if (hasNetwork()) getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
        if (!previous.equals(status) || wasFormed != formed || watts > 0) { setChanged();sendData(); }
    }

    private static net.createplant.thermal.PhaseChange.Transfer liquefy(FluidStack input) {
        if(!net.createplant.thermal.FluidState.gas(input) ||
            !(input.is(CEIsotopeGases.HELIUM_GAS.get()) || input.is(net.createplant.registry.CEFluids.NITROGEN_GAS.get())
                || input.is(net.createplant.registry.CEFluids.OXYGEN_GAS.get()))) return null;
        int pressure=net.createplant.thermal.FluidState.pressure(input);
        for(int level=net.createplant.thermal.FluidState.temperature(input)-1;level>=0;level--) {
            if(!net.createplant.thermal.FluidState.gas(input,level,pressure))return net.createplant.thermal.PhaseChange.transfer(input,level,pressure);
        }
        return null;
    }

    private void runCycle() {
        var recipe=liquefy(gas.getFluid());
        int workPerLiquid=gas.getFluid().is(CEIsotopeGases.HELIUM_GAS.get()) ? 4000 : 1000;
        int maximum = (int) (256 * WATTS_PER_RPM * .25 / workPerLiquid);
        if (recipe != null) ProductionMonitor.plan(this, recipe.output(maximum).getAmount() * 4d, 0, 0);
        if(Math.abs(getSpeed())<32 || isOverStressed()){status="speed";return;}
        if(level.hasNeighborSignal(worldPosition)){status="redstone";return;}
        if(recipe==null){status="feed";return;}
        if(!net.createplant.thermal.FluidState.liquidWater(water.getFluid())){status="water";return;}
        if(gas.getFluidAmount()<recipe.consumed()){status="feed";return;}
        int coolantPerLiquid=workPerLiquid/20;
        int power = (int)(Math.min(256,Math.abs(getSpeed()))*WATTS_PER_RPM*.25/workPerLiquid);
        int feed = Math.min(gas.getFluidAmount()/recipe.consumed(), power);
        int cooled = Math.min(feed,water.getFluidAmount()/coolantPerLiquid);
        int count = cooled;
        count=Math.min(count,recipe.fitting(count,liquid));
        // The heat taken from the gas (cooling it to its boiling point, condensing it, cooling the liquid) and the work done on it
        // both leave with the cooling water, which is never allowed to boil in the circuit.
        var discharge=water.getFluid().copyWithAmount(Math.max(1,count*coolantPerLiquid));
        int taken=Math.max(1,Math.min(count,cooled));
        var sample=gas.getFluid().copyWithAmount(recipe.consumed()*taken);
        var liquidProduct=recipe.output(taken);
        double boil=net.createplant.thermal.FluidState.boilingKelvin(sample,net.createplant.thermal.FluidState.pressure(sample));
        double toBoil=Math.max(0,gas.kelvin()-boil)*net.createplant.thermal.HeatContent.capacity(sample);
        double subcool=Math.max(0,boil-net.createplant.thermal.FluidState.kelvin(net.createplant.thermal.FluidState.temperature(liquidProduct)))*net.createplant.thermal.HeatContent.capacity(liquidProduct);
        double heat=toBoil+net.createplant.thermal.HeatContent.latent(sample)+subcool+taken*workPerLiquid*4d;
        double outlet=Math.min(WATER_LIMIT_KELVIN,water.kelvin()+heat/Math.max(1e-9,net.createplant.thermal.HeatContent.capacity(discharge)));
        discharge=net.createplant.thermal.FluidState.with(discharge,net.createplant.thermal.FluidState.levelFor(discharge,outlet,net.createplant.thermal.FluidState.pressure(discharge),false),net.createplant.thermal.FluidState.pressure(discharge));
        count=Math.min(count,warm.fill(discharge,SIMULATE)/coolantPerLiquid);
        if(count<=0){status=water.getFluidAmount()<coolantPerLiquid ? "water" : "output";return;}
        lastStressApplied=(float)(count*workPerLiquid*4/Math.abs(getSpeed()));
        if(hasNetwork())getOrCreateNetwork().updateStressFor(this,lastStressApplied);if(isOverStressed()){status="speed";return;}
        var product=recipe.output(count);gas.drain(count*recipe.consumed(),EXECUTE);liquid.fill(product,EXECUTE);water.drain(count*coolantPerLiquid,EXECUTE);warm.fillAt(discharge.copyWithAmount(count*coolantPerLiquid),outlet,EXECUTE);
        ProductionMonitor.produced(this, product.getAmount(), 0, 0);
        ProductionMonitor.limitedBy(this, count < cooled ? "output" : cooled < feed ? "water" : feed < power ? "feed" : power < maximum ? "speed" : "rated");
        watts=count*workPerLiquid*4;flow=count;status="running";
    }


    @Override public float calculateStressApplied() { return lastStressApplied = watts <= 0 ? 0 : (float)(watts / Math.max(1, Math.abs(getSpeed()))); }
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CEIsotopeGases.COLD_BOX_ENTITY.get(), (be, side) -> net.createplant.mixture.GasMixturePort.both(be.port(side), be));
    }
    public IFluidHandler port(Direction side) {
        if (side == null || side == front() || side == front().getOpposite()) return null;
        if (side == Direction.UP) return port(gas, true, true);
        if (side == Direction.DOWN) return port(liquid, false, true);
        return side == front().getClockWise() ? port(water, true, false) : port(warm, false, false);
    }
    private boolean livePort() {
        if (level == null || isRemoved() || !formed || !level.hasChunkAt(worldPosition) || level.getBlockEntity(worldPosition) != this) return false;
        for (int x = -1; x <= 1; x += 2) for (int y = -1; y <= 1; y += 2) for (int z = 1; z <= 3; z += 2)
            if (!level.hasChunkAt(body(x, y, z))) return false;
        return true;
    }
    private IFluidHandler port(ThermalFluidTank tank, boolean input, boolean helium) {
        int lease=portEpoch;return new IFluidHandler() {
            @Override public int getTanks() { return 1; }
            @Override public FluidStack getFluidInTank(int index) { return index == 0 && (lease==portEpoch && livePort()) ? tank.getFluid().copy() : FluidStack.EMPTY; }
            @Override public int getTankCapacity(int index) { return index == 0 && (lease==portEpoch && livePort()) ? tank.getCapacity() : 0; }
            @Override public boolean isFluidValid(int index, FluidStack fluid) { return (lease==portEpoch && livePort()) && index == 0 && input && (helium ? liquefy(fluid)!=null : net.createplant.thermal.FluidState.liquidWater(fluid)); }
            @Override public int fill(FluidStack fluid, FluidAction action) { return isFluidValid(0, fluid) ? tank.fill(fluid, action) : 0; }
            @Override public FluidStack drain(int amount, FluidAction action) { return input || !(lease==portEpoch && livePort()) ? FluidStack.EMPTY : tank.drain(amount, action); }
            @Override public FluidStack drain(FluidStack fluid, FluidAction action) { return input || !(lease==portEpoch && livePort()) ? FluidStack.EMPTY : tank.drain(fluid, action); }
        };
    }
    public void vent() {
        if (!(level instanceof ServerLevel server)) return;
        if (gas.getFluidAmount() + liquid.getFluidAmount() + water.getFluidAmount() + warm.getFluidAmount() == 0) return;
        server.sendParticles(ParticleTypes.CLOUD, worldPosition.getX() + .5, worldPosition.getY() + 1, worldPosition.getZ() + .5, 12, .5, .5, .5, .03);
        level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, .7f, .7f);
    }
    @Override public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        tooltip.add(getBlockState().getBlock().getName());
        tooltip.add(Component.translatable("cold_box.create_refinery_n_reactors.status." + status));
        if(!liquid.isEmpty())tooltip.add(FluidThermal.readout(liquid.getFluid()));
        tooltip.add(Component.translatable("cold_box.create_refinery_n_reactors.output", liquid.getFluidAmount(), String.format(Locale.ROOT, "%.0f", watts / 1000)));
        return true;
    }
    @Override protected void write(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        super.write(tag, lookup, packet);
        tag.put("HeliumIn", gas.writeToNBT(lookup, new CompoundTag()));tag.put("HeliumOut", liquid.writeToNBT(lookup, new CompoundTag()));
        tag.put("WaterIn", water.writeToNBT(lookup, new CompoundTag()));tag.put("WaterOut", warm.writeToNBT(lookup, new CompoundTag()));
        tag.putDouble("Watts", watts);tag.putInt("Flow", flow);tag.putBoolean("Formed", formed);tag.putString("Status", status);
    }
    @Override protected void read(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        super.read(tag, lookup, packet);if(!packet){portEpoch++;if(level!=null)level.invalidateCapabilities(worldPosition);}
        gas.readFromNBT(lookup, tag.getCompound("HeliumIn"));liquid.readFromNBT(lookup, tag.getCompound("HeliumOut"));
        water.readFromNBT(lookup, tag.getCompound("WaterIn"));warm.readFromNBT(lookup, tag.getCompound("WaterOut"));
        formed = packet && tag.getBoolean("Formed");
        watts = packet ? Math.max(0, tag.getDouble("Watts")) : 0;flow = packet ? Math.max(0, tag.getInt("Flow")) : 0;status = packet ? tag.getString("Status") : "structure";
    }
}
