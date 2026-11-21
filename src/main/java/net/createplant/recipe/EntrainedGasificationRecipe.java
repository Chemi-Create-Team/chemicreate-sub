package net.createplant.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.createplant.registry.CEProcessFluids;
import net.createplant.registry.CERecipeTypes;
import net.createplant.registry.CEThermal;
import net.createplant.mixture.MixtureHelper;
import net.createplant.thermal.SteamGrades;
import net.createplant.thermal.Grade;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

public record EntrainedGasificationRecipe(Ingredient ingredient, SizedFluidIngredient steam,
    int duration, FluidStack gasResult, int steamCarryover) implements Recipe<RecipeInput> {

    private static final Codec<FluidStack> GAS = RecordCodecBuilder.<FluidStack>create(instance -> instance.group(
        BuiltInRegistries.FLUID.byNameCodec().fieldOf("fluid").forGetter(FluidStack::getFluid),
        Codec.intRange(1, 16000).fieldOf("amount").forGetter(FluidStack::getAmount)
    ).apply(instance, FluidStack::new)).validate(stack -> stack.is(CEProcessFluids.WATER_GAS.get())
        ? DataResult.success(stack) : DataResult.error(() -> "Expected water_gas (a carbon monoxide and hydrogen mixture)"));

    private static final MapCodec<EntrainedGasificationRecipe> CODEC = RecordCodecBuilder.<EntrainedGasificationRecipe>mapCodec(instance -> instance.group(
        Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(EntrainedGasificationRecipe::ingredient),
        SizedFluidIngredient.FLAT_CODEC.fieldOf("steam").forGetter(EntrainedGasificationRecipe::steam),
        Codec.intRange(1, 72000).fieldOf("duration").forGetter(EntrainedGasificationRecipe::duration),
        GAS.fieldOf("gas_result").forGetter(EntrainedGasificationRecipe::gasResult),
        Codec.intRange(0, 16000).optionalFieldOf("steam_carryover", 0).forGetter(EntrainedGasificationRecipe::steamCarryover)
    ).apply(instance, EntrainedGasificationRecipe::new)).validate(recipe ->
        recipe.steamCarryover() + recipe.gasResult.getAmount() <= 16000 && recipe.steamCarryover() <= recipe.steam().amount()
            ? DataResult.success(recipe) : DataResult.error(() -> "Steam carryover exceeds the gas product or steam feed"));
    private static final StreamCodec<RegistryFriendlyByteBuf, EntrainedGasificationRecipe> STREAM =
        ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());

    @Override public FluidStack gasResult() { return gasResult.copy(); }
    public FluidStack hotGasResult() {
        var product = gasResult();
        net.createplant.thermal.FluidState.setTemperature(product,12);
        if (steamCarryover == 0) return product;
        var steam = SteamGrades.ORDINARY.stack(steamCarryover);
        net.createplant.thermal.FluidState.setTemperature(steam,12);
        return MixtureHelper.merge(product, steam, true);
    }
    @Override public NonNullList<Ingredient> getIngredients() { return NonNullList.of(Ingredient.EMPTY, ingredient); }
    @Override public boolean matches(RecipeInput input, Level level) { return false; }
    @Override public ItemStack assemble(RecipeInput input, HolderLookup.Provider lookup) { return ItemStack.EMPTY; }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public ItemStack getResultItem(HolderLookup.Provider lookup) { return ItemStack.EMPTY; }
    @Override public boolean isSpecial() { return true; }
    @Override public RecipeSerializer<?> getSerializer() { return CERecipeTypes.ENTRAINED_GASIFICATION.getSerializer(); }
    @Override public RecipeType<?> getType() { return CERecipeTypes.ENTRAINED_GASIFICATION.getType(); }

    public static final class Serializer implements RecipeSerializer<EntrainedGasificationRecipe> {
        @Override public MapCodec<EntrainedGasificationRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, EntrainedGasificationRecipe> streamCodec() { return STREAM; }
    }
}
