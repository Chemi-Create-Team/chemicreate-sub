package net.createplant.block;

import java.util.function.Supplier;
import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import net.createplant.registry.CETransport;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/** Create's fluid tank laid on its side, like its item vault: it connects only with tanks lying along the same axis, has no windows, and is a boiler like the standing tank. */
public class HorizontalFluidTankBlock extends FluidTankBlock {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    private final Supplier<? extends BlockEntityType<? extends FluidTankBlockEntity>> type;

    public HorizontalFluidTankBlock(Properties properties) {
        this(properties, CETransport.HORIZONTAL_FLUID_TANK_ENTITY);
    }

    public HorizontalFluidTankBlock(Properties properties, Supplier<? extends BlockEntityType<? extends FluidTankBlockEntity>> type) {
        super(properties, false);
        this.type = type;
        registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.X));
    }

    @Override public BlockEntityType<? extends FluidTankBlockEntity> getBlockEntityType() { return type.get(); }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(AXIS);
    }

    /** A tank placed against another horizontal tank continues its length; otherwise it lies along the way the player faces. */
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var player = context.getPlayer();
        BlockState against = context.getLevel().getBlockState(context.getClickedPos().relative(context.getClickedFace().getOpposite()));
        if (against.is(this) && (player == null || !player.isShiftKeyDown())) return defaultBlockState().setValue(AXIS, against.getValue(AXIS));
        return defaultBlockState().setValue(AXIS, context.getHorizontalDirection().getAxis());
    }

    @Override public BlockState rotate(BlockState state, Rotation rotation) {
        return rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90 ? state.setValue(AXIS, state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X) : state;
    }

    @Override public BlockState mirror(BlockState state, Mirror mirror) { return state; }

    /** Breaking a cell that holds boiler steam lets it out. */
    @Override public void onRemove(BlockState state, net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && level instanceof net.minecraft.server.level.ServerLevel server && level.getBlockEntity(pos) instanceof net.createplant.block.entity.HorizontalTankBlockEntity tank && !tank.steam().isEmpty()) {
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5, 12, .2, .3, .2, .03);
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH, net.minecraft.sounds.SoundSource.BLOCKS, .6f, .8f);
        }
        super.onRemove(state, level, pos, next, moving);
    }
}
