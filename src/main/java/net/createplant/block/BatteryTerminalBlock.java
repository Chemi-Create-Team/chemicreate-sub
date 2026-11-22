package net.createplant.block;

import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.base.SimpleElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.NodeConfigurator;
import com.simibubi.create.foundation.block.IBE;
import java.util.Map;
import net.createplant.block.entity.BatteryTerminalBlockEntity;
import net.createplant.compat.electro.BatteryDevice;
import net.createplant.compat.electro.ElectroDevices;
import net.createplant.registry.CEBattery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The terminal on top of a battery bank: it owns the bank, its electrolyte and its charge, and carries the positive and negative posts for Electro Energetics wires. */
public class BatteryTerminalBlock extends SimpleElectricalDeviceBlock<BatteryDevice> implements IBE<BatteryTerminalBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final NodeConfigurator NODES = new NodeConfigurator.Builder().add(4, 18.5f, 8).add(12, 18.5f, 8).simple(Direction.NORTH);
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 14, 16), Block.box(3, 14, 7, 5, 17, 9), Block.box(11, 14, 7, 13, 17, 9));
    private final BatteryChemistry chemistry;

    public BatteryTerminalBlock(Properties properties, BatteryChemistry chemistry) {
        super(properties);
        this.chemistry = chemistry;
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    public BatteryChemistry chemistry() { return chemistry; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { super.createBlockStateDefinition(builder);builder.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()); }
    @Override public SimulatedDeviceType<BatteryDevice> getDevice() { return ElectroDevices.BATTERY.get(); }
    @Override public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) { return NODES.getNodes(state.getValue(FACING)); }
    @Override public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int node) { return NODES.getNodePos(state.getValue(FACING), node); }
    @Override public MutableComponent getNodeLabel(Level level, BlockPos pos, BlockState state, int node) {
        return Component.translatable("battery.create_refinery_n_reactors.terminal." + (node == 0 ? "positive" : "negative"));
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override public Class<BatteryTerminalBlockEntity> getBlockEntityClass() { return BatteryTerminalBlockEntity.class; }
    @Override public BlockEntityType<? extends BatteryTerminalBlockEntity> getBlockEntityType() { return CEBattery.TERMINAL_ENTITY.get(); }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        super.onRemove(state, level, pos, newState, moved);
        IBE.onRemove(state, level, pos, newState);
    }
}
