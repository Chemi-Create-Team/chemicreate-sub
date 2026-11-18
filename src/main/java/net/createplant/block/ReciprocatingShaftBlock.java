package net.createplant.block;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllShapes;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.AbstractShaftBlock;
import net.createplant.block.entity.ReciprocatingShaftBlockEntity;
import net.createplant.registry.CEReciprocating;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A Create shaft taken over by a reciprocating compressor so the compressor's load reaches the kinetic network; it is a shaft again once the compressor goes. */
public class ReciprocatingShaftBlock extends AbstractShaftBlock {
    public ReciprocatingShaftBlock(Properties properties) { super(properties); }

    public static BlockState driven(BlockState shaft) {
        return CEReciprocating.SHAFT.get().defaultBlockState().setValue(AXIS, shaft.getValue(AXIS)).setValue(WATERLOGGED, shaft.getValue(WATERLOGGED));
    }

    public static BlockState released(BlockState driven) {
        return AllBlocks.SHAFT.getDefaultState().setValue(AXIS, driven.getValue(AXIS)).setValue(WATERLOGGED, driven.getValue(WATERLOGGED));
    }

    @Override public BlockEntityType<? extends KineticBlockEntity> getBlockEntityType() { return CEReciprocating.SHAFT_ENTITY.get(); }

    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return AllShapes.SIX_VOXEL_POLE.get(state.getValue(RotatedPillarKineticBlock.AXIS));
    }

    @Override public InteractionResult onWrenched(BlockState state, UseOnContext context) { return InteractionResult.PASS; }

    @Override public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, net.minecraft.world.entity.player.Player player) {
        return AllBlocks.SHAFT.asStack();
    }
}
