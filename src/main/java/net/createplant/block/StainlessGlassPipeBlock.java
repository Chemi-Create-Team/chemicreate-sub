package net.createplant.block;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pipes.EncasedPipeBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.GlassFluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.StraightPipeBlockEntity;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import net.createmod.catnip.data.Iterate;
import net.createplant.registry.CETransport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Create's glass fluid pipe in stainless steel: wrenching or encasing it keeps to the stainless pipe family. */
public class StainlessGlassPipeBlock extends GlassFluidPipeBlock {
    public StainlessGlassPipeBlock(Properties properties) {
        super(properties);
    }

    /** Create's casing interaction, ending in the stainless encased pipe. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!AllBlocks.COPPER_CASING.isIn(stack))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide)
            return ItemInteractionResult.SUCCESS;
        BlockState newState = CETransport.STAINLESS_ENCASED_PIPE.get().defaultBlockState();
        for (Direction d : Iterate.directionsInAxis(getAxis(state)))
            newState = newState.setValue(EncasedPipeBlock.FACING_TO_PROPERTY_MAP.get(d), true);
        FluidTransportBehaviour.cacheFlows(level, pos);
        level.setBlockAndUpdate(pos, newState);
        FluidTransportBehaviour.loadFlows(level, pos);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public BlockState toRegularPipe(LevelAccessor world, BlockPos pos, BlockState state) {
        Direction side = Direction.get(Direction.AxisDirection.POSITIVE, state.getValue(AXIS));
        var pipe = CETransport.STAINLESS_PIPE.get();
        return pipe.updateBlockState(pipe.defaultBlockState()
            .setValue(FluidPipeBlock.PROPERTY_BY_DIRECTION.get(side), true)
            .setValue(FluidPipeBlock.PROPERTY_BY_DIRECTION.get(side.getOpposite()), true), side, null, world, pos);
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return new ItemStack(CETransport.STAINLESS_PIPE.get());
    }

    @Override
    public ItemRequirement getRequiredItems(BlockState state, BlockEntity be) {
        return ItemRequirement.of(CETransport.STAINLESS_PIPE.get().defaultBlockState(), be);
    }

    @Override
    public BlockEntityType<? extends StraightPipeBlockEntity> getBlockEntityType() {
        return CETransport.STAINLESS_GLASS_PIPE_ENTITY.get();
    }
}
