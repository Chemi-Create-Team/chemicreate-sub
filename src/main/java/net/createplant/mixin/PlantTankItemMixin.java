package net.createplant.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.content.fluids.tank.FluidTankItem;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import net.createplant.block.GasHolderBlock;
import net.createplant.block.PlantFluidTankBlock;
import net.createplant.item.PlantTankItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value=FluidTankItem.class,remap=false)
public class PlantTankItemMixin {
    @WrapOperation(method="tryMultiPlace",at=@At(value="INVOKE",target="Lcom/simibubi/create/api/connectivity/ConnectivityHandler;partAt(Lnet/minecraft/world/level/block/entity/BlockEntityType;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
    private BlockEntity createPlant$type(BlockEntityType<?> type,BlockGetter level,BlockPos pos,Operation<BlockEntity> original) {
        var block=((BlockItem)(Object)this).getBlock();
        if (!(block instanceof PlantFluidTankBlock) && !(block instanceof GasHolderBlock)) return original.call(type,level,pos);
        var tank=(FluidTankBlock)block;
        var part=original.call(tank.getBlockEntityType(),level,pos);
        return part instanceof FluidTankBlockEntity be && be.getControllerBE()!=null && !PlantTankItem.bulkPlaces(tank,be.getControllerBE().getWidth()) ? null : part;
    }
}
