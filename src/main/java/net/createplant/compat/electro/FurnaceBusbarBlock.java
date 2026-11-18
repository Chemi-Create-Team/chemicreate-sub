package net.createplant.compat.electro;

import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.base.SimpleElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.NodeConfigurator;
import com.simibubi.create.foundation.block.IBE;
import java.util.Map;
import net.createplant.block.entity.ElectrolysisFurnaceBlockEntity;
import net.createplant.registry.CEFurnace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.Vec3;

/**
 * Busbar of the electrolysis furnace, hung on the outside of the middle of a top side facing the anode, its bars reaching over the rim. Its two terminals take Electro
 * Energetics wires: the anode beam on the left and the cathode collector on the right, seen from the furnace.
 */
public class FurnaceBusbarBlock extends SimpleElectricalDeviceBlock<FurnaceBusbarDevice> implements IBE<ElectrolysisFurnaceBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Authored facing north: anode terminal at the west top, cathode terminal at the east top. */
    private static final NodeConfigurator NODES = new NodeConfigurator.Builder().add(4.5f, 26, 5).add(11.5f, 26, 5).simple(Direction.NORTH);
    public FurnaceBusbarBlock(Properties properties) { super(properties);registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { super.createBlockStateDefinition(builder);builder.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getHorizontalDirection()); }
    @Override public SimulatedDeviceType<FurnaceBusbarDevice> getDevice() { return ElectroDevices.FURNACE.get(); }
    @Override public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) { return NODES.getNodes(state.getValue(FACING)); }
    @Override public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int node) { return NODES.getNodePos(state.getValue(FACING), node); }
    @Override public Class<ElectrolysisFurnaceBlockEntity> getBlockEntityClass() { return ElectrolysisFurnaceBlockEntity.class; }
    @Override public BlockEntityType<? extends ElectrolysisFurnaceBlockEntity> getBlockEntityType() { return CEFurnace.CONTROLLER_ENTITY.get(); }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ElectrolysisFurnaceBlockEntity furnace) furnace.dropContents();
        super.onRemove(state, level, pos, newState, moved);
        IBE.onRemove(state, level, pos, newState);
    }
}
