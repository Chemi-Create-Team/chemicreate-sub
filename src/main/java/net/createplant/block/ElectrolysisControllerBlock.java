package net.createplant.block;

import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.NodeConfigurator;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import java.util.ArrayList;
import java.util.Map;
import net.createplant.block.entity.ElectrolysisControllerBlockEntity;
import net.createplant.compat.electro.ElectroDevices;
import net.createplant.compat.electro.ElectrolysisCellDevice;
import net.createplant.registry.CEElectrolysis;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * One unit of an electrolysis stack's two-unit end panel. The upper unit carries the stack's two power terminals on its top, drawn as
 * Electro Energetics' own connectors at two thirds of their size, and is the stack's device in the Electro Energetics circuit.
 */
public class ElectrolysisControllerBlock extends Block implements IBE<ElectrolysisControllerBlockEntity>, IWrenchable, ElectricalDeviceBlock<ElectrolysisCellDevice> {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** 0: the load dial below; 1: the meters and START/STOP above. */
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 1);
    private static final NodeConfigurator NODES = new NodeConfigurator.Builder().add(4, 16 + 8 * 2 / 3f, 8).add(12, 16 + 8 * 2 / 3f, 8).simple(Direction.NORTH);

    public ElectrolysisControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(PART, 0));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, PART); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()); }

    @Override public SimulatedDeviceType<ElectrolysisCellDevice> getDevice() { return ElectroDevices.ELECTROLYZER.get(); }
    @Override public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        return state.getValue(PART) == 1 ? NODES.getNodes(state.getValue(FACING)) : Map.of();
    }
    @Override public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int node) { return NODES.getNodePos(state.getValue(FACING), node); }

    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moving) {
        super.onPlace(state, level, pos, previous, moving);
        if (!level.getBlockTicks().hasScheduledTick(pos, this)) level.scheduleTick(pos, this, 1);
        if (previous.is(this)) return;
        for (var direction : Direction.values()) {
            var next = pos.relative(direction);
            if (!level.hasChunkAt(next)) continue;
            if (level.getBlockEntity(next) instanceof ElectrolysisControllerBlockEntity part) part.invalidateMachine();
            if (level.getBlockEntity(next) instanceof net.createplant.block.entity.ElectrolysisCellBlockEntity cell) cell.invalidateMachine();
        }
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
        if (hit.getDirection() != state.getValue(FACING) || !(level.getBlockEntity(pos) instanceof ElectrolysisControllerBlockEntity part)) return InteractionResult.PASS;
        double x = hit.getLocation().x - pos.getX(), z = hit.getLocation().z - pos.getZ();
        double across = switch (state.getValue(FACING)) { case SOUTH -> x;case WEST -> z;case EAST -> 1 - z;default -> 1 - x; };
        double height = hit.getLocation().y - pos.getY();
        int panel = state.getValue(PART);double px = across * 16, py = height * 16;
        boolean button = panel == 1 && py >= 1.5 && py <= 5.4 && (px >= 2.2 && px <= 5.8 || px >= 10.2 && px <= 13.8);
        boolean dial = panel == 0 && (px-8)*(px-8) + (py-7)*(py-7) <= 3.1*3.1;
        if (!button && !dial) return InteractionResult.PASS;
        if (!level.isClientSide) {
            var owner = part.master();
            if (button) part.setRunning(across < .5);
            else if (owner != null) part.selectLoad(owner.limit() + (across < .5 ? -1 : 1) * (player.isShiftKeyDown() ? 10 : 1));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Registers the terminals the unit shows now: two on the upper unit, none on the lower. */
    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        InfrastructureSavedData.load(level).registerOrUpdateNodes(pos, new ArrayList<>(getNodePositions(level, pos, state).keySet()));
        super.tick(state, level, pos, random);
    }

    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel server) {
            var circuit = InfrastructureSavedData.load(server);
            for (var node : circuit.getNodesAt(pos)) for (var connection : circuit.getConnections(node)) {
                if (player.isCreative()) circuit.removeConnectionNoDrops(connection);else circuit.removeConnection(connection);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        removeWiresByPlayer(context.getPlayer(), context.getLevel(), context.getClickedPos());
        return IWrenchable.super.onSneakWrenched(state, context);
    }

    /** A comparator reads how full the electrode racks are: 15 fully loaded, 0 with none left. */
    @Override protected boolean hasAnalogOutputSignal(BlockState state) { return true; }
    @Override protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        var owner = level.getBlockEntity(pos) instanceof ElectrolysisControllerBlockEntity part ? part.master() : null;
        return owner == null || owner.plateCapacity() == 0 ? 0 : (owner.plates() * 15 + owner.plateCapacity() - 1) / owner.plateCapacity();
    }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof ElectrolysisControllerBlockEntity controller) {
            controller.invalidateMachine();
            controller.dropCharge();
        }
        super.onRemove(state, level, pos, next, moving);
        IBE.onRemove(state, level, pos, next);
    }

    @Override public Class<ElectrolysisControllerBlockEntity> getBlockEntityClass() { return ElectrolysisControllerBlockEntity.class; }
    @Override public BlockEntityType<? extends ElectrolysisControllerBlockEntity> getBlockEntityType() { return CEElectrolysis.CONTROLLER_ENTITY.get(); }
}
