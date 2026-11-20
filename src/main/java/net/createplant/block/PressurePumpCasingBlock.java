package net.createplant.block;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.createplant.block.PressurePumpStructure.Part;
import net.createplant.block.entity.HighPressurePumpBlockEntity;
import net.createplant.block.entity.PressurePumpPortBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public final class PressurePumpCasingBlock extends HorizontalDirectionalBlock implements IWrenchable, EntityBlock {
    private static final MapCodec<PressurePumpCasingBlock> CODEC = simpleCodec(PressurePumpCasingBlock::new);
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class, part -> part != Part.DRIVE && part != Part.SUCTION);

    public PressurePumpCasingBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, Part.RECEIVE));
    }

    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        var part = state.getValue(PART);
        return part == Part.RECEIVE || part == Part.FEED ? new PressurePumpPortBlockEntity(pos, state) : null;
    }

    public static IFluidHandler port(Level level, BlockPos pos, BlockState state, BlockEntity ignored, Direction side) {
        var front = state.getValue(FACING);
        var part = state.getValue(PART);
        boolean fill = part == Part.RECEIVE && side == front.getOpposite();
        if (!fill && !(part == Part.FEED && side == front)) return null;
        var controller = part.drive(pos, front);
        return level.hasChunkAt(controller) && level.getBlockEntity(controller) instanceof HighPressurePumpBlockEntity pump
            && pump.formed() ? pump.internalPort(fill) : null;
    }

    @Override public InteractionResult onWrenched(BlockState state, UseOnContext context) { return InteractionResult.PASS; }

    @Override public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        if (!context.getLevel().isClientSide) context.getLevel().destroyBlock(context.getClickedPos(), false);
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        PressurePumpStructure.creativeBreak(level, pos, state.getValue(PART), state.getValue(FACING), player.isCreative());
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())) PressurePumpStructure.dismantle(level, pos, state.getValue(PART), state.getValue(FACING), moving);
        super.onRemove(state, level, pos, replacement, moving);
    }
}
