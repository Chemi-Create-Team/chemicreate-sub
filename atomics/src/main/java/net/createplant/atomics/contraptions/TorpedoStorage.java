package net.createplant.atomics.contraptions;

import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorageType;
import com.simibubi.create.content.fluids.tank.storage.FluidTankMountedStorage;
import net.createplant.atomics.block.entity.TorpedoTankBlockEntity;
import net.createplant.atomics.registry.CEConverter;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;

public final class TorpedoStorage extends FluidTankMountedStorage {
    private TorpedoStorage(int capacity, FluidStack fluid) {
        super(CEConverter.TORPEDO_STORAGE.get(), capacity, fluid);
    }

    @Override public boolean isFluidValid(int tank, FluidStack stack) {
        return tank == 0 && TorpedoTankBlockEntity.accepts(stack);
    }

    @Override public int fill(FluidStack stack, FluidAction action) {
        return TorpedoTankBlockEntity.accepts(stack) ? super.fill(stack, action) : 0;
    }

    public static final class Type extends MountedFluidStorageType<TorpedoStorage> {
        public Type() {
            super(RecordCodecBuilder.mapCodec(instance -> instance.group(
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("capacity").forGetter(TorpedoStorage::getCapacity),
                FluidStack.OPTIONAL_CODEC.fieldOf("fluid").forGetter(TorpedoStorage::getFluid)
            ).apply(instance, TorpedoStorage::new)));
        }

        @Override public TorpedoStorage mount(Level level, BlockState state, BlockPos pos, BlockEntity entity) {
            if (!(entity instanceof TorpedoTankBlockEntity tank) || !tank.isController()) return null;
            return new TorpedoStorage(tank.getTankInventory().getCapacity(), tank.getTankInventory().getFluid().copy());
        }
    }
}
