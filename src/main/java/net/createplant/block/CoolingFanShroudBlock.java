package net.createplant.block;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;

/** Fan ring and guard around a cooling fan hub. FACING points to the hub (SIDE), or the hub lies at FACING plus its counter-clockwise neighbour (CORNER). */
public class CoolingFanShroudBlock extends Block {
    public enum Part implements StringRepresentable {
        NONE, SIDE, CORNER;
        @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
    }
    public static final EnumProperty<Part> PART = EnumProperty.create("part",Part.class);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    // Boxes for FACING=south, matching the unrotated block models.
    private static final double[][] SIDE = {{0,0,0,16,14,3},{0,14,0,16,16,16}};
    private static final double[][] CORNER = {{0,0,0,16,14,3},{0,0,3,3,14,16},{3,0,3,7,14,7},{0,14,0,16,16,16}};
    private static final VoxelShape DECK = box(0,0,0,16,2,16);
    private static final Map<Part,Map<Direction,VoxelShape>> SHAPES = new EnumMap<>(Part.class);
    static {
        for (Part part:Part.values()) {
            Map<Direction,VoxelShape> byFacing=new EnumMap<>(Direction.class);
            for (Direction facing:Direction.Plane.HORIZONTAL)
                byFacing.put(facing,part==Part.NONE ? DECK : rotated(part==Part.SIDE ? SIDE : CORNER,facing));
            SHAPES.put(part,byFacing);
        }
    }
    public CoolingFanShroudBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(PART,Part.NONE).setValue(FACING,Direction.SOUTH));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(PART,FACING); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(PART)).get(state.getValue(FACING));
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos=context.getClickedPos();
        for (int dx=-1;dx<=1;dx++) for (int dz=-1;dz<=1;dz++)
            if ((dx!=0 || dz!=0) && context.getLevel().getBlockState(pos.offset(dx,0,dz)).getBlock() instanceof CoolingFanBlock)
                return around(defaultBlockState(),dx,dz);
        return defaultBlockState();
    }
    /** Orients a shroud whose hub lies at the given horizontal offset. */
    public static BlockState around(BlockState state, int dx, int dz) {
        if (dx==0 || dz==0) return state.setValue(PART,Part.SIDE).setValue(FACING,Direction.fromDelta(dx,0,dz));
        for (Direction facing:Direction.Plane.HORIZONTAL) {
            Direction next=facing.getCounterClockWise();
            if (facing.getStepX()+next.getStepX()==dx && facing.getStepZ()+next.getStepZ()==dz)
                return state.setValue(PART,Part.CORNER).setValue(FACING,facing);
        }
        return state;
    }
    // Same turn as the blockstate y rotation: south 0, west 90, north 180, east 270.
    private static VoxelShape rotated(double[][] boxes, Direction facing) {
        VoxelShape shape=Shapes.empty();
        for (double[] b:boxes) {
            double[] r=b.clone();
            for (int turn=0;turn<facing.get2DDataValue();turn++) r=new double[]{16-r[5],r[1],r[0],16-r[2],r[4],r[3]};
            shape=Shapes.or(shape,box(r[0],r[1],r[2],r[3],r[4],r[5]));
        }
        return shape;
    }
}
