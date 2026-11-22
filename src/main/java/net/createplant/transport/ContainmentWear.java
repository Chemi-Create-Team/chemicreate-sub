package net.createplant.transport;

import java.util.ArrayList;
import java.util.List;
import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.api.event.BlockEntityBehaviourEvent;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createplant.registry.CETransport;
import net.createplant.safety.ToxicGas;
import net.createplant.thermal.TankGrade;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Wear from a fluid that a pipe, pump or tank is not rated for. The fluid is let in; the walls crack over a minute and then burst,
 * letting the contents out. Once the fluid is gone the cracks close again at the same pace.
 */
public final class ContainmentWear extends BlockEntityBehaviour {
    public static final BehaviourType<ContainmentWear> TYPE = new BehaviourType<>();
    public static final int LIFETIME = 1200;
    private static final int INTERVAL = 10, RESEND = 200, PIPE_LEAK = 100;
    private int wear, countdown, shownStage = -1, sinceShown;
    private String reason = "";

    private ContainmentWear(SmartBlockEntity owner) {
        super(owner);
        countdown = Math.floorMod(owner.getBlockPos().hashCode(), INTERVAL);
    }

    public static void attach(BlockEntityBehaviourEvent event) {
        for (var type : List.of(AllBlockEntityTypes.FLUID_PIPE.get(), AllBlockEntityTypes.GLASS_FLUID_PIPE.get(), AllBlockEntityTypes.ENCASED_FLUID_PIPE.get(),
                AllBlockEntityTypes.SMART_FLUID_PIPE.get(), CETransport.PIPE.get(),
                CETransport.STAINLESS_PIPE_ENTITY.get(), CETransport.STAINLESS_ENCASED_PIPE_ENTITY.get(), CETransport.STAINLESS_GLASS_PIPE_ENTITY.get(),
                AllBlockEntityTypes.MECHANICAL_PUMP.get(), CETransport.MATERIAL_PUMP_ENTITY.get(),
                AllBlockEntityTypes.FLUID_TANK.get(), CETransport.PRESSURE_TANK_ENTITY.get(), CETransport.STAINLESS_STEEL_TANK_ENTITY.get(),
                CETransport.CRYOGENIC_TANK_ENTITY.get(), CETransport.GAS_RECEIVER_ENTITY.get(), CETransport.HORIZONTAL_FLUID_TANK_ENTITY.get(),
                CETransport.GAS_TANK_CONTAINER_ENTITY.get()))
            event.forType(type, owner -> event.attach(new ContainmentWear(owner)));
    }

    @Override public BehaviourType<?> getType() { return TYPE; }
    public int wear() { return wear; }
    public String reason() { return reason; }

    @Override public void tick() {
        if (!(getWorld() instanceof ServerLevel level) || ++countdown < INTERVAL) return;
        countdown = 0;
        if (blockEntity instanceof FluidTankBlockEntity tank && !tank.isController()) return;
        FluidStack fluid = FluidStack.EMPTY;
        String next = "";
        if (blockEntity instanceof FluidTankBlockEntity tank) {
            var grade = TankGrade.of(tank);
            fluid = tank.getTankInventory().getFluid();
            String problem = grade == null || fluid.isEmpty() ? "safe" : grade.incompatibility(fluid);
            if (!problem.equals("safe") && !problem.equals("fluid")) next = problem.equals("corrosion") ? name(grade.attacking(fluid)) : problem;
        } else {
            var material = PipeMaterial.carrier(blockEntity.getBlockState());
            var transport = blockEntity.getBehaviour(FluidTransportBehaviour.TYPE);
            if (material != null && transport != null) for (var side : Direction.values()) {
                var flow = transport.getFlow(side);
                if (flow == null || flow.fluid.isEmpty()) continue;
                String problem = material.problem(flow.fluid);
                if (!problem.equals("safe")) { fluid = flow.fluid; next = problem.equals("corrosion") ? name(material.attacking(fluid)) : problem; break; }
            }
        }
        if (next.isEmpty() && wear == 0) {
            if (!reason.isEmpty()) { reason = "";blockEntity.sendData(); }
            return;
        }
        wear = Math.clamp(wear + (next.isEmpty() ? -INTERVAL : INTERVAL), 0, LIFETIME);
        if (wear >= LIFETIME) { burst(level, fluid);return; }
        int stage = wear == 0 ? -1 : wear * 10 / LIFETIME;
        sinceShown += INTERVAL;
        if (stage != shownStage || stage >= 0 && sinceShown >= RESEND) show(level, stage);
        if (stage != shownStage || !next.equals(reason)) { reason = next;shownStage = stage;blockEntity.sendData(); }
        blockEntity.setChanged();
    }

    /** A corrosion's reason names the kind, so the warning says which fluid the walls do not resist. */
    private static String name(FluidHazards.Corrosion corrosion) { return corrosion.name().toLowerCase(java.util.Locale.ROOT); }

    /** Vanilla's breaking cracks over every block of the vessel; a client drops them unless they are sent again within 400 ticks. */
    private void show(ServerLevel level, int stage) {
        sinceShown = 0;
        for (var cell : cells(level)) level.destroyBlockProgress(breaker(cell), cell, stage);
    }

    private void burst(ServerLevel level, FluidStack fluid) {
        var cells = cells(level);
        FluidStack contents;
        if (blockEntity instanceof FluidTankBlockEntity tank) {
            contents = tank.getTankInventory().getFluid().copy();
            tank.getTankInventory().setFluid(FluidStack.EMPTY);
        } else contents = fluid.copyWithAmount(Math.min(fluid.getAmount(), PIPE_LEAK));
        for (var cell : cells) {
            level.destroyBlockProgress(breaker(cell), cell, -1);
            level.destroyBlock(cell, false);
        }
        ToxicGas.release(level, getPos(), contents);
    }

    /** The blocks of this pipe or pump, or of the whole tank this controller runs. */
    private List<BlockPos> cells(ServerLevel level) {
        if (!(blockEntity instanceof FluidTankBlockEntity tank)) return List.of(getPos());
        var axis = tank.getMainConnectionAxis();
        var across = axis == Direction.Axis.Y ? Direction.Axis.X : Direction.Axis.Y;
        var depth = axis == Direction.Axis.Z ? Direction.Axis.X : Direction.Axis.Z;
        List<BlockPos> cells = new ArrayList<>(tank.getWidth() * tank.getWidth() * tank.getHeight());
        for (int along = 0; along < tank.getHeight(); along++) for (int a = 0; a < tank.getWidth(); a++) for (int b = 0; b < tank.getWidth(); b++) {
            var cell = getPos().relative(Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE), along)
                .relative(Direction.fromAxisAndDirection(across, Direction.AxisDirection.POSITIVE), a)
                .relative(Direction.fromAxisAndDirection(depth, Direction.AxisDirection.POSITIVE), b);
            if (level.getBlockEntity(cell) instanceof IMultiBlockEntityContainer part && getPos().equals(part.getController())) cells.add(cell);
        }
        return cells;
    }

    /** Negative, so it never matches a player's entity id and every watcher is sent the cracks. */
    private static int breaker(BlockPos cell) { return Integer.MIN_VALUE | Long.hashCode(cell.asLong()); }

    @Override public void destroy() {
        if (shownStage >= 0 && getWorld() instanceof ServerLevel level) level.destroyBlockProgress(breaker(getPos()), getPos(), -1);
    }

    @Override public void write(CompoundTag tag, HolderLookup.Provider lookup, boolean clientPacket) {
        if (wear > 0) tag.putInt("ContainmentWear", wear);
        if (!reason.isEmpty()) tag.putString("ContainmentHazard", reason);
    }

    @Override public void read(CompoundTag tag, HolderLookup.Provider lookup, boolean clientPacket) {
        wear = Math.clamp(tag.getInt("ContainmentWear"), 0, LIFETIME);
        reason = tag.getString("ContainmentHazard");
    }

    /** The warning while the contents are something the block is not rated for, with the wear so far; read from the controller for a tank part. */
    public static Component warning(BlockEntity be) {
        if (be instanceof IMultiBlockEntityContainer part && part.getControllerBE() instanceof BlockEntity owner) be = owner;
        var wear = be == null ? null : get(be, TYPE);
        if (wear == null || wear.reason.isEmpty()) return null;
        return Component.translatable("transport.create_refinery_n_reactors.wear", Component.translatable("transport.create_refinery_n_reactors.wear." + wear.reason),
            wear.wear * 100 / LIFETIME).withStyle(ChatFormatting.RED);
    }

    public static void addWarning(BlockEntity be, List<Component> lines) {
        var warning = warning(be);
        if (warning != null) lines.add(warning);
    }
}
