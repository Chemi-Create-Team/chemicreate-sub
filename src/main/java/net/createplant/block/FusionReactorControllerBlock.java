package net.createplant.block;

import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.base.SimpleElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.NodeConfigurator;
import com.simibubi.create.foundation.block.IBE;
import java.util.Map;
import net.createplant.block.entity.FusionReactorControllerBlockEntity;
import net.createplant.compat.electro.ElectroDevices;
import net.createplant.compat.electro.FusionMagnetDevice;
import net.createplant.registry.CEFusion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Magnet feeder, plant controller and emergency local station.  Ordinary operation belongs at the linked physical desk. */
public class FusionReactorControllerBlock extends SimpleElectricalDeviceBlock<FusionMagnetDevice>
    implements IBE<FusionReactorControllerBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    private static final NodeConfigurator NODES = new NodeConfigurator.Builder()
        .add(4, 18.5f, 8).add(12, 18.5f, 8).simple(Direction.NORTH);

    public FusionReactorControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false));
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder); builder.add(FACING, ACTIVE);
    }

    @Override public SimulatedDeviceType<FusionMagnetDevice> getDevice() { return ElectroDevices.FUSION_MAGNET.get(); }
    @Override public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) { return NODES.getNodes(state.getValue(FACING)); }
    @Override public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int node) { return NODES.getNodePos(state.getValue(FACING), node); }
    @Override public MutableComponent getNodeLabel(Level level, BlockPos pos, BlockState state, int node) {
        return Component.translatable("fusion_reactor.create_refinery_n_reactors.terminal." + (node == 0 ? "positive" : "negative"));
    }

    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (!level.getBlockTicks().hasScheduledTick(pos, this)) level.scheduleTick(pos, this, 1);
    }

    @Override public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        ensureNodesExist(level, pos, state);
        if (!level.getBlockTicks().hasScheduledTick(pos, this)) level.scheduleTick(pos, this, 40);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        withBlockEntityDo(level, pos, controller -> {
            if (player.isShiftKeyDown()) controller.localEmergencyToggle(player);
            else controller.describeTo(player);
        });
        return InteractionResult.SUCCESS;
    }

    @Override public Class<FusionReactorControllerBlockEntity> getBlockEntityClass() { return FusionReactorControllerBlockEntity.class; }
    @Override public BlockEntityType<? extends FusionReactorControllerBlockEntity> getBlockEntityType() { return CEFusion.CONTROLLER_ENTITY.get(); }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock()) && !level.isClientSide()
            && level.getBlockEntity(pos) instanceof FusionReactorControllerBlockEntity controller)
            controller.releaseContents();
        super.onRemove(state, level, pos, next, moved);
        IBE.onRemove(state, level, pos, next);
    }
}
