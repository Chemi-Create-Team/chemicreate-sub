package net.createplant.block;

import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import net.createplant.block.entity.GasHolderBlockEntity;
import net.createplant.registry.CEGasBell;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class GasHolderBlock extends FluidTankBlock {
    public GasHolderBlock(Properties properties) { super(properties,false); }
    @Override public BlockEntityType<? extends FluidTankBlockEntity> getBlockEntityType() { return CEGasBell.TANK_ENTITY.get(); }
    @Override public InteractionResult onSneakWrenched(BlockState state,UseOnContext context) {
        if (context.getLevel().getBlockEntity(context.getClickedPos()) instanceof GasHolderBlockEntity part) {
            var owner=part.getControllerBE();
            if (owner!=null && !owner.gas().isEmpty()) {if (!context.getLevel().isClientSide) owner.purge();return InteractionResult.SUCCESS;}
        }
        return super.onSneakWrenched(state,context);
    }
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving) {
        if (!state.is(next.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof GasHolderBlockEntity part) part.breakHolder();
        super.onRemove(state,level,pos,next,moving);
    }
}
