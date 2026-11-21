package net.createplant.mixin;

import com.simibubi.create.content.kinetics.fan.NozzleBlock;
import com.simibubi.create.content.kinetics.fan.NozzleBlockEntity;
import net.createplant.registry.CELegacyIds;
import net.createplant.transport.NozzleIntake;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NozzleBlockEntity.class)
public class NozzleBlockEntityMixin {
    @Shadow private BlockPos fanPos;
    @Unique private boolean createPlant$orientLegacyIntake;

    @Inject(method = "read", at = @At("HEAD"))
    private void createPlant$readLegacyIntake(CompoundTag tag, HolderLookup.Provider lookup, boolean packet, CallbackInfo callback) {
        if (!packet) createPlant$orientLegacyIntake = tag.getBoolean("CreatePlantOrientLegacyIntake") || CELegacyIds.wasAirFilter(tag.getString("id"));
    }

    @Inject(method = "write", at = @At("HEAD"))
    private void createPlant$saveLegacyIntake(CompoundTag tag, HolderLookup.Provider lookup, boolean packet, CallbackInfo callback) {
        if (!packet && createPlant$orientLegacyIntake) tag.putBoolean("CreatePlantOrientLegacyIntake", true);
    }

    @Inject(method = "lazyTick", at = @At("HEAD"))
    private void createPlant$orientLegacyIntake(CallbackInfo callback) {
        if (!createPlant$orientLegacyIntake) return;
        var nozzle = (NozzleBlockEntity) (Object) this;
        var level = nozzle.getLevel();
        if (level == null || level.isClientSide) return;
        var pos = nozzle.getBlockPos();
        for (Direction face : Direction.values()) {
            var adjacent = pos.relative(face);
            if (!level.isOutsideBuildHeight(adjacent) && !level.hasChunkAt(adjacent)) return;
        }
        createPlant$orientLegacyIntake = false;
        var state = nozzle.getBlockState();
        Direction mouth = state.getValue(NozzleBlock.FACING);
        if (!NozzleIntake.supported(level, pos, mouth)) for (Direction face : Direction.values()) {
            if (!NozzleIntake.supported(level, pos, face)) continue;
            level.setBlock(pos, state.setValue(NozzleBlock.FACING, face), Block.UPDATE_ALL);
            level.invalidateCapabilities(pos);
            fanPos = pos.relative(face.getOpposite());
            break;
        }
        nozzle.setChanged();
    }
}
