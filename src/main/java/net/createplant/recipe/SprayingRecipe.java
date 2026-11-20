package net.createplant.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.createplant.CreatePlant;
import net.createplant.registry.CERecipeTypes;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

public record SprayingRecipe(SizedFluidIngredient liquid, Effect effect) implements Recipe<RecipeInput> {
    public enum Effect implements StringRepresentable {
        GROWTH("growth"), PROTECTION("protection");

        private final String name;
        Effect(String name) { this.name = name; }
        @Override public String getSerializedName() { return name; }
        public Component description(int dose) {
            return Component.translatable("jei." + CreatePlant.MOD_ID + ".spraying." + name, dose);
        }
    }

    private static final Codec<Effect> EFFECTS = StringRepresentable.fromEnum(Effect::values);
    private static final MapCodec<SprayingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        SizedFluidIngredient.FLAT_CODEC.fieldOf("liquid").forGetter(SprayingRecipe::liquid),
        EFFECTS.fieldOf("effect").forGetter(SprayingRecipe::effect)
    ).apply(instance, SprayingRecipe::new));
    private static final StreamCodec<RegistryFriendlyByteBuf, SprayingRecipe> STREAM =
        ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());

    public static List<RecipeHolder<SprayingRecipe>> available(Level level) {
        return level.getRecipeManager().getAllRecipesFor(CERecipeTypes.SPRAYING.<RecipeInput, SprayingRecipe>getType());
    }

    @Override public boolean matches(RecipeInput input, Level level) { return false; }
    @Override public ItemStack assemble(RecipeInput input, HolderLookup.Provider lookup) { return ItemStack.EMPTY; }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public ItemStack getResultItem(HolderLookup.Provider lookup) { return ItemStack.EMPTY; }
    @Override public boolean isSpecial() { return true; }
    @Override public RecipeSerializer<?> getSerializer() { return CERecipeTypes.SPRAYING.getSerializer(); }
    @Override public RecipeType<?> getType() { return CERecipeTypes.SPRAYING.getType(); }

    public static final class Serializer implements RecipeSerializer<SprayingRecipe> {
        @Override public MapCodec<SprayingRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, SprayingRecipe> streamCodec() { return STREAM; }
    }
}
