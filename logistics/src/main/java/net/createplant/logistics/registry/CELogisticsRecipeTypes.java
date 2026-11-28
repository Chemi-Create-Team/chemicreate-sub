package net.createplant.logistics.registry;

import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import com.simibubi.create.foundation.recipe.IRecipeTypeInfo;

import net.createplant.CreatePlant;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public enum CELogisticsRecipeTypes implements IRecipeTypeInfo, StringRepresentable {
    INDUSTRIAL_DELIVERY("industrial_delivery", () -> new StandardProcessingRecipe.Serializer<>(net.createplant.logistics.recipe.IndustrialDeliveryRecipe::new)),
    ARM_ASSEMBLY("arm_assembly", net.createplant.logistics.arm.ArmRecipe.Serializer::new);

    private final ResourceLocation id;
    private final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<?>> serializer;
    private final DeferredHolder<RecipeType<?>, RecipeType<?>> type;

    CELogisticsRecipeTypes(String id, java.util.function.Supplier<RecipeSerializer<?>> serializer) {
        this.id = CreatePlant.location(id);
        this.serializer = Registers.SERIALIZERS.register(id, serializer);
        this.type = Registers.TYPES.register(id, () -> RecipeType.simple(this.id));
    }

    public static void register(IEventBus modEventBus) {
        Registers.SERIALIZERS.register(modEventBus);
        Registers.TYPES.register(modEventBus);
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends RecipeSerializer<?>> T getSerializer() {
        return (T) serializer.get();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <I extends RecipeInput, R extends Recipe<I>> RecipeType<R> getType() {
        return (RecipeType<R>) type.get();
    }

    @Override
    public String getSerializedName() {
        return id.toString();
    }

    private static class Registers {
        private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(BuiltInRegistries.RECIPE_SERIALIZER, CreatePlant.MOD_ID);
        private static final DeferredRegister<RecipeType<?>> TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, CreatePlant.MOD_ID);
    }
}
