package net.createplant.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.tank.CreativeFluidTankBlockEntity;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import net.createplant.registry.CETransport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import org.slf4j.Logger;

/**
 * Builds a development playtest plant with real Create pipes, pumps, motors, tanks and signs: stainless lines between machine ports that never
 * join a neighbour by accident, pumps driven through cogs, stores for side products, and the checks that the lines do not wear.
 */
public final class PlaytestPlant {
    public static final int RPM = 128, PUMP_SPACING = 14;
    public final List<BlockPos> LINES = new ArrayList<>(), PUMPS = new ArrayList<>();
    public final List<BlockPos[]> DRIVES = new ArrayList<>();
    public final List<Object[]> ROUTES = new ArrayList<>();
    private final ServerLevel level;
    private final int ground;

    public PlaytestPlant(ServerLevel level, int ground) {
        this.level = level;
        this.ground = ground;
    }

    /** Fits every pump laid so far with its cog and motor; called once the lines are laid so the drives keep clear of them. */
    public void driveAll() { for (BlockPos pump : PUMPS) drive(pump); }

    /** A cog or belt train sets each drive's turning direction; reverses the motor of any drive the test says runs backwards. */
    public void correctDrives(BiPredicate<BlockPos, Float> wrongWay) {
        for (BlockPos[] pair : DRIVES) {
            var driven = level.getBlockEntity(pair[0]) instanceof KineticBlockEntity kinetic ? kinetic : null;
            if (driven != null && wrongWay.test(pair[0], driven.getSpeed()) && level.getBlockEntity(pair[1]) instanceof CreativeMotorBlockEntity motor)
                motor.generatedSpeed.setValue(-motor.generatedSpeed.getValue());
        }
    }

    /** Fails on the first line block that has started to wear from what it carries. */
    public void requireNoWear() {
        for (var pos : LINES) {
            var wear = level.getBlockEntity(pos) instanceof com.simibubi.create.foundation.blockEntity.SmartBlockEntity smart ? smart.getBehaviour(net.createplant.transport.ContainmentWear.TYPE) : null;
            require(wear == null || wear.wear() == 0, "The line at " + pos.toShortString() + " wears: " + wear.reason());
        }
    }

    /** Logs what waits at each line's source and target, for following a stalled plant. */
    public void logLines(Logger log, String tag) {
        for (int r = 0; r < ROUTES.size(); r++) {
            @SuppressWarnings("unchecked") var cells = (List<BlockPos>) ROUTES.get(r)[0];
            Direction from = (Direction) ROUTES.get(r)[1], into = (Direction) ROUTES.get(r)[2];
            var source = level.getCapability(Capabilities.FluidHandler.BLOCK, cells.getFirst().relative(from.getOpposite()), from);
            var target = level.getCapability(Capabilities.FluidHandler.BLOCK, cells.getLast().relative(into), into.getOpposite());
            log.info("{} line {} {}: source={} target={}", tag, r, cells.getFirst().toShortString(), source == null ? "-" : describeAll(source), target == null ? "-" : describeAll(target));
        }
    }

    public static String describeAll(net.neoforged.neoforge.fluids.capability.IFluidHandler handler) {
        var parts = new ArrayList<String>();
        for (int t = 0; t < handler.getTanks(); t++) parts.add(handler.getFluidInTank(t).isEmpty() ? "-" : handler.getFluidInTank(t).getAmount() + " " + BuiltInRegistries.FLUID.getKey(handler.getFluidInTank(t).getFluid()).getPath());
        return parts.toString();
    }

    public static String describe(FluidStack stack) {
        return stack.isEmpty() ? "empty" : stack.getAmount() + " " + BuiltInRegistries.FLUID.getKey(stack.getFluid()) + " " + stack.getComponentsPatch();
    }

    public int amount(BlockPos pos, Direction face, Fluid fluid) {
        var handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, face);
        int total = 0;
        if (handler != null) for (int tank = 0; tank < handler.getTanks(); tank++)
            if (fluid == null || handler.getFluidInTank(tank).is(fluid)) total += handler.getFluidInTank(tank).getAmount();
        return total;
    }

    public void supply(BlockPos pos, FluidStack stack) {
        var tank = ((FluidTankBlockEntity) level.getBlockEntity(pos)).getControllerBE();
        ((CreativeFluidTankBlockEntity.CreativeSmartFluidTank) tank.getTankInventory()).setContainedFluid(stack);
    }

    /**
     * A stainless line from a port: it leaves through {@code from} at {@code first}, rises one block where a pump turned by a cog and motor sits,
     * runs straight between the waypoints and enters the last one's neighbour through {@code into}. Returns the pipe positions.
     */
    public List<BlockPos> route(Direction from, BlockPos first, List<BlockPos> waypoints, Direction into) {
        return route(from, first, waypoints, into, -1);
    }

    /** As above, with the first pump at the given cell instead of the first rise. */
    public List<BlockPos> route(Direction from, BlockPos first, List<BlockPos> waypoints, Direction into, int firstPump) {
        var cells = new ArrayList<BlockPos>();
        cells.add(first);
        BlockPos at = first;
        for (BlockPos target : waypoints) {
            require(at.getX() == target.getX() && at.getY() == target.getY() || at.getX() == target.getX() && at.getZ() == target.getZ()
                || at.getY() == target.getY() && at.getZ() == target.getZ(), "A straight segment from " + at.toShortString() + " to " + target.toShortString());
            while (!at.equals(target)) {
                at = at.offset(Integer.signum(target.getX() - at.getX()), Integer.signum(target.getY() - at.getY()), Integer.signum(target.getZ() - at.getZ()));
                cells.add(at);
            }
        }
        // The pump stands on the first vertical step, or on the second cell when the line starts level.
        int pump = 1;
        if (firstPump > 0) pump = firstPump;
        else for (int i = 1; i < cells.size() - 1; i++) if (cells.get(i).getY() != cells.get(i - 1).getY()) { pump = i; break; }
        for (int i = 0; i < cells.size(); i++) {
            BlockPos cell = cells.get(i);
            Direction back = i == 0 ? from.getOpposite() : direction(cell, cells.get(i - 1));
            Direction ahead = i == cells.size() - 1 ? into : direction(cell, cells.get(i + 1));
            require(level.getBlockState(cell).isAir(), "The line crosses " + level.getBlockState(cell) + " at " + cell.toShortString());
            // Create joins an open pipe to any neighbouring pipe or fluid port; where a line passes one, it runs in an encased pipe open only along the line.
            boolean crowded = false;
            for (Direction side : Direction.values()) {
                if (side == back || side == ahead) continue;
                BlockPos beside = cell.relative(side);
                crowded |= LINES.contains(beside) || level.getBlockState(beside).getBlock() instanceof com.simibubi.create.content.fluids.pipes.FluidPipeBlock
                    || level.getCapability(Capabilities.FluidHandler.BLOCK, beside, side.getOpposite()) != null;
            }
            // Create's pumps reach 16 blocks, so a long line carries another pump every 14.
            if (i >= pump && back == ahead.getOpposite() && i != cells.size() - 1) {
                put(cell, BuiltInRegistries.BLOCK.get(net.createplant.CreatePlant.location("stainless_steel_pump")).defaultBlockState().setValue(BlockStateProperties.FACING, ahead));
                PUMPS.add(cell);
                pump = i + PUMP_SPACING;
            }
            else if (crowded) {
                BlockState state = CETransport.STAINLESS_ENCASED_PIPE.get().defaultBlockState();
                for (Direction side : Direction.values())
                    state = state.setValue(com.simibubi.create.content.fluids.pipes.EncasedPipeBlock.FACING_TO_PROPERTY_MAP.get(side), side == back || side == ahead);
                put(cell, state);
            } else {
                BlockState state = CETransport.STAINLESS_PIPE.get().defaultBlockState();
                for (Direction side : Direction.values()) state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side), side == back || side == ahead);
                put(cell, state);
            }
            LINES.add(cell);
        }
        ROUTES.add(new Object[] {cells, from, into});
        return cells;
    }

    private static Direction direction(BlockPos from, BlockPos to) {
        return Direction.fromDelta(to.getX() - from.getX(), to.getY() - from.getY(), to.getZ() - from.getZ());
    }

    /** Turns a placed pump through a cog beside it and a motor beyond the cog, once every line is laid. */
    private void drive(BlockPos pos) {
        var axis = level.getBlockState(pos).getValue(BlockStateProperties.FACING).getAxis();
        for (Direction side : Direction.values()) {
            if (side.getAxis() == axis) continue;
            BlockPos cog = pos.relative(side);
            // The motor sits on the cog's own axis, which is the pump's.
            for (var along : new Direction[] {Direction.get(Direction.AxisDirection.POSITIVE, axis), Direction.get(Direction.AxisDirection.NEGATIVE, axis)}) {
                BlockPos motor = cog.relative(along);
                if (!level.getBlockState(cog).isAir() || !level.getBlockState(motor).isAir()) continue;
                put(cog, AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS, axis));
                motor(motor, along.getOpposite());
                DRIVES.add(new BlockPos[] {pos, motor});
                return;
            }
        }
        throw new IllegalStateException("No room to drive the pump at " + pos.toShortString());
    }

    public enum Kind { LIQUID, GAS, ANY }

    /** A two-block store beside a line end that the pipe opens into: a gas tank container for gases, a Create tank for liquids, a stainless tank for either. */
    public void store(BlockPos pipe, Direction side, String name, boolean gas) { store(pipe, side, name, gas ? Kind.GAS : Kind.LIQUID); }

    public void store(BlockPos pipe, Direction side, String name, Kind kind) {
        BlockPos at = pipe.relative(side);
        var state = level.getBlockState(pipe);
        if (state.hasProperty(PipeBlock.PROPERTY_BY_DIRECTION.get(side))) put(pipe, state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side), true));
        var tank = switch (kind) {
            case GAS -> CETransport.GAS_TANK_CONTAINER.get().defaultBlockState();
            case LIQUID -> AllBlocks.FLUID_TANK.getDefaultState();
            case ANY -> CETransport.STAINLESS_STEEL_TANK.get().defaultBlockState();
        };
        put(at, tank);
        put(at.relative(side == Direction.DOWN ? Direction.DOWN : Direction.UP), tank);
        sign(at.relative(side.getAxis() == Direction.Axis.Y ? Direction.NORTH : side.getClockWise()), name);
    }

    public void burner(BlockPos pos, BlazeBurnerBlock.HeatLevel heat) {
        put(pos, AllBlocks.BLAZE_BURNER.getDefaultState().setValue(BlazeBurnerBlock.HEAT_LEVEL, heat));
        if (level.getBlockEntity(pos) instanceof BlazeBurnerBlockEntity burner) burner.isCreative = true;
    }

    public void motor(BlockPos pos, Direction facing) { motor(pos, facing, RPM); }

    public void motor(BlockPos pos, Direction facing, int rpm) {
        put(pos, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, facing));
        ((CreativeMotorBlockEntity) level.getBlockEntity(pos)).generatedSpeed.setValue(rpm);
    }

    public static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    public void put(BlockPos pos, BlockState state) { level.setBlock(pos, state, Block.UPDATE_ALL); }

    /** A sign on the ground below the given place, where it stands without support. */
    public void sign(BlockPos at, String label) {
        BlockPos pos = at.atY(ground);
        while (!level.getBlockState(pos).isAir() && pos.getY() < at.getY() + 8) pos = pos.above();
        if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.below()).isSolid()) return;
        put(pos, Blocks.OAK_SIGN.defaultBlockState());
        if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) sign.setText(sign.getFrontText().setMessage(0, Component.literal(label)), true);
    }
}
