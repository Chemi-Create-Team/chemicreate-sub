package net.createplant.block.entity;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createplant.nuclear.Finite;
import net.createplant.nuclear.ReactorFuel;
import net.createplant.nuclear.FuelPoolStructure;
import net.createplant.registry.CENuclear;
import net.createplant.thermal.FluidState;
import net.createplant.thermal.FluidThermal;
import net.createplant.thermal.ThermalFluidTank;
import net.createplant.thermal.TwoPhase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

public class FuelPoolBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    public static final byte EMPTY = 0, FRESH = 1, SPENT = 2;
    public static final int WATER_PER_CELL = (FuelPoolStructure.DEPTH + 1) * 1000, COVER_PER_CELL = FuelPoolStructure.DEPTH * 1000;
    public static final double HEAT_PER_ROD = 1000;
    private static final double HOT_KELVIN = 363.15, BOILING_KELVIN = 373.15, BOIL_JOULES_PER_MB = 2260;
    private static final Map<Level, Set<BlockPos>> LOADED = new WeakHashMap<>();
    private final byte[] slots = new byte[FuelChannelBlockEntity.RODS];
    private final byte[] fuels = new byte[FuelChannelBlockEntity.RODS];
    private final net.createplant.nuclear.Radionuclides[] activity = new net.createplant.nuclear.Radionuclides[FuelChannelBlockEntity.RODS];
    private final ThermalFluidTank water = new ThermalFluidTank(WATER_PER_CELL, f -> setChanged()) {
        @Override public boolean isFluidValid(FluidStack stack) { return FluidState.liquidWater(stack) && !TwoPhase.is(stack) && FluidThermal.valid(stack); }
    };
    private double boilingJoules;
    private long lastHeated = Long.MIN_VALUE;
    private int cells, amount, fresh, spent, waterDeciC;
    private int reducedFresh, reducedSpent;
    private String status = "size";
    private BlockPos fault;

    public FuelPoolBlockEntity(BlockPos pos, BlockState state) {
        super(CENuclear.FUEL_POOL_ENTITY.get(), pos, state);
        setLazyTickRate(20);
    }

    /** Maps the four corners of a former larger square rack to the new 2 x 2 order. */
    private static int[] reducedGridSources(int slots) {
        int grid = (int) Math.round(Math.sqrt(slots));
        if (grid * grid != slots || grid <= net.createplant.nuclear.ReactorPhysics.CHANNEL_GRID) grid = 3;
        return new int[] {0, grid - 1, grid * (grid - 1), grid * grid - 1};
    }

    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) { }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CENuclear.FUEL_POOL_ENTITY.get(), (pool, side) -> side == null || side == Direction.UP || side == Direction.DOWN ? pool.port : null);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CENuclear.FUEL_POOL_ENTITY.get(), (pool, side) -> side == null || side == Direction.DOWN ? pool.fluidPort() : null);
    }

    public static List<BlockPos> within(Level level, int minX, int minZ, int maxX, int maxZ) {
        var cells = LOADED.get(level);
        if (cells == null) return List.of();
        return cells.stream().filter(p -> p.getX() >= minX && p.getX() <= maxX && p.getZ() >= minZ && p.getZ() <= maxZ)
            .filter(level::hasChunkAt).sorted().toList();
    }

    @Override public void initialize() {
        super.initialize();
        if (!level.isClientSide()) {
            LOADED.computeIfAbsent(level, l -> new java.util.HashSet<>()).add(worldPosition);
            FuelPoolStructure.invalidate(level);
            for (int i = 0; i < slots.length; i++) if (slots[i] == SPENT && (activity[i] == null || activity[i].total() == 0)) {
                activity[i] = net.createplant.nuclear.RadiationSources.profile(CENuclear.SPENT_FUEL_ROD.toStack()).fresh(net.createplant.nuclear.RadiationSources.now(level));setChanged();
            }
        }
    }

    @Override public void invalidate() {
        super.invalidate();
        if (level != null) {
            if (LOADED.containsKey(level)) LOADED.get(level).remove(worldPosition);
            FuelPoolStructure.invalidate(level);
        }
    }

    @Override public void tick() {
        super.tick();
        releaseReducedGridItems();
    }

    private FuelPoolStructure.Result structure() { return FuelPoolStructure.get(level, worldPosition); }

    private IFluidHandler fluidPort() {
        var shape = structure();
        return shape.formed() && level.getBlockEntity(shape.cells().getFirst()) instanceof FuelPoolBlockEntity owner ? owner.coolant : coolant;
    }

    private List<FuelPoolBlockEntity> members() {
        if (isRemoved() || level == null || !level.hasChunkAt(worldPosition)) return List.of();
        var structure = structure();
        if (!structure.formed()) return List.of(this);
        return structure.cells().stream().map(p -> level.getBlockEntity(p) instanceof FuelPoolBlockEntity pool ? pool : null)
            .filter(java.util.Objects::nonNull).toList();
    }

    public boolean ready() {
        if (level == null || level.isClientSide()) return cells > 0 && amount >= cells * COVER_PER_CELL && !status.equals("hot");
        var shape = structure();
        if (!shape.formed()) return false;
        int volume = 0;
        double volumeTemperature = 0;
        for (var cell : shape.cells()) if (level.getBlockEntity(cell) instanceof FuelPoolBlockEntity pool) {
            int held = pool.water.getFluidAmount();
            volume += held;
            volumeTemperature += held * pool.water.kelvin();
        }
        return volume >= shape.cells().size() * COVER_PER_CELL && volumeTemperature < volume * HOT_KELVIN;
    }

    public BlockPos surface() { return worldPosition.above(FuelPoolStructure.DEPTH); }

    public net.minecraft.world.phys.AABB radiationWaterBounds() {
        var members = members();
        if (members.isEmpty()) return null;
        double depth = members.stream().mapToInt(p -> p.water.getFluidAmount()).sum() / (members.size() * 1000d);
        if (depth <= 0) return null;
        int x = members.stream().mapToInt(p -> p.worldPosition.getX()).min().orElse(worldPosition.getX());
        int z = members.stream().mapToInt(p -> p.worldPosition.getZ()).min().orElse(worldPosition.getZ());
        int maxX = members.stream().mapToInt(p -> p.worldPosition.getX()).max().orElse(x) + 1;
        int maxZ = members.stream().mapToInt(p -> p.worldPosition.getZ()).max().orElse(z) + 1;
        return new net.minecraft.world.phys.AABB(x, worldPosition.getY() + .25, z, maxX, worldPosition.getY() + .25 + depth, maxZ);
    }
    public byte slot(int i) { return slots[i]; }
    public ReactorFuel fuel(int i) { return ReactorFuel.fromId(fuels[i]); }
    public void setFuel(int i, ReactorFuel value) { fuels[i] = (byte) value.ordinal();setChanged(); }
    public int cellCount() { return cells; }
    public int waterAmount() { return amount; }
    public double waterCelsius() { return waterDeciC / 10d; }
    public int freshCount() { return fresh; }
    public int spentCount() { return spent; }
    public String status() { return status; }
    public float waterLevel() { return cells == 0 ? 0 : amount / (float) (cells * WATER_PER_CELL); }

    public int find(byte state) {
        if (!ready()) return -1;
        for (int i = 0; i < slots.length; i++) if (slots[i] == state) return i;
        return -1;
    }

    public void set(int i, byte state) {
        if (i < 0 || i >= slots.length || state < EMPTY || state > SPENT) throw new IllegalArgumentException("Invalid fuel pool slot");
        slots[i] = state;
        if (state == EMPTY) activity[i] = null;
        if (state != FRESH) fuels[i] = 0;
        setChanged();
        sendData();
    }

    private final IItemHandler port = new IItemHandler() {
        @Override public int getSlots() { return slots.length; }
        @Override public ItemStack getStackInSlot(int i) {
            var stack = slots[i] == FRESH ? new ItemStack(fuel(i).item()) : slots[i] == SPENT ? new ItemStack(CENuclear.SPENT_FUEL_ROD.get()) : ItemStack.EMPTY;
            if (!stack.isEmpty() && activity[i] != null && activity[i].total() > 0) stack.set(net.createplant.registry.CERadiation.MATERIAL.get(), activity[i]);
            return stack;
        }
        @Override public ItemStack insertItem(int i, ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || slots[i] != EMPTY || !isItemValid(i, stack) || !ready()) return stack;
            if (!simulate) {
                var stamped = net.createplant.nuclear.RadiationSources.stamp(stack.copyWithCount(1), level);
                activity[i] = stamped.get(net.createplant.registry.CERadiation.MATERIAL.get());
                setFuel(i, ReactorFuel.of(stack));set(i, CENuclear.isFuel(stack) ? FRESH : SPENT);
            }
            return stack.copyWithCount(stack.getCount() - 1);
        }
        @Override public ItemStack extractItem(int i, int amount, boolean simulate) {
            if (slots[i] != SPENT || amount <= 0) return ItemStack.EMPTY;
            var result = getStackInSlot(i);
            if (!simulate) { activity[i] = null;set(i, EMPTY); }
            return result;
        }
        @Override public int getSlotLimit(int i) { return 1; }
        @Override public boolean isItemValid(int i, ItemStack stack) { return CENuclear.isFuel(stack) || stack.is(CENuclear.SPENT_FUEL_ROD.get()); }
    };

    private record PoolWater(FluidStack fluid, double kelvin) { }

    private static PoolWater sharedWater(List<FuelPoolBlockEntity> parts) {
        FluidStack total = FluidStack.EMPTY;
        double volumeTemperature = 0;
        for (var pool : parts) if (!pool.water.isEmpty()) {
            var fluid = pool.water.getFluid();
            volumeTemperature += pool.water.kelvin() * fluid.getAmount();
            total = total.isEmpty() ? fluid.copy() : FluidThermal.mix(total, fluid, fluid.getAmount());
        }
        double kelvin = total.isEmpty() ? FluidState.kelvin(FluidState.AMBIENT) : volumeTemperature / total.getAmount();
        if (!total.isEmpty()) FluidState.setTemperature(total, FluidState.levelFor(total, kelvin, FluidState.pressure(total), false));
        return new PoolWater(total, kelvin);
    }

    private void storeWater(FluidStack fluid, double kelvin) {
        if (!fluid.isEmpty()) FluidState.setTemperature(fluid, FluidState.levelFor(fluid, kelvin, FluidState.pressure(fluid), false));
        water.setFluid(fluid);
        water.heat().set(fluid, kelvin, 0);
        if (fluid.isEmpty()) boilingJoules = 0;
        setChanged();
    }

    private final IFluidHandler coolant = new IFluidHandler() {
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int tank) { return tank == 0 ? sharedWater(members()).fluid() : FluidStack.EMPTY; }
        @Override public int getTankCapacity(int tank) { return tank == 0 ? members().size() * WATER_PER_CELL : 0; }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return tank == 0 && water.isFluidValid(stack); }
        @Override public int fill(FluidStack stack, FluidAction action) {
            if (isRemoved() || !isFluidValid(0, stack) || !structure().formed()) return 0;
            var parts = members();
            var shared = sharedWater(parts);
            var stored = shared.fluid();
            if (!stored.isEmpty() && !FluidThermal.mixable(stored, stack)) return 0;
            int accepted = Math.max(0, Math.min(stack.getAmount(), parts.size() * WATER_PER_CELL - stored.getAmount()));
            if (accepted > 0 && action.execute()) {
                var mixed = stored.isEmpty() ? stack.copyWithAmount(accepted) : FluidThermal.mix(stored, stack, accepted);
                double kelvin = (shared.kelvin() * stored.getAmount() + Math.min(BOILING_KELVIN, FluidThermal.kelvin(stack)) * accepted) / mixed.getAmount();
                store(parts, mixed, kelvin);
            }
            return accepted;
        }
        @Override public FluidStack drain(FluidStack stack, FluidAction action) {
            var total = getFluidInTank(0);
            return stack.isEmpty() || total.isEmpty() || !FluidThermal.compatible(stack, total) ? FluidStack.EMPTY : drain(stack.getAmount(), action);
        }
        @Override public FluidStack drain(int maximum, FluidAction action) {
            if (maximum <= 0) return FluidStack.EMPTY;
            var parts = members();
            var shared = sharedWater(parts);
            var total = shared.fluid();
            int amount = Math.min(maximum, total.getAmount());
            if (amount == 0) return FluidStack.EMPTY;
            var portion = total.copyWithAmount(amount);
            if (action.execute()) store(parts, total.copyWithAmount(total.getAmount() - amount), shared.kelvin());
            return portion;
        }
        private void store(List<FuelPoolBlockEntity> parts, FluidStack total, double kelvin) {
            if (parts.isEmpty()) return;
            int each = total.getAmount() / parts.size(), remainder = total.getAmount() % parts.size();
            for (var pool : parts) {
                pool.storeWater(total.copyWithAmount(each + (remainder-- > 0 ? 1 : 0)), kelvin);
            }
        }
    };

    @Override public void lazyTick() {
        super.lazyTick();
        if (level == null || level.isClientSide()) return;
        var shape = structure();
        var parts = members();
        if (shape.formed() && shape.cells().getFirst().equals(worldPosition)) {
            int rods = 0, volume = 0;
            for (var pool : parts) {
                for (byte slot : pool.slots) if (slot == SPENT) rods++;
                volume += pool.water.getFluidAmount();
            }
            if (rods > 0 && volume > 0) for (var pool : parts) if (!pool.water.isEmpty() && (pool.lastHeated == Long.MIN_VALUE || level.getGameTime() - pool.lastHeated >= 20)) {
                pool.lastHeated = level.getGameTime();
                double joules = rods * HEAT_PER_ROD * pool.water.getFluidAmount() / volume + pool.boilingJoules;
                double kelvin = pool.water.kelvin(), capacity = FluidThermal.heatCapacity(pool.water.getFluid());
                double warming = Math.min(joules, capacity * Math.max(0, BOILING_KELVIN - kelvin));
                double latent = joules - warming;
                int evaporated = (int) Math.min(pool.water.getFluidAmount(), latent / BOIL_JOULES_PER_MB);
                pool.boilingJoules = latent - evaporated * BOIL_JOULES_PER_MB;
                pool.storeWater(pool.water.getFluid().copyWithAmount(pool.water.getFluidAmount() - evaporated),
                    Math.min(BOILING_KELVIN, kelvin + warming / capacity));
            }
        }
        var shared = sharedWater(parts);
        var fluid = shared.fluid();
        int nextWaterDeciC = (int) Math.round((shared.kelvin() - 273.15) * 10);
        int nextCells = shape.formed() ? parts.size() : 0;
        int nextFresh = 0, nextSpent = 0;
        for (var pool : parts) for (byte slot : pool.slots) if (slot == FRESH) nextFresh++; else if (slot == SPENT) nextSpent++;
        String nextStatus = !shape.formed() ? shape.status() : fluid.getAmount() < nextCells * COVER_PER_CELL ? "water"
            : shared.kelvin() >= HOT_KELVIN ? "hot" : nextSpent > 0 ? "cooling" : "ready";
        if (cells != nextCells || amount != fluid.getAmount() || fresh != nextFresh || spent != nextSpent || waterDeciC != nextWaterDeciC
            || !status.equals(nextStatus) || !shape.fault().equals(fault)) {
            cells = nextCells; amount = fluid.getAmount(); fresh = nextFresh; spent = nextSpent; status = nextStatus; fault = shape.fault();
            waterDeciC = nextWaterDeciC;
            sendData();
        }
    }

    public void dismantle() {
        if (level == null || level.isClientSide()) return;
        releaseReducedGridItems();
        for (int i = 0; i < slots.length; i++) if (slots[i] != EMPTY) {
            Containers.dropItemStack(level, worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5, port.getStackInSlot(i));
            slots[i] = EMPTY;
        }
        // The removed floor opens the basin: its own coolant is released, the other rack tiles keep theirs.
        water.setFluid(FluidStack.EMPTY);
        FuelPoolStructure.invalidate(level);
    }

    /** A reduced rack cannot retain every old position; return their contents to the world instead of deleting them. */
    private void releaseReducedGridItems() {
        if (level == null || level.isClientSide() || reducedFresh + reducedSpent == 0) return;
        double x = worldPosition.getX() + .5, y = worldPosition.getY() + .5, z = worldPosition.getZ() + .5;
        for (int i = 0; i < reducedFresh; i++) Containers.dropItemStack(level, x, y, z, new ItemStack(CENuclear.FUEL_ROD.get()));
        for (int i = 0; i < reducedSpent; i++) Containers.dropItemStack(level, x, y, z, new ItemStack(CENuclear.SPENT_FUEL_ROD.get()));
        reducedFresh = reducedSpent = 0;
        setChanged();
    }

    @Override public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        tooltip.add(Component.translatable("fuel_pool.create_refinery_n_reactors.status." + status));
        tooltip.add(Component.translatable("fuel_pool.create_refinery_n_reactors.rods", fresh, spent, (cells == 0 ? 1 : cells) * slots.length));
        tooltip.add(Component.translatable("fuel_pool.create_refinery_n_reactors.water", amount, (cells == 0 ? 1 : cells) * WATER_PER_CELL,
            amount > 0 ? Component.literal(String.format(Locale.ROOT, "%.1f °C", waterCelsius())) : Component.literal("--")));
        if (cells == 0 && fault != null) tooltip.add(Component.translatable("fuel_pool.create_refinery_n_reactors.fault", fault.getX(), fault.getY(), fault.getZ()));
        return true;
    }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putByteArray("Slots", slots.clone());
        tag.putByteArray("FuelKinds", fuels.clone());
        var radioactivity = new net.minecraft.nbt.ListTag();
        for (var material : activity) radioactivity.add(net.createplant.nuclear.RadiationWorldData.write(material == null ? net.createplant.nuclear.Radionuclides.ZERO : material));
        tag.put("Radioactivity", radioactivity);
        tag.put("PoolWater", water.writeToNBT(registries, new CompoundTag()));
        tag.putDouble("PoolBoiling", boilingJoules);
        if (reducedFresh > 0) tag.putInt("ReducedGridFresh", reducedFresh);
        if (reducedSpent > 0) tag.putInt("ReducedGridSpent", reducedSpent);
        if (clientPacket) {
            tag.putInt("PoolCells", cells); tag.putInt("PoolAmount", amount); tag.putInt("PoolFresh", fresh); tag.putInt("PoolSpent", spent);
            tag.putInt("PoolWaterDeciC", waterDeciC);
            tag.putString("PoolStatus", status);
            if (fault != null) tag.putLong("PoolFault", fault.asLong());
        }
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        byte[] saved = tag.getByteArray("Slots");
        var radioactivity = tag.getList("Radioactivity", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < activity.length; i++) activity[i] = i < radioactivity.size() ? net.createplant.nuclear.RadiationWorldData.read(radioactivity.getCompound(i)) : null;
        byte[] savedFuels = tag.getByteArray(tag.contains("FuelKinds") ? "FuelKinds" : "Mox");
        boolean reducedGrid = saved.length > slots.length;
        int[] sources = reducedGrid ? reducedGridSources(saved.length) : null;
        for (int i = 0; i < slots.length; i++) {
            int source = reducedGrid ? sources[i] : i;
            slots[i] = source < saved.length && saved[source] >= EMPTY && saved[source] <= SPENT ? saved[source] : EMPTY;
            fuels[i] = (byte) ReactorFuel.fromId(source < savedFuels.length ? savedFuels[source] : 0).ordinal();
        }
        if (!clientPacket) {
            reducedFresh = Math.max(0, tag.getInt("ReducedGridFresh"));
            reducedSpent = Math.max(0, tag.getInt("ReducedGridSpent"));
            if (reducedGrid) for (int i = 0; i < saved.length; i++) {
                boolean retained = false;
                for (int source : sources) if (i == source) retained = true;
                if (!retained && saved[i] == FRESH) reducedFresh++;
                else if (!retained && saved[i] == SPENT) reducedSpent++;
            }
        }
        water.readFromNBT(registries, tag.getCompound("PoolWater"));
        if (!water.isEmpty() && (!net.createplant.thermal.FluidState.liquidWater(water.getFluid()) || !FluidThermal.valid(water.getFluid()))) water.setFluid(FluidStack.EMPTY);
        boilingJoules = Double.isFinite(tag.getDouble("PoolBoiling")) ? Math.clamp(tag.getDouble("PoolBoiling"), 0, BOIL_JOULES_PER_MB) : 0;
        if (tag.contains("PoolCells")) {
            cells = tag.getInt("PoolCells"); amount = tag.getInt("PoolAmount"); fresh = tag.getInt("PoolFresh"); spent = tag.getInt("PoolSpent");
            waterDeciC = tag.contains("PoolWaterDeciC") ? tag.getInt("PoolWaterDeciC") : 250;
            status = tag.getString("PoolStatus"); fault = tag.contains("PoolFault") ? BlockPos.of(tag.getLong("PoolFault")) : null;
        }
    }
}
