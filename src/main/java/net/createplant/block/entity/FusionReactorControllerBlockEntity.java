package net.createplant.block.entity;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.createplant.block.FusionFluidPortBlock;
import net.createplant.block.FusionReactorControllerBlock;
import net.createplant.fusion.FusionPhysics;
import net.createplant.fusion.FusionStructure;
import net.createplant.registry.CEFusion;
import net.createplant.safety.ToxicGas;
import net.createplant.thermal.FluidThermal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Server authority for the tokamak.  It owns finite isotope, helium and blanket-coolant inventories and couples them to
 * vacuum, magnet, plasma and blanket energy balances.  Loss of any confinement condition extinguishes the plasma; a magnet
 * quench is dumped as heat instead of turning fusion into a fictitious chain-reaction explosion.
 */
public class FusionReactorControllerBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    public enum Command { VACUUM, CRYOGENICS, MAGNETS, PLASMA }

    public static final int FUEL_CAPACITY = 64_000;
    public static final int LIQUID_HELIUM_CAPACITY = 64_000;
    public static final int HELIUM_EXHAUST_CAPACITY = 64_000_000;
    public static final int COOLANT_CAPACITY = 1_200_000;
    public static final int HELIUM_VOLUME_RATIO = 753;
    public static final double MIN_PLASMA_HEATING_WATTS = 40_000_000;
    private static final double BLANKET_HEAT_CAPACITY = 500_000_000;
    private static final double BLANKET_TRIP_KELVIN = 650;
    private static final double COIL_COOLDOWN_HELIUM_MB = 32_000;
    /** Create moves large fluid parcels asynchronously; one second filters scheduling gaps without masking a stopped loop. */
    private static final int COOLANT_FLOW_GRACE_TICKS = 20;
    /** The hot plasma-facing assembly does not lose its full 1 GJ startup inventory in a 50 ms transfer transient. */
    private static final int PLASMA_HEAT_GRACE_TICKS = 20;
    private static final String KEY = "fusion_reactor.create_refinery_n_reactors.";
    private static final Map<Level, Set<BlockPos>> CONTROLLERS = new WeakHashMap<>();

    private FusionStructure.Result structure;
    private int structureWidth = FusionPhysics.WIDTH;
    private final Set<Long> members = new HashSet<>();
    private String status = "structure";
    private BlockPos fault;
    private boolean vacuumCommand, cryogenicCommand, magnetCommand, plasmaCommand, emergency, quench;
    private int targetPercent = 80, clock;
    private long lastElectricalTick = Long.MIN_VALUE;
    private double vacuumPascals = 101_325;
    private double coilKelvin = 293.15;
    private double magnetJoules, startupJoules, blanketKelvin = FusionPhysics.AMBIENT_KELVIN;
    private double fusionWatts, electricalWatts, terminalVolts, coolantFlow;
    private int coolantGraceTicks;
    private int plasmaHeatGraceTicks;
    private double fuelDue, ashDue, bredTritiumDue, heliumBoilDue, blanketDamageDue;
    private double divertorDamageDue;
    private FluidStack deuterium = FluidStack.EMPTY, tritium = FluidStack.EMPTY, ash = FluidStack.EMPTY;
    private FluidStack liquidHelium = FluidStack.EMPTY, heliumExhaust = FluidStack.EMPTY;
    private FluidStack coolantIn = FluidStack.EMPTY, coolantOut = FluidStack.EMPTY;
    private double coolantHeatJoules;
    private final Map<FusionFluidPortBlock.Kind, IFluidHandler> ports = new EnumMap<>(FusionFluidPortBlock.Kind.class);
    private final ItemStackHandler blanket = new ItemStackHandler(1) {
        @Override public boolean isItemValid(int slot, ItemStack stack) { return stack.is(CEFusion.LITHIUM_LEAD_BLANKET.get()) || stack.is(CEFusion.IRRADIATION_CARTRIDGE.get()); }
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override protected void onContentsChanged(int slot) { setChanged(); }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return maintenanceReady() ? super.insertItem(slot, stack, simulate) : stack; }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return maintenanceReady() ? super.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }
    };
    private final ItemStackHandler divertor = new ItemStackHandler(1) {
        @Override public boolean isItemValid(int slot, ItemStack stack) { return stack.is(CEFusion.DIVERTOR_CASSETTE.get()); }
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override protected void onContentsChanged(int slot) { setChanged(); }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return maintenanceReady() ? super.insertItem(slot, stack, simulate) : stack; }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return maintenanceReady() ? super.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }
    };
    private final IItemHandler maintenance = new net.neoforged.neoforge.items.wrapper.CombinedInvWrapper(blanket, divertor);

    public FusionReactorControllerBlockEntity(BlockPos pos, BlockState state) {
        super(CEFusion.CONTROLLER_ENTITY.get(), pos, state);
        fault = pos;
        for (FusionFluidPortBlock.Kind kind : FusionFluidPortBlock.Kind.values()) ports.put(kind, new Port(kind));
    }

    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) { }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CEFusion.CONTROLLER_ENTITY.get(), (controller, side) -> controller.maintenance);
    }

    public Direction inward() { return getBlockState().getValue(FusionReactorControllerBlock.FACING); }
    public int structureWidth() { return structureWidth; }
    public BlockPos centre() { return FusionStructure.centre(worldPosition, inward(), structureWidth); }
    public boolean formed() { return structure != null && structure.formed(); }
    public boolean owns(BlockPos pos) {
        if (!formed()) return false;
        if (!members.isEmpty()) return members.contains(pos.asLong());
        BlockPos centre = centre();
        return Math.abs(pos.getX() - centre.getX()) <= (structureWidth / 2)
            && Math.abs(pos.getZ() - centre.getZ()) <= (structureWidth / 2)
            && pos.getY() >= centre.getY() - FusionPhysics.MIDPLANE
            && pos.getY() < centre.getY() - FusionPhysics.MIDPLANE + FusionPhysics.HEIGHT;
    }
    public IFluidHandler port(FusionFluidPortBlock.Kind kind) { return ports.get(kind); }
    public IItemHandler blanketPort() { return maintenance; }
    public static List<BlockPos> controllers(net.minecraft.world.level.Level level) {
        synchronized (CONTROLLERS) { return CONTROLLERS.get(level) == null ? List.of() : List.copyOf(CONTROLLERS.get(level)); }
    }

    /** Nearby passive parts use this small live index instead of scanning tens of thousands of blocks on every hover. */
    public static FusionReactorControllerBlockEntity find(Level level, BlockPos part) {
        if (level == null) return null;
        Set<BlockPos> positions;
        synchronized (CONTROLLERS) { positions = CONTROLLERS.get(level) == null ? Set.of() : Set.copyOf(CONTROLLERS.get(level)); }
        FusionReactorControllerBlockEntity best = null;
        double distance = Double.MAX_VALUE;
        for (BlockPos pos : positions) {
            if (pos.distSqr(part) > 32 * 32 || !level.hasChunkAt(pos)
                || !(level.getBlockEntity(pos) instanceof FusionReactorControllerBlockEntity candidate) || !candidate.owns(part)) continue;
            double next = pos.distSqr(part);
            if (next < distance) { best = candidate; distance = next; }
        }
        return best;
    }

    private void index() {
        synchronized (CONTROLLERS) { CONTROLLERS.computeIfAbsent(level, ignored -> new HashSet<>()).add(worldPosition.immutable()); }
    }

    private void unindex() {
        synchronized (CONTROLLERS) {
            Set<BlockPos> positions = CONTROLLERS.get(level);
            if (positions != null) positions.remove(worldPosition);
        }
    }

    @Override public void tick() {
        super.tick();
        if (level == null) return;
        index();
        if (level.isClientSide()) return;
        clock++;
        double previousFusion = fusionWatts;
        if (lastElectricalTick < level.getGameTime() - 1) {
            electricalWatts = 0;
            terminalVolts = 0;
        }
        if (structure == null || clock % 40 == 1) validate();
        if (!formed()) {
            if (previousFusion > 0) damageFromDisruption(previousFusion);
            fusionWatts = 0;
            startupJoules = 0;
            electricalWatts = 0;
            if (magnetJoules > 0) emergency = true;
            dumpMagnets(false);
            updateStatus();
            sync(false);
            return;
        }

        double pumpRpm = pumpRpm();
        vacuumPascals = FusionPhysics.vacuumStep(vacuumPascals, pumpRpm, vacuumCommand);
        coolMagnets();

        if (coilKelvin >= FusionPhysics.QUENCH_KELVIN && magnetJoules > FusionPhysics.MAGNET_STORED_JOULES * .01) {
            quench = true;
            emergency = true;
        }
        if (emergency) {
            fusionWatts = 0;
            startupJoules = 0;
            dumpMagnets(quench);
        } else {
            chargeMagnets();
            runPlasma(pumpRpm);
        }
        transferBlanketHeat();
        if (previousFusion > 0 && fusionWatts == 0 && (plasmaCommand || emergency || quench)) damageFromDisruption(previousFusion);
        updateStatus();
        sync(fusionWatts > 0);
    }

    private void validate() {
        boolean was = formed();
        int previousWidth = structureWidth;
        structure = FusionStructure.scan(level, worldPosition, inward(), structureWidth);
        structureWidth = structure.width();
        if (previousWidth != structureWidth) {
            refreshPortCapabilities(previousWidth);
            invalidateRenderBoundingBox();
        }
        fault = structure.fault();
        members.clear();
        if (structure.formed()) for (FusionStructure.Cell cell : structure.cells()) members.add(cell.pos().asLong());
        if (was != structure.formed() || previousWidth != structureWidth) {
            refreshPortCapabilities();
            setChanged(); sendData();
        }
    }

    private void refreshPortCapabilities() { refreshPortCapabilities(structureWidth); }

    private void refreshPortCapabilities(int width) {
        for (FusionStructure.Cell cell : FusionStructure.cells(worldPosition, inward(), width)) {
            if (cell.role() != FusionStructure.Role.DEUTERIUM && cell.role() != FusionStructure.Role.TRITIUM
                && cell.role() != FusionStructure.Role.ASH && cell.role() != FusionStructure.Role.HELIUM_IN
                && cell.role() != FusionStructure.Role.HELIUM_OUT && cell.role() != FusionStructure.Role.COOLANT_IN
                && cell.role() != FusionStructure.Role.COOLANT_OUT || !level.hasChunkAt(cell.pos())) continue;
            level.invalidateCapabilities(cell.pos());
            BlockState state = level.getBlockState(cell.pos());
            state.updateNeighbourShapes(level, cell.pos(), Block.UPDATE_ALL);
            level.updateNeighborsAt(cell.pos(), state.getBlock());
        }
    }

    private double pumpRpm() {
        Direction right = inward().getClockWise();
        BlockPos pos = centre().relative(right, (structureWidth / 2));
        return level.getBlockEntity(pos) instanceof FusionVacuumPumpBlockEntity pump ? Math.abs(pump.getSpeed()) : 0;
    }

    private void coolMagnets() {
        boolean cooling = cryogenicCommand && !liquidHelium.isEmpty() && heliumExhaust.getAmount() <= HELIUM_EXHAUST_CAPACITY - HELIUM_VOLUME_RATIO;
        if (!cooling) {
            coilKelvin = Math.min(293.15, coilKelvin + .001 + FusionPhysics.magnetCharge(magnetJoules) * .001);
            return;
        }
        if (coilKelvin > FusionPhysics.LIQUID_HELIUM_KELVIN) {
            double step = Math.min(.12, coilKelvin - FusionPhysics.LIQUID_HELIUM_KELVIN);
            heliumBoilDue += step * COIL_COOLDOWN_HELIUM_MB / (293.15 - FusionPhysics.LIQUID_HELIUM_KELVIN);
            int boiled = boilHelium(whole(heliumBoilDue));
            if (boiled > 0) {
                double actual = boiled * (293.15 - FusionPhysics.LIQUID_HELIUM_KELVIN) / COIL_COOLDOWN_HELIUM_MB;
                coilKelvin = Math.max(FusionPhysics.LIQUID_HELIUM_KELVIN, coilKelvin - actual);
                heliumBoilDue -= boiled;
            }
        } else {
            heliumBoilDue += .001 + .003 * FusionPhysics.magnetCharge(magnetJoules);
            int boiled = boilHelium(whole(heliumBoilDue));
            heliumBoilDue -= boiled;
            coilKelvin = FusionPhysics.LIQUID_HELIUM_KELVIN;
        }
    }

    private int boilHelium(int requested) {
        int room = (HELIUM_EXHAUST_CAPACITY - heliumExhaust.getAmount()) / HELIUM_VOLUME_RATIO;
        int amount = Math.min(requested, Math.min(liquidHelium.getAmount(), room));
        if (amount <= 0) return 0;
        liquidHelium = shrink(liquidHelium, amount);
        FluidStack gas = FluidThermal.at(new FluidStack(CEFusion.HELIUM_GAS.get(), amount * HELIUM_VOLUME_RATIO), 293.15);
        heliumExhaust = FluidThermal.add(heliumExhaust, gas, gas.getAmount());
        setChanged();
        return amount;
    }

    private void chargeMagnets() {
        if (!magnetCommand || coilKelvin > 5 || magnetJoules >= FusionPhysics.MAGNET_STORED_JOULES) return;
        double accepted = Math.min(electricalWatts / 20, FusionPhysics.MAGNET_STORED_JOULES - magnetJoules);
        if (accepted > 0) magnetJoules += accepted;
    }

    private boolean readyForPlasma(double pumpRpm) {
        return plasmaCommand && magnetCommand && cryogenicCommand && vacuumCommand
            && FusionPhysics.vacuumReady(vacuumPascals) && pumpRpm >= 32
            && FusionPhysics.magnetCharge(magnetJoules) >= .98 && coilKelvin < 5
            && !deuterium.isEmpty() && !tritium.isEmpty() && hasBlanket() && hasDivertor() && coolantReady();
    }

    /** Native pipes may drain one buffer just after this block ticks and refill it before the next tick. */
    private boolean coolantReady() {
        return coolantGraceTicks > 0 || !coolantIn.isEmpty() && coolantOut.getAmount() < COOLANT_CAPACITY;
    }

    private void runPlasma(double pumpRpm) {
        if (!readyForPlasma(pumpRpm)) {
            fusionWatts = 0;
            startupJoules = 0;
            plasmaHeatGraceTicks = 0;
            return;
        }
        if (electricalWatts < MIN_PLASMA_HEATING_WATTS) {
            fusionWatts = 0;
            if (plasmaHeatGraceTicks > 0) plasmaHeatGraceTicks--;
            else startupJoules = 0;
            return;
        }
        plasmaHeatGraceTicks = PLASMA_HEAT_GRACE_TICKS;
        if (startupJoules < FusionPhysics.STARTUP_HEATING_JOULES) {
            startupJoules = Math.min(FusionPhysics.STARTUP_HEATING_JOULES, startupJoules + electricalWatts / 20);
            fusionWatts = 0;
            return;
        }
        double target = Math.min(FusionPhysics.targetWatts(targetPercent), electricalWatts * 10);
        double needed = FusionPhysics.fuelMbPerTick(target);
        if (deuterium.getAmount() < Math.ceil(fuelDue + needed) || tritium.getAmount() < Math.ceil(fuelDue + needed)
            || ash.getAmount() > HELIUM_EXHAUST_CAPACITY - Math.ceil(ashDue + needed * 2)) {
            fusionWatts = 0;
            return;
        }
        fusionWatts = target;
        fuelDue += needed;
        ashDue += needed * 2;
        if (breedsTritium()) bredTritiumDue += needed * 1.05;
        int fuel = whole(fuelDue), helium = whole(ashDue), bred = whole(bredTritiumDue);
        if (fuel > 0) {
            deuterium = shrink(deuterium, fuel);
            tritium = shrink(tritium, fuel);
            fuelDue -= fuel;
        }
        if (helium > 0) {
            FluidStack made = FluidThermal.at(new FluidStack(CEFusion.HELIUM_GAS.get(), helium), 293.15);
            ash = FluidThermal.add(ash, made, helium);
            ashDue -= helium;
        }
        if (bred > 0) {
            int accepted = Math.min(bred, FUEL_CAPACITY - tritium.getAmount());
            if (accepted > 0) {
                FluidStack made = FluidThermal.at(new FluidStack(CEFusion.TRITIUM_GAS.get(), accepted), 293.15);
                tritium = FluidThermal.add(tritium, made, accepted);
                bredTritiumDue -= accepted;
            }
        }
        blanketDamageDue += fusionWatts / FusionPhysics.RATED_FUSION_WATTS / 20;
        int blanketDamage = whole(blanketDamageDue);
        damageBlanket(blanketDamage);
        blanketDamageDue -= blanketDamage;
        divertorDamageDue += fusionWatts / FusionPhysics.RATED_FUSION_WATTS / 20;
        int divertorDamage = whole(divertorDamageDue);
        damageComponent(divertor, divertorDamage);
        divertorDamageDue -= divertorDamage;
        blanketKelvin += (fusionWatts + electricalWatts) / 20 / BLANKET_HEAT_CAPACITY;
        if (blanketKelvin >= BLANKET_TRIP_KELVIN) emergency = true;
        setChanged();
    }

    private void damageBlanket(int amount) {
        damageComponent(blanket, amount);
    }
    private void damageComponent(ItemStackHandler handler, int amount) {
        ItemStack stack = handler.getStackInSlot(0).copy();
        if (amount <= 0 || stack.isEmpty()) return;
        stack.setDamageValue(Math.min(stack.getMaxDamage(), stack.getDamageValue() + amount));
        handler.setStackInSlot(0, stack);
    }
    private void damageFromDisruption(double previousWatts) {
        double fraction = Math.clamp(previousWatts / FusionPhysics.RATED_FUSION_WATTS, 0, 1);
        damageComponent(blanket, (int) Math.ceil(1200 * fraction));
        damageComponent(divertor, (int) Math.ceil((quench ? 4800 : 2400) * fraction));
        setChanged();
    }
    public boolean maintenanceReady() { return fusionWatts <= 0 && magnetCharge() < .01 && blanketKelvin <= 373.15; }
    public int componentRemaining(int index) {
        ItemStack stack = (index == 0 ? blanket : divertor).getStackInSlot(0);
        return stack.isEmpty() ? 0 : Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
    }
    public boolean exchangeComponent(int index, ItemStack replacement) {
        if (index < 0 || index > 1 || !maintenanceReady()) return false;
        var handler = index == 0 ? blanket : divertor;
        if (replacement.getCount() != 1 || !handler.isItemValid(0, replacement) || replacement.getDamageValue() != 0) return false;
        handler.setStackInSlot(0, replacement.copy());setChanged();sendData();
        return true;
    }

    private void dumpMagnets(boolean fast) {
        if (magnetJoules <= 0) { magnetJoules = 0; return; }
        double fraction = fast ? .01 : .0025;
        double released = Math.min(magnetJoules, magnetJoules * fraction + 1);
        magnetJoules -= released;
        coilKelvin = Math.min(293.15, coilKelvin + released / FusionPhysics.MAGNET_STORED_JOULES * (fast ? 8 : .8));
        if (fast && !liquidHelium.isEmpty()) boilHelium(Math.min(liquidHelium.getAmount(), Math.max(1, (int) (released / 10_000_000))));
    }

    private void transferBlanketHeat() {
        coolantFlow=0;if(coolantGraceTicks>0)coolantGraceTicks--;
        if(coolantIn.isEmpty() || coolantOut.getAmount()>=COOLANT_CAPACITY)return;
        var preview=new FluidStack(net.createplant.registry.CEProcessFluids.HOT_REACTOR_COOLANT.get(),1);FluidThermal.processComponents(coolantIn,preview);if(!coolantOut.isEmpty() && !FluidThermal.compatible(coolantOut,preview))return;
        double inlet=FusionPhysics.COOLANT_INLET_KELVIN,outlet=Math.min(FusionPhysics.COOLANT_OUTLET_KELVIN,blanketKelvin-5);if(outlet<=inlet)return;
        double perMb=FusionPhysics.WATER_JOULES_PER_MB_KELVIN*(outlet-inlet);
        double cost=FusionPhysics.WATER_JOULES_PER_MB_KELVIN*(FusionPhysics.COOLANT_OUTLET_KELVIN-inlet);
        int room=Math.min(coolantIn.getAmount(),COOLANT_CAPACITY-coolantOut.getAmount());int amount=Math.min(FusionPhysics.RATED_COOLANT_MB_PER_TICK,room);
        double removable=Math.max(0,blanketKelvin-inlet-5)*BLANKET_HEAT_CAPACITY;
        double joules=Math.min(removable,Math.min(amount*perMb,Math.max(0,room*cost-coolantHeatJoules)));if(joules<=0)return;
        coolantHeatJoules+=joules;int produced=Math.min(room,(int)Math.floor(coolantHeatJoules/cost));
        if(produced>0){var heated=new FluidStack(net.createplant.registry.CEProcessFluids.HOT_REACTOR_COOLANT.get(),produced);FluidThermal.processComponents(coolantIn,heated);coolantIn=shrink(coolantIn,produced);coolantOut=FluidThermal.add(coolantOut,heated,produced);coolantHeatJoules-=produced*cost;}
        blanketKelvin=Math.max(inlet+5,blanketKelvin-joules/BLANKET_HEAT_CAPACITY);coolantFlow=amount;coolantGraceTicks=COOLANT_FLOW_GRACE_TICKS;setChanged();
    }

    private void updateStatus() {
        if (!formed()) status = structure == null ? "structure" : structure.reason();
        else if (quench) status = "quench";
        else if (emergency) status = "dumping";
        else if (!vacuumCommand) status = "vacuum_off";
        else if (!FusionPhysics.vacuumReady(vacuumPascals)) status = "pumping";
        else if (!cryogenicCommand || liquidHelium.isEmpty()) status = "helium";
        else if (coilKelvin > 5) status = "cooling";
        else if (!magnetCommand) status = "magnets_off";
        else if (FusionPhysics.magnetCharge(magnetJoules) < .98) status = "charging";
        else if (!hasBlanket()) status = "blanket";
        else if (!hasDivertor()) status = "divertor";
        else if (deuterium.isEmpty() || tritium.isEmpty()) status = "fuel";
        else if (fusionWatts > 0) status = "burning";
        else if (!coolantReady()) status = "coolant";
        else if (!plasmaCommand) status = "ready";
        else if (startupJoules < FusionPhysics.STARTUP_HEATING_JOULES) status = "heating";
        else status = "heating_power";
    }

    private void sync(boolean active) {
        BlockState state = getBlockState();
        if (state.getValue(FusionReactorControllerBlock.ACTIVE) != active)
            level.setBlock(worldPosition, state.setValue(FusionReactorControllerBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        if (clock % 20 == 0) { setChanged(); sendData(); }
    }

    public double electricalDemandWatts() {
        if (!formed() || emergency || quench) return 0;
        if (magnetCommand && cryogenicCommand && coilKelvin <= 5 && magnetJoules < FusionPhysics.MAGNET_STORED_JOULES)
            return FusionMotorGeneratorBlockEntity.MAX_ELECTRICAL_WATTS;
        if (!plasmaCommand || !magnetCommand || FusionPhysics.magnetCharge(magnetJoules) < .98) return 0;
        return Math.max(MIN_PLASMA_HEATING_WATTS, FusionPhysics.targetWatts(targetPercent) / 10);
    }

    public void supplyElectrical(double watts, double volts) {
        if (level == null || level.isClientSide()) return;
        long tick = level.getGameTime();
        if (lastElectricalTick != tick) {
            electricalWatts = 0;
            terminalVolts = 0;
        }
        double supplied = Double.isFinite(watts) ? Math.max(0, watts) : 0;
        if (supplied >= electricalWatts) {
            electricalWatts = supplied;
            terminalVolts = Double.isFinite(volts) ? Math.max(0, volts) : 0;
        }
        lastElectricalTick = tick;
    }

    public void toggle(Command command, Player player) {
        if (emergency) { player.displayClientMessage(Component.translatable(KEY + "control.emergency_latched"), true); return; }
        switch (command) {
            case VACUUM -> vacuumCommand = !vacuumCommand;
            case CRYOGENICS -> cryogenicCommand = !cryogenicCommand;
            case MAGNETS -> magnetCommand = !magnetCommand;
            case PLASMA -> plasmaCommand = !plasmaCommand;
        }
        setChanged(); sendData();
        player.displayClientMessage(Component.translatable(KEY + "control." + command.name().toLowerCase(Locale.ROOT),
            Component.translatable(KEY + "control.state." + (command(command) ? "on" : "off"))), true);
    }

    public void adjustTarget(int delta, Player player) {
        targetPercent = Math.clamp(targetPercent + delta, 10, 100);
        setChanged(); sendData();
        player.displayClientMessage(Component.translatable(KEY + "control.target", targetPercent), true);
    }

    public void emergency(Player player, boolean reset) {
        if (reset) {
            if (magnetJoules > FusionPhysics.MAGNET_STORED_JOULES * .005 || coilKelvin > 5) {
                player.displayClientMessage(Component.translatable(KEY + "control.reset_blocked"), true); return;
            }
            emergency = quench = false;
            player.displayClientMessage(Component.translatable(KEY + "control.reset"), true);
        } else {
            emergency = true; plasmaCommand = false;
            player.displayClientMessage(Component.translatable(KEY + "control.dump"), true);
        }
        setChanged(); sendData();
    }

    public void localEmergencyToggle(Player player) { emergency(player, emergency); }
    public void describeTo(Player player) { player.displayClientMessage(Component.translatable(KEY + "status." + status), true); }

    private boolean command(Command command) {
        return switch (command) { case VACUUM -> vacuumCommand; case CRYOGENICS -> cryogenicCommand;
            case MAGNETS -> magnetCommand; case PLASMA -> plasmaCommand; };
    }

    private boolean hasBlanket() {
        ItemStack stack = blanket.getStackInSlot(0);
        return blanket.isItemValid(0, stack) && stack.getDamageValue() < stack.getMaxDamage();
    }
    /** Only a lithium-lead blanket breeds; an irradiation cartridge in its place leaves the plant on stored tritium. */
    public boolean breedsTritium() { return blanket.getStackInSlot(0).is(CEFusion.LITHIUM_LEAD_BLANKET.get()); }
    private boolean hasDivertor() {
        ItemStack stack = divertor.getStackInSlot(0);
        return stack.is(CEFusion.DIVERTOR_CASSETTE.get()) && stack.getDamageValue() < stack.getMaxDamage();
    }

    private static int whole(double amount) { return (int) Math.floor(amount + 1e-9); }
    private static FluidStack shrink(FluidStack stack, int amount) {
        if (amount <= 0) return stack;
        FluidStack result = stack.copyWithAmount(Math.max(0, stack.getAmount() - amount));
        return result.isEmpty() ? FluidStack.EMPTY : result;
    }

    public String status() { return status; }
    public BlockPos fault() { return fault; }
    public int targetPercent() { return targetPercent; }
    public double vacuumPascals() { return fusionWatts > 0 ? FusionPhysics.OPERATING_PRESSURE_PASCALS : vacuumPascals; }
    public double baseVacuumPascals() { return vacuumPascals; }
    public double coilKelvin() { return coilKelvin; }
    public double magnetJoules() { return magnetJoules; }
    public double magnetCharge() { return FusionPhysics.magnetCharge(magnetJoules); }
    public double magneticFieldTesla() { return FusionPhysics.magneticFieldTesla(magnetJoules); }
    public double plasmaKelvin() { return startupJoules / FusionPhysics.STARTUP_HEATING_JOULES * FusionPhysics.PLASMA_KELVIN; }
    public double fusionWatts() { return fusionWatts; }
    public double electricalWatts() { return electricalWatts; }
    public double terminalVolts() { return terminalVolts; }
    public double blanketKelvin() { return blanketKelvin; }
    public double coolantFlow() { return coolantFlow; }
    public boolean emergency() { return emergency; }
    public boolean quench() { return quench; }
    public boolean vacuumCommand() { return vacuumCommand; }
    public boolean cryogenicCommand() { return cryogenicCommand; }
    public boolean magnetCommand() { return magnetCommand; }
    public boolean plasmaCommand() { return plasmaCommand; }
    public FluidStack deuterium() { return deuterium.copy(); }
    public FluidStack tritium() { return tritium.copy(); }
    public FluidStack liquidHelium() { return liquidHelium.copy(); }
    public FluidStack heliumExhaust() { return heliumExhaust.copy(); }
    public FluidStack ash() { return ash.copy(); }
    public double coolantHeatCredit(){return coolantHeatJoules;}
    public FluidStack coolantIn() { return coolantIn.copy(); }
    public FluidStack coolantOut() { return coolantOut.copy(); }
    public int blanketRemaining() {
        ItemStack stack = blanket.getStackInSlot(0);
        return stack.isEmpty() ? 0 : stack.getMaxDamage() - stack.getDamageValue();
    }

    /** GameTest commissioning hook; production code has no caller. */
    public void commissionForTest() {
        vacuumPascals = FusionPhysics.BASE_VACUUM_PASCALS;
        coilKelvin = FusionPhysics.LIQUID_HELIUM_KELVIN;
        magnetJoules = FusionPhysics.MAGNET_STORED_JOULES;
        startupJoules = FusionPhysics.STARTUP_HEATING_JOULES;
        plasmaHeatGraceTicks = PLASMA_HEAT_GRACE_TICKS;
        vacuumCommand = cryogenicCommand = magnetCommand = plasmaCommand = true;
        electricalWatts = 50_000_000;
        coolantGraceTicks = COOLANT_FLOW_GRACE_TICKS;
        if (level != null) lastElectricalTick = level.getGameTime();
    }

    /** GameTest-only thermal shortcut used to iterate the full shaft handover without waiting several real minutes. */
    public void preheatBlanketForTest(double kelvin) {
        blanketKelvin = Double.isFinite(kelvin)
            ? Math.clamp(kelvin, FusionPhysics.AMBIENT_KELVIN, BLANKET_TRIP_KELVIN - 1)
            : FusionPhysics.AMBIENT_KELVIN;
    }

    /** Opt-in rendered-check hook.  It only alters the disposable client copy used by {@code FusionClientCheck}. */
    public void previewForClient() {
        if (level == null || !level.isClientSide()) return;
        structure = new FusionStructure.Result(true, centre(), worldPosition, "ready", List.of(), structureWidth);
        vacuumPascals = FusionPhysics.BASE_VACUUM_PASCALS;
        coilKelvin = FusionPhysics.LIQUID_HELIUM_KELVIN;
        magnetJoules = FusionPhysics.MAGNET_STORED_JOULES;
        startupJoules = FusionPhysics.STARTUP_HEATING_JOULES;
        fusionWatts = FusionPhysics.targetWatts(targetPercent);
        vacuumCommand = cryogenicCommand = magnetCommand = plasmaCommand = true;
        status = "burning";
    }

    /** Deliberate dismantling boundary: finite inventories leave the machine instead of vanishing with its block entity. */
    public void releaseContents() {
        if (!(level instanceof ServerLevel server)) return;
        ToxicGas.release(server, worldPosition, tritium);
        boolean filled = !deuterium.isEmpty() || !tritium.isEmpty() || !ash.isEmpty() || !liquidHelium.isEmpty()
            || !heliumExhaust.isEmpty() || !coolantIn.isEmpty() || !coolantOut.isEmpty() || coolantHeatJoules>0 || magnetJoules > 0;
        ItemStack cartridge = blanket.getStackInSlot(0).copy();
        blanket.setStackInSlot(0, ItemStack.EMPTY);
        Containers.dropItemStack(server, worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5, cartridge);
        Containers.dropItemStack(server, worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5, divertor.getStackInSlot(0));
        divertor.setStackInSlot(0, ItemStack.EMPTY);
        deuterium = tritium = ash = liquidHelium = heliumExhaust = coolantIn = coolantOut = FluidStack.EMPTY;coolantHeatJoules=0;
        fusionWatts = startupJoules = magnetJoules = 0;
        if (filled) server.sendParticles(ParticleTypes.CLOUD, worldPosition.getX() + .5, worldPosition.getY() + .5,
            worldPosition.getZ() + .5, 24, .45, .45, .45, .035);
        unindex();
        setChanged();
    }

    @Override protected AABB createRenderBoundingBox() {
        BlockPos c = centre();
        return AABB.encapsulatingFullBlocks(c.offset(-(structureWidth / 2), -FusionPhysics.MIDPLANE, -(structureWidth / 2)),
            c.offset((structureWidth / 2), FusionPhysics.ROOF_OFFSET, (structureWidth / 2))).inflate(1);
    }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        super.write(tag, lookup, packet);
        save(tag, lookup, "Deuterium", deuterium); save(tag, lookup, "Tritium", tritium); save(tag, lookup, "Ash", ash);
        save(tag, lookup, "LiquidHelium", liquidHelium); save(tag, lookup, "HeliumExhaust", heliumExhaust);
        save(tag, lookup, "CoolantIn", coolantIn); save(tag, lookup, "CoolantOut", coolantOut);tag.putDouble("CoolantHeatCredit",coolantHeatJoules);
        tag.put("Blanket", blanket.serializeNBT(lookup));
        tag.put("Divertor", divertor.serializeNBT(lookup));tag.putDouble("DivertorDamageDue", divertorDamageDue);
        tag.putInt("StructureWidth", structureWidth);
        tag.putBoolean("VacuumCommand", vacuumCommand); tag.putBoolean("CryogenicCommand", cryogenicCommand);
        tag.putBoolean("MagnetCommand", magnetCommand); tag.putBoolean("PlasmaCommand", plasmaCommand);
        tag.putBoolean("Emergency", emergency); tag.putBoolean("Quench", quench); tag.putInt("TargetPercent", targetPercent);
        tag.putDouble("VacuumPascals", vacuumPascals); tag.putDouble("CoilKelvin", coilKelvin);
        tag.putDouble("MagnetJoules", magnetJoules); tag.putDouble("StartupJoules", startupJoules);
        tag.putDouble("BlanketKelvin", blanketKelvin); tag.putDouble("FuelDue", fuelDue); tag.putDouble("AshDue", ashDue);
        tag.putDouble("BredTritiumDue", bredTritiumDue); tag.putDouble("HeliumBoilDue", heliumBoilDue);
        tag.putDouble("BlanketDamageDue", blanketDamageDue); tag.putString("Status", status);
        if (fault != null) tag.putLong("Fault", fault.asLong());
        if (packet) {
            tag.putBoolean("Formed", formed()); tag.putDouble("FusionWatts", fusionWatts);
            tag.putDouble("ElectricalWatts", electricalWatts); tag.putDouble("TerminalVolts", terminalVolts);
            tag.putDouble("CoolantFlow", coolantFlow);
        }
    }

    private static void save(CompoundTag tag, HolderLookup.Provider lookup, String name, FluidStack stack) {
        if (!stack.isEmpty()) tag.put(name, stack.save(lookup));
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        super.read(tag, lookup, packet);
        int width = tag.getInt("StructureWidth");
        structureWidth = FusionStructure.WIDTHS.contains(width) ? width : FusionPhysics.WIDTH;
        invalidateRenderBoundingBox();
        deuterium = fluid(tag, lookup, "Deuterium"); tritium = fluid(tag, lookup, "Tritium"); ash = fluid(tag, lookup, "Ash");
        liquidHelium = fluid(tag, lookup, "LiquidHelium"); heliumExhaust = fluid(tag, lookup, "HeliumExhaust");
        coolantIn = fluid(tag, lookup, "CoolantIn"); coolantOut = fluid(tag, lookup, "CoolantOut");double credit=tag.getDouble("CoolantHeatCredit");coolantHeatJoules=Double.isFinite(credit) ? Math.clamp(credit,0,COOLANT_CAPACITY*FusionPhysics.WATER_JOULES_PER_MB_KELVIN*(FusionPhysics.COOLANT_OUTLET_KELVIN-FusionPhysics.COOLANT_INLET_KELVIN)) : 0;
        if (tag.contains("Blanket")) blanket.deserializeNBT(lookup, tag.getCompound("Blanket"));
        if (tag.contains("Divertor")) divertor.deserializeNBT(lookup, tag.getCompound("Divertor"));
        divertorDamageDue = fraction(tag.getDouble("DivertorDamageDue"));
        vacuumCommand = tag.getBoolean("VacuumCommand"); cryogenicCommand = tag.getBoolean("CryogenicCommand");
        magnetCommand = tag.getBoolean("MagnetCommand"); plasmaCommand = tag.getBoolean("PlasmaCommand");
        emergency = tag.getBoolean("Emergency"); quench = tag.getBoolean("Quench");
        targetPercent = tag.contains("TargetPercent") ? Math.clamp(tag.getInt("TargetPercent"), 10, 100) : 80;
        vacuumPascals = finite(tag.getDouble("VacuumPascals"), 101_325, FusionPhysics.BASE_VACUUM_PASCALS, 101_325);
        coilKelvin = finite(tag.getDouble("CoilKelvin"), 293.15, FusionPhysics.LIQUID_HELIUM_KELVIN, 293.15);
        magnetJoules = finite(tag.getDouble("MagnetJoules"), 0, 0, FusionPhysics.MAGNET_STORED_JOULES);
        startupJoules = finite(tag.getDouble("StartupJoules"), 0, 0, FusionPhysics.STARTUP_HEATING_JOULES);
        blanketKelvin = finite(tag.getDouble("BlanketKelvin"), FusionPhysics.AMBIENT_KELVIN, 1, 2000);
        fuelDue = fraction(tag.getDouble("FuelDue")); ashDue = fraction(tag.getDouble("AshDue"));
        bredTritiumDue = pending(tag.getDouble("BredTritiumDue"), FUEL_CAPACITY);
        heliumBoilDue = pending(tag.getDouble("HeliumBoilDue"), LIQUID_HELIUM_CAPACITY);
        blanketDamageDue = fraction(tag.getDouble("BlanketDamageDue"));
        status = tag.contains("Status") ? tag.getString("Status") : "structure";
        fault = tag.contains("Fault") ? BlockPos.of(tag.getLong("Fault")) : worldPosition;
        if (packet) {
            fusionWatts = finite(tag.getDouble("FusionWatts"), 0, 0, FusionPhysics.RATED_FUSION_WATTS);
            electricalWatts = finite(tag.getDouble("ElectricalWatts"), 0, 0, 1e10);
            terminalVolts = finite(tag.getDouble("TerminalVolts"), 0, 0, 1e7);
            coolantFlow = finite(tag.getDouble("CoolantFlow"), 0, 0, FusionPhysics.RATED_COOLANT_MB_PER_TICK);
            // Client-side formation is represented by a compact result so the plasma renderer has no giant member packet.
            structure = tag.getBoolean("Formed") ? new FusionStructure.Result(true, centre(), worldPosition, "ready", List.of(), structureWidth) : null;
        }
    }

    private static FluidStack fluid(CompoundTag tag, HolderLookup.Provider lookup, String name) {
        return tag.contains(name) ? FluidStack.parseOptional(lookup, tag.getCompound(name)) : FluidStack.EMPTY;
    }
    private static double finite(double value, double fallback, double low, double high) {
        return Double.isFinite(value) ? Math.clamp(value, low, high) : fallback;
    }
    private static double fraction(double value) { return Double.isFinite(value) ? Math.clamp(value, 0, 1) : 0; }
    private static double pending(double value, double maximum) { return Double.isFinite(value) ? Math.clamp(value, 0, maximum) : 0; }

    @Override public boolean addToGoggleTooltip(List<Component> text, boolean sneaking) {
        text.add(Component.translatable(KEY + "goggle.status", Component.translatable(KEY + "status." + status)));
        if (!formed()) {
            text.add(Component.translatable(KEY + "goggle.size", structureWidth, structureWidth, FusionPhysics.HEIGHT));
            text.add(Component.translatable(KEY + "goggle.fault", fault.getX(), fault.getY(), fault.getZ()));
            text.add(Component.translatable(KEY + "goggle.ponder"));
            return true;
        }
        text.add(Component.translatable(KEY + "goggle.power", format(fusionWatts / 1e6), targetPercent));
        if (sneaking) {
            text.add(Component.translatable(KEY + "goggle.fuel", deuterium.getAmount(), tritium.getAmount(), ash.getAmount()));
            text.add(Component.translatable(KEY + "goggle.coolant", format(blanketKelvin - 273.15), (int) coolantFlow, coolantOut.getAmount()));
        } else {
            text.add(Component.translatable(KEY + "goggle.magnet", format(coilKelvin), format(magneticFieldTesla()), format(magnetCharge() * 100)));
            text.add(Component.translatable(KEY + "goggle.vacuum", String.format(Locale.ROOT, "%.2e", vacuumPascals), format(plasmaKelvin() / 1e6)));
        }
        return true;
    }

    private static String format(double value) { return String.format(Locale.ROOT, "%.1f", value); }

    private final class Port implements IFluidHandler {
        private final FusionFluidPortBlock.Kind kind;
        private Port(FusionFluidPortBlock.Kind kind) { this.kind = kind; }
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int tank) { return stored().copy(); }
        @Override public int getTankCapacity(int tank) { return capacity(); }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return accepts(stack); }

        @Override public int fill(FluidStack resource, FluidAction action) {
            if (!input() || resource.isEmpty() || !accepts(resource)) return 0;
            FluidStack stored = stored();
            if (!stored.isEmpty() && !FluidThermal.compatible(stored, resource)) return 0;
            int accepted = Math.min(resource.getAmount(), capacity() - stored.getAmount());
            if (accepted <= 0 || action.simulate()) return Math.max(0, accepted);
            assign(FluidThermal.add(stored, resource, accepted));
            setChanged();
            return accepted;
        }

        @Override public FluidStack drain(FluidStack resource, FluidAction action) {
            if (input()) return FluidStack.EMPTY;
            FluidStack stored = stored();
            return resource.isEmpty() || stored.isEmpty() || !resource.is(stored.getFluid()) ? FluidStack.EMPTY : drain(resource.getAmount(), action);
        }

        @Override public FluidStack drain(int maxDrain, FluidAction action) {
            if (input()) return FluidStack.EMPTY;
            FluidStack stored = stored();
            int amount = Math.min(Math.max(0, maxDrain), stored.getAmount());
            if (amount <= 0) return FluidStack.EMPTY;
            FluidStack result = stored.copyWithAmount(amount);
            if (action.execute()) { assign(shrink(stored, amount)); setChanged(); }
            return result;
        }

        private boolean input() {
            return kind == FusionFluidPortBlock.Kind.DEUTERIUM || kind == FusionFluidPortBlock.Kind.TRITIUM
                || kind == FusionFluidPortBlock.Kind.HELIUM_IN || kind == FusionFluidPortBlock.Kind.COOLANT_IN;
        }
        private boolean accepts(FluidStack stack) {
            return switch (kind) {
                case DEUTERIUM -> stack.is(CEFusion.DEUTERIUM_GAS.get());
                case TRITIUM -> stack.is(CEFusion.TRITIUM_GAS.get());
                case HELIUM_IN -> stack.is(CEFusion.LIQUID_HELIUM.get());
                case COOLANT_IN -> stack.is(net.createplant.registry.CEProcessFluids.PRESSURISED_WATER.get());
                default -> false;
            };
        }
        private int capacity() {
            return switch (kind) {
                case DEUTERIUM, TRITIUM -> FUEL_CAPACITY;
                case HELIUM_IN -> LIQUID_HELIUM_CAPACITY;
                case ASH, HELIUM_OUT -> HELIUM_EXHAUST_CAPACITY;
                case COOLANT_IN, COOLANT_OUT -> COOLANT_CAPACITY;
            };
        }
        private FluidStack stored() {
            return switch (kind) {
                case DEUTERIUM -> deuterium; case TRITIUM -> tritium; case ASH -> ash;
                case HELIUM_IN -> liquidHelium; case HELIUM_OUT -> heliumExhaust;
                case COOLANT_IN -> coolantIn; case COOLANT_OUT -> coolantOut;
            };
        }
        private void assign(FluidStack stack) {
            switch (kind) {
                case DEUTERIUM -> deuterium = stack; case TRITIUM -> tritium = stack; case ASH -> ash = stack;
                case HELIUM_IN -> liquidHelium = stack; case HELIUM_OUT -> heliumExhaust = stack;
                case COOLANT_IN -> coolantIn = stack; case COOLANT_OUT -> coolantOut = stack;
            }
        }
    }
}
