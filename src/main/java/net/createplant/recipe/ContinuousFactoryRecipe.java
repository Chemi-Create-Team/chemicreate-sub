package net.createplant.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.createplant.factory.FactoryKind;
import net.createplant.registry.CERecipeTypes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

public record ContinuousFactoryRecipe(FactoryKind machine, SizedIngredient feed, Optional<SizedIngredient> auxiliary, ItemStack result,
                                      int work, int heat, float startingThickness, int minimumDamage) implements Recipe<RecipeInput> {
    private static final MapCodec<ContinuousFactoryRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        FactoryKind.CODEC.fieldOf("machine").forGetter(ContinuousFactoryRecipe::machine),
        SizedIngredient.FLAT_CODEC.fieldOf("feed").forGetter(ContinuousFactoryRecipe::feed),
        SizedIngredient.FLAT_CODEC.optionalFieldOf("auxiliary").forGetter(ContinuousFactoryRecipe::auxiliary),
        ItemStack.STRICT_CODEC.fieldOf("result").forGetter(ContinuousFactoryRecipe::result),
        Codec.intRange(20, 12000).optionalFieldOf("work", 400).forGetter(ContinuousFactoryRecipe::work),
        Codec.intRange(0, 2).optionalFieldOf("heat", 0).forGetter(ContinuousFactoryRecipe::heat),
        Codec.floatRange(.06f, .9f).optionalFieldOf("starting_thickness", .58f).forGetter(ContinuousFactoryRecipe::startingThickness),
        Codec.intRange(0, 10000).optionalFieldOf("minimum_damage", 0).forGetter(ContinuousFactoryRecipe::minimumDamage)
    ).apply(i, ContinuousFactoryRecipe::new));
    private static final StreamCodec<RegistryFriendlyByteBuf, ContinuousFactoryRecipe> STREAM = ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());

    @Override public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(feed.ingredient());
        auxiliary.ifPresent(value -> ingredients.add(value.ingredient()));
        return ingredients;
    }
    @Override public boolean matches(RecipeInput input, Level level) {
        return input.size() >= 1 && feed.test(input.getItem(0)) && input.getItem(0).getDamageValue() >= minimumDamage
            && (auxiliary.isEmpty() || input.size() >= 2 && auxiliary.get().test(input.getItem(1)));
    }
    @Override public ItemStack assemble(RecipeInput input, HolderLookup.Provider lookup) { return result.copy(); }
    @Override public ItemStack getResultItem(HolderLookup.Provider lookup) { return result; }
    @Override public boolean isSpecial() { return true; }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public RecipeSerializer<?> getSerializer() { return CERecipeTypes.CONTINUOUS_PROCESSING.getSerializer(); }
    @Override public RecipeType<?> getType() { return CERecipeTypes.CONTINUOUS_PROCESSING.getType(); }

    public static final class Serializer implements RecipeSerializer<ContinuousFactoryRecipe> {
        @Override public MapCodec<ContinuousFactoryRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, ContinuousFactoryRecipe> streamCodec() { return STREAM; }
    }
}
