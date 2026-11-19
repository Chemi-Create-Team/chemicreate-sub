package net.createplant.block;

import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.device.SimpleNonTickingElectricalDevice;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.createplant.compat.electro.ElectroDevices;
import net.createplant.block.entity.TransmissionTowerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The six insulator ends are independent nodes of the same, ground-level block. */
public final class TransmissionTowerBlock extends BaseEntityBlock implements ElectricalDeviceBlock<SimpleNonTickingElectricalDevice>, IWrenchable {
    public static final int HEIGHT = 24;
    public static final int DEGREES_PER_STEP = 15;
    private static final int STEPS_PER_QUARTER = 90 / DEGREES_PER_STEP;
    private static final int HEADING_COUNT = 360 / DEGREES_PER_STEP;
    public static final IntegerProperty ROTATION_STEP = IntegerProperty.create("rotation_step", 0, STEPS_PER_QUARTER - 1);
    public static final MapCodec<TransmissionTowerBlock> CODEC = simpleCodec(TransmissionTowerBlock::new);
    private static final VoxelShape BASE = box(0, 0, 0, 16, 14, 16);
    private static final List<Map<Integer, Vec3>> NODES = nodes();

    public TransmissionTowerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
            .setValue(ROTATION_STEP, 0));
    }

    private static List<Map<Integer, Vec3>> nodes() {
        var variants = new ArrayList<Map<Integer, Vec3>>(HEADING_COUNT);
        for (int heading = 0; heading < HEADING_COUNT; heading++) {
            var points = new LinkedHashMap<Integer, Vec3>();
            for (int bank = 0; bank < 2; bank++) {
                for (int row = 0; row < 3; row++) {
                    double span = row == 1 ? 4.42 : 3.42;
                    var relative = new Vec3(bank == 0 ? -span : span, 21.82 - row * 3, 0);
                    points.put(bank * 3 + row, turn(relative, heading).add(.5, 0, .5));
                }
            }
            variants.add(Map.copyOf(points));
        }
        return List.copyOf(variants);
    }

    private static int heading(BlockState state) {
        int quarter = Math.floorMod(state.getValue(HorizontalDirectionalBlock.FACING).get2DDataValue()
            - Direction.NORTH.get2DDataValue(), 4);
        return quarter * STEPS_PER_QUARTER + state.getValue(ROTATION_STEP);
    }

    private static BlockState withHeading(BlockState state, int heading) {
        int wrapped = Math.floorMod(heading, HEADING_COUNT);
        int facing = (Direction.NORTH.get2DDataValue() + wrapped / STEPS_PER_QUARTER) % 4;
        return state.setValue(HorizontalDirectionalBlock.FACING, Direction.from2DDataValue(facing))
            .setValue(ROTATION_STEP, wrapped % STEPS_PER_QUARTER);
    }

    private static Vec3 turn(Vec3 relative, int heading) {
        int wrapped = Math.floorMod(heading, HEADING_COUNT);
        int step = wrapped % STEPS_PER_QUARTER;
        if (step != 0) {
            double radians = Math.toRadians(step * DEGREES_PER_STEP);
            double cos = Math.cos(radians);
            double sin = Math.sin(radians);
            relative = new Vec3(relative.x * cos - relative.z * sin, relative.y, relative.x * sin + relative.z * cos);
        }
        return switch (wrapped / STEPS_PER_QUARTER) {
            case 1 -> new Vec3(-relative.z, relative.y, relative.x);
            case 2 -> new Vec3(-relative.x, relative.y, -relative.z);
            case 3 -> new Vec3(relative.z, relative.y, -relative.x);
            default -> relative;
        };
    }

    public int terminalAt(BlockState state, Vec3 localHit) {
        var local = turn(localHit.subtract(.5, 0, .5), -heading(state));
        int row = Mth.clamp((int) ((local.x + .5) * 3), 0, 2);
        return (local.z < 0 ? 0 : 3) + row;
    }

    @Override public SimulatedDeviceType<SimpleNonTickingElectricalDevice> getDevice() {
        return ElectroDevices.TRANSMISSION_TOWER.get();
    }

    @Override public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        return NODES.get(heading(state));
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
        if (level instanceof ServerLevel server && state != old) ensureNodesExist(server, pos, state);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (context.getClickedPos().getY() + HEIGHT > context.getLevel().getMaxBuildHeight()) return null;
        if (context.getPlayer() == null) {
            return defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, context.getHorizontalDirection().getOpposite());
        }
        return withHeading(defaultBlockState(), Math.round(context.getRotation() / DEGREES_PER_STEP));
    }

    @Override public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        var level = context.getLevel();
        if (!level.isClientSide) {
            var pos = context.getClickedPos();
            level.setBlockAndUpdate(pos, withHeading(state, heading(state) + 1));
            IWrenchable.playRotateSound(level, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(HorizontalDirectionalBlock.FACING, rotation.rotate(state.getValue(HorizontalDirectionalBlock.FACING)));
    }

    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        return switch (mirror) {
            case LEFT_RIGHT -> withHeading(state, HEADING_COUNT / 2 - heading(state));
            case FRONT_BACK -> withHeading(state, -heading(state));
            case NONE -> state;
        };
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HorizontalDirectionalBlock.FACING, ROTATION_STEP);
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return BASE; }
    @Override protected MapCodec<TransmissionTowerBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.ENTITYBLOCK_ANIMATED; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TransmissionTowerBlockEntity(pos, state);
    }
}
