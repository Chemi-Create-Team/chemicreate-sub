package net.createplant.block.entity;

import java.util.List;
import java.util.Locale;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
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
import net.minecraft.world.phys.AABB;
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
    private int connectedSides;
    private final int[] pressureCredit = new int[2];

    public ReciprocatingCompressorBlockEntity(BlockPos pos, BlockState state) {
        super(CEReciprocating.COMPRESSOR_ENTITY.get(), pos, state);
        setLazyTickRate(10);
    }

    public BlockPos shaftPos() { return ReciprocatingCompressorBlock.shaftPos(getBlockState(), worldPosition); }

    public String status() { return status; }

    public int flow() { return flow; }

    public double work() { return work; }

    public int connectedSides() { return connectedSides; }

    private boolean opposed() { return getBlockState().getValue(ReciprocatingCompressorBlock.OPPOSED); }

    private ReciprocatingCylinderBlockEntity cylinder(int side) {
        BlockPos pos;
        if (opposed()) {
            var direction = ReciprocatingCompressorBlock.cylinderSide(getBlockState(), side);
            var gap = worldPosition.relative(direction);
            if (!level.isLoaded(gap) || !level.getBlockState(gap).isAir()) return null;
            pos = gap.relative(direction);
        } else {
            if (side != 0) return null;
            pos = worldPosition.relative(ReciprocatingCompressorBlock.crankDirection(getBlockState()).getOpposite());
        }
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof ReciprocatingCylinderBlockEntity be ? be : null;
    }

    boolean connects(ReciprocatingCylinderBlockEntity cylinder) {
        return cylinder(0) == cylinder || opposed() && cylinder(1) == cylinder;
    }

    private ReciprocatingCylinderBlockEntity claimCylinder(int side) {
        var cylinder = cylinder(side);
        if (cylinder != null && cylinder.acceptDrive(this)) return cylinder;
        pressureCredit[side] = 0;
        return null;
    }

    public ReciprocatingShaftBlockEntity driveShaft() { return driveShaft(getBlockState()); }

    private ReciprocatingShaftBlockEntity driveShaft(BlockState state) {
        BlockPos input = ReciprocatingCompressorBlock.shaftPos(state, worldPosition);
        if (!level.isLoaded(input)) return null;
        BlockPos owner = worldPosition;
        if (state.getValue(ReciprocatingCompressorBlock.OPPOSED)) {
            Direction rear = state.getValue(ReciprocatingCompressorBlock.FACING).getOpposite();
            for (int row = 1; row < ReciprocatingCompressorBlock.MAX_ROWS
                && ReciprocatingCompressorBlock.sameLine(state, level.getBlockState(input)); row++) {
                owner = input;
                input = input.relative(rear);
                if (!level.isLoaded(input)) return null;
            }
        }
        return level.getBlockEntity(input) instanceof ReciprocatingShaftBlockEntity shaft
            && (level.isClientSide || owner.equals(shaft.engine())) ? shaft : null;
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
        if (level.getBlockEntity(pos) instanceof ReciprocatingShaftBlockEntity be && (be.engine() == null || be.engine().equals(worldPosition))) be.bind(worldPosition);
    }

    public void releaseShaft(BlockState engine) {
        var shaft = driveShaft(engine);
        if (shaft == null) return;
        shaft.setLoad(worldPosition, 0);
        if (worldPosition.equals(shaft.engine()))
            level.setBlock(shaft.getBlockPos(), ReciprocatingShaftBlock.released(shaft.getBlockState()), Block.UPDATE_ALL);
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
        if (shaft != null) shaft.setLoad(worldPosition, 0);
    }

    private void step() {
        work = 0;
        flow = 0;
        var left = claimCylinder(0);
        var right = opposed() ? claimCylinder(1) : null;
        int sides = (left == null ? 0 : 1) | (right == null ? 0 : 2);
        if (connectedSides != sides) { connectedSides = sides;dirty = true; }
        ReciprocatingShaftBlockEntity shaft = driveShaft();
        if (sides == 0) { idle("no_cylinder", shaft);return; }
        if (shaft == null) { idle("no_shaft", null);return; }
        int side = left == null ? 1 : 0;
        if (left != null && right != null) {
            boolean leftReady = canCompress(left), rightReady = canCompress(right);
            if (rightReady && (!leftReady || (level.getGameTime() & 1) != 0)) side = 1;
        }
        compress(side == 0 ? left : right, shaft, side);
    }

    /** Both cylinders share the original drive budget. An idle side leaves its turn to the other side. */
    private static boolean canCompress(ReciprocatingCylinderBlockEntity cylinder) {
        var inlet = cylinder.inlet();
        if (inlet.isEmpty()) return false;
        var change = Compression.change(inlet.getFluid(), true);
        return change != null && change.fitting(change.batches(inlet.getFluidAmount()), cylinder.outlet()) > 0;
    }

    private void compress(ReciprocatingCylinderBlockEntity cylinder, ReciprocatingShaftBlockEntity shaft, int side) {
        double rpm = Math.abs(shaft.getSpeed());
        var inlet = cylinder.inlet();
        var outlet = cylinder.outlet();
        if (inlet.isEmpty()) { pressureCredit[side]=0;idle(rpm < MIN_RPM ? "speed" : "feed", shaft);return; }
        var change=Compression.change(inlet.getFluid(),true);
        if(change==null){idle("feed",shaft);return;}
        var converted=change.prototype();
        double perMb=Math.max(1,net.createplant.transport.FluidHazards.of(converted).pressure().ordinal())/EFFICIENCY;
        double maxRpm = com.simibubi.create.infrastructure.config.AllConfigs.server().kinetics.maxRotationSpeed.get();
        int maximum = capacityAt(maxRpm, perMb);
        ProductionMonitor.plan(this, maximum * 20d, 0, 0);
        if (rpm < MIN_RPM) { idle("speed", shaft);return; }
        int capacity = capacityAt(rpm, perMb);
        pressureCredit[side]=(int)Math.min(capacity+(long)change.consumed()-1,pressureCredit[side]+(long)capacity);
        int feed=Math.min(inlet.getFluidAmount(),pressureCredit[side]);
        int available=change.batches(feed);
        if(available==0){idle("feed",shaft);return;}
        int batches=change.fitting(available,outlet);
        int n=batches*change.consumed();
        if(n<=0){idle("output",shaft);return;}
        load=n*perMb*20/Math.max(1,rpm);
        shaft.setLoad(worldPosition, (float)load);
        if(shaft.isOverStressed()){status="power";ProductionMonitor.limitedBy(this, status);return;}
        pressureCredit[side]-=n;
        // Compression changes the pressure level only: the gas leaves at the real temperature it came in at.
        double kelvin=inlet.kelvin();
        inlet.drain(n,FluidAction.EXECUTE);
        ProductionMonitor.produced(this, outlet.fillAt(change.output(batches),kelvin,FluidAction.EXECUTE), 0, 0);
        ProductionMonitor.limitedBy(this, n < feed ? "output" : feed < capacity ? "feed" : capacity < maximum ? "speed" : "rated");
        flow=n;work=n*perMb;
        status = "running";
    }

    private static int capacityAt(double rpm, double work) {
        return Math.min(Math.max(1, (int) (rpm * FLOW_PER_RPM)), (int) (rpm * WATTS_PER_RPM / 20 / work));
    }

    @Override protected AABB createRenderBoundingBox() {
        if (opposed()) {
            var state = getBlockState();
            Direction front = state.getValue(ReciprocatingCompressorBlock.FACING);
            return AABB.encapsulatingFullBlocks(worldPosition.relative(ReciprocatingCompressorBlock.cylinderSide(state, 0), 2),
                worldPosition.relative(ReciprocatingCompressorBlock.cylinderSide(state, 1), 2))
                .inflate(front.getAxis() == Direction.Axis.X ? 2 : .25, 0, front.getAxis() == Direction.Axis.Z ? 2 : .25)
                .expandTowards(0, 2.25, 0);
        }
        return AABB.encapsulatingFullBlocks(worldPosition.relative(ReciprocatingCompressorBlock.crankDirection(getBlockState()).getOpposite()), shaftPos()).inflate(.25);
    }

    @Override public boolean addToGoggleTooltip(List<Component> text, boolean sneaking) {
        text.add(Component.translatable("reciprocating.create_refinery_n_reactors." + status));
        if (opposed()) text.add(Component.translatable("reciprocating.create_refinery_n_reactors.cylinders", Integer.bitCount(connectedSides)));
        if (connectedSides != 0) {
            for (int side = 0; side < 2; side++) {
                if ((connectedSides & (1 << side)) == 0) continue;
                var cylinder = cylinder(side);
                if (cylinder != null && !cylinder.outlet().isEmpty()) text.add(FluidThermal.readout(cylinder.outlet().getFluid()));
            }
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
        tag.putInt("ConnectedSides", connectedSides);
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider lookup, boolean clientPacket) {
        super.read(tag, lookup, clientPacket);
        if (!clientPacket) return;
        status = tag.getString("Status");
        flow = tag.getInt("Flow");
        work = tag.getDouble("Work");
        connectedSides = tag.getInt("ConnectedSides") & 3;
    }
}
