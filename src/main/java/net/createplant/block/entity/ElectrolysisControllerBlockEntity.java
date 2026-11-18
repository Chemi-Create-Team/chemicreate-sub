package net.createplant.block.entity;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.createplant.block.ElectrolysisCellBlock;
import net.createplant.block.ElectrolysisControllerBlock;
import net.createplant.block.ElectrolysisStructure;
import net.createplant.chemistry.ElectrolysisProcess;
import net.createplant.registry.CEChlorAlkali;
import net.createplant.registry.CEElectrolysis;
import net.createplant.registry.CEItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

/** One end-panel owner commits whole batches against finite fluid, electrode and electrical inventories, and sends the electrode heads up and down. */
public class ElectrolysisControllerBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    private BlockPos controller;
    private ElectrolysisStructure structure;
    private List<ElectrolysisCellBlockEntity> cells = List.of();
    private List<ElectrodeHeadBlockEntity> heads = List.of();
    private boolean enabled;
    private CompoundTag lastClientState;
    private boolean electrodeInventoryDirty = true, cachedIntact;
    private long checkedAt = Long.MIN_VALUE;
    private int limit = 100, running, plates, nickel;
    private int cathodeRate, anodeRate, sodaRate, metalRate, recoveredAcidRate, pressTicks;
    private boolean pressedStart;
    private double joules, watts, volts, consumed;
    private volatile double suppliedVoltsSquared, suppliedOhms;
    private volatile long suppliedAt = Long.MIN_VALUE;
    private String status = "structure";

    public ElectrolysisControllerBlockEntity(BlockPos pos, BlockState state) {
        super(CEElectrolysis.CONTROLLER_ENTITY.get(), pos, state);
        setLazyTickRate(20);
    }
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) { }
    public boolean isMaster() { return controller == null || controller.equals(worldPosition); }
    public ElectrolysisControllerBlockEntity master() {
        if (isMaster()) return this;
        return level != null && level.hasChunkAt(controller) && level.getBlockEntity(controller) instanceof ElectrolysisControllerBlockEntity owner ? owner : null;
    }
    public boolean formed() { return structure != null; }
    public boolean intact() {
        if (structure == null || level == null) return false;
        if (checkedAt != level.getGameTime()) { checkedAt = level.getGameTime();cachedIntact = structure.intact(level); }
        return cachedIntact;
    }
    public ElectrolysisStructure structure() { return structure; }
    public List<ElectrolysisCellBlockEntity> cells() { return cells; }
    public List<ElectrodeHeadBlockEntity> heads() { return heads; }
    public int running() { return running; }
    public int plates() { return plates; }
    public int plateCapacity() { return heads.size() * ElectrolysisProcess.PLATES_PER_HEAD; }
    public int limit() { return limit; }
    public boolean enabled() { return enabled; }
    public int cathodeRate() { return cathodeRate; }
    public int anodeRate() { return anodeRate; }
    public int sodaRate() { return sodaRate; }
    public int metalRate() { return metalRate; }
    public int recoveredAcidRate() { return recoveredAcidRate; }
    public int metalStored() { return heads.stream().mapToInt(head -> head.products.getStackInSlot(0).getCount()).sum(); }
    public boolean pressed(boolean start) { return pressTicks > 0 && pressedStart == start; }
    public double watts() { return watts; }
    public double volts() { return volts; }
    public double amps() { return volts > 0 ? watts / volts : 0; }
    public double joules() { return joules; }
    public double consumed() { return consumed; }
    public String status() { return status; }
    public int stored(int tank) { return cells.stream().mapToInt(cell -> cell.tanks.get(tank).getFluidAmount()).sum(); }
    public void electrodeInventoryChanged() { electrodeInventoryDirty = true; }
    public FluidStack fluid(int tank) { return cells.stream().map(cell -> cell.tanks.get(tank).getFluid()).filter(fluid -> !fluid.isEmpty()).findFirst().orElse(FluidStack.EMPTY); }
    public ElectrolysisProcess process() { return ElectrolysisProcess.of(fluid(0)); }

    public void bind(BlockPos owner) {
        if (owner.equals(controller)) return;
        controller = owner;setChanged();sendData();
    }

    public boolean validate() {
        var shape = ElectrolysisStructure.find(level, worldPosition);
        if (shape == null) { invalidateMachine();return false; }
        var owner = (ElectrolysisControllerBlockEntity) level.getBlockEntity(shape.panel());
        var parts = new ArrayList<ElectrolysisCellBlockEntity>();
        for (var at : shape.cells()) {
            if (!(level.getBlockEntity(at) instanceof ElectrolysisCellBlockEntity part)) { invalidateMachine();return false; }
            parts.add(part);
        }
        var racks = new ArrayList<ElectrodeHeadBlockEntity>();
        for (var at : shape.heads()) {
            if (!(level.getBlockEntity(at) instanceof ElectrodeHeadBlockEntity head)) { invalidateMachine();return false; }
            racks.add(head);
        }
        owner.structure = shape;
        owner.cells = List.copyOf(parts);owner.heads = List.copyOf(racks);
        owner.checkedAt = Long.MIN_VALUE;owner.electrodeInventoryDirty = true;
        shape.bind(level, owner.enabled);

        return true;
    }

    public void invalidateMachine() {
        var owner = master();
        if (owner != null) {
            owner.structure = null;owner.cells = List.of();owner.heads = List.of();owner.running = owner.cathodeRate = owner.anodeRate = owner.sodaRate = owner.metalRate = owner.recoveredAcidRate = 0;owner.watts = 0;owner.volts = 0;
            owner.checkedAt = Long.MIN_VALUE;owner.electrodeInventoryDirty = true;
            owner.suppliedOhms = 0;owner.suppliedVoltsSquared = 0;owner.status = "structure";
        }
    }
    public void dropCharge() { joules = 0; }
    @Override public void lazyTick() {
        super.lazyTick();
        if (level != null && !level.isClientSide && (isMaster() || master() == null) && !intact()) validate();
    }

    private boolean ready() {
        if (!intact()) { status = "structure";return false; }
        if (electrodeInventoryDirty) {
            int before = plates;
            plates = heads.stream().mapToInt(ElectrodeHeadBlockEntity::plates).sum();
            nickel = heads.stream().mapToInt(ElectrodeHeadBlockEntity::nickel).sum();
            electrodeInventoryDirty = false;
            if (plates != before) for (var at : structure.controls()) level.updateNeighbourForOutputSignal(at, getBlockState().getBlock());
        }
        if (!enabled) { status = "stopped";return false; }
        for (var at : structure.controls()) if (level.hasNeighborSignal(at)) { status = "redstone";return false; }
        if (plates == 0) { status = "plates";return false; }
        // Plates still on their way down after START only wait; a head held up or rising interlocks the stack.
        if (!heads.stream().allMatch(ElectrodeHeadBlockEntity::lowered)) {
            status = heads.stream().allMatch(head -> head.lowered() || head.lowering()) ? "lowering" : "raised";return false;
        }
        return true;
    }

    private double cycleWork(ElectrolysisProcess recipe, int cycles) {
        double base = ElectrolysisProcess.work(recipe, cycles, nickel);
        if (recipe == null || !recipe.electrowinning() || cycles <= 0) return base;
        int remaining = cycles * recipe.input();long suspended = 0;
        for (var cell : cells) {
            var fluid = cell.tanks.getFirst().getFluid();
            if (!fluid.is(recipe.feed())) continue;
            int amount = Math.min(remaining, fluid.getAmount());
            suspended += (long) amount * fluid.getOrDefault(net.createplant.registry.CEThermal.SUSPENDED_IMPURITY.get(), 0);
            remaining -= amount;if (remaining == 0) break;
        }
        return base * (1 + suspended / (1000d * cycles * recipe.input()));
    }

    private int maximum(ElectrolysisProcess recipe) {
        if (recipe == null) { status = "feed";return 0; }
        int feed = cells.stream().mapToInt(cell -> cell.tanks.getFirst().getFluid().is(recipe.feed()) ? cell.tanks.getFirst().getFluidAmount() : 0).sum();
        int cycles = Math.min((plates * limit + 99) / 100, feed / recipe.input());
        if (cycles == 0) { status = "feed";return 0; }
        int low = 0, high = cycles;
        while (low < high) {
            int candidate = (low + high + 1) / 2;
            if (fits(recipe, candidate)) low = candidate;else high = candidate - 1;
        }
        if (low == 0) status = "output";
        return low;
    }

    private boolean fits(ElectrolysisProcess recipe, int cycles) {
        if (recipe.electrowinning()) return metalProducts(recipe, cycles, false) && fill(3, new FluidStack(recipe.recovered(), cycles * recipe.recoveredAmount()), false) == cycles * recipe.recoveredAmount();
        return !(fill(1, new FluidStack(recipe.cathode(), cycles * recipe.cathodeAmount()), false) < cycles * recipe.cathodeAmount()
            || recipe.anodeGas() && fill(2, new FluidStack(recipe.anode(), cycles), false) < cycles
            || recipe.recoveredAmount() > 0 && fill(3, new FluidStack(recipe.recovered(), cycles * recipe.recoveredAmount()), false) < cycles * recipe.recoveredAmount()
            || recipe.soda() > 0 && fill(3, new FluidStack(CEChlorAlkali.CAUSTIC_SODA.get(), cycles * recipe.soda() + membrane(cycles * recipe.input(), false)), false)
                < cycles * recipe.soda() + membrane(cycles * recipe.input(), false));
    }
    private boolean metalProducts(ElectrolysisProcess recipe, int cycles, boolean execute) {
        var remaining = new ItemStack(recipe.metal(), recipe.metalAmount() * cycles);
        for (var head : heads) {
            remaining = head.products.insertItem(0, remaining, !execute);
            if (remaining.isEmpty()) return true;
        }
        return false;
    }

    public double resistance() {
        if (!ready()) return 0;
        int cycles = maximum(process());
        double work = Math.min(ElectrolysisProcess.BUFFER_JOULES / 2, ElectrolysisProcess.work(Math.min(cycles, plates * limit / 100d), nickel));
        work = Math.min(work, ElectrolysisProcess.BUFFER_JOULES - joules);
        return work > 1e-9 ? 400 * 400 / (work * 20) : 0;
    }

    public void supply(long time, double resistance, double voltageSquared) {
        suppliedAt = time;
        suppliedOhms = Double.isFinite(resistance) && resistance > 0 ? resistance : 0;
        suppliedVoltsSquared = Double.isFinite(voltageSquared) && voltageSquared >= 0 ? voltageSquared : 0;
    }

    @Override public void tick() {
        super.tick();
        if (level == null || level.isClientSide || !isMaster()) return;
        step();
        if (pressTicks > 0) pressTicks--;
        if (level.getGameTime() % 10 == 0) {
            var snapshot = writeClient(new CompoundTag(), level.registryAccess());
            if (!snapshot.equals(lastClientState)) { lastClientState = snapshot;setChanged();sendData(); }
        }
    }

    public void step() {
        running = cathodeRate = anodeRate = sodaRate = metalRate = recoveredAcidRate = 0;watts = 0;volts = 0;
        if (!ready()) return;
        if (suppliedAt >= level.getGameTime() - 2 && suppliedOhms > 0) {
            volts = Math.sqrt(suppliedVoltsSquared);
            watts = suppliedVoltsSquared / suppliedOhms;
            double accepted = Math.min(watts / 20, ElectrolysisProcess.BUFFER_JOULES - joules);
            joules += accepted;consumed += accepted;
        }
        var recipe = process();
        int cycles = maximum(recipe);
        if (cycles == 0) return;
        while (cycles > 0 && cycleWork(recipe, cycles) > joules + 1e-9) cycles--;
        if (cycles == 0) { status = "power";return; }
        double usedWork = cycleWork(recipe, cycles);
        int input = cycles * recipe.input(), soda = cycles * recipe.soda();
        if (recipe.soda() > 0) soda += membrane(input, true);
        int left = input;
        for (var cell : cells) if (cell.tanks.getFirst().getFluid().is(recipe.feed())) left -= cell.tanks.getFirst().drain(left, EXECUTE).getAmount();
        if (recipe.electrowinning()) {
            metalProducts(recipe, cycles, true);fill(3, new FluidStack(recipe.recovered(), cycles * recipe.recoveredAmount()), true);
        } else {
            fill(1, new FluidStack(recipe.cathode(), cycles * recipe.cathodeAmount()), true);
            if (recipe.anodeGas()) fill(2, new FluidStack(recipe.anode(), cycles), true);
            if (recipe.recoveredAmount() > 0) fill(3, new FluidStack(recipe.recovered(), cycles * recipe.recoveredAmount()), true);
            if (soda > 0) fill(3, new FluidStack(CEChlorAlkali.CAUSTIC_SODA.get(), soda), true);
        }
        joules = Math.max(0, joules - usedWork);
        running = cycles;cathodeRate = cycles * recipe.cathodeAmount();anodeRate = recipe.anodeGas() && !recipe.electrowinning() ? cycles : 0;metalRate = cycles * recipe.metalAmount();sodaRate = soda;recoveredAcidRate = cycles * recipe.recoveredAmount();status = "running";
    }

    private int fill(int bank, FluidStack fluid, boolean execute) {
        int left = fluid.getAmount();
        for (var cell : cells) left -= cell.tanks.get(bank).fill(fluid.copyWithAmount(left), execute ? EXECUTE : SIMULATE);
        return fluid.getAmount() - left;
    }

    private int membrane(int brine, boolean execute) {
        int bonus = 0;
        for (var head : heads) {
            var stack = head.electrodes.getStackInSlot(2).copy();
            if (!stack.is(CEItems.ASBESTOS.get()) || brine == 0) continue;
            int wear = Math.clamp(stack.getOrDefault(CEChlorAlkali.DIAPHRAGM_WEAR.get(), 0), 0, CEChlorAlkali.DIAPHRAGM_LIFE - 1);
            int used = Math.min(brine, stack.getCount() * CEChlorAlkali.DIAPHRAGM_LIFE - wear);
            bonus += (wear + used) / 4 - wear / 4;
            brine -= used;
            if (execute) {
                int total = wear + used;
                stack.shrink(total / CEChlorAlkali.DIAPHRAGM_LIFE);
                stack.set(CEChlorAlkali.DIAPHRAGM_WEAR.get(), total % CEChlorAlkali.DIAPHRAGM_LIFE);
                head.electrodes.setStackInSlot(2, stack);
            }
        }
        return bonus;
    }

    public void selectLoad(int percent) {
        if (!formed()) validate();
        var owner = master();
        if (owner == null) return;
        owner.limit = Math.clamp(percent, 1, 100);
        owner.setChanged();owner.sendData();
        level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, .45f, .6f);
    }

    public void setRunning(boolean start) {
        if (!formed()) validate();
        var owner = master();
        if (owner == null) return;
        owner.enabled = start;
        for (var head : owner.heads) head.lower(start);
        owner.setChanged();
        owner.pressedStart = start;owner.pressTicks = 8;owner.sendData();
        level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, .6f, start ? .7f : .55f);
    }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider lookup, boolean clientPacket) {
        super.write(tag, lookup, clientPacket);
        if (controller != null) tag.putLong("Controller", controller.asLong());
        tag.putBoolean("Enabled", enabled);tag.putInt("Limit", limit);tag.putDouble("Joules", joules);tag.putDouble("Consumed", consumed);
        if (!clientPacket) return;
        tag.putString("Status", status);tag.putInt("Running", running);tag.putInt("Plates", plates);tag.putInt("Nickel", nickel);
        tag.putDouble("Watts", watts);tag.putDouble("Volts", volts);
        tag.putInt("CathodeRate", cathodeRate);tag.putInt("AnodeRate", anodeRate);tag.putInt("SodaRate", sodaRate);tag.putInt("MetalRate", metalRate);tag.putInt("RecoveredAcidRate", recoveredAcidRate);
        tag.putBoolean("PressedStart", pressedStart);tag.putInt("PressTicks", pressTicks);
        if (formed()) { tag.putInt("Length", structure.length());tag.putLong("Minimum", structure.minimum().asLong()); }
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider lookup, boolean clientPacket) {
        super.read(tag, lookup, clientPacket);
        controller = tag.contains("Controller") ? BlockPos.of(tag.getLong("Controller")) : null;
        enabled = tag.getBoolean("Enabled") && !tag.getBoolean("EmergencyStop");limit = tag.contains("Limit") ? Math.clamp(tag.getInt("Limit"), 1, 100) : 100;
        double saved = tag.getDouble("Joules"), total = tag.getDouble("Consumed");
        joules = Double.isFinite(saved) ? Math.clamp(saved, 0, ElectrolysisProcess.BUFFER_JOULES) : 0;
        consumed = Double.isFinite(total) ? Math.max(0, total) : 0;
        if (!clientPacket) { structure = null;cells = List.of();heads = List.of();return; }
        status = tag.getString("Status");running = tag.getInt("Running");plates = tag.getInt("Plates");nickel = tag.getInt("Nickel");
        watts = tag.getDouble("Watts");volts = tag.getDouble("Volts");
        cathodeRate = tag.getInt("CathodeRate");anodeRate = tag.getInt("AnodeRate");sodaRate = tag.getInt("SodaRate");metalRate = tag.getInt("MetalRate");recoveredAcidRate = tag.getInt("RecoveredAcidRate");
        pressedStart = tag.getBoolean("PressedStart");pressTicks = tag.getInt("PressTicks");
        if (tag.contains("Length")) {
            var facing = getBlockState().getValue(ElectrolysisControllerBlock.FACING);
            var minimum = BlockPos.of(tag.getLong("Minimum"));
            int length = Math.clamp(tag.getInt("Length"), 1, ElectrolysisStructure.MAX_LENGTH);
            var positions = new ArrayList<BlockPos>();
            for (int n = 0; n < length; n++) positions.add(minimum.relative(facing.getAxis() == net.minecraft.core.Direction.Axis.X ? net.minecraft.core.Direction.EAST : net.minecraft.core.Direction.SOUTH, n));
            var racks = ElectrolysisStructure.heads(positions);
            structure = new ElectrolysisStructure(worldPosition, facing, minimum, length, List.copyOf(positions), List.of(worldPosition, worldPosition.above()), racks);
            cells = positions.stream().map(pos -> level.getBlockEntity(pos)).filter(be -> be instanceof ElectrolysisCellBlockEntity)
                .map(be -> (ElectrolysisCellBlockEntity) be).toList();
            heads = racks.stream().map(pos -> level.getBlockEntity(pos)).filter(be -> be instanceof ElectrodeHeadBlockEntity)
                .map(be -> (ElectrodeHeadBlockEntity) be).toList();
        } else { structure = null;cells = List.of();heads = List.of(); }
    }

    public void ponderVisual(boolean complete) {
        validate();enabled = true;
        plates = heads.stream().mapToInt(ElectrodeHeadBlockEntity::plates).sum();
        nickel = heads.stream().mapToInt(ElectrodeHeadBlockEntity::nickel).sum();
        var recipe = process();
        running = complete ? 0 : (int) (plates * limit / 100d);watts = complete || recipe == null ? 0 : ElectrolysisProcess.work(plates * limit / 100d, nickel) * 20;volts = complete ? 0 : 400;
        cathodeRate = recipe == null ? 0 : running * recipe.cathodeAmount();anodeRate = recipe != null && recipe.anodeGas() && !recipe.electrowinning() ? running : 0;sodaRate = recipe == null ? 0 : running * recipe.soda();metalRate = recipe == null ? 0 : running * recipe.metalAmount();recoveredAcidRate = recipe == null ? 0 : running * recipe.recoveredAmount();
        status = complete ? "idle" : "running";
    }

    public Component electricalReading() {
        return Component.translatable("electrolysis.electric", number(volts), number(amps()), number(watts / 1000));
    }

    @Override public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        var owner = master();
        if (owner != null && owner != this) return owner.addToGoggleTooltip(tooltip, sneaking);
        tooltip.add(Component.translatable("electrolysis.status." + status));
        tooltip.add(electricalReading());
        boolean metal = process() != null && process().electrowinning() || metalStored() > 0;
        if (metal) {
            tooltip.add(Component.translatable("electrolysis.metal_rate", metalRate, recoveredAcidRate, plates, limit));
            tooltip.add(Component.translatable("electrolysis.metal_storage", stored(0), metalStored(), stored(3)));
            return true;
        }
        tooltip.add(Component.translatable("electrolysis.rate", cathodeRate, anodeRate, sodaRate, plates, limit));
        tooltip.add(Component.translatable("electrolysis.storage", stored(0), stored(1), stored(2), stored(3)));
        return true;
    }
    private static String number(double value) { return String.format(Locale.ROOT, "%.1f", value); }
}
