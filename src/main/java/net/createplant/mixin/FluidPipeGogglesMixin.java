package net.createplant.mixin;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;
import java.util.List;
import net.createplant.transport.PipeMaterial;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = FluidPipeBlockEntity.class, remap = false)
abstract class FluidPipeGogglesMixin implements IHaveGoggleInformation {
    @Override public boolean addToGoggleTooltip(List<Component> lines, boolean sneaking) {
        var material = PipeMaterial.pipe(((FluidPipeBlockEntity) (Object) this).getBlockState());
        if (material == null) return false;
        material.describe(lines);
        return true;
    }
}
