package net.createplant.block;

import com.simibubi.create.content.kinetics.base.AbstractEncasedShaftBlock;
import com.simibubi.create.foundation.block.IBE;
import net.createplant.block.entity.ReactorMachineBlockEntity;
import net.createplant.chemistry.ReactorModule;
import net.createplant.registry.CEReactor;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.*;

public class ReactorMachineBlock extends AbstractEncasedShaftBlock implements IBE<ReactorMachineBlockEntity> {
    public final ReactorModule module;
    public ReactorMachineBlock(Properties properties,ReactorModule module) {
        super(properties);this.module=module;
        registerDefaultState(defaultBlockState().setValue(AXIS,Direction.Axis.Y).setValue(BlockStateProperties.LIT,false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block,BlockState> builder) {
        super.createBlockStateDefinition(builder);builder.add(BlockStateProperties.LIT);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        // The drive sits in the middle of the bottom layer, ringed by jackets, so its shaft comes up from below.
        return super.getStateForPlacement(context).setValue(AXIS,Direction.Axis.Y);
    }
    @Override public InteractionResult onWrenched(BlockState state,UseOnContext context) {
        return InteractionResult.PASS;
    }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) { return ReactorShapes.machine(state); }
    @Override public Class<ReactorMachineBlockEntity> getBlockEntityClass() { return ReactorMachineBlockEntity.class; }
    @Override public BlockEntityType<? extends ReactorMachineBlockEntity> getBlockEntityType() { return CEReactor.ENTITY.get(); }
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving) {
        if (state.getBlock()!=next.getBlock() && level.getBlockEntity(pos) instanceof ReactorMachineBlockEntity be) be.dismantle();
        super.onRemove(state,level,pos,next,moving);
    }
}
