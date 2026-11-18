package net.createplant.block.entity;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.List;
import net.createplant.block.FlowRegulatorBlock;
import net.createplant.block.ProcessControlBlock;
import net.createplant.registry.CEPlantSystems;
import net.createplant.thermal.PidLoop;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;

public class ProcessControlBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    public static final int ACTUATED_FLOW = 262_144;
    private final PidLoop pid = new PidLoop();
    private double target = 50, kp = 2, ti = 10, td, measurement, output;
    private boolean automatic = true, reverse, valid;
    private long received = Long.MIN_VALUE;
    private int clock;
    private String status = "signal";
    public ProcessControlBlockEntity(BlockPos pos, BlockState state) { super(CEPlantSystems.CONTROL_ENTITY.get(), pos, state); }
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) { }
    public ProcessControlBlock.Kind kind() { return ((ProcessControlBlock) getBlockState().getBlock()).kind; }
    public double measurement() { return measurement; }
    public double output() { return output; }
    public double target() { return target; }
    public double kp() { return kp; }
    public double ti() { return ti; }
    public double td() { return td; }
    public boolean reverse() { return reverse; }
    public boolean valid() { return valid; }
    public String status() { return status; }
    public BlockPos attachment() { return worldPosition.relative(getBlockState().getValue(ProcessControlBlock.FACING).getOpposite()); }
    public boolean assembled() {
        return level != null && level.getBlockState(worldPosition.above()).is(CEPlantSystems.PID_RACK.get())
            && level.getBlockState(worldPosition.above()).getValue(BlockStateProperties.HORIZONTAL_FACING) == getBlockState().getValue(ProcessControlBlock.FACING);
    }
    public void receive(double measuredPercent) {
        if (level == null || level.isClientSide() || !Double.isFinite(measuredPercent)) return;
        measurement = Math.clamp(measuredPercent, 0, 100);received = level.getGameTime();
    }
    @Override public void tick() {
        super.tick();
        if (level == null || level.isClientSide()) return;
        clock++;
        if (kind() == ProcessControlBlock.Kind.ACTUATOR) {
            valid = level.getBlockState(attachment()).getBlock() instanceof FlowRegulatorBlock;
            int signal = valid ? level.getBestNeighborSignal(worldPosition) : 0;
            output = signal / 15d * 100;status = valid ? "actuator" : "structure";indicate(signal);
        } else if (clock % 5 == 0) {
            if (kind() == ProcessControlBlock.Kind.INSTRUMENT) sample();
            else {
                valid = assembled() && received >= level.getGameTime() - 40;
                if (!valid) { output = 0;status = assembled() ? "signal" : "structure"; }
                else { if (automatic) output = pid.step(target, measurement, kp, ti, td, reverse, .25);status = automatic ? "automatic" : "manual"; }
            }
            indicate(valid ? (int) Math.round(output * .15) : 0);
            setChanged();if (clock % 20 == 0) sendData();
        }
    }
    public void sample() {
        BlockPos at = attachment();valid = false;
        if (!level.hasChunkAt(at)) { status = "unloaded";return; }
        if (level.getBlockEntity(at) instanceof LiddedVesselBlockEntity tank) {
            var controller = tank.getControllerBE();
            if (controller != null) {
                var inventory = controller.getTankInventory();
                measurement = inventory.getFluidAmount() * 100d / Math.max(1, inventory.getCapacity());valid = true;
            }
        } else if (level.getBlockEntity(at) instanceof FluidTankBlockEntity tank) {
            var controller = tank.getControllerBE();
            if (controller != null) {
                var inventory = controller.getTankInventory();
                measurement = inventory.getFluidAmount() * 100d / Math.max(1, inventory.getCapacity());valid = true;
            }
        } else {
            var handler = level.getCapability(Capabilities.FluidHandler.BLOCK, at, getBlockState().getValue(ProcessControlBlock.FACING));
            if (handler != null && handler.getTanks() > 0 && handler.getTankCapacity(0) > 0) {
                measurement = handler.getFluidInTank(0).getAmount() * 100d / handler.getTankCapacity(0);valid = true;
            }
        }
        output = valid ? normalized() : 0;status = valid ? "measuring" : "signal";
    }
    public double normalized() { return Math.clamp(measurement, 0, 100); }
    private void indicate(int signal) {
        BlockState state = getBlockState();
        if (state.getValue(ProcessControlBlock.INDICATOR) == signal) return;
        level.setBlock(worldPosition, state.setValue(ProcessControlBlock.INDICATOR, signal), 3);
        level.updateNeighbourForOutputSignal(worldPosition, state.getBlock());
    }
    public void operate(Player player, BlockHitResult hit) {
        int direction = player.isShiftKeyDown() ? -1 : 1;
        if (kind() == ProcessControlBlock.Kind.PID) {
            Direction facing = getBlockState().getValue(ProcessControlBlock.FACING);
            double horizontal = facing.getAxis() == Direction.Axis.Z ? hit.getLocation().x - worldPosition.getX() : hit.getLocation().z - worldPosition.getZ();
            if (facing == Direction.SOUTH || facing == Direction.WEST) horizontal = 1 - horizontal;
            int column = Math.clamp((int) (horizontal * 3), 0, 2);
            boolean upper = hit.getBlockPos().getY() > worldPosition.getY();
            int control = column + (upper ? 0 : 3);
            switch (control) {
                case 0 -> target = Math.clamp(target + direction, 0, 100);
                case 1 -> kp = Math.clamp(Math.round((kp + direction * .1) * 10) / 10d, 0, 20);
                case 2 -> ti = Math.clamp(ti + direction, 0, 120);
                case 3 -> td = Math.clamp(td + direction * .25, 0, 30);
                case 4 -> reverse = !reverse;
                case 5 -> { automatic = !automatic;pid.track(output, target, measurement, kp, reverse); }
                default -> { }
            }
            if (!automatic && control == 0) output = target;
        }
        setChanged();sendData();
        player.displayClientMessage(Component.translatable("plant_systems.control.settings", Math.round(target), kp, ti, td, reverse, automatic), true);
    }
    @Override protected void write(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        super.write(tag, lookup, packet);
        tag.putDouble("Target", target);tag.putDouble("Kp", kp);tag.putDouble("Ti", ti);tag.putDouble("Td", td);
        tag.putDouble("Measurement", measurement);tag.putDouble("Output", output);
        tag.putDouble("Integral", pid.integral());tag.putBoolean("Auto", automatic);tag.putBoolean("Reverse", reverse);
        tag.putBoolean("Valid", valid);tag.putString("Status", status);
    }
    @Override protected void read(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        super.read(tag, lookup, packet);
        target = finite(tag, "Target", 50, 100);kp = finite(tag, "Kp", 2, 20);ti = finite(tag, "Ti", 10, 120);td = finite(tag, "Td", 0, 30);
        measurement = finite(tag, "Measurement", 0, 1000);output = finite(tag, "Output", 0, 100);
        pid.restore(tag.getDouble("Integral"));automatic = !tag.contains("Auto") || tag.getBoolean("Auto");reverse = tag.getBoolean("Reverse");
        valid = packet && tag.getBoolean("Valid");status = tag.getString("Status");received = Long.MIN_VALUE;
    }
    private static double finite(CompoundTag tag, String key, double fallback, double max) {
        double value = tag.contains(key) ? tag.getDouble(key) : fallback;return Double.isFinite(value) ? Math.clamp(value, 0, max) : fallback;
    }
    @Override public boolean addToGoggleTooltip(List<Component> text, boolean sneaking) {
        text.add(Component.translatable("plant_systems.control.state", Component.translatable("plant_systems.status." + status)));
        text.add(Component.translatable("plant_systems.control.reading", valid ? String.format(java.util.Locale.ROOT, "%.2f", measurement) : "--", "%", Math.round(output)));
        if (kind() == ProcessControlBlock.Kind.PID) {
            text.add(Component.translatable("plant_systems.control.tuning", Math.round(target), kp, ti, td));
            text.add(Component.translatable("plant_systems.control.action", reverse, automatic));
        }
        return true;
    }
}
