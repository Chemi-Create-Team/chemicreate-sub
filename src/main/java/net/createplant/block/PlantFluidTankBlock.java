package net.createplant.block;

import java.util.function.Predicate;
import java.util.function.Supplier;
import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.fluid.FluidHelper;
import net.createplant.block.entity.PlantFluidTankBlockEntity;
import net.createplant.registry.CETransport;
import net.createplant.thermal.TankGrade;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;

/** A Create fluid tank with its own multiblock type and accepted fluids; Create keeps connection, windows, rendering and pipes. */
public class PlantFluidTankBlock extends FluidTankBlock {
    private final Supplier<? extends BlockEntityType<? extends PlantFluidTankBlockEntity>> type;
    public final Predicate<FluidStack> accepts;
    public PlantFluidTankBlock(Properties properties, Supplier<? extends BlockEntityType<? extends PlantFluidTankBlockEntity>> type, Predicate<FluidStack> accepts) {
        super(properties,false);this.type=type;this.accepts=accepts;
    }
    @Override public BlockEntityType<? extends FluidTankBlockEntity> getBlockEntityType() { return type.get(); }

    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                        Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isCreative() || !state.is(CETransport.STAINLESS_STEEL_TANK.get())
            || FluidUtil.getFluidContained(stack).filter(TankGrade.STAINLESS::accepts).isEmpty())
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        if (level.getBlockEntity(pos) instanceof PlantFluidTankBlockEntity tank
            && FluidHelper.tryEmptyItemIntoBE(level, player, hand, stack, tank))
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        return ItemInteractionResult.FAIL;
    }
}
