package net.createplant.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.foundation.utility.BlockHelper;
import java.util.List;
import java.util.function.Consumer;
import net.createplant.agriculture.FertilizedCrops;
import net.createplant.event.FertilizerEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CropBlock;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = BlockHelper.class, remap = false)
abstract class CreateCropDropsMixin {
    @WrapOperation(method = "destroyBlockAs", at = @At(value = "INVOKE", ordinal = 1,
        target = "Lnet/neoforged/bus/api/IEventBus;post(Lnet/neoforged/bus/api/Event;)Lnet/neoforged/bus/api/Event;"))
    private static Event createPlant$harvesterDrops(IEventBus bus, Event event, Operation<Event> original,
            @Local List<ItemStack> drops, @Local(argsOnly = true) Consumer<ItemStack> collect) {
        // This Create version posts an empty drop-event list and uses a separate list for the contraption.
        if (!(event instanceof BlockDropsEvent crop) || !(crop.getState().getBlock() instanceof CropBlock))
            return original.call(bus, event);
        var posted = FertilizerEvents.postCreateDrops(crop, () -> original.call(bus, event));
        if (!crop.isCanceled()) {
            var bonus = FertilizedCrops.harvest(crop.getLevel(), crop.getPos(), crop.getState(), drops);
            if (!bonus.isEmpty()) collect.accept(bonus);
        }
        return posted;
    }
}
