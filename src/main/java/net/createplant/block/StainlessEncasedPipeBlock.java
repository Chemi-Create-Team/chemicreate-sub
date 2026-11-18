package net.createplant.block;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pipes.EncasedPipeBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import net.createmod.catnip.data.Iterate;
import net.createplant.registry.CETransport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;

/** Create's copper-cased fluid pipe around a stainless steel pipe; removing the casing gives the stainless pipe back. */
public class StainlessEncasedPipeBlock extends EncasedPipeBlock {
    public StainlessEncasedPipeBlock(Properties properties) {
        super(properties, AllBlocks.COPPER_CASING::get);
    }

    /** Create's casing removal, ending in the stainless pipe. */
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (world.isClientSide)
            return InteractionResult.SUCCESS;
        world.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
        var pipe = CETransport.STAINLESS_PIPE.get();
        BlockState equivalentPipe = transferSixWayProperties(state, pipe.defaultBlockState());
        Direction firstFound = Direction.UP;
        for (Direction d : Iterate.directions) {
            if (state.getValue(FACING_TO_PROPERTY_MAP.get(d))) {
                firstFound = d;
                break;
            }
        }
        FluidTransportBehaviour.cacheFlows(world, pos);
        world.setBlockAndUpdate(pos, pipe.updateBlockState(equivalentPipe, firstFound, null, world, pos));
        FluidTransportBehaviour.loadFlows(world, pos);
        return InteractionResult.SUCCESS;
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
    public BlockEntityType<? extends FluidPipeBlockEntity> getBlockEntityType() {
        return CETransport.STAINLESS_ENCASED_PIPE_ENTITY.get();
    }
}
