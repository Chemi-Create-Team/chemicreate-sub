package net.createplant.block.entity;

import java.util.List;
import java.util.Locale;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import net.createplant.block.ReciprocatingCompressorBlock;
import net.createplant.block.ReciprocatingShaftBlock;
import net.createplant.registry.CEReciprocating;
import net.createplant.thermal.Compression;
import net.createplant.thermal.FluidThermal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

/** Shaft-driven, finite-flow compression by one pressure grade. */
public class ReciprocatingCompressorBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    public static final int MIN_RPM = 16;
    public static final double EFFICIENCY = .68, FLOW_PER_RPM = .125, WATTS_PER_RPM = 24;
    private String status = "no_cylinder";
    private int flow;
    private double work, load;
    private boolean dirty;
    private long lastSync = -20;

    public ReciprocatingCompressorBlockEntity(BlockPos pos, BlockState state) {
        super(CEReciprocating.COMPRESSOR_ENTITY.get(), pos, state);
        setLazyTickRate(10);
    }

    public BlockPos shaftPos() { return ReciprocatingCompressorBlock.shaftPos(getBlockState(), worldPosition); }

    public String status() { return status; }

    public int flow() { return flow; }

    public double work() { return work; }

    private ReciprocatingCylinderBlockEntity cylinder() {
        BlockPos pos = worldPosition.relative(ReciprocatingCompressorBlock.crankDirection(getBlockState()).getOpposite());
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof ReciprocatingCylinderBlockEntity be ? be : null;
    }

    private ReciprocatingShaftBlockEntity shaft() {
        BlockPos pos = shaftPos();
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof ReciprocatingShaftBlockEntity be && worldPosition.equals(be.engine()) ? be : null;
    }

    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) { }

    /** Takes over the Create shaft at the end of the crank so the engine's load reaches its network. */
    public void attachShaft() {
        if (level == null || level.isClientSide) return;
        BlockPos pos = shaftPos();
        if (!level.isLoaded(pos)) return;
        BlockState shaft = level.getBlockState(pos);
        if (!ReciprocatingCompressorBlock.isShaftValid(getBlockState(), shaft)) return;
        if (!(shaft.getBlock() instanceof ReciprocatingShaftBlock)) level.setBlock(pos, ReciprocatingShaftBlock.driven(shaft), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof ReciprocatingShaftBlockEntity be) be.bind(worldPosition);
    }

    public void releaseShaft(BlockState engine) {
        BlockPos pos = ReciprocatingCompressorBlock.shaftPos(engine, worldPosition);
        if (!level.isLoaded(pos)) return;
        BlockState shaft = level.getBlockState(pos);
        if (shaft.getBlock() instanceof ReciprocatingShaftBlock && level.getBlockEntity(pos) instanceof ReciprocatingShaftBlockEntity be && worldPosition.equals(be.engine())) {
            be.setLoad(0);
            level.setBlock(pos, ReciprocatingShaftBlock.released(shaft), Block.UPDATE_ALL);
        }
    }

    @Override public void lazyTick() {
        super.lazyTick();
        attachShaft();
    }

    @Override public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        String before = status;
        step();
        if (!before.equals(status)) dirty = true;
        if (dirty && level.getGameTime() - lastSync >= 20) {
            sendData();
            lastSync = level.getGameTime();
            dirty = false;
        }
    }

    private void idle(String reason, ReciprocatingShaftBlockEntity shaft) {
        status = reason;
        ProductionMonitor.limitedBy(this, reason.equals("no_cylinder") || reason.equals("no_shaft") ? "structure" : reason);
        load = 0;
        if (shaft != null) shaft.setLoad(0);
    }

    private void step() {
        work = 0;
        flow = 0;
        ReciprocatingCylinderBlockEntity cylinder = cylinder();
        if (cylinder == null) { idle("no_cylinder", shaft());return; }
        ReciprocatingShaftBlockEntity shaft = shaft();
        if (shaft == null) { idle("no_shaft", null);return; }
        double rpm = Math.abs(shaft.getSpeed());
        var inlet = cylinder.inlet();
        var outlet = cylinder.outlet();
        if (inlet.isEmpty()) { idle(rpm < MIN_RPM ? "speed" : "feed", shaft);return; }
        var converted=Compression.convert(inlet.getFluid(),true);
        double perMb=(net.createplant.transport.FluidHazards.of(converted).pressure().ordinal()+1)/EFFICIENCY;
        double maxRpm = com.simibubi.create.infrastructure.config.AllConfigs.server().kinetics.maxRotationSpeed.get();
        int maximum = capacityAt(maxRpm, perMb);
        ProductionMonitor.plan(this, maximum * 20d, 0, 0);
        if (rpm < MIN_RPM) { idle("speed", shaft);return; }
        int capacity = capacityAt(rpm, perMb);
        int feed = Math.min(inlet.getFluidAmount(), capacity);
        int n = Math.min(feed, outlet.fill(converted.copyWithAmount(feed),FluidAction.SIMULATE));
        if(n<=0){idle("output",shaft);return;}
        load=n*perMb*20/Math.max(1,rpm);
        shaft.setLoad((float)load);
        if(shaft.isOverStressed()){status="power";ProductionMonitor.limitedBy(this, status);return;}
        inlet.drain(n,FluidAction.EXECUTE);
        ProductionMonitor.produced(this, outlet.fill(converted.copyWithAmount(n),FluidAction.EXECUTE), 0, 0);
        ProductionMonitor.limitedBy(this, n < feed ? "output" : feed < capacity ? "feed" : capacity < maximum ? "speed" : "rated");
        flow=n;work=n*perMb;
        status = "running";
    }

    private static int capacityAt(double rpm, double work) {
        return Math.min(Math.max(1, (int) (rpm * FLOW_PER_RPM)), (int) (rpm * WATTS_PER_RPM / 20 / work));
    }

    @Override protected net.minecraft.world.phys.AABB createRenderBoundingBox() {
        return net.minecraft.world.phys.AABB.encapsulatingFullBlocks(worldPosition.relative(ReciprocatingCompressorBlock.crankDirection(getBlockState()).getOpposite()),shaftPos()).inflate(.25);
    }

    @Override public boolean addToGoggleTooltip(List<Component> text, boolean sneaking) {
        text.add(Component.translatable("reciprocating.create_refinery_n_reactors." + status));
        if (level.getBlockEntity(worldPosition.relative(ReciprocatingCompressorBlock.crankDirection(getBlockState()).getOpposite())) instanceof ReciprocatingCylinderBlockEntity cylinder) {
            if(!cylinder.outlet().isEmpty())text.add(FluidThermal.readout(cylinder.outlet().getFluid()));
            text.add(Component.translatable("gas_compression.create_refinery_n_reactors.flow", flow, format(work * 20)));
        }
        return true;
    }

    private static String format(double value) { return String.format(Locale.ROOT, "%.2f", value); }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider lookup, boolean clientPacket) {
        super.write(tag, lookup, clientPacket);
        if (!clientPacket) return;
        tag.putString("Status", status);
        tag.putInt("Flow", flow);
        tag.putDouble("Work", work);
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider lookup, boolean clientPacket) {
        super.read(tag, lookup, clientPacket);
        if (!clientPacket) return;
        status = tag.getString("Status");
        flow = tag.getInt("Flow");
        work = tag.getDouble("Work");
    }
}
