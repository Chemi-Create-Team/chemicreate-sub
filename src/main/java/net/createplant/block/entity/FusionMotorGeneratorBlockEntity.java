package net.createplant.block.entity;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import java.util.List;
import java.util.Locale;
import net.createplant.block.FusionMotorGeneratorBlock;
import net.createplant.block.LargeFlywheelBlock;
import net.createplant.registry.CEFusion;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Converts Create shaft work into the tokamak's internal pulsed electrical feed at one SU per mechanical watt. */
public class FusionMotorGeneratorBlockEntity extends KineticBlockEntity implements IHaveGoggleInformation {
    public static final double RATED_RPM = 256;
    public static final double MAX_ELECTRICAL_WATTS = 120_000_000;
    public static final double EFFICIENCY = .95;
    public static final double INTERNAL_VOLTS = 20_000;
    private static final String KEY = "fusion_motor_generator.create_refinery_n_reactors.";

    private double electricalWatts;
    private float stressPerRpm;
    private float publishedStress = -1;
    private String status = "assembly";

    public FusionMotorGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(CEFusion.MOTOR_GENERATOR_ENTITY.get(), pos, state);
    }

    @Override public void tick() {
        super.tick();
        if (level == null || level.isClientSide()) return;

        FusionReactorControllerBlockEntity controller = controller();
        boolean assembled = controller != null && hasFlywheel(controller);
        double demand = assembled ? Math.min(MAX_ELECTRICAL_WATTS, controller.electricalDemandWatts()) : 0;
        float nextStress = demand <= 0 ? 0 : (float) (demand / EFFICIENCY / RATED_RPM);
        if (nextStress != stressPerRpm) {
            stressPerRpm = nextStress;
            publishStress();
        }

        double rpm = Math.min(RATED_RPM, Math.abs(getSpeed()));
        electricalWatts = !assembled || isOverStressed() ? 0 : Math.min(demand, rpm * stressPerRpm * EFFICIENCY);
        if (controller == null) status = "controller";
        else if (!hasFlywheel(controller)) status = "flywheel";
        else if (demand <= 0) status = "idle";
        else if (isOverStressed()) status = "overload";
        else if (rpm < 1) status = "stopped";
        else status = "running";
        if (controller != null) controller.supplyElectrical(electricalWatts, INTERNAL_VOLTS);

        boolean active = electricalWatts > 0;
        BlockState state = getBlockState();
        if (state.getValue(FusionMotorGeneratorBlock.ACTIVE) != active)
            level.setBlock(worldPosition, state.setValue(FusionMotorGeneratorBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        if (level.getGameTime() % 10 == 0) { setChanged(); sendData(); }
    }

    private void publishStress() {
        if (!hasNetwork() || publishedStress == stressPerRpm) return;
        publishedStress = stressPerRpm;
        KineticNetwork network = getOrCreateNetwork();
        network.updateStressFor(this, calculateStressApplied());
    }

    public FusionReactorControllerBlockEntity controller() {
        if (level == null) return null;
        Direction.Axis axis = getBlockState().getValue(FusionMotorGeneratorBlock.AXIS);
        for (Direction.AxisDirection sign : Direction.AxisDirection.values()) {
            Direction side = Direction.fromAxisAndDirection(axis, sign);
            BlockPos pos = worldPosition.relative(side);
            if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof FusionReactorControllerBlockEntity controller)
                return controller;
        }
        return null;
    }

    private boolean hasFlywheel(FusionReactorControllerBlockEntity controller) {
        Direction towardController = controllerDirection();
        if (towardController == null) return false;
        BlockPos flywheelPos = worldPosition.relative(towardController.getOpposite());
        if (!level.hasChunkAt(flywheelPos) || !(level.getBlockEntity(flywheelPos) instanceof LargeFlywheelBlockEntity)) return false;
        BlockState flywheel = level.getBlockState(flywheelPos);
        return flywheel.getBlock() instanceof LargeFlywheelBlock
            && flywheel.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS)
                == getBlockState().getValue(FusionMotorGeneratorBlock.AXIS);
    }

    private Direction controllerDirection() {
        if (level == null) return null;
        Direction.Axis axis = getBlockState().getValue(FusionMotorGeneratorBlock.AXIS);
        for (Direction.AxisDirection sign : Direction.AxisDirection.values()) {
            Direction side = Direction.fromAxisAndDirection(axis, sign);
            BlockPos pos = worldPosition.relative(side);
            if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof FusionReactorControllerBlockEntity) return side;
        }
        return null;
    }

    @Override public float calculateStressApplied() {
        lastStressApplied = stressPerRpm;
        return stressPerRpm;
    }

    public double electricalWatts() { return electricalWatts; }
    public String status() { return status; }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean packet) {
        super.write(tag, registries, packet);
        tag.putDouble("ElectricalWatts", electricalWatts);
        tag.putFloat("StressPerRpm", stressPerRpm);
        tag.putString("Status", status);
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean packet) {
        super.read(tag, registries, packet);
        electricalWatts = finite(tag.getDouble("ElectricalWatts"), 0, MAX_ELECTRICAL_WATTS);
        stressPerRpm = (float) finite(tag.getFloat("StressPerRpm"), 0, MAX_ELECTRICAL_WATTS / EFFICIENCY);
        status = tag.contains("Status") ? tag.getString("Status") : "assembly";
    }

    private static double finite(double value, double low, double high) {
        return Double.isFinite(value) ? Math.clamp(value, low, high) : low;
    }

    @Override public boolean addToGoggleTooltip(List<Component> text, boolean sneaking) {
        text.add(Component.translatable(KEY + "status", Component.translatable(KEY + "status." + status)));
        text.add(Component.translatable(KEY + "power", format(electricalWatts / 1e6), format(MAX_ELECTRICAL_WATTS / 1e6))
            .withStyle(electricalWatts > 0 ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY));
        text.add(Component.translatable(KEY + "speed", format(Math.abs(getSpeed())), format(RATED_RPM)));
        text.add(Component.translatable(KEY + "efficiency", format(EFFICIENCY * 100)));
        return true;
    }

    private static String format(double value) { return String.format(Locale.ROOT, "%.1f", value); }
}
