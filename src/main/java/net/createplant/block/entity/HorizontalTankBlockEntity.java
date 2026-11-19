package net.createplant.block.entity;

import com.simibubi.create.api.boiler.BoilerHeater;
import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import java.util.List;
import net.createmod.catnip.animation.LerpedFloat;
import net.createplant.registry.CETransport;
import net.createplant.block.HorizontalFluidTankBlock;
import net.createplant.block.TankCells;
import net.createplant.registry.CEProcessFluids;
import net.createplant.thermal.FluidThermal;
import net.createplant.thermal.SteamGrades;
import net.createplant.thermal.ThermalFluidTank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Create's tank logic for a tank lying along X or Z: the length runs along the block's axis, the section is width x width, and the boiler
 * counts burners under the whole lowest layer. Create's own tank code assumes a vertical column, so every place that walks the cells is replaced.
 * Holding water over burners and with no steam engine attached, it is a fire-tube boiler: the last cell of each bottom row boils the water
 * heated by the burners under its row into high-pressure steam, or superheated steam over superheated burners.
 */
public class HorizontalTankBlockEntity extends FluidTankBlockEntity {
    public static final int STEAM_CAPACITY = 16_000;
    private final ThermalFluidTank steam = new ThermalFluidTank(STEAM_CAPACITY, stack -> setChanged()) { @Override protected int storageRatio() { return 1; } };
    private final IFluidHandler steamOutlet = new IFluidHandler() {
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int tank) { return tank == 0 ? steam.getFluid().copy() : FluidStack.EMPTY; }
        @Override public int getTankCapacity(int tank) { return tank == 0 ? STEAM_CAPACITY : 0; }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return false; }
        @Override public int fill(FluidStack stack, FluidAction action) { return 0; }
        @Override public FluidStack drain(int amount, FluidAction action) { return isRemoved() ? FluidStack.EMPTY : steam.drain(amount, action); }
        @Override public FluidStack drain(FluidStack request, FluidAction action) { return FluidThermal.compatible(steam.getFluid(), request) ? drain(request.getAmount(), action) : FluidStack.EMPTY; }
    };
    private double heatJoules, heatWatts;
    private String status = "heat";

    public HorizontalTankBlockEntity(BlockPos pos, BlockState state) { this(CETransport.HORIZONTAL_FLUID_TANK_ENTITY.get(), pos, state); }

    protected HorizontalTankBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        boiler = new HorizontalBoilerData();
    }

    @Override protected com.simibubi.create.foundation.fluid.SmartFluidTank createInventory() {
        return new net.createplant.thermal.GradeFluidTank(getCapacityMultiplier(),this::onFluidStackChanged,this);
    }

    /** The state a part takes once the tank around it has formed again, beyond the marks for the tank's two ends. */
    protected BlockState shaped(BlockState state) { return state; }
    @Override public Direction.Axis getMainConnectionAxis() {
        BlockState state = getBlockState();
        return state.getBlock() instanceof HorizontalFluidTankBlock ? state.getValue(HorizontalFluidTankBlock.AXIS) : Direction.Axis.X;
    }

    @Override public int getMaxLength(Direction.Axis axis, int width) { return axis == getMainConnectionAxis() ? getMaxHeight() : getMaxWidth(); }

    @Override public void notifyMultiUpdated() {
        BlockState state = getBlockState();
        if (state.getBlock() instanceof HorizontalFluidTankBlock) {
            Direction.Axis axis = getMainConnectionAxis();
            int mine = axis.choose(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
            int start = axis.choose(getController().getX(), getController().getY(), getController().getZ());
            level.setBlock(worldPosition, shaped(state.setValue(FluidTankBlock.BOTTOM, start == mine).setValue(FluidTankBlock.TOP, start + height - 1 == mine)), Block.UPDATE_CLIENTS | Block.UPDATE_INVISIBLE);
        }
        if (isController()) setWindows(false);
        onFluidStackChanged(tankInventory.getFluid());
        updateBoilerState();
        setChanged();
    }

    /** Horizontal tanks have no windows, so the shape stays plain and the boiler needs no window update. */
    @Override public void setWindows(boolean on) { window = false; }

    @Override public void toggleWindows() { }

    @Override public void updateBoilerState() {
        if (!isController()) return;
        boolean wasActive = boiler.isActive();
        boolean changed = boiler.evaluate(this);
        if (wasActive != boiler.isActive() || changed) notifyUpdate();
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CETransport.HORIZONTAL_FLUID_TANK_ENTITY.get(), (be, side) -> be.port(side));
    }

    public ThermalFluidTank steam() { return steam; }
    public double heatWatts() { return heatWatts; }
    public String status() { return status; }
    public boolean boiling() { return !steam.isEmpty() || status.equals("running") || status.equals("working") || status.equals("output"); }

    /** The top and the outer end of a boiling cell give its steam; every other face, and these while nothing boils, are the tank's own. */
    private IFluidHandler port(Direction side) {
        if (side != Direction.UP && side != Direction.fromAxisAndDirection(getMainConnectionAxis(), Direction.AxisDirection.POSITIVE)) return fluidCapability;
        return new IFluidHandler() {
            private IFluidHandler held() { return boiling() ? steamOutlet : fluidCapability; }
            @Override public int getTanks() { return held().getTanks(); }
            @Override public FluidStack getFluidInTank(int tank) { return held().getFluidInTank(tank); }
            @Override public int getTankCapacity(int tank) { return held().getTankCapacity(tank); }
            @Override public boolean isFluidValid(int tank, FluidStack stack) { return held().isFluidValid(tank, stack); }
            @Override public int fill(FluidStack stack, FluidAction action) { return held().fill(stack, action); }
            @Override public FluidStack drain(int amount, FluidAction action) { return held().drain(amount, action); }
            @Override public FluidStack drain(FluidStack request, FluidAction action) { return held().drain(request, action); }
        };
    }

    private static boolean boils(FluidStack fluid) { return fluid.is(Fluids.WATER) || fluid.is(CEProcessFluids.WARM_WATER.get()); }

    @Override public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        String previous = status;
        int amount = steam.getFluidAmount();
        generateSteam();
        if (isController()) reportProduction();
        if (!previous.equals(status) || amount != steam.getFluidAmount()) notifyUpdate();
    }

    private void planProduction() {
        int length = getHeight();
        double high = (2 * length - 1) * 80_000 / SteamGrades.HIGH_PRESSURE.joulesPerMb();
        double superheated = length * 160_000 / SteamGrades.SUPERHEATED.joulesPerMb();
        ProductionMonitor.plan(this, getWidth() * Math.max(high, superheated), 0, 0);
    }

    private void reportProduction() {
        var meter = ProductionMonitor.of(this);
        if (meter == null) return;
        var feed = getTankInventory().getFluid();
        var rates = meter.read(ProductionMonitor.Output.FLUID);
        if (!boils(feed) && rates == null) return;
        planProduction();
        int length = getHeight();
        double maximum = meter.read(ProductionMonitor.Output.FLUID).maximum() / getWidth();
        String limit = !boils(feed) ? "water" : boiler.attachedEngines > 0 ? "engines" : "rated";
        for (int across = 0; limit.equals("rated") && across < getWidth(); across++) {
            var end = getMainConnectionAxis() == Direction.Axis.X ? TankCells.at(this, length - 1, 0, across) : TankCells.at(this, length - 1, across, 0);
            if (!level.hasChunkAt(end) || !(level.getBlockEntity(end) instanceof HorizontalTankBlockEntity cell)) limit = "structure";
            else if (cell.status.equals("output")) limit = "output";
            else if (cell.status.equals("water")) limit = "water";
            else if (cell.status.equals("structure")) limit = "structure";
            else if (cell.status.equals("heat") || cell.heatWatts / cell.heatedGrade.joulesPerMb() + .001 < maximum) limit = "heat";
        }
        ProductionMonitor.limitedBy(this, limit);
    }

    private SteamGrades heatedGrade = SteamGrades.HIGH_PRESSURE;

    public void generateSteam() {
        heatWatts = 0;
        if (!level.hasChunkAt(getController()) || !(getControllerBE() instanceof HorizontalTankBlockEntity root)) { status = "structure"; return; }
        int along = getMainConnectionAxis().choose(worldPosition.getX() - root.getBlockPos().getX(), 0, worldPosition.getZ() - root.getBlockPos().getZ());
        if (worldPosition.getY() != root.getBlockPos().getY() || along != root.getHeight() - 1) { status = "heat"; return; }
        var feed = root.getTankInventory();
        var water = feed.getFluid();
        if (!boils(water)) { status = "water"; return; }
        if (root.boiler.attachedEngines > 0) { status = "engines"; return; }
        var under = worldPosition.below();
        float heat = BoilerHeater.findHeat(level, under, level.getBlockState(under));
        if (!(heat > 0)) { status = "heat"; return; }
        var grade = heat >= 2 ? SteamGrades.SUPERHEATED : SteamGrades.HIGH_PRESSURE;
        heatedGrade = grade;
        var sample = new FluidStack(grade.fluid(), 1);
        FluidThermal.processComponents(water, sample);
        if (steam.fill(sample, IFluidHandler.FluidAction.SIMULATE) != 1) { status = "output"; return; }
        double provided = 0;
        for (int section = 0; section <= along; section++) {
            var burner = getMainConnectionAxis() == Direction.Axis.X ? worldPosition.offset(section - along, -1, 0) : worldPosition.offset(0, -1, section - along);
            if (!level.hasChunkAt(burner)) { status = "structure"; return; }
            float available = BoilerHeater.findHeat(level, burner, level.getBlockState(burner));
            if (available <= 0) continue;
            provided += Math.min(Math.max(0, 400000 - heatJoules - provided), available * 4000);
        }
        heatJoules += provided;
        heatWatts = provided * 20;
        int amount = Math.min(feed.getFluidAmount(), (int) Math.floor(heatJoules / grade.joulesPerMb()));
        amount = Math.min(amount, steam.fill(sample.copyWithAmount(amount), IFluidHandler.FluidAction.SIMULATE));
        if (amount <= 0) {
            status = "working";
            if (provided > 0) setChanged();
            return;
        }
        root.planProduction();
        feed.drain(amount, IFluidHandler.FluidAction.EXECUTE);
        ProductionMonitor.produced(root, steam.fill(sample.copyWithAmount(amount), IFluidHandler.FluidAction.EXECUTE), 0, 0);
        heatJoules -= amount * grade.joulesPerMb();
        status = "running";
        setChanged();
    }

    /** Create's tank readout, and while any bottom row boils, the steam its end cells hold and the heat they take. */
    @Override public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        boolean shown = super.addToGoggleTooltip(tooltip, sneaking);
        if (level == null || !(getControllerBE() instanceof HorizontalTankBlockEntity root)) return shown;
        int held = 0;
        double watts = 0;
        HorizontalTankBlockEntity sample = null;
        for (int across = 0; across < root.getWidth(); across++) {
            var end = getMainConnectionAxis() == Direction.Axis.X ? TankCells.at(root, root.getHeight() - 1, 0, across) : TankCells.at(root, root.getHeight() - 1, across, 0);
            if (!(level.getBlockEntity(end) instanceof HorizontalTankBlockEntity cell) || !cell.boiling()) continue;
            held += cell.steam.getFluidAmount();
            watts += cell.heatWatts;
            if (sample == null || !cell.steam.isEmpty()) sample = cell;
        }
        if (sample == null) return shown;
        tooltip.add(Component.translatable("tubular_boiler." + sample.status));
        if (!sample.steam.isEmpty()) tooltip.add(FluidThermal.readout(sample.steam.getFluid()));
        tooltip.add(Component.translatable("tubular_boiler.contents", held, watts / 1000));
        return true;
    }

    @Override public void write(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        super.write(tag, lookup, packet);
        tag.put("TubeSteam", steam.writeToNBT(lookup, new CompoundTag()));
        tag.putDouble("TubeHeat", heatJoules);
        tag.putDouble("TubeWatts", heatWatts);
        tag.putString("TubeStatus", status);
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        super.read(tag, lookup, packet);
        steam.readFromNBT(lookup, tag.getCompound("TubeSteam"));
        heatJoules = Double.isFinite(tag.getDouble("TubeHeat")) ? Math.clamp(tag.getDouble("TubeHeat"), 0, 400000) : 0;
        heatWatts = Math.max(0, tag.getDouble("TubeWatts"));
        status = tag.contains("TubeStatus") ? tag.getString("TubeStatus") : "heat";
    }

    /** Lava and other light sources light the cells the fluid reaches, counted from the lowest row of the section. */
    @Override protected void onFluidStackChanged(FluidStack fluid) {
        if (!hasLevel()) return;
        var type = fluid.getFluid().getFluidType();
        int light = (int) (type.getLightLevel(fluid) / 1.2f);
        boolean lighter = type.isLighterThanAir();
        int rows = (int) (getFillState() * width + 1);
        TankCells.forEach(this, cell -> {
            if (!(level.getBlockEntity(cell) instanceof HorizontalTankBlockEntity part)) return;
            level.updateNeighbourForOutputSignal(cell, getBlockState().getBlock());
            int row = cell.getY() - worldPosition.getY();
            boolean lit = lighter ? width - row <= rows : row < rows;
            part.setLuminosity(lit ? light : light > 0 ? 1 : 0);
        });
        if (!level.isClientSide) {
            setChanged();
            sendData();
        }
        if (isVirtual() && getFluidLevel() == null) setFluidLevel(LerpedFloat.linear().startWithValue(getFillState()));
        if (getFluidLevel() != null) getFluidLevel().chase(getFillState(), .5, LerpedFloat.Chaser.EXP);
    }

    @Override protected AABB createRenderBoundingBox() {
        if (!isController()) return super.createRenderBoundingBox();
        BlockPos far = TankCells.at(this, height - 1, width - 1, width - 1);
        return new AABB(worldPosition).minmax(new AABB(far));
    }
}
