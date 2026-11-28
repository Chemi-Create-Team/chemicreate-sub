package net.createplant.atomics.block;

import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.createplant.atomics.block.entity.FusionMotorGeneratorBlockEntity;
import net.createplant.atomics.registry.CEFusion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** Shaft-driven pulsed-power head used between the tokamak feeder and a large flywheel. */
public class FusionMotorGeneratorBlock extends RotatedPillarKineticBlock implements IBE<FusionMotorGeneratorBlockEntity> {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public FusionMotorGeneratorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(ACTIVE, false));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ACTIVE);
    }

    @Override public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == getRotationAxis(state);
    }

    @Override public Direction.Axis getRotationAxis(BlockState state) { return state.getValue(AXIS); }
    @Override public Class<FusionMotorGeneratorBlockEntity> getBlockEntityClass() { return FusionMotorGeneratorBlockEntity.class; }
    @Override public BlockEntityType<? extends FusionMotorGeneratorBlockEntity> getBlockEntityType() { return CEFusion.MOTOR_GENERATOR_ENTITY.get(); }
}
