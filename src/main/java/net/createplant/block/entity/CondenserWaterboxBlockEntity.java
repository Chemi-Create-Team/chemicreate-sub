package net.createplant.block.entity;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.ArrayList;
import java.util.List;
import net.createplant.registry.CEPlantSystems;
import net.createplant.thermal.FluidThermal;
import net.createplant.thermal.ThermalFluidTank;
import net.createplant.thermal.TurbineThermal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** The secondary water circuit stays physically separate from the 30 C turbine condensate. */
public class CondenserWaterboxBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, net.createplant.block.PlantStructureListener {
    public record Cell(BlockPos pos, BlockState state) { }
    public static final int CAPACITY = 4_000_000;
    private final ThermalFluidTank cold = new ThermalFluidTank(CAPACITY, stack -> setChanged());
    private final ThermalFluidTank hot = new ThermalFluidTank(CAPACITY, stack -> setChanged());
    private boolean formed;
    private int clock;
    private double watts;
    public CondenserWaterboxBlockEntity(BlockPos pos, BlockState state) { super(CEPlantSystems.CONDENSER_ENTITY.get(), pos, state); }
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) { }
    public static List<Cell> cells(BlockPos origin, Direction facing) {
        List<Cell> cells = new ArrayList<>();
        Direction across = facing.getClockWise();
        for (int d = 0; d < 7; d++) for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) {
            BlockPos pos = origin.relative(facing.getOpposite(), d).relative(across, x).above(y);
            Block block = d == 0 && y == 0 && x == -1 ? CEPlantSystems.CONDENSER_INLET.get()
                : d == 0 && y == 0 && x == 1 ? CEPlantSystems.CONDENSER_OUTLET.get()
                : d == 0 && x == 0 && y == 0 ? CEPlantSystems.CONDENSER.get()
                : d > 0 && d < 6 && x == 0 && y == 0 ? CEPlantSystems.CONDENSER_TUBES.get() : CEPlantSystems.CONDENSER_SHELL.get();
            BlockState state = block.defaultBlockState();
            if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) state = state.setValue(BlockStateProperties.HORIZONTAL_FACING,
                block == CEPlantSystems.CONDENSER_INLET.get() ? across.getOpposite() : block == CEPlantSystems.CONDENSER_OUTLET.get() ? across : facing);
            cells.add(new Cell(pos, state));
        }
        return cells;
    }
    public SteamTurbineBlockEntity rotor() { return level == null ? null : TurbineThermal.source(level, worldPosition.relative(getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING))); }
    public boolean formed() { return formed; }
    public double watts() { return watts; }
    public ThermalFluidTank cold() { return cold; }
    public ThermalFluidTank hot() { return hot; }
    public String status() {
        if (!formed) return "structure";
        if (rotor() == null) return "turbine";
        if (cold.isEmpty()) return "feed";
        if (hot.getSpace() <= 0) return "output";
        return watts > 0 ? "cooling" : "idle";
    }
    @Override public void initialize() { super.initialize();net.createplant.event.PlantStructureEvents.register(level, worldPosition); }
    @Override public void invalidate() { net.createplant.event.PlantStructureEvents.remove(level, worldPosition);super.invalidate(); }
    @Override public boolean containsStructure(BlockPos pos) {
        return pos.distManhattan(worldPosition) < 10 && cells(worldPosition, getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING)).stream().anyMatch(cell -> cell.pos().equals(pos));
    }
    @Override public void structureChanged() { formed = false;watts = 0;setChanged(); }
    @Override public void tick() {
        super.tick();if (level == null || level.isClientSide()) return;
        if (clock++ % 20 == 0) {
            formed = true;
            for (Cell cell : cells(worldPosition, getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING)))
                if (!level.hasChunkAt(cell.pos()) || !level.getBlockState(cell.pos()).is(cell.state().getBlock())
                    || cell.pos().equals(worldPosition) && !level.getBlockState(cell.pos()).equals(cell.state())) { formed = false;break; }
            if (formed) for (Cell cell : cells(worldPosition, getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING))) {
                if (level.getBlockEntity(cell.pos()) instanceof CondenserCoolingPortBlockEntity port) {
                    if (!level.getBlockState(cell.pos()).equals(cell.state())) level.setBlock(cell.pos(), cell.state(), 2);
                    port.assign(worldPosition);
                }
            }
            setChanged();sendData();
        }
        watts = 0;
        var rotor = rotor();
        if (!formed || rotor == null) { ProductionMonitor.limitedBy(this, "structure");return; }
        int maximum = Math.min(845000, (int) (net.createplant.thermal.TurbineThermal.MAX_FLOW * net.createplant.thermal.SteamGrades.ORDINARY.condenserHeatPerMb() / 20));
        ProductionMonitor.plan(this, maximum * 20d, 0, 0);
        if (cold.isEmpty() || rotor.thermodynamics().pendingCondenserJoules() <= 0) {
            ProductionMonitor.limitedBy(this, cold.isEmpty() ? "water" : "steam");return;
        }
        int available = Math.min(845000, (int) Math.floor(rotor.thermodynamics().pendingCondenserJoules()/20));
        int amount = Math.min(cold.getFluidAmount(), available);
        if(amount<=0)return;
        var result=net.createplant.thermal.ThermalStreams.coolant(cold.getFluid()).result(cold.getFluid(),amount);
        if(hot.fill(result,IFluidHandler.FluidAction.SIMULATE)!=amount){ProductionMonitor.limitedBy(this, "output");return;}
        ProductionMonitor.limitedBy(this, amount < available ? "water" : amount < maximum ? "steam" : "rated");
        double q=amount*20d;cold.drain(amount,IFluidHandler.FluidAction.EXECUTE);hot.fill(result,IFluidHandler.FluidAction.EXECUTE);
        rotor.thermodynamics().removeCondenserHeat(q);watts = q * 20;setChanged();
        ProductionMonitor.produced(this, amount, 0, 0);
    }
    public IFluidHandler coolingPort(boolean input) {
        return new IFluidHandler() {
            @Override public int getTanks() { return 1; }
            @Override public FluidStack getFluidInTank(int index) { return index == 0 ? (input ? cold : hot).getFluid().copy() : FluidStack.EMPTY; }
            @Override public int getTankCapacity(int index) { return index == 0 ? CAPACITY : 0; }
            @Override public boolean isFluidValid(int index, FluidStack stack) { return index == 0 && input && stack.is(Fluids.WATER) && FluidThermal.valid(stack); }
            @Override public int fill(FluidStack stack, FluidAction action) { return formed && !isRemoved() && isFluidValid(0, stack) ? cold.fill(stack, action) : 0; }
            @Override public FluidStack drain(int amount, FluidAction action) { return formed && !isRemoved() && !input ? hot.drain(amount, action) : FluidStack.EMPTY; }
            @Override public FluidStack drain(FluidStack stack, FluidAction action) { return formed && !isRemoved() && !input ? hot.drain(stack, action) : FluidStack.EMPTY; }
        };
    }
    public void release() {
        if (level instanceof ServerLevel server && (!cold.isEmpty() || !hot.isEmpty()))
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH, worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5, 24, 1, 1, 1, .05);
        cold.setFluid(FluidStack.EMPTY);hot.setFluid(FluidStack.EMPTY);formed = false;
    }
    @Override protected void write(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        super.write(tag, lookup, packet);tag.put("Cold", cold.writeToNBT(lookup, new CompoundTag()));tag.put("Hot", hot.writeToNBT(lookup, new CompoundTag()));
        tag.putBoolean("Formed", formed);tag.putDouble("Watts", watts);
    }
    @Override protected void read(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        super.read(tag, lookup, packet);cold.readFromNBT(lookup, tag.getCompound("Cold"));hot.readFromNBT(lookup, tag.getCompound("Hot"));
        formed = packet && tag.getBoolean("Formed");watts = packet && Double.isFinite(tag.getDouble("Watts")) ? Math.max(0, tag.getDouble("Watts")) : 0;
    }
    @Override public boolean addToGoggleTooltip(List<Component> text, boolean sneaking) {
        text.add(Component.translatable("plant_systems.condenser.state", formed, rotor() != null));
        if(!hot.isEmpty())text.add(FluidThermal.readout(hot.getFluid()));
        text.add(Component.translatable("plant_systems.condenser.water", cold.getFluidAmount(), hot.getFluidAmount()));
        text.add(Component.translatable("plant_systems.status." + status()));
        return true;
    }
}
