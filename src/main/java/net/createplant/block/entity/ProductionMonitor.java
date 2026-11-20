package net.createplant.block.entity;

import java.util.Arrays;
import java.util.List;
import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.api.event.BlockEntityBehaviourEvent;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createplant.CreatePlant;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.fluids.FluidStack;

/** Samples committed output in game time and synchronizes only the resulting rates. */
public final class ProductionMonitor extends BlockEntityBehaviour {
    public enum Output { FLUID, ITEMS, POWER }
    public record Rates(double current, double maximum) { }

    private static final BehaviourType<ProductionMonitor> TYPE = new BehaviourType<>();
    private static final int SECONDS = 20, CHANNELS = Output.values().length;
    private static final class Types {
        private static final List<BlockEntityType<?>> INCLUDED = BuiltInRegistries.BLOCK_ENTITY_TYPE.stream()
            .filter(ProductionMonitor::tracked).toList();
    }

    private double[] maximum, current, lastMaximum, lastCurrent;
    private double[][] buckets;
    private long second = Long.MIN_VALUE;
    private int sampled;
    private String limit = "", lastLimit = "";

    private ProductionMonitor(SmartBlockEntity owner) { super(owner); }

    public static void attach(BlockEntityBehaviourEvent event) {
        for (var type : Types.INCLUDED) attach(event, type);
    }

    @SuppressWarnings("unchecked")
    private static void attach(BlockEntityBehaviourEvent event, BlockEntityType<?> type) {
        event.forType((BlockEntityType<SmartBlockEntity>) type, owner -> event.attach(new ProductionMonitor(owner)));
    }

    private static boolean tracked(BlockEntityType<?> type) {
        if (type == AllBlockEntityTypes.FLUID_TANK.get()) return true;
        var id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
        if (id == null || !id.getNamespace().equals(CreatePlant.MOD_ID)) return false;
        return switch (id.getPath()) {
            case "horizontal_fluid_tank", "stainless_boiler", "entrained_gasifier", "lidded_vessel", "reactor_machine", "reaction_tower", "ammonia_separator", "fractionating_column",
                "electrolysis_cell_controller", "electrolysis_furnace", "wet_process_drive", "gas_centrifuge", "cubic_press", "cryogenic_cold_box",
                "steam_turbine", "reciprocating_compressor", "fuel_cell", "natural_draft_cooling_tower", "condenser_waterbox",
                "fuel_cycle_machine", "converter_trunnion_bearing", "cooler_radiator",
                "absorption_tower", "fusion_reactor_controller", "reactor_fuel_channel", "gas_recovery_duct" -> true;
            default -> false;
        };
    }

    @Override public BehaviourType<?> getType() { return TYPE; }
    public static ProductionMonitor of(BlockEntity owner) { return owner == null ? null : BlockEntityBehaviour.get(owner, TYPE); }

    public static void plan(BlockEntity owner, double fluidPerSecond, double itemsPerSecond, double kiloWatts) {
        var meter = of(owner);
        if (meter == null || owner.getLevel() == null || owner.getLevel().isClientSide) return;
        if (meter.maximum == null) {
            meter.maximum = new double[CHANNELS];
            meter.current = new double[CHANNELS];
            Arrays.fill(meter.current, -1);
            meter.buckets = new double[CHANNELS][SECONDS + 1];
            meter.second = Math.floorDiv(owner.getLevel().getGameTime(), 20);
        }
        meter.maximum[0] = Math.max(0, fluidPerSecond);
        meter.maximum[1] = Math.max(0, itemsPerSecond);
        meter.maximum[2] = Math.max(0, kiloWatts);
    }

    public static void produced(BlockEntity owner, double fluid, double items, double joules) {
        var meter = of(owner);
        if (meter == null || meter.buckets == null) return;
        meter.advance();
        int index = Math.floorMod(meter.second, SECONDS + 1);
        meter.buckets[0][index] += Math.max(0, fluid);
        meter.buckets[1][index] += Math.max(0, items);
        meter.buckets[2][index] += Math.max(0, joules) / 1000;
    }

    public static void limitedBy(BlockEntity owner, String reason) {
        var meter = of(owner);
        if (meter != null && owner.getLevel() != null && !owner.getLevel().isClientSide) meter.limit = reason;
    }

    public Rates read(Output output) {
        int index = output.ordinal();
        if (maximum == null || maximum[index] <= 0 || !Double.isFinite(maximum[index])) return null;
        return new Rates(current[index], maximum[index]);
    }

    public String limit() { return limit; }

    public static double batchRate(double amount, int parallel, double ticks, double speed) {
        return amount * parallel * 20 / Math.max(1, Math.ceil(ticks / speed));
    }

    public static int fluids(List<FluidStack> stacks) { return stacks.stream().mapToInt(FluidStack::getAmount).sum(); }
    public static int items(List<ItemStack> stacks) { return stacks.stream().mapToInt(ItemStack::getCount).sum(); }

    private void advance() {
        long now = Math.floorDiv(getWorld().getGameTime(), 20);
        if (now == second) return;
        if (now < second || now - second > SECONDS) {
            for (var bucket : buckets) Arrays.fill(bucket, 0);
            sampled = 0;
        } else {
            for (long next = second + 1; next <= now; next++) {
                int index = Math.floorMod(next, SECONDS + 1);
                for (var bucket : buckets) bucket[index] = 0;
            }
            sampled = Math.min(SECONDS, sampled + (int) (now - second));
        }
        second = now;
        for (int channel = 0; channel < CHANNELS; channel++) {
            double total = 0;
            for (double value : buckets[channel]) total += value;
            current[channel] = sampled == 0 ? -1 : total / sampled;
        }
    }

    @Override public void tick() {
        if (getWorld() == null || getWorld().isClientSide || buckets == null && limit.isEmpty()) return;
        if (Math.floorMod(getWorld().getGameTime() + getPos().asLong(), 20) != 0) return;
        if (buckets != null) advance();
        if (Arrays.equals(maximum, lastMaximum) && Arrays.equals(current, lastCurrent) && limit.equals(lastLimit)) return;
        lastMaximum = maximum == null ? null : maximum.clone();
        lastCurrent = current == null ? null : current.clone();
        lastLimit = limit;
        blockEntity.sendData();
    }

    @Override public void write(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        if (!packet || maximum == null && limit.isEmpty()) return;
        var data = new CompoundTag();
        for (int i = 0; maximum != null && i < CHANNELS; i++) {
            data.putDouble("Maximum" + i, maximum[i]);
            data.putDouble("Current" + i, current[i]);
        }
        data.putString("Limit", limit);
        tag.put("Production", data);
    }

    @Override public void read(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        if (!packet) return;
        if (!tag.contains("Production")) {
            maximum = current = null;
            limit = "";
            return;
        }
        var data = tag.getCompound("Production");
        if (maximum == null) {
            maximum = new double[CHANNELS];
            current = new double[CHANNELS];
        }
        for (int i = 0; i < CHANNELS; i++) {
            maximum[i] = data.getDouble("Maximum" + i);
            current[i] = data.getDouble("Current" + i);
        }
        limit = data.getString("Limit");
    }
}
