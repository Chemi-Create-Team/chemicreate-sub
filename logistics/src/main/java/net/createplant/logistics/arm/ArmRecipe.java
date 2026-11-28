package net.createplant.logistics.arm;
import net.createplant.logistics.registry.CELogisticsRecipeTypes;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.createplant.registry.CERecipeTypes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * An assembly the industrial arms do on an item lying on a depot or belt they take from, like Create's sequenced assembly but by
 * arms: the steps are done in order, each by an arm with the step's tool, using up the step's parts. Between steps the item is the
 * transitional item (or stays the input) and carries its progress; after the last step it becomes the result.
 */
public record ArmRecipe(Item input, Optional<Item> transitional, List<Step> steps, ItemStack result) implements Recipe<RecipeInput> {
    /** Most parts one step may use up; a core memory plane takes 144 cores. */
    public static final int MAX_CONSUME = 1024;
    private static final String TAG = "create_refinery_n_reactors:arm_assembly";

    /** Parts a step uses up. */
    public record Part(Item item, int count) {
        static final Codec<Part> CODEC = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("id").forGetter(Part::item),
            Codec.intRange(1, MAX_CONSUME).optionalFieldOf("count", 1).forGetter(Part::count)
        ).apply(i, Part::new));
    }

    /** One step: {@code tool} works the item along {@code pattern} for {@code time} ticks at 16 rpm, using up {@code consume}, with {@code helpers} more bare arms. */
    public record Step(ArmTool tool, ArmPattern pattern, Optional<Part> consume, int helpers, int time) {
        static final Codec<Step> CODEC = RecordCodecBuilder.<Step>create(i -> i.group(
            ArmTool.CODEC.optionalFieldOf("tool", ArmTool.HAND).forGetter(Step::tool),
            ArmPattern.CODEC.optionalFieldOf("pattern").forGetter(s -> Optional.of(s.pattern)),
            Part.CODEC.optionalFieldOf("consume").forGetter(Step::consume),
            Codec.intRange(0, 1).optionalFieldOf("helpers", 0).forGetter(Step::helpers),
            Codec.intRange(1, 72000).optionalFieldOf("time", 40).forGetter(Step::time)
        ).apply(i, (tool, pattern, consume, helpers, time) -> new Step(tool, pattern.orElse(ArmPattern.defaultFor(tool)), consume, helpers, time)))
            .validate(s -> (s.tool == ArmTool.WELDING_TORCH) == (s.pattern == ArmPattern.SEAM_WELD) ? DataResult.success(s)
                : DataResult.error(() -> "welding needs the welding torch, and the torch only welds"));
    }

    public static final MapCodec<ArmRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        BuiltInRegistries.ITEM.byNameCodec().fieldOf("input").forGetter(ArmRecipe::input),
        BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("transitional").forGetter(ArmRecipe::transitional),
        ExtraCodecs.nonEmptyList(Step.CODEC.listOf()).fieldOf("steps").forGetter(ArmRecipe::steps),
        ItemStack.CODEC.fieldOf("result").forGetter(ArmRecipe::result)
    ).apply(i, ArmRecipe::new));
    private static final StreamCodec<RegistryFriendlyByteBuf, ArmRecipe> STREAM = ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());

    /** The item between steps. */
    public Item between() {
        return transitional.orElse(input);
    }

    public static List<RecipeHolder<ArmRecipe>> all(Level level) {
        return level.getRecipeManager().getAllRecipesFor(CELogisticsRecipeTypes.ARM_ASSEMBLY.<RecipeInput, ArmRecipe>getType());
    }

    /** A recipe and the step of it to do next. */
    public record Next(RecipeHolder<ArmRecipe> recipe, int step) {
        public Step get() {
            return recipe.value().steps.get(step);
        }
    }

    /** What may be done next on this stack: a stack partway carries its recipe and step; a fresh one may start any recipe it is the input of. */
    @SuppressWarnings("unchecked")
    public static List<Next> next(Level level, ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null && data.contains(TAG)) {
            CompoundTag t = data.copyTag().getCompound(TAG);
            ResourceLocation id = ResourceLocation.tryParse(t.getString("recipe"));
            int step = t.getInt("step");
            var holder = id == null ? Optional.<RecipeHolder<?>>empty() : level.getRecipeManager().byKey(id);
            if (holder.isPresent() && holder.get().value() instanceof ArmRecipe r && step > 0 && step < r.steps.size() && stack.is(r.between()))
                return List.of(new Next((RecipeHolder<ArmRecipe>) holder.get(), step));
            return List.of(); // a recipe that is gone or changed leaves the item as it is
        }
        return all(level).stream().filter(h -> stack.is(h.value().input)).map(h -> new Next(h, 0)).toList();
    }

    /** One item after {@code step} is done: the result after the last step, otherwise the transitional item carrying the next step. */
    public ItemStack after(ResourceLocation id, int step) {
        if (step + 1 >= steps.size()) return result.copy();
        ItemStack s = new ItemStack(between());
        CompoundTag t = new CompoundTag();
        t.putString("recipe", id.toString());
        t.putInt("step", step + 1);
        CompoundTag root = new CompoundTag();
        root.put(TAG, t);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        return s;
    }

    public boolean usesTorch() {
        return steps.stream().anyMatch(s -> s.tool == ArmTool.WELDING_TORCH);
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        list.add(Ingredient.of(input));
        for (Step s : steps) s.consume.ifPresent(p -> list.add(Ingredient.of(p.item())));
        return list;
    }

    @Override public boolean matches(RecipeInput input, Level level) { return false; }
    @Override public ItemStack assemble(RecipeInput input, HolderLookup.Provider lookup) { return result.copy(); }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public ItemStack getResultItem(HolderLookup.Provider lookup) { return result; }
    @Override public boolean isSpecial() { return true; }
    @Override public RecipeSerializer<?> getSerializer() { return CELogisticsRecipeTypes.ARM_ASSEMBLY.getSerializer(); }
    @Override public RecipeType<?> getType() { return CELogisticsRecipeTypes.ARM_ASSEMBLY.getType(); }

    public static final class Serializer implements RecipeSerializer<ArmRecipe> {
        @Override public MapCodec<ArmRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, ArmRecipe> streamCodec() { return STREAM; }
    }
}
