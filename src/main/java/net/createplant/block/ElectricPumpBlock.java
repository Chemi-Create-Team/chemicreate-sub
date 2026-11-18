package net.createplant.block;

import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.NodeConfigurator;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;

import net.createplant.compat.electro.ElectricUtilityDevice;
import net.createplant.compat.electro.ElectroDevices;
import net.createplant.registry.CEElectricUtilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

/** Create's pump and fluid behaviour, with an enclosed electrical drive instead of a cog. */
public class ElectricPumpBlock extends PumpBlock
        implements ElectricalDeviceBlock<ElectricUtilityDevice> {
    private static final NodeConfigurator NODES =
            new NodeConfigurator.Builder().add(4, 15, 11).add(12, 15, 11).simple(Direction.NORTH);

    public ElectricPumpBlock(Properties properties) {
        super(properties);
    }

    @Override
    public SimulatedDeviceType<ElectricUtilityDevice> getDevice() {
        return ElectroDevices.PUMP.get();
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        return NODES.getNodes(state.getValue(FACING));
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int node) {
        return NODES.getNodePos(state.getValue(FACING), node);
    }

    @Override
    public BlockEntityType<? extends PumpBlockEntity> getBlockEntityType() {
        return CEElectricUtilities.PUMP_ENTITY.get();
    }

    @Override
    public boolean hasShaftTowards(
            LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return false;
    }

    @Override
    public boolean isSmallCog() {
        return false;
    }

    @Override
    public boolean isLargeCog() {
        return false;
    }

    @Override
    public void onPlace(
            BlockState state, Level level, BlockPos pos, BlockState previous, boolean moving) {
        super.onPlace(state, level, pos, previous, moving);
        if (!level.getBlockTicks().hasScheduledTick(pos, this)) level.scheduleTick(pos, this, 1);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.tick(state, level, pos, random);
        ensureNodesExist(level, pos, state);
    }
}
