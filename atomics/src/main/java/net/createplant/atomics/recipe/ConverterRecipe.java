package net.createplant.atomics.recipe;
import net.createplant.atomics.registry.CEAtomicsRecipeTypes;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.createplant.registry.CERecipeTypes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

/** A whole converter charge. Counts are intentionally not limited by crafting-grid or stack-size limits. */
public final class ConverterRecipe implements Recipe<RecipeInput> {
    private static final Codec<ItemStack> OUTPUT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        BuiltInRegistries.ITEM.byNameCodec().fieldOf("id").forGetter(ItemStack::getItem),
        ExtraCodecs.POSITIVE_INT.fieldOf("count").forGetter(ItemStack::getCount)
    ).apply(instance, ItemStack::new));

    public static final MapCodec<ConverterRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        SizedIngredient.FLAT_CODEC.listOf().fieldOf("ingredients").forGetter(ConverterRecipe::itemIngredients),
        SizedFluidIngredient.FLAT_CODEC.fieldOf("gas").forGetter(ConverterRecipe::gasIngredient),
        OUTPUT_CODEC.fieldOf("result").forGetter(ConverterRecipe::result),
        FluidStack.OPTIONAL_CODEC.optionalFieldOf("byproduct", FluidStack.EMPTY).forGetter(ConverterRecipe::byproduct),
        ExtraCodecs.POSITIVE_INT.optionalFieldOf("processingTime", 800).forGetter(ConverterRecipe::duration),
        SizedFluidIngredient.FLAT_CODEC.optionalFieldOf("metal").forGetter(ConverterRecipe::metalIngredient),
        FluidStack.OPTIONAL_CODEC.optionalFieldOf("metal_result", FluidStack.EMPTY).forGetter(ConverterRecipe::metalResult),
        Codec.BOOL.optionalFieldOf("legacy", false).forGetter(ConverterRecipe::legacy)
    ).apply(instance, ConverterRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConverterRecipe> STREAM_CODEC = StreamCodec.of(
        (buffer, recipe) -> {
            ByteBufCodecs.VAR_INT.encode(buffer, recipe.itemIngredients.size());
            for (SizedIngredient ingredient : recipe.itemIngredients) SizedIngredient.STREAM_CODEC.encode(buffer, ingredient);
            SizedFluidIngredient.STREAM_CODEC.encode(buffer, recipe.gasIngredient);
            ItemStack.STREAM_CODEC.encode(buffer, recipe.result);
            FluidStack.OPTIONAL_STREAM_CODEC.encode(buffer, recipe.byproduct);
            ByteBufCodecs.VAR_INT.encode(buffer, recipe.duration);
            ByteBufCodecs.optional(SizedFluidIngredient.STREAM_CODEC).encode(buffer, recipe.metalIngredient);
            FluidStack.OPTIONAL_STREAM_CODEC.encode(buffer, recipe.metalResult);
            buffer.writeBoolean(recipe.legacy);
        },
        buffer -> {
            int size = Math.clamp(ByteBufCodecs.VAR_INT.decode(buffer), 0, 64);
            NonNullList<SizedIngredient> ingredients = NonNullList.create();
            for (int i = 0; i < size; i++) ingredients.add(SizedIngredient.STREAM_CODEC.decode(buffer));
            return new ConverterRecipe(ingredients, SizedFluidIngredient.STREAM_CODEC.decode(buffer),
                ItemStack.STREAM_CODEC.decode(buffer), FluidStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.optional(SizedFluidIngredient.STREAM_CODEC).decode(buffer),
                FluidStack.OPTIONAL_STREAM_CODEC.decode(buffer), buffer.readBoolean());
        });

    private final List<SizedIngredient> itemIngredients;
    private final SizedFluidIngredient gasIngredient;
    private final ItemStack result;
    private final FluidStack byproduct;
    private final int duration;
    private final Optional<SizedFluidIngredient> metalIngredient;
    private final FluidStack metalResult;
    private final boolean legacy;

    public ConverterRecipe(List<SizedIngredient> itemIngredients, SizedFluidIngredient gasIngredient, ItemStack result,
        FluidStack byproduct, int duration, Optional<SizedFluidIngredient> metalIngredient, FluidStack metalResult,
        boolean legacy) {
        this.itemIngredients = List.copyOf(itemIngredients);
        this.gasIngredient = gasIngredient;
        this.result = result.copy();
        this.byproduct = byproduct.copy();
        this.duration = duration;
        this.metalIngredient = metalIngredient;
        this.metalResult = metalResult.copy();
        this.legacy = legacy;
    }

    public List<SizedIngredient> itemIngredients() { return itemIngredients; }
    public SizedFluidIngredient gasIngredient() { return gasIngredient; }
    public ItemStack result() { return result.copy(); }
    public FluidStack byproduct() { return byproduct.copy(); }
    public int duration() { return duration; }
    public Optional<SizedFluidIngredient> metalIngredient() { return metalIngredient; }
    public FluidStack metalResult() { return metalResult.copy(); }
    public boolean legacy() { return legacy; }

    @Override public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> result = NonNullList.create();
        for (SizedIngredient ingredient : itemIngredients) result.add(ingredient.ingredient());
        return result;
    }

    @Override public boolean matches(RecipeInput input, Level level) { return false; }
    @Override public ItemStack assemble(RecipeInput input, HolderLookup.Provider registries) { return result(); }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public ItemStack getResultItem(HolderLookup.Provider registries) { return result(); }
    @Override public boolean isSpecial() { return true; }
    @Override public RecipeSerializer<?> getSerializer() { return CEAtomicsRecipeTypes.CONVERTER.getSerializer(); }
    @Override public RecipeType<?> getType() { return CEAtomicsRecipeTypes.CONVERTER.getType(); }

    public static final class Serializer implements RecipeSerializer<ConverterRecipe> {
        @Override public MapCodec<ConverterRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, ConverterRecipe> streamCodec() { return STREAM_CODEC; }
    }
}
