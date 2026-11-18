package net.createplant.block;

import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.createplant.block.entity.FractionatingColumnBlockEntity;
import net.createplant.registry.CEDistillation;
import net.minecraft.core.*;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;

public class FractionatingColumnBlock extends Block implements IBE<FractionatingColumnBlockEntity>, IWrenchable {
    public enum Section implements StringRepresentable {
        SINGLE, BASE, SERVICE, BODY, TOP;
        @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
    }
    public static final IntegerProperty WIDTH=IntegerProperty.create("width",1,3), CELL=IntegerProperty.create("cell",0,8), CUT=IntegerProperty.create("cut",0,5), INLET=IntegerProperty.create("inlet",0,2);
    public static final EnumProperty<Section> SECTION=EnumProperty.create("section",Section.class);
    public FractionatingColumnBlock(Properties properties) {
        super(properties);registerDefaultState(defaultBlockState().setValue(WIDTH,1).setValue(CELL,0).setValue(CUT,0).setValue(INLET,0).setValue(SECTION,Section.SINGLE));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(WIDTH,CELL,CUT,INLET,SECTION); }
    @Override public Class<FractionatingColumnBlockEntity> getBlockEntityClass() { return FractionatingColumnBlockEntity.class; }
    @Override public BlockEntityType<? extends FractionatingColumnBlockEntity> getBlockEntityType() { return CEDistillation.COLUMN_ENTITY.get(); }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) { return FractionatingColumnShapes.of(state); }
    @Override public void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moved) {
        super.onPlace(state,level,pos,old,moved);
        if (!level.isClientSide() && state.getBlock()!=old.getBlock()) changed(level,pos);
    }
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moved) {
        if (!level.isClientSide() && state.getBlock()!=next.getBlock() && level.getBlockEntity(pos) instanceof FractionatingColumnBlockEntity be) {
            be.vent();level.removeBlockEntity(pos);ConnectivityHandler.splitMulti(be);changed(level,pos);
        }
        super.onRemove(state,level,pos,next,moved);
    }
    public static void changed(Level level,BlockPos pos) {
        if (level.isClientSide()) return;
        if (level.getBlockEntity(pos) instanceof FractionatingColumnBlockEntity be) be.requestConnectivity();
        for (Direction side:Direction.values()) {
            BlockPos at=pos.relative(side);
            if (level.hasChunkAt(at) && level.getBlockEntity(at) instanceof FractionatingColumnBlockEntity be) be.requestConnectivity();
        }
    }
}
