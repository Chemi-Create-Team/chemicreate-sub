package net.createplant.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import net.createplant.block.entity.HighPressurePumpBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = PumpBlockEntity.class, remap = false)
abstract class HighPressurePumpRangeMixin {
    @WrapOperation(method = "distributePressureTo", at = @At(value = "INVOKE",
        target = "Lcom/simibubi/create/content/fluids/FluidPropagator;getPumpRange()I"))
    private int createPlant$range(Operation<Integer> original) {
        // Create truncates the search before it enqueues a pipe at the supplied range.
        return (Object) this instanceof HighPressurePumpBlockEntity ? HighPressurePumpBlockEntity.RANGE + 1 : original.call();
    }

    @WrapOperation(method = "distributePressureTo", at = @At(value = "INVOKE",
        target = "Ljava/lang/Math;abs(F)F"))
    private float createPlant$pressure(float rpm, Operation<Float> original) {
        float speed = original.call(rpm);
        return (Object) this instanceof HighPressurePumpBlockEntity ? speed * 4 : speed;
    }
}
