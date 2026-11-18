package net.createplant.block;

import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.device.SimpleNonTickingElectricalDevice;
import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.createplant.compat.electro.ElectroDevices;
import net.createplant.block.entity.TransmissionTowerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The six insulator ends are independent nodes of the same, ground-level block. */
public final class TransmissionTowerBlock extends BaseEntityBlock implements ElectricalDeviceBlock<SimpleNonTickingElectricalDevice> {
    public static final int HEIGHT = 24;
    public static final MapCodec<TransmissionTowerBlock> CODEC = simpleCodec(TransmissionTowerBlock::new);
    private static final VoxelShape BASE = box(0, 0, 0, 16, 14, 16);
    private static final Map<Direction, Map<Integer, Vec3>> NODES = nodes();

    public TransmissionTowerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
    }

    private static Map<Direction, Map<Integer, Vec3>> nodes() {
        var variants = new EnumMap<Direction, Map<Integer, Vec3>>(Direction.class);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var points = new LinkedHashMap<Integer, Vec3>();
            for (int bank = 0; bank < 2; bank++) {
                for (int row = 0; row < 3; row++) {
                    double span = row == 1 ? 4.42 : 3.42;
                    var relative = new Vec3(bank == 0 ? -span : span, 21.82 - row * 3, 0);
                    points.put(bank * 3 + row, rotate(relative, facing).add(.5, 0, .5));
                }
            }
            variants.put(facing, Map.copyOf(points));
        }
        return Map.copyOf(variants);
    }

    private static Vec3 rotate(Vec3 relative, Direction facing) {
        return switch (facing) {
            case EAST -> new Vec3(-relative.z, relative.y, relative.x);
            case SOUTH -> new Vec3(-relative.x, relative.y, -relative.z);
            case WEST -> new Vec3(relative.z, relative.y, -relative.x);
            default -> relative;
        };
    }

    public int terminalAt(BlockState state, Vec3 localHit) {
        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        Direction inverse = facing.getAxis() == Direction.Axis.X ? facing.getOpposite() : facing;
        var local = rotate(localHit.subtract(.5, 0, .5), inverse);
        int row = Mth.clamp((int) ((local.x + .5) * 3), 0, 2);
        return (local.z < 0 ? 0 : 3) + row;
    }

    @Override public SimulatedDeviceType<SimpleNonTickingElectricalDevice> getDevice() {
        return ElectroDevices.TRANSMISSION_TOWER.get();
    }

    @Override public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        return NODES.get(state.getValue(HorizontalDirectionalBlock.FACING));
    }

    @Override public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        return getNodePositions(level, pos, state).getOrDefault(id, Vec3.ZERO);
    }

    @Override public MutableComponent getNodeLabel(Level level, BlockPos pos, BlockState state, int id) {
        return Component.translatable("transmission_tower.create_refinery_n_reactors.terminal", id + 1);
    }

    @Override public boolean canSelfConnect(Level level, BlockPos pos, BlockState state, int first, int second) { return false; }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (level instanceof ServerLevel server && !state.is(old.getBlock())) ensureNodesExist(server, pos, state);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (context.getClickedPos().getY() + HEIGHT > context.getLevel().getMaxBuildHeight()) return null;
        return defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(HorizontalDirectionalBlock.FACING, rotation.rotate(state.getValue(HorizontalDirectionalBlock.FACING)));
    }

    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(HorizontalDirectionalBlock.FACING)));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HorizontalDirectionalBlock.FACING);
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return BASE; }
    @Override protected MapCodec<TransmissionTowerBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.ENTITYBLOCK_ANIMATED; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TransmissionTowerBlockEntity(pos, state);
    }
}
