package net.createplant.block;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.createplant.block.entity.ReliefScrubberBlockEntity;
import net.createplant.registry.CECompressor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A compact wash vessel: relief gas enters below, caustic is serviced at the marked side and residual gas leaves above. */
public class ReliefScrubberBlock extends Block implements IBE<ReliefScrubberBlockEntity>, IWrenchable {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ACTIVE=BooleanProperty.create("active");
    private static final double[][] BOXES={{1,0,1,15,4,15},{2,4,2,14,13,14},{5,13,5,11,16,11},{5,6,0,11,10,3}};
    private static final VoxelShape[] SHAPES=new VoxelShape[4];
    static { for (Direction facing:Direction.Plane.HORIZONTAL) SHAPES[facing.get2DDataValue()]=CompressorShapes.of(BOXES,facing); }

    public ReliefScrubberBlock(Properties properties) { super(properties);registerDefaultState(defaultBlockState().setValue(FACING,Direction.NORTH).setValue(ACTIVE,false)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(FACING,ACTIVE); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite()); }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) { return SHAPES[state.getValue(FACING).get2DDataValue()]; }
    @Override public Class<ReliefScrubberBlockEntity> getBlockEntityClass() { return ReliefScrubberBlockEntity.class; }
    @Override public BlockEntityType<? extends ReliefScrubberBlockEntity> getBlockEntityType() { return CECompressor.RELIEF_SCRUBBER_ENTITY.get(); }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moved) {
        super.onRemove(state,level,pos,next,moved);IBE.onRemove(state,level,pos,next);
    }
}
