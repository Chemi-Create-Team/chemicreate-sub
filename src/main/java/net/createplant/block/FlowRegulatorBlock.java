package net.createplant.block;

import com.simibubi.create.content.fluids.pipes.AxisPipeBlock;
import com.simibubi.create.foundation.block.IBE;
import net.createplant.block.entity.FlowRegulatorBlockEntity;
import net.createplant.registry.CEHeating;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class FlowRegulatorBlock extends AxisPipeBlock implements IBE<FlowRegulatorBlockEntity> {
    public FlowRegulatorBlock(Properties properties) { super(properties); }
    @Override public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,net.minecraft.world.phys.shapes.CollisionContext context) { return HeatingShapes.regulator(state.getValue(AXIS)); }
    @Override public Class<FlowRegulatorBlockEntity> getBlockEntityClass() { return FlowRegulatorBlockEntity.class; }
    @Override public BlockEntityType<? extends FlowRegulatorBlockEntity> getBlockEntityType() { return CEHeating.REGULATOR_ENTITY.get(); }
    @Override public InteractionResult onWrenched(BlockState state,UseOnContext context) { return InteractionResult.PASS; }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,net.minecraft.world.entity.player.Player player,InteractionHand hand,BlockHitResult hit) { return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION; }
    @Override public ItemStack getCloneItemStack(BlockState state,net.minecraft.world.phys.HitResult target,LevelReader level,BlockPos pos,net.minecraft.world.entity.player.Player player) { return new ItemStack(this); }
}
