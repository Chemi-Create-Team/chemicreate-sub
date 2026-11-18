package net.createplant.mixin;

import com.simibubi.create.content.fluids.tank.FluidTankModel;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import net.minecraft.client.resources.model.BakedModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Create only exposes factories for its own tank textures; this reuses its connected tank model with ours. */
@Mixin(FluidTankModel.class)
public interface FluidTankModelInvoker {
    @Invoker("<init>")
    static FluidTankModel createPlant$create(BakedModel model, CTSpriteShiftEntry side, CTSpriteShiftEntry top, CTSpriteShiftEntry inner) {
        throw new AssertionError();
    }
}
