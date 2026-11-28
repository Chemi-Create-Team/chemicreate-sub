package net.createplant.compat.electro;

import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.base.SimpleElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.NodeConfigurator;
import com.simibubi.create.foundation.block.IBE;
import java.util.Map;
import net.createplant.block.entity.FuelCycleMachineBlockEntity;
import net.createplant.registry.CEFuelCycle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

public class VitrificationBusbarBlock extends SimpleElectricalDeviceBlock<VitrificationMelterDevice> implements IBE<FuelCycleMachineBlockEntity> {
    private static final NodeConfigurator NODES = new NodeConfigurator.Builder().add(3, 15, 5).add(13, 15, 5).simple(Direction.NORTH);
    public VitrificationBusbarBlock(Properties properties) { super(properties);registerDefaultState(defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { super.createBlockStateDefinition(builder);builder.add(BlockStateProperties.HORIZONTAL_FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, context.getHorizontalDirection().getOpposite()); }
    @Override public SimulatedDeviceType<VitrificationMelterDevice> getDevice() { return AtomicsElectroDevices.VITRIFICATION.get(); }
    @Override public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) { return NODES.getNodes(state.getValue(BlockStateProperties.HORIZONTAL_FACING)); }
    @Override public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int node) { return NODES.getNodePos(state.getValue(BlockStateProperties.HORIZONTAL_FACING), node); }
    @Override public Class<FuelCycleMachineBlockEntity> getBlockEntityClass() { return FuelCycleMachineBlockEntity.class; }
    @Override public BlockEntityType<? extends FuelCycleMachineBlockEntity> getBlockEntityType() { return CEFuelCycle.MACHINE_ENTITY.get(); }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!next.is(state.getBlock()) && level.getBlockEntity(pos) instanceof FuelCycleMachineBlockEntity be) be.release();
        super.onRemove(state, level, pos, next, moved);
    }
}
