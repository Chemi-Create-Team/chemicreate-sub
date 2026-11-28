package net.createplant.atomics.recipe;
import net.createplant.atomics.registry.CEAtomicsRecipeTypes;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.createplant.registry.CERecipeTypes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

public record LargePressingRecipe(Ingredient ingredient, ItemStack result) implements Recipe<SingleRecipeInput> {
    private static final MapCodec<LargePressingRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(LargePressingRecipe::ingredient),
        ItemStack.STRICT_CODEC.fieldOf("result").forGetter(LargePressingRecipe::result)
    ).apply(i, LargePressingRecipe::new));
    private static final StreamCodec<RegistryFriendlyByteBuf, LargePressingRecipe> STREAM = ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());

    @Override public NonNullList<Ingredient> getIngredients() { return NonNullList.of(Ingredient.EMPTY, ingredient); }
    @Override public boolean matches(SingleRecipeInput input, Level level) { return ingredient.test(input.item()); }
    @Override public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider lookup) { return result.copy(); }
    @Override public ItemStack getResultItem(HolderLookup.Provider lookup) { return result; }
    @Override public boolean isSpecial() { return true; }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public RecipeSerializer<?> getSerializer() { return CEAtomicsRecipeTypes.LARGE_PRESSING.getSerializer(); }
    @Override public RecipeType<?> getType() { return CEAtomicsRecipeTypes.LARGE_PRESSING.getType(); }

    public static final class Serializer implements RecipeSerializer<LargePressingRecipe> {
        @Override public MapCodec<LargePressingRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, LargePressingRecipe> streamCodec() { return STREAM; }
    }
}
