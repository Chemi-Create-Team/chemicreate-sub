package net.createplant.block.entity;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.List;
import net.createplant.block.CoolingTowerStructure;
import net.createplant.registry.CEPlantSystems;
import net.createplant.thermal.FluidThermal;
import net.createplant.thermal.ThermalFluidTank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public class CoolingTowerBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, net.createplant.block.PlantStructureListener {
    private static final int CAPACITY = 16_000_000, SCAN_BUDGET = 2048;
    public static final int MAX_FLOW=845000;
    private final ThermalFluidTank[] tanks = { tank(), tank(), tank() };
    private int cursor, clock;
    private int sizeIndex = CoolingTowerStructure.DEFAULT_SIZE;
    private boolean formed;
    private BlockPos fault;
    private double watts;
    private int flow;
    private String status = "validating";
    public CoolingTowerBlockEntity(BlockPos pos, BlockState state) { super(CEPlantSystems.TOWER_ENTITY.get(), pos, state);fault = pos; }
    private ThermalFluidTank tank() { return new ThermalFluidTank(CAPACITY, stack -> setChanged()); }
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) { }
    public boolean formed() { return formed; }
    public double watts() { return watts; }
    public int maximumFlow() { return (int) Math.round(MAX_FLOW * net.createplant.thermal.SeasonalThermal.cooling(level)); }
    public boolean steaming() { return formed && watts > 0; }




    public String status() { return status; }
    public ThermalFluidTank tank(int index) { return tanks[index]; }
    public int sizeIndex() { return sizeIndex; }
    public CoolingTowerStructure.Size size() { return CoolingTowerStructure.SIZES.get(sizeIndex); }
    public BlockPos centre() { return CoolingTowerStructure.centre(worldPosition, sizeIndex); }
    public void invalidateStructure() { if (formed || cursor != 0) { formed = false;cursor = 0;watts = 0;status = "validating";setChanged();sendData(); } }
    @Override public void initialize() { super.initialize();net.createplant.event.PlantStructureEvents.register(level, worldPosition); }
    @Override public void invalidate() { net.createplant.event.PlantStructureEvents.remove(level, worldPosition);super.invalidate(); }
    @Override public boolean containsStructure(BlockPos pos) { BlockPos c = centre();return Math.abs(pos.getX() - c.getX()) <= size().radius() && Math.abs(pos.getZ() - c.getZ()) <= size().radius() && pos.getY() >= c.getY() && pos.getY() < c.getY() + size().height(); }
    @Override public void structureChanged() { invalidateStructure(); }
    @Override protected AABB createRenderBoundingBox() {
        BlockPos c = centre();
        return new AABB(c).inflate(size().radius(), 0, size().radius()).expandTowards(0, size().height(), 0);
    }

    @Override public void tick() {
        super.tick();
        if (level == null) return;
        if (level.isClientSide()) { steam();return; }
        clock++;
        if (!loaded()) {
            boolean knownSize = formed;
            invalidateStructure();status = "unloaded";ProductionMonitor.limitedBy(this, "structure");
            if (!knownSize) nextSize();
            return;
        }
        if (!formed || clock % 20 == 0 || cursor != 0) validateSlice();
        watts = 0;
        if (formed) {
            process();
        } else ProductionMonitor.limitedBy(this, "structure");
        if (clock % 20 == 0) { setChanged();sendData(); }
    }

    private void steam() {
        if (!steaming() || Math.floorMod(level.getGameTime() + worldPosition.asLong(), 2) != 0) return;
        var c = centre();double y = c.getY() + size().height() - .2;
        if (level.getNearestPlayer(c.getX() + .5, y, c.getZ() + .5, 128, false) == null) return;
        int count = Math.clamp(size().radius() / 3, 2, 5);
        for (int i = 0; i < count; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2, radius = Math.sqrt(level.random.nextDouble()) * size().topRadius() * .82;
            level.addParticle(net.createplant.registry.CEParticles.TOWER_STEAM.get(), c.getX() + .5 + Math.cos(angle) * radius, y,
                c.getZ() + .5 + Math.sin(angle) * radius, .012 + Math.cos(angle) * radius * .012, .18 + level.random.nextDouble() * .09, .006 + Math.sin(angle) * radius * .012);
        }
    }

    private boolean loaded() {
        BlockPos c = centre();
        for (int x = (c.getX() - size().radius()) >> 4; x <= (c.getX() + size().radius()) >> 4; x++)
            for (int z = (c.getZ() - size().radius()) >> 4; z <= (c.getZ() + size().radius()) >> 4; z++)
                if (!level.hasChunk(x, z)) return false;
        return true;
    }

    /** Repeated bounded audits also catch commands and non-player changes inside the airway. */
    public void validateSlice() {
        List<CoolingTowerStructure.Cell> cells = CoolingTowerStructure.cells(sizeIndex);
        int end = Math.min(cells.size(), cursor + SCAN_BUDGET);
        BlockPos c = centre();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (; cursor < end; cursor++) {
            var cell = cells.get(cursor);
            at.set(c.getX() + cell.offset().getX(), c.getY() + cell.offset().getY(), c.getZ() + cell.offset().getZ());
            if (!level.hasChunkAt(at) || !level.getBlockState(at).is(cell.block())) {
                formed = false;fault = at.immutable();cursor = 0;status = "structure";
                nextSize();return;
            }
            if ((cell.role() == CoolingTowerStructure.Role.HOT || cell.role() == CoolingTowerStructure.Role.COLD || cell.role() == CoolingTowerStructure.Role.MAKEUP)
                && level.getBlockEntity(at) instanceof CoolingTowerPortBlockEntity port) port.assign(worldPosition);
        }
        if (cursor == cells.size()) { formed = true;cursor = 0;status = "idle";setChanged();sendData(); }
    }

    private void nextSize() {
        cursor = 0;
        sizeIndex = (sizeIndex + 1) % CoolingTowerStructure.SIZES.size();
        invalidateRenderBoundingBox();
    }

    public void process() {
        watts=0;flow=0;if(!formed || isRemoved()){status="structure";ProductionMonitor.limitedBy(this, status);return;}
        int maximum = maximumFlow();
        ProductionMonitor.plan(this, maximum * 20d, 0, 0);
        if(tanks[0].isEmpty() && tanks[2].isEmpty()){status="feed";ProductionMonitor.limitedBy(this, status);return;}
        var recipe=net.createplant.thermal.ThermalStreams.cooling(tanks[0].getFluid());
        int feed = Math.min(maximum, tanks[0].getFluidAmount());
        if(recipe!=null && tanks[0].getFluid().is(net.createplant.registry.CEProcessFluids.WARM_WATER.get())){
            int count=Math.min(feed,tanks[1].fill(recipe.result(tanks[0].getFluid(),feed),IFluidHandler.FluidAction.SIMULATE));
            if(count>0){var product=recipe.result(tanks[0].getFluid(),count);tanks[0].drain(count,IFluidHandler.FluidAction.EXECUTE);ProductionMonitor.produced(this, tanks[1].fill(product,IFluidHandler.FluidAction.EXECUTE), 0, 0);flow=count;watts=count*400d;setChanged();}
        }
        ProductionMonitor.limitedBy(this, flow < feed ? "output" : feed < maximum ? "feed" : "rated");
        if(!tanks[2].isEmpty()){
            int count=tanks[1].fill(tanks[2].getFluid().copyWithAmount(Math.min(MAX_FLOW,tanks[2].getFluidAmount())),IFluidHandler.FluidAction.SIMULATE);
            if(count>0)tanks[1].fill(tanks[2].drain(count,IFluidHandler.FluidAction.EXECUTE),IFluidHandler.FluidAction.EXECUTE);
        }
        status=flow>0 ? "cooling" : tanks[1].getSpace()==0 ? "output" : "idle";
    }

    public IFluidHandler port(int index) {
        return new IFluidHandler() {
            private boolean ready() { return formed && !isRemoved(); }
            @Override public int getTanks() { return 1; }
            @Override public FluidStack getFluidInTank(int tank) { return tank == 0 ? tanks[index].getFluid().copy() : FluidStack.EMPTY; }
            @Override public int getTankCapacity(int tank) { return tank == 0 ? CAPACITY : 0; }
            @Override public boolean isFluidValid(int tank, FluidStack fluid) { return tank == 0 && (index==0 ? fluid.is(net.createplant.registry.CEProcessFluids.WARM_WATER.get()) : index==2 && fluid.is(Fluids.WATER)); }
            @Override public int fill(FluidStack fluid, FluidAction action) { return ready() && isFluidValid(0, fluid) ? tanks[index].fill(fluid, action) : 0; }
            @Override public FluidStack drain(int amount, FluidAction action) { return ready() && index == 1 ? tanks[index].drain(amount, action) : FluidStack.EMPTY; }
            @Override public FluidStack drain(FluidStack fluid, FluidAction action) { return ready() && index == 1 ? tanks[index].drain(fluid, action) : FluidStack.EMPTY; }
        };
    }

    public void release() {
        if (level instanceof ServerLevel server && java.util.Arrays.stream(tanks).anyMatch(tank -> !tank.isEmpty()))
            server.sendParticles(ParticleTypes.SPLASH, worldPosition.getX() + .5, worldPosition.getY() + 1, worldPosition.getZ() + .5, 40, 2, 1, 2, .1);
        for (var tank : tanks) tank.setFluid(FluidStack.EMPTY);
        formed = false;
    }
    @Override protected void write(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        super.write(tag, lookup, packet);
        for (int i = 0; i < 3; i++) tag.put("TowerTank" + i, tanks[i].writeToNBT(lookup, new CompoundTag()));
        tag.putBoolean("Formed", formed);tag.putDouble("Watts", watts);tag.putInt("TowerSize", sizeIndex);
        tag.putInt("Flow",flow);tag.putString("Status", status);
    }
    @Override protected void read(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        super.read(tag, lookup, packet);
        sizeIndex = tag.contains("TowerSize") ? Math.clamp(tag.getInt("TowerSize"), 0, CoolingTowerStructure.SIZES.size() - 1) : CoolingTowerStructure.DEFAULT_SIZE;
        invalidateRenderBoundingBox();
        for (int i = 0; i < 3; i++) tanks[i].readFromNBT(lookup, tag.getCompound("TowerTank" + i));
        formed = packet && tag.getBoolean("Formed");cursor = 0;
        watts = packet ? finite(tag.getDouble("Watts"),0,1e9) : 0;flow=packet ? Math.max(0,tag.getInt("Flow")) : 0;status = tag.getString("Status");
    }
    private static double finite(double value, double min, double max) { return Double.isFinite(value) ? Math.clamp(value, min, max) : min; }
    @Override public boolean addToGoggleTooltip(List<Component> text, boolean sneaking) {
        text.add(Component.translatable("plant_systems.tower.state", Component.translatable("plant_systems.status." + status)));
        text.add(Component.translatable("grade.create_refinery_n_reactors.tower",flow));
        text.add(Component.translatable("plant_systems.tower.water", tanks[0].getFluidAmount(), tanks[1].getFluidAmount(), tanks[2].getFluidAmount()));
        if (formed && net.createplant.thermal.SeasonalThermal.available()) text.add(net.createplant.thermal.SeasonalThermal.reading(level, false));
        if (!formed) text.add(Component.translatable("plant_systems.structure_at", fault.toShortString()));
        return true;
    }
}
