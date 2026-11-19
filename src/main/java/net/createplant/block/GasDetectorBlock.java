package net.createplant.block;

import net.createplant.safety.FlammableGas;
import net.createplant.safety.ProcessGasIncidents;
import net.createplant.safety.ToxicGas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;

public final class GasDetectorBlock extends Block {
    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    private static final int SAMPLE_INTERVAL = 10;
    private static final double REACH = 4;

    public GasDetectorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(POWER, 0));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(POWER); }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moved) {
        if (!level.isClientSide && !previous.is(this)) level.scheduleTick(pos, this, SAMPLE_INTERVAL);
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int signal = (15 * ProcessGasIncidents.concentration(level, pos) + 99) / 100;
        for (var cloud : level.getEntitiesOfClass(AreaEffectCloud.class, new AABB(pos).inflate(REACH),
                cloud -> cloud.isAlive() && (ToxicGas.toxic(cloud) || FlammableGas.cloud(cloud)))) {
            double distance = Math.sqrt(pos.distToCenterSqr(cloud.getX(), cloud.getY(), cloud.getZ()));
            double reach = cloud.getRadius() + REACH;
            signal = Math.max(signal, Mth.ceil(15 * Math.max(0, 1 - distance / reach)));
        }
        if (signal != state.getValue(POWER)) {
            level.setBlockAndUpdate(pos, state.setValue(POWER, signal));
            level.updateNeighbourForOutputSignal(pos, this);
        }
        level.scheduleTick(pos, this, SAMPLE_INTERVAL);
    }

    @Override protected boolean isSignalSource(BlockState state) { return true; }
    @Override protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction toward) { return state.getValue(POWER); }
    @Override protected boolean hasAnalogOutputSignal(BlockState state) { return true; }
    @Override protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) { return state.getValue(POWER); }
}
