package net.createplant.block.entity;

import java.util.List;
import java.util.Locale;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createplant.block.FuelChannelBlock;
import net.createplant.nuclear.Finite;
import net.createplant.nuclear.ReactorCore;
import net.createplant.nuclear.ReactorCoreStructure;
import net.createplant.nuclear.ReactorCores;
import net.createplant.nuclear.ReactorFuel;
import net.createplant.nuclear.ReactorPhysics;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CENuclear;
import net.createplant.registry.CEUranium;
import net.createplant.nuclear.ReactorCoolant;
import net.createplant.registry.CEProcessFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * One block of fuel channels: graphite moderator with a two-by-two grid of pressure-channel positions. Each position can be a
 * fuel or control channel. Standard and compact assemblies have seven or three metres of fuel and matching shielding plugs
 * and suspension rods. The bottom cell of a column holds its rods, its water and the temperature of its fuel. Water comes in through the feed bundle under it; the core gives out
 * steam and water through the outward side faces of its edge blocks, so the top faces stay free for loading fuel rods and
 * taking spent ones out. The first column's bottom cell runs the core.
 */
public class FuelChannelBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    /** Four channel positions to a block, with 1000 mB of water around each active channel in every core layer. */
    public static final int RODS = ReactorPhysics.RODS_PER_BLOCK, COOLANT_PER_CHANNEL = 1000, COOLANT_PER_CELL = RODS * COOLANT_PER_CHANNEL;
    public static final double ROD_JOULES = 4e9 * ReactorPhysics.CORE_HEIGHT;
    private static final byte EMPTY = 0, FRESH = 1, SPENT = 2, LITHIUM_TARGET = 3, IRRADIATED_TARGET = 4;
    public static final double TARGET_FULL_POWER_SECONDS = 600;
    public static final byte FUEL = 0, CONTROL = 1;
    /** The four channel centres of a block, as x and z offsets; slot order. */
    public static final float[][] HOLES = holes();
    private static final String KEY = "reactor_core.create_refinery_n_reactors.";
    private int coolant, coreHeight, assemblyHeight = ReactorPhysics.CORE_HEIGHT;
    /** Executed inlet/outlet transfers in the current one-second window and the resulting measured bundle flow in mB/t. */
    private int inletWindow, outletWindow, flowTicks;
    private double inletJoulesWindow, inletJoules;
    private float flowRate;
    private int activeMask = ReactorCoreStructure.FULL_CHANNEL_MASK;
    private boolean layoutInitialized;
    private final byte[] holes = new byte[RODS];
    private final byte[] fuels = new byte[RODS];
    private final byte[] channelTypes = new byte[RODS], controlRods = new byte[RODS];
    private final double[] burnt = new double[RODS];
    private final float[] controlDepth = new float[RODS], controlWear = new float[RODS];
    private double specific = ReactorCoolant.CP * 293.15, fuelKelvin = 293.15;
    private int reducedFresh, reducedSpent, reducedControl, reducedSpentControl;
    private BlockPos controller;
    private ReactorCore core;
    private long seenVersion = -1;
    private String reason = "structure";
    private BlockPos fault;

    public FuelChannelBlockEntity(BlockPos pos, BlockState state) {
        super(CENuclear.FUEL_CHANNEL_ENTITY.get(), pos, state);
        fault = pos;
        setLazyTickRate(20);
    }

    private static float[][] holes() {
        float[][] holes = new float[RODS][2];
        for (int i = 0; i < RODS; i++) {
            holes[i][0] = (i % ReactorPhysics.CHANNEL_GRID + .5f) / ReactorPhysics.CHANNEL_GRID - .5f;
            holes[i][1] = (i / ReactorPhysics.CHANNEL_GRID + .5f) / ReactorPhysics.CHANNEL_GRID - .5f;
        }
        return holes;
    }

    /** Maps the four corners of a former larger square grid to the new 2 x 2 order. */
    private static int[] reducedGridSources(int slots) {
        int grid = (int) Math.round(Math.sqrt(slots));
        if (grid * grid != slots || grid <= ReactorPhysics.CHANNEL_GRID) grid = 3;
        return new int[] {0, grid - 1, grid * (grid - 1), grid * grid - 1};
    }

    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) { }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CENuclear.FUEL_CHANNEL_ENTITY.get(), FuelChannelBlockEntity::fluidPort);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CENuclear.FUEL_CHANNEL_ENTITY.get(), FuelChannelBlockEntity::itemPort);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CENuclear.TOP_CAP_ENTITY.get(), (cap, side) -> cap.itemPort(side));
    }

    public BlockPos controllerPos() { return controller; }
    public ReactorCore core() { return core; }
    public ReactorPhysics.Layout layout() { return ReactorPhysics.layout(coreHeight > 0 ? coreHeight : assemblyHeight); }
    public double rodJoules() { return ROD_JOULES * layout().scale(); }
    /** Assembly items retain their physical length through a teardown and cannot gain fuel energy by being stretched. */
    public boolean acceptsAssemblyHeight(int height) { return height == assemblyHeight || !hasAssemblyItems(); }
    public boolean hasAssemblyItems() {
        for (int i = 0; i < RODS; i++) if (holes[i] != EMPTY || controlRods[i] != 0) return true;
        return false;
    }
    /** A previous bottom cannot become an inaccessible middle cell while still holding inventory or stored heat. */
    public boolean hasStoredColumnState() { return hasAssemblyItems() || coolant > 0 || Math.abs(fuelKelvin - 293.15) > 1e-6; }
    public int fresh() { return count(FRESH); }
    public int spent() { return count(SPENT); }
    public int lithiumTargets() { return count(LITHIUM_TARGET); }
    public int irradiatedTargets() { return count(IRRADIATED_TARGET); }
    public double targetExposure() {
        var column = level == null ? this : bottom();
        double highest = 0;
        if (column != null) for (int i = 0; i < RODS; i++) if (column.holes[i] == LITHIUM_TARGET) highest = Math.max(highest, column.burnt[i]);
        return highest > 0 || lithiumTargets() > 0 ? highest / TARGET_FULL_POWER_SECONDS * 100 : irradiatedTargets() > 0 ? 100 : 0;
    }
    public boolean active(int i) { var column = level == null ? this : bottom(); return column != null && i >= 0 && i < RODS && (column.activeMask & 1 << i) != 0; }
    public boolean control(int i) { var column = level == null ? this : bottom(); return column != null && column.active(i) && column.channelTypes[i] == CONTROL; }
    public boolean controlInstalled(int i) { var column = level == null ? this : bottom(); return column != null && column.control(i) && column.controlRods[i] != 0; }
    /** Whether fuel position {@code i} of this column holds a rod; only the bottom cell knows. */
    public boolean loaded(int i) { var column = level == null ? this : bottom(); return column != null && column.active(i) && !column.control(i) && column.holes[i] != EMPTY; }
    public int activeChannels() { var column = level == null ? this : bottom(); return column == null ? 0 : Integer.bitCount(column.activeMask); }
    public int controlChannels() { return channels(CONTROL); }
    public int fuelChannels() { return channels(FUEL); }

    private int channels(byte type) {
        var column = level == null ? this : bottom();
        if (column == null) return 0;
        int count = 0;
        for (int i = 0; i < RODS; i++) if (column.active(i) && column.channelTypes[i] == type) count++;
        return count;
    }

    private int count(byte state) {
        var column = level == null ? this : bottom();
        int n = 0;
        if (column != null) for (int i = 0; i < RODS; i++) if (column.active(i) && !column.control(i) && column.holes[i] == state) n++;
        return n;
    }
    public double fuelKelvin() { return fuelKelvin; }
    /** Temperature of this column's water, or of its fuel when it has none. */
    public double coolantKelvin() { return coolant > 0 ? ReactorPhysics.coolantKelvin(specific) : fuelKelvin; }
    /** Estimated hottest fuel-rod cladding surface in this bundle. */
    public double fuelSurfaceKelvin() {
        int height = Math.max(1, coreHeight);
        return ReactorPhysics.fuelSurfaceKelvin(fuelKelvin, coolantKelvin(), voidFraction(height));
    }
    public int coolantAmount() { return coolant; }
    /** Measured water/steam throughput of this 2 x 2 pressure-channel bundle, in millibuckets per tick. */
    public float flowRate() { return flowRate; }
    /** Energy of the water that entered this bundle over the last one-second flow window, in joules per tick. */
    public double inletJoulesPerTick() { return inletJoules; }

    @Override public void tick() {
        super.tick();
        if (level != null && !level.isClientSide()) {
            if (++flowTicks >= 20) {
                float next = Math.max(inletWindow, outletWindow) / 20f;
                if (Math.abs(next - flowRate) >= .05f) setChanged();
                flowRate = next;
                inletJoules = inletJoulesWindow / 20;
                inletJoulesWindow = 0;
                inletWindow = outletWindow = flowTicks = 0;
            }
            releaseReducedGridItems();
            if (level.getGameTime() % 20 == 0) irradiateTargets();
            if (core != null) {
                core.tick();
                if (core.formed()) {
                    ProductionMonitor.plan(this, 0, 0, core.nominalWatts() / 1000);
                    ProductionMonitor.produced(this, 0, 0, core.power() / 20);
                    ProductionMonitor.limitedBy(this, switch (core.status()) {
                        case "critical" -> core.power() < core.nominalWatts() * .99 ? "reactor_load" : "rated";
                        case "subcritical" -> "reactor_load";
                        case "shutdown" -> "stopped";
                        case "overheat" -> "coolant";
                        case "rods_jammed" -> "maintenance";
                        default -> "safety";
                    });
                } else ProductionMonitor.limitedBy(this, "structure");
            }
        }
    }

    /** Joins a formed core again, or tries to form one, after any core block in the level changed. */
    @Override public void lazyTick() {
        super.lazyTick();
        if (level == null || level.isClientSide() || core != null && core.formed()) return;
        if (controller != null && level.getBlockEntity(controller) instanceof FuelChannelBlockEntity lead && lead.core != null && lead.core.formed()
            && lead.core.box().isInside(worldPosition)) return;
        if (controller != null) joinCore(null, 0);
        if (level.getBlockState(worldPosition.below()).getBlock() instanceof FuelChannelBlock) return;
        long version = ReactorCores.version(level);
        if (version == seenVersion) return;
        seenVersion = version;
        var result = ReactorCoreStructure.scan(level, worldPosition);
        if (result.formed() && level.getBlockEntity(result.controller()) instanceof FuelChannelBlockEntity lead) lead.adopt(result, null);
        else if (!result.reason().equals(reason) || !result.fault().equals(fault)) {
            reason = result.reason();
            fault = result.fault();
            sendData();
        }
    }

    public void adopt(ReactorCoreStructure.Result result, ReactorPhysics.Kinetics carried) {
        if (core == null) core = new ReactorCore(this);
        if (carried != null) core.inherit(carried);
        core.form(level, result);
    }

    public void coreChanged() { if (core != null) core.changed(); }
    public void signalChanged() { if (core != null) core.signalChanged(); }

    public void coreSync() {
        setChanged();
        sendData();
    }

    public void joinCore(BlockPos lead, int height) {
        if (java.util.Objects.equals(lead, controller) && height == coreHeight) return;
        if (ReactorPhysics.supportedHeight(height)) resizeAssembly(height);
        controller = lead;
        coreHeight = height;
        if (lead != null && coolant > height * COOLANT_PER_CELL) vent(coolant - height * COOLANT_PER_CELL);
        level.invalidateCapabilities(worldPosition);
        // Pipes laid before the core formed connect to the new ports, and let go of them when it breaks.
        getBlockState().updateNeighbourShapes(level, worldPosition, Block.UPDATE_ALL);
        setChanged();
        sendData();
    }

    private void resizeAssembly(int height) {
        if (height == assemblyHeight) return;
        // Added material is cold; removed material takes its heat away instead of concentrating it in the remainder.
        if (height > assemblyHeight) fuelKelvin = 293.15 + (fuelKelvin - 293.15) * assemblyHeight / height;
        assemblyHeight = height;
    }

    /** Applies the fine channel circle and, once for a new bundle, its default fuel/control layout. */
    public void initializeLayout(int mask, int controls) {
        var column = bottom();
        if (column == null || column != this) return;
        int removed = activeMask & ~mask;
        if (removed != 0 && level != null && !level.isClientSide()) for (int i = 0; i < RODS; i++) if ((removed & 1 << i) != 0) {
            if (holes[i] != EMPTY) Containers.dropItemStack(level, worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5,
                new ItemStack(contentsItem(i)));
            if (controlRods[i] != 0) Containers.dropItemStack(level, worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5,
                new ItemStack(controlWear[i] > 0 ? CENuclear.SPENT_CONTROL_ROD.get() : CENuclear.CONTROL_ROD.get()));
            holes[i] = EMPTY;
            controlRods[i] = 0;
            burnt[i] = 0;
            controlDepth[i] = controlWear[i] = 0;
        }
        activeMask = mask;
        if (coreHeight > 0 && coolant > coolantCapacity() && level != null && !level.isClientSide()) vent(coolant - coolantCapacity());
        if (!layoutInitialized) {
            for (int i = 0; i < RODS; i++) channelTypes[i] = (controls & 1 << i) != 0 ? CONTROL : FUEL;
            layoutInitialized = true;
        }
        setChanged();
        sendData();
    }

    public boolean selectChannel(int slot, byte type) {
        var column = bottom();
        if (column == null || slot < 0 || slot >= RODS || !column.activeDirect(slot) || type < FUEL || type > CONTROL
            || column.holes[slot] != EMPTY || column.controlRods[slot] != 0) return false;
        if (column.channelTypes[slot] == type) return true;
        column.channelTypes[slot] = type;
        column.controlDepth[slot] = column.controlWear[slot] = 0;
        column.layoutChanged();
        return true;
    }

    public boolean installControlRod(int slot) {
        var column = bottom();
        if (column == null || slot < 0 || slot >= RODS || !column.activeDirect(slot) || column.holes[slot] != EMPTY || column.controlRods[slot] != 0) return false;
        if (column.controller == null) {
            int height = 1;
            while (height <= ReactorCoreStructure.HEIGHT && level.getBlockState(column.worldPosition.above(height)).getBlock() instanceof FuelChannelBlock) height++;
            if (!ReactorPhysics.supportedHeight(height) || !column.acceptsAssemblyHeight(height)) return false;
            column.resizeAssembly(height);
        }
        column.channelTypes[slot] = CONTROL;
        column.controlRods[slot] = 1;
        column.controlDepth[slot] = column.controlWear[slot] = 0;
        column.layoutChanged();
        return true;
    }

    public ItemStack removeControlRod(int slot) {
        var column = bottom();
        if (column == null || slot < 0 || slot >= RODS || !column.controlDirect(slot) || column.controlRods[slot] == 0 || column.controlDepth[slot] > 1f / 512) return ItemStack.EMPTY;
        ItemStack result = net.createplant.nuclear.RadiationSources.stamp(new ItemStack(column.controlWear[slot] > 0 ? CENuclear.SPENT_CONTROL_ROD.get() : CENuclear.CONTROL_ROD.get()), column.level);
        column.controlRods[slot] = 0;
        column.controlDepth[slot] = column.controlWear[slot] = 0;
        column.layoutChanged();
        return result;
    }

    public float controlDepth(int slot) { var column = bottom(); return column == null || !column.controlDirect(slot) ? 0 : column.controlDepth[slot]; }
    public float controlWear(int slot) { var column = bottom(); return column == null || !column.controlDirect(slot) ? 0 : column.controlWear[slot]; }

    public void controlDepth(int slot, float depth) {
        if (!controlDirect(slot) || controlRods[slot] == 0) return;
        controlDepth[slot] = Math.clamp(depth, 0, layout().coreHeight());
        setChanged();
    }

    public float wearControl(int slot, double added) {
        if (!controlDirect(slot) || controlRods[slot] == 0) return 0;
        controlWear[slot] = (float) Math.min(ReactorPhysics.MAX_ABSORBER_WEAR, controlWear[slot] + added);
        setChanged();
        return controlWear[slot];
    }

    public boolean renewControl(int slot) {
        if (!controlDirect(slot) || controlRods[slot] == 0 || controlDepth[slot] > 1f / 512) return false;
        controlWear[slot] = 0;
        setChanged();
        sendData();
        return true;
    }

    private boolean activeDirect(int slot) { return slot >= 0 && slot < RODS && (activeMask & 1 << slot) != 0; }
    private boolean controlDirect(int slot) { return activeDirect(slot) && channelTypes[slot] == CONTROL; }

    private void layoutChanged() {
        layoutInitialized = true;
        setChanged();
        sendData();
        fuelChanged();
        if (level != null) ReactorCores.changed(level, worldPosition);
    }

    private ReactorCore leadCore() {
        if (controller == null || level == null) return null;
        return level.getBlockEntity(controller) instanceof FuelChannelBlockEntity lead && lead.core != null && lead.core.formed() ? lead.core : null;
    }

    public ReactorCore formedCore() { return leadCore(); }

    public FuelChannelBlockEntity bottom() {
        BlockPos pos = worldPosition;
        for (int i = 0; i < ReactorCoreStructure.HEIGHT && level.getBlockState(pos.below()).getBlock() instanceof FuelChannelBlock; i++) pos = pos.below();
        return level.getBlockEntity(pos) instanceof FuelChannelBlockEntity cell ? cell : null;
    }

    public boolean top() { return !(level.getBlockState(worldPosition.above()).getBlock() instanceof FuelChannelBlock); }

    // Fuel, kept by the bottom cell of the column

    /** Number-equivalent fuel load of this cell: fresh fuel less burn-up, and a little poison in spent rods. */
    public double cellLoad() {
        var column = bottom();
        if (column == null) return 0;
        double load = 0;
        for (int i = 0; i < RODS; i++) if (column.activeDirect(i) && !column.controlDirect(i))
            load += column.holes[i] == FRESH ? 1 - column.burnt[i] / column.rodJoules() : column.holes[i] == SPENT ? -0.1 : 0;
        return load;
    }

    /** Extra neutron worth is independent of the unchanged fuel energy and power weighting. */
    public double cellReactivityBonus() {
        var column = bottom();
        if (column == null) return 0;
        double bonus = 0;
        for (int i = 0; i < RODS; i++) if (column.activeDirect(i) && !column.controlDirect(i) && column.holes[i] == FRESH)
            bonus += (ReactorFuel.fromId(column.fuels[i]).worth() - ReactorPhysics.FUEL_WORTH) * (1 - column.burnt[i] / column.rodJoules());
        return bonus;
    }

    /** Burns the fresh rods of this bottom cell's column evenly; a rod that has given all its energy is spent. */
    public void burn(double joules) {
        int fresh = 0;
        for (int i = 0; i < RODS; i++) if (activeDirect(i) && !controlDirect(i) && holes[i] == FRESH) fresh++;
        if (fresh == 0) return;
        boolean changed = false;
        for (int i = 0; i < RODS; i++) {
            if (!activeDirect(i) || controlDirect(i) || holes[i] != FRESH) continue;
            burnt[i] += joules / fresh;
            if (burnt[i] >= rodJoules()) {
                holes[i] = SPENT;
                burnt[i] = 0;
                changed = true;
            }
        }
        setChanged();
        if (changed) fuelChanged();
    }

    private net.minecraft.world.item.Item contentsItem(int slot) {
        return switch (holes[slot]) {
            case LITHIUM_TARGET -> net.createplant.registry.CEFusionFuel.LITHIUM_TARGET.get();
            case IRRADIATED_TARGET -> net.createplant.registry.CEFusionFuel.IRRADIATED_TARGET.get();
            case FRESH -> freshItem(slot);
            default -> CENuclear.SPENT_FUEL_ROD.get();
        };
    }

    public void irradiateTargets() {
        if (controller == null || coreHeight <= 0 || level.getBlockState(worldPosition.below()).getBlock() instanceof FuelChannelBlock
            || !(level.getBlockEntity(controller) instanceof FuelChannelBlockEntity lead) || lead.core == null || !lead.core.formed()) return;
        double flux = Math.clamp(lead.core.irradiationPower() / Math.max(1, lead.core.nominalWatts()), 0, 2);
        if (flux <= 0) return;
        boolean changed = false;
        for (int i = 0; i < RODS; i++) if (activeDirect(i) && !controlDirect(i) && holes[i] == LITHIUM_TARGET) {
            burnt[i] = Math.min(TARGET_FULL_POWER_SECONDS, burnt[i] + flux);
            if (burnt[i] >= TARGET_FULL_POWER_SECONDS) { holes[i] = IRRADIATED_TARGET;burnt[i] = 0;changed = true; }
            setChanged();
        }
        if (changed) fuelChanged();
    }

    private net.minecraft.world.item.Item freshItem(int slot) {
        return ReactorFuel.fromId(fuels[slot]).item();
    }

    private IItemHandler itemPort(Direction side) {
        if (controller == null || side != Direction.UP && side != null || !top()) return null;
        return new ColumnItems(bottom());
    }

    public IItemHandler topItems() {
        var column = bottom();
        return controller == null || column == null ? null : new ColumnItems(column);
    }

    /** One slot for each hole of the column: a fresh rod goes into an empty hole, a spent one comes out. */
    private record ColumnItems(FuelChannelBlockEntity bottom) implements IItemHandler {
        @Override public int getSlots() { return bottom == null ? 0 : RODS; }
        @Override public ItemStack getStackInSlot(int slot) {
            if (bottom == null || slot < 0 || slot >= RODS || !bottom.activeDirect(slot) || bottom.controlDirect(slot)) return ItemStack.EMPTY;
            return switch (bottom.holes[slot]) {
                case FRESH -> new ItemStack(bottom.freshItem(slot));
                case SPENT -> new ItemStack(CENuclear.SPENT_FUEL_ROD.get());
                case LITHIUM_TARGET, IRRADIATED_TARGET -> new ItemStack(bottom.contentsItem(slot));
                default -> ItemStack.EMPTY;
            };
        }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (bottom == null || slot < 0 || slot >= RODS || !isItemValid(slot, stack) || bottom.holes[slot] != EMPTY) return stack;
            if (!simulate) {
                bottom.holes[slot] = stack.is(net.createplant.registry.CEFusionFuel.LITHIUM_TARGET.get()) ? LITHIUM_TARGET : FRESH;
                bottom.fuels[slot] = (byte) ReactorFuel.of(stack).ordinal();
                bottom.burnt[slot] = 0;
                bottom.fuelChanged();
            }
            return stack.copyWithCount(stack.getCount() - 1);
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (bottom == null || slot < 0 || slot >= RODS || !bottom.activeDirect(slot) || bottom.controlDirect(slot) || amount <= 0 || bottom.holes[slot] != SPENT && bottom.holes[slot] != IRRADIATED_TARGET) return ItemStack.EMPTY;
            ItemStack result = net.createplant.nuclear.RadiationSources.stamp(new ItemStack(bottom.contentsItem(slot)), bottom.level);
            if (!simulate) {
                bottom.holes[slot] = EMPTY;
                bottom.fuelChanged();
            }
            return result;
        }
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return bottom != null && slot >= 0 && slot < RODS && bottom.activeDirect(slot) && !bottom.controlDirect(slot)
                && (CENuclear.isFuel(stack) && ReactorFuel.of(stack).acceptsHeight(bottom.layout().coreHeight())
                    || stack.is(net.createplant.registry.CEFusionFuel.LITHIUM_TARGET.get()));
        }
    }

    private void fuelChanged() {
        setChanged();
        sendData();
        if (controller != null && level.getBlockEntity(controller) instanceof FuelChannelBlockEntity lead && lead.core != null) lead.core.fuelChanged();
    }

    // Coolant, kept by the bottom cell of the column

    public double voidFraction(int height) { return ReactorPhysics.voidFraction(coolant / 1000d, specific, activeChannels() * height); }

    /** Heats the fuel by the fission power and passes heat to the water, never past the temperature of the hotter side. */
    public void heat(double watts, int height) {
        double share = Math.max(1, activeChannels()) / (double) RODS;
        double capacity = ReactorPhysics.CELL_HEAT_CAPACITY * height * share, dt = ReactorPhysics.TICK_SECONDS, joules = 0;
        if (coolant > 0) {
            double kilograms = coolant / 1000d, water = ReactorPhysics.coolantKelvin(specific);
            double conductance = ReactorPhysics.CELL_CONDUCTANCE * height * share
                * ReactorPhysics.nucleateBoilingHeatTransfer(watts / (ReactorPhysics.NOMINAL_COLUMN_WATTS * share * ReactorPhysics.heightScale(height)))
                * ReactorPhysics.coolantHeatTransfer(voidFraction(height));
            joules = conductance * (fuelKelvin - water) * dt;
            double reachable = kilograms * (specificAt(fuelKelvin) - specific);
            joules = joules > 0 ? Math.min(joules, Math.min(reachable, capacity * (fuelKelvin - water)))
                : Math.max(joules, Math.max(reachable, capacity * (fuelKelvin - water)));
            specific += joules / kilograms;
        }
        fuelKelvin = Math.clamp(fuelKelvin + (watts * dt - joules) / capacity, 1, 10000);
        setChanged();
    }

    /** Specific energy of water at the temperature: liquid up to boiling, fully vapour above it. */
    private static double specificAt(double kelvin) {
        return ReactorCoolant.CP * kelvin + (kelvin > ReactorPhysics.SATURATION_KELVIN ? ReactorCoolant.LATENT : 0);
    }

    /** Wet water/steam leaves through the core's side; water comes in only through a directly piped feed-bundle network. */
    private IFluidHandler fluidPort(Direction side) {
        if (controller == null) return null;
        return outlet(this, controller, side);
    }

    /** The core's outlet for a channel face that looks out of the core sideways, or for the null face of diagnostics. */
    public static IFluidHandler outlet(net.minecraft.world.level.block.entity.BlockEntity part, BlockPos lead, Direction side) {
        var level = part.getLevel();
        if (level == null || lead == null || side != null && !side.getAxis().isHorizontal()) return null;
        if (side != null && ReactorCoreStructure.kind(level.getBlockState(part.getBlockPos().relative(side)).getBlock()) != null) return null;
        return level.getBlockEntity(lead) instanceof FuelChannelBlockEntity controller && controller.core != null && controller.core.formed() ? controller.core.outlet() : null;
    }

    private int coolantCapacity() { return coreHeight * activeChannels() * COOLANT_PER_CHANNEL; }

    /**
     * Takes a bulk wet parcel. Its specific energy encodes the equilibrium water/steam ratio, so neither phase can be selected at
     * the reactor outlet; a gas-liquid separator must split it downstream.
     */


    private net.minecraft.world.level.material.Fluid liquidType(){double kelvin=waterKelvin();return kelvin>=ReactorCoolant.OPERATING_INLET_KELVIN ? CEProcessFluids.PRESSURISED_WATER.get() : kelvin>=330 ? CEProcessFluids.WARM_WATER.get() : Fluids.WATER;}
    public int phaseAvailable(net.minecraft.world.level.material.Fluid type){
        if(coolant<=0)return 0;double quality=ReactorPhysics.quality(specific);
        return type==CEProcessFluids.HIGH_PRESSURE_STEAM.get() ? (int)Math.floor(coolant*quality) : type==liquidType() ? (int)Math.floor(coolant*(1-quality)) : 0;
    }
    public FluidStack takePhase(net.minecraft.world.level.material.Fluid type,int requested,boolean execute){
        int amount=Math.min(Math.max(0,requested),phaseAvailable(type));if(amount<=0)return FluidStack.EMPTY;
        if(execute){double perKilogram=ReactorCoolant.CP*(type==CEProcessFluids.HIGH_PRESSURE_STEAM.get() ? ReactorPhysics.SATURATION_KELVIN : waterKelvin())+(type==CEProcessFluids.HIGH_PRESSURE_STEAM.get() ? ReactorCoolant.LATENT : 0);remove(amount,perKilogram);outletWindow+=amount;}
        return new FluidStack(type,amount);
    }
    private double waterKelvin() { return Math.min(ReactorPhysics.SATURATION_KELVIN, ReactorPhysics.coolantKelvin(specific)); }

    private FluidStack water(int amount) {
        return amount<=0 ? FluidStack.EMPTY : new FluidStack(liquidType(),amount);
    }

    /** Removes {@code amount} mB that carry {@code perKilogram} J/kg, keeping the energy of what stays. */
    private void remove(int amount, double perKilogram) {
        double joules = coolant / 1000d * specific - amount / 1000d * perKilogram;
        coolant -= amount;
        specific = coolant > 0 ? Math.clamp(joules / (coolant / 1000d), ReactorCoolant.CP, ReactorCoolant.CP * 10000 + ReactorCoolant.LATENT) : ReactorCoolant.CP * 293.15;
        setChanged();
    }

    /** The channel has burst: all its water escapes as steam. Returns how much there was. */
    public int flash() {
        int amount = coolant;
        if (amount > 0) vent(amount);
        return amount;
    }

    private void vent(int amount) {
        remove(amount, specific);
        if (level instanceof ServerLevel server)
            server.sendParticles(ParticleTypes.CLOUD, worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5, 12, .3, .4, .3, .02);
    }

    /** This bottom cell's water inlet, reached from any directly piped feed bundle in the connected bed. */
    public IFluidHandler inlet() { return new Inlet(this); }

    public boolean fed() {
        if (level == null) return false;
        for (int y = 1; y <= layout().lowerStructureHeight(); y++)
            if (!level.getBlockState(worldPosition.below(y)).is(CENuclear.FEED_BUNDLE.get())) return false;
        return true;
    }

    private record Inlet(FuelChannelBlockEntity cell) implements IFluidHandler {
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int tank) { return tank == 0 && cell.coolant > 0 ? cell.water(cell.coolant) : FluidStack.EMPTY; }
        @Override public int getTankCapacity(int tank) { return tank == 0 ? cell.coolantCapacity() : 0; }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return tank == 0 && ReactorCoolant.accepts(stack); }
        @Override public int fill(FluidStack stack, FluidAction action) {
            if (cell.controller == null || !ReactorCoolant.accepts(stack) || !cell.fed()) return 0;
            int amount = Math.min(stack.getAmount(), cell.coolantCapacity() - cell.coolant);
            if (amount <= 0) return 0;
            if (action.execute()) {
                double entering = amount / 1000d * ReactorCoolant.inletSpecific(stack), joules = cell.coolant / 1000d * cell.specific + entering;
                cell.coolant += amount;
                cell.specific = joules / (cell.coolant / 1000d);
                cell.inletWindow += amount;
                cell.inletJoulesWindow += entering;
                cell.setChanged();
            }
            return amount;
        }
        @Override public FluidStack drain(FluidStack stack, FluidAction action) { return FluidStack.EMPTY; }
        @Override public FluidStack drain(int amount, FluidAction action) { return FluidStack.EMPTY; }
    }


    // Removal and information

    public void dismantle() {
        if (level == null || level.isClientSide()) return;
        releaseReducedGridItems();
        if (core != null) core.release(level);
        double x = worldPosition.getX() + .5, y = worldPosition.getY() + .5, z = worldPosition.getZ() + .5;
        for (int i = 0; i < RODS; i++) {
            if (holes[i] != EMPTY) Containers.dropItemStack(level, x, y, z, new ItemStack(contentsItem(i)));
            if (controlRods[i] != 0) Containers.dropItemStack(level, x, y, z, new ItemStack(controlWear[i] > 0 ? CENuclear.SPENT_CONTROL_ROD.get() : CENuclear.CONTROL_ROD.get()));
            holes[i] = EMPTY;
            controlRods[i] = 0;
        }
        if (coolant > 0) vent(coolant);
    }

    /** A reduced grid cannot retain every old position; return their contents to the world instead of deleting them. */
    private void releaseReducedGridItems() {
        if (level == null || level.isClientSide() || reducedFresh + reducedSpent + reducedControl + reducedSpentControl == 0) return;
        double x = worldPosition.getX() + .5, y = worldPosition.getY() + .5, z = worldPosition.getZ() + .5;
        for (int i = 0; i < reducedFresh; i++) Containers.dropItemStack(level, x, y, z, new ItemStack(CENuclear.FUEL_ROD.get()));
        for (int i = 0; i < reducedSpent; i++) Containers.dropItemStack(level, x, y, z, new ItemStack(CENuclear.SPENT_FUEL_ROD.get()));
        for (int i = 0; i < reducedControl; i++) Containers.dropItemStack(level, x, y, z, new ItemStack(CENuclear.CONTROL_ROD.get()));
        for (int i = 0; i < reducedSpentControl; i++) Containers.dropItemStack(level, x, y, z, new ItemStack(CENuclear.SPENT_CONTROL_ROD.get()));
        reducedFresh = reducedSpent = reducedControl = reducedSpentControl = 0;
        setChanged();
    }

    /** The top cell draws the long upper fuel assemblies and the control rods above and below the core. */
    @Override protected net.minecraft.world.phys.AABB createRenderBoundingBox() {
        var layout = layout();
        return super.createRenderBoundingBox().expandTowards(0, layout.upperStructureHeight(), 0)
            .expandTowards(0, -(layout.coreHeight() + layout.displacerLength() + layout.connectorLength()), 0);
    }

    @Override public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        var lead = leadCore();
        var column = bottom();
        if (lead == null) {
            var origin = column == null ? this : column;
            tooltip.add(Component.translatable(KEY + "status." + origin.reason));
            tooltip.add(Component.translatable(KEY + "fault", origin.fault.getX(), origin.fault.getY(), origin.fault.getZ()));
            return true;
        }
        summary(tooltip, lead);
        BlockPos base = column == null ? worldPosition : column.worldPosition;
        // Keep Create's four-row goggle contract: the normal view shows the requested temperatures, while Shift shows inventory.
        if (sneaking && lithiumTargets() + irradiatedTargets() > 0) tooltip.add(Component.translatable("fusion_fuel.create_refinery_n_reactors.targets", lithiumTargets(), irradiatedTargets(), percent(targetExposure() / 100)));
        else if (sneaking) tooltip.add(Component.translatable(KEY + "fuel", fresh(), spent(), fuelChannels(), controlChannels(),
            lead.columnCoolant(base), percent(lead.columnVoid(base))));
        else tooltip.add(Component.translatable(KEY + "fuel_temperature", celsius(lead.columnKelvin(base)),
            celsius(lead.columnSurfaceKelvin(base)), percent(lead.xenon())));
        return true;
    }

    /** The three common lines every reactor part shows. */
    public static void summary(List<Component> tooltip, ReactorCore core) {
        Component status = Component.translatable(KEY + "status." + core.status());
        tooltip.add(status);
        tooltip.add(Component.translatable(KEY + "power", String.format(Locale.ROOT, "%.2f", core.power() / 1e6),
            String.format(Locale.ROOT, "%+.2f", core.reactivity() / ReactorPhysics.BETA), percent(core.averageVoid())));
        tooltip.add(Component.translatable(KEY + "core", celsius(core.averageFuelKelvin()), celsius(core.hottestKelvin()),
            celsius(core.hottestSurfaceKelvin()), percent(core.rodInsertion()), percent(core.xenon()), core.fuelChannels(), core.controlChannels()));
    }

    public static String percent(double fraction) { return String.format(Locale.ROOT, "%.1f", fraction * 100); }

    public static String celsius(double kelvin) { return String.format(Locale.ROOT, "%.0f", kelvin - 273.15); }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putByteArray("Holes", holes);
        tag.putByteArray("FuelKinds", fuels);
        tag.putByteArray("ChannelTypes", channelTypes);
        tag.putByteArray("ControlRods", controlRods);
        tag.putInt("ActiveMask", activeMask);
        tag.putBoolean("LayoutInitialized", layoutInitialized);
        for (int i = 0; i < RODS; i++) tag.putDouble("Burnt" + i, burnt[i]);
        for (int i = 0; i < RODS; i++) {
            tag.putFloat("ControlDepth" + i, controlDepth[i]);
            tag.putFloat("ControlWear" + i, controlWear[i]);
        }
        tag.putInt("Coolant", coolant);
        tag.putDouble("Specific", specific);
        tag.putDouble("FuelKelvin", fuelKelvin);
        tag.putInt("CoreHeight", coreHeight);
        tag.putInt("AssemblyHeight", assemblyHeight);
        if (reducedFresh > 0) tag.putInt("ReducedGridFresh", reducedFresh);
        if (reducedSpent > 0) tag.putInt("ReducedGridSpent", reducedSpent);
        if (reducedControl > 0) tag.putInt("ReducedGridControl", reducedControl);
        if (reducedSpentControl > 0) tag.putInt("ReducedGridSpentControl", reducedSpentControl);
        if (controller != null) tag.putLong("Controller", controller.asLong());
        tag.putString("Reason", reason);
        tag.putLong("Fault", fault.asLong());
        if (core != null) {
            var state = new CompoundTag();
            core.write(state, clientPacket);
            tag.put("Core", state);
        }
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        int savedHeight = tag.contains("AssemblyHeight") ? tag.getInt("AssemblyHeight") : tag.getInt("CoreHeight");
        assemblyHeight = ReactorPhysics.supportedHeight(savedHeight) ? savedHeight : ReactorPhysics.CORE_HEIGHT;
        coreHeight = ReactorPhysics.supportedHeight(tag.getInt("CoreHeight")) ? tag.getInt("CoreHeight") : 0;
        byte[] saved = tag.getByteArray("Holes");
        byte[] savedFuels = tag.getByteArray(tag.contains("FuelKinds") ? "FuelKinds" : "Mox");
        byte[] savedTypes = tag.getByteArray("ChannelTypes"), savedControls = tag.getByteArray("ControlRods");
        boolean reducedGrid = saved.length > RODS || savedTypes.length > RODS || savedControls.length > RODS;
        int oldSlots = Math.max(saved.length, Math.max(savedTypes.length, savedControls.length));
        int[] sources = reducedGrid ? reducedGridSources(oldSlots) : null;
        for (int i = 0; i < RODS; i++) {
            int source = reducedGrid ? sources[i] : i;
            holes[i] = source < saved.length && saved[source] >= EMPTY && saved[source] <= IRRADIATED_TARGET ? saved[source] : EMPTY;
            fuels[i] = (byte) ReactorFuel.fromId(source < savedFuels.length ? savedFuels[source] : 0).ordinal();
            burnt[i] = Finite.of(tag.getDouble("Burnt" + source), rodJoules(), 0, rodJoules());
            channelTypes[i] = source < savedTypes.length && savedTypes[source] == CONTROL ? CONTROL : FUEL;
            controlRods[i] = (byte) (source < savedControls.length && savedControls[source] != 0 ? 1 : 0);
            controlDepth[i] = Finite.of(tag.getFloat("ControlDepth" + source), 0, 0, layout().coreHeight());
            controlWear[i] = Finite.of(tag.getFloat("ControlWear" + source), (float) ReactorPhysics.MAX_ABSORBER_WEAR, 0, (float) ReactorPhysics.MAX_ABSORBER_WEAR);
        }
        if (reducedGrid && tag.contains("ActiveMask")) {
            int savedMask = tag.getInt("ActiveMask");
            activeMask = 0;
            for (int i = 0; i < RODS; i++) if ((savedMask & 1 << sources[i]) != 0) activeMask |= 1 << i;
        } else activeMask = tag.contains("ActiveMask") ? tag.getInt("ActiveMask") & ReactorCoreStructure.FULL_CHANNEL_MASK : ReactorCoreStructure.FULL_CHANNEL_MASK;
        layoutInitialized = tag.getBoolean("LayoutInitialized") || savedTypes.length > 0;
        if (!clientPacket) {
            reducedFresh = Math.max(0, tag.getInt("ReducedGridFresh"));
            reducedSpent = Math.max(0, tag.getInt("ReducedGridSpent"));
            reducedControl = Math.max(0, tag.getInt("ReducedGridControl"));
            reducedSpentControl = Math.max(0, tag.getInt("ReducedGridSpentControl"));
            if (reducedGrid) {
                for (int i = 0; i < oldSlots; i++) {
                    boolean retained = false;
                    for (int source : sources) if (i == source) retained = true;
                    if (retained) continue;
                    if (i < saved.length && saved[i] == FRESH) reducedFresh++;
                    else if (i < saved.length && saved[i] == SPENT) reducedSpent++;
                    if (i < savedControls.length && savedControls[i] != 0) {
                        if (tag.getFloat("ControlWear" + i) > 0) reducedSpentControl++;
                        else reducedControl++;
                    }
                }
            }
        }
        coolant = Math.max(0, tag.getInt("Coolant"));
        specific = Finite.of(tag.contains("Specific") ? tag.getDouble("Specific") : ReactorCoolant.CP * 293.15, ReactorCoolant.CP * 293.15, 0, Double.MAX_VALUE);
        fuelKelvin = Finite.of(tag.contains("FuelKelvin") ? tag.getDouble("FuelKelvin") : 293.15, 293.15, 1, 10000);
        controller = tag.contains("Controller") ? BlockPos.of(tag.getLong("Controller")) : null;
        reason = tag.getString("Reason");
        if (reason.isEmpty()) reason = "structure";
        fault = BlockPos.of(tag.getLong("Fault"));
        if (tag.contains("Core")) {
            if (core == null) core = new ReactorCore(this);
            core.read(tag.getCompound("Core"), clientPacket);
            // A saved core forms again from the world after loading rather than trusting a stale shape.
            if (!clientPacket) core.changed();
        }
    }
}
