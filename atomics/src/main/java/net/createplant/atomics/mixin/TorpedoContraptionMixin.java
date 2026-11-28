package net.createplant.atomics.mixin;

import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.MountedStorageManager;
import com.simibubi.create.content.contraptions.StructureTransform;
import net.createplant.CreatePlant;
import net.createplant.block.TankCells;
import net.createplant.atomics.block.entity.TorpedoTankBlockEntity;
import net.createplant.atomics.contraptions.TorpedoStorage;
import net.createplant.atomics.registry.CEConverter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Contraption.class, remap = false)
public abstract class TorpedoContraptionMixin {
    @Shadow protected MountedStorageManager storage;

    @Inject(method = "searchMovedStructure", at = @At("RETURN"))
    private void createPlant$requireWholeTorpedo(Level world, BlockPos anchor, Direction forced,
        CallbackInfoReturnable<Boolean> callback) throws AssemblyException {
        if (!callback.getReturnValue()) return;
        var blocks = ((Contraption) (Object) this).getBlocks();
        for (var info : blocks.values()) {
            if (!info.state().is(CEConverter.TORPEDO_TANK.get())) continue;
            if (!(world.getBlockEntity(anchor.offset(info.pos())) instanceof TorpedoTankBlockEntity part)
                || !(part.getControllerBE() instanceof TorpedoTankBlockEntity root)
                || !blocks.containsKey(root.getBlockPos().subtract(anchor))) throw incomplete();
            if (!part.isController()) continue;
            if (!root.hasFullSection()) throw new AssemblyException(Component.translatable(TorpedoTankBlockEntity.SECTION_REQUIRED));
            int width = root.getWidth();
            for (int length = 0; length < root.getHeight(); length++)
                for (int u = 0; u < width; u++)
                    for (int v = 0; v < width; v++) {
                        var cell = blocks.get(TankCells.at(root, length, u, v).subtract(anchor));
                        if (cell == null || !cell.state().is(CEConverter.TORPEDO_TANK.get())) throw incomplete();
                    }
        }
    }

    @Inject(method = "addBlocksToWorld", at = @At("HEAD"))
    private void createPlant$restoreMovedTankContents(Level world, StructureTransform transform, CallbackInfo callback) {
        var contraption = (Contraption) (Object) this;
        if (contraption.getBlocks().values().stream().noneMatch(info -> info.state().is(CEConverter.TORPEDO_TANK.get()))) return;
        // Create may move the tank's controller to another block when restoring a rotated carriage.
        storage.getFluids().storages.forEach((pos, mounted) -> {
            if (!(mounted instanceof TorpedoStorage tank)) return;
            var info = contraption.getBlocks().get(pos);
            if (info == null || info.nbt() == null) return;
            var inventory = new CompoundTag();
            if (!tank.getFluid().isEmpty()) inventory.put("Fluid", tank.getFluid().save(world.registryAccess()));
            info.nbt().put("TankContent", inventory);
        });
    }

    private static AssemblyException incomplete() {
        return new AssemblyException(Component.translatable("gui.assembly.exception." + CreatePlant.MOD_ID + ".torpedo_incomplete"));
    }
}
