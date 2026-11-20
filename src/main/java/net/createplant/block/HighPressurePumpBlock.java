package net.createplant.block;

import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import net.createplant.block.PressurePumpStructure.Part;
import net.createplant.registry.CEPressureInfrastructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class HighPressurePumpBlock extends PumpBlock {
    private final boolean drive;

    public HighPressurePumpBlock(Properties properties, boolean drive) {
        super(properties);
        this.drive = drive;
    }

    public Part part() { return drive ? Part.DRIVE : Part.SUCTION; }
    @Override public Direction.Axis getRotationAxis(BlockState state) { return Direction.Axis.Y; }
    @Override public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return drive && face == Direction.UP;
    }
    @Override public boolean isSmallCog() { return false; }
    @Override public boolean isLargeCog() { return false; }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override public BlockEntityType<? extends PumpBlockEntity> getBlockEntityType() {
        return CEPressureInfrastructure.PUMP_ENTITY.get();
    }

    @Override public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return InteractionResult.PASS;
    }

    @Override public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        if (!context.getLevel().isClientSide) context.getLevel().destroyBlock(context.getClickedPos(), false);
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        PressurePumpStructure.creativeBreak(level, pos, part(), state.getValue(FACING), player.isCreative());
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())) PressurePumpStructure.dismantle(level, pos, part(), state.getValue(FACING), moving);
        super.onRemove(state, level, pos, replacement, moving);
    }
}
