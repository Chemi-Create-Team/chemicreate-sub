package net.createplant.mixin;

import net.createplant.CreatePlant;
import net.createplant.registry.CEBlocks;
import net.createplant.registry.CEWetProcessing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(BlockEntity.class)
abstract class ClarifierWallLoadMixin {
    @ModifyVariable(method = "loadStatic", at = @At("HEAD"), argsOnly = true)
    private static CompoundTag createPlant$loadClarifierWallAsTank(CompoundTag tag, BlockPos pos, BlockState state) {
        if (!state.is(CEBlocks.TANK_CASING.get())) return tag;
        var savedType = ResourceLocation.tryParse(tag.getString("id"));
        if (savedType == null || BuiltInRegistries.BLOCK_ENTITY_TYPE.get(savedType) != CEWetProcessing.PART_ENTITY.get()) return tag;

        // The lining shared its saved type with the live ports, so only casing states can use the replacement.
        var migrated = tag.copy();
        migrated.putString("id", CreatePlant.location("tank_casing").toString());
        migrated.remove("Controller");
        return migrated;
    }
}
