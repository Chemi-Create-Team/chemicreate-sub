package net.createplant.arm;

import java.util.List;
import net.createplant.CreatePlant;
import net.createplant.block.entity.IndustrialArmBlockEntity;
import net.createplant.registry.CEArm;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Choosing an arm's points as with Create's mechanical arm: with the arm in hand, right-clicking a container, belt or depot marks it
 * as a place to take from, again as a place to put to, a third time unmarks it. Placing the arm sends the marks to the server.
 */
public record ArmPoints(BlockPos arm, List<BlockPos> inputs, List<BlockPos> outputs) implements CustomPacketPayload {
    public static final String KEY = "arm.create_refinery_n_reactors.point.";
    private static final Type<ArmPoints> TYPE = new Type<>(CreatePlant.location("arm_points"));
    private static final StreamCodec<RegistryFriendlyByteBuf, ArmPoints> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ArmPoints::arm,
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(IndustrialArmBlockEntity.MAX_POINTS)), ArmPoints::inputs,
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(IndustrialArmBlockEntity.MAX_POINTS)), ArmPoints::outputs,
            ArmPoints::new);

    @Override public Type<ArmPoints> type() { return TYPE; }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, CODEC, (points, context) -> {
            if (context.player() instanceof ServerPlayer player) points.apply(player);
        });
    }

    /**
     * Marks that came before their arm: the client sends them while it places the arm, which is before the server has placed it.
     * The server keeps them per player until its own placement of the arm picks them up.
     */
    private static final java.util.Map<java.util.UUID, ArmPoints> WAITING = new java.util.concurrent.ConcurrentHashMap<>();

    /** Server: gives the points to the arm the player just placed; points out of reach are left out and the player is told. */
    public void apply(ServerPlayer player) {
        Level level = player.level();
        if (!level.isLoaded(arm) || player.distanceToSqr(arm.getCenter()) > 64) return;
        if (!(level.getBlockEntity(arm) instanceof IndustrialArmBlockEntity be)) {
            WAITING.put(player.getUUID(), this);
            return;
        }
        int dropped = be.setPoints(inputs, outputs);
        player.displayClientMessage(Component.translatable(KEY + "saved", be.inputs.size(), be.outputs.size()), true);
        if (dropped > 0) player.displayClientMessage(Component.translatable(KEY + "dropped", dropped), false);
    }

    /** Server: the player placed an arm at {@code pos}; marks that arrived first are given to it now. */
    public static void placed(ServerPlayer player, BlockPos pos) {
        ArmPoints waiting = WAITING.remove(player.getUUID());
        if (waiting != null && waiting.arm.equals(pos)) waiting.apply(player);
    }

    /** Anything an arm could take from or put to: a container, a belt or a depot. Range is checked when the arm is placed. */
    public static boolean candidate(Level level, BlockPos p) {
        var be = level.getBlockEntity(p);
        if (be == null || be instanceof IndustrialArmBlockEntity) return false;
        return com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour.get(level, p,
                com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TYPE) != null
                || level.getCapability(Capabilities.ItemHandler.BLOCK, p, null) != null
                || level.getCapability(Capabilities.ItemHandler.BLOCK, p, net.minecraft.core.Direction.UP) != null;
    }

    /** Both sides: a right-click on a candidate with the arm in hand marks it instead of opening it or placing the arm. */
    public static void rightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getItemStack().is(CEArm.ARM_ITEM.get()) || event.getEntity().isShiftKeyDown() || !candidate(event.getLevel(), event.getPos())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getLevel().isClientSide) net.createplant.client.ArmPointSelection.toggle(event.getPos());
    }
}
