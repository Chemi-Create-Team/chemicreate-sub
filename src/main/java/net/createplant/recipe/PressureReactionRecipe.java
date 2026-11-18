package net.createplant.recipe;

import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.createplant.chemistry.ReactorModule;
import net.minecraft.util.StringRepresentable;
import net.createplant.registry.CERecipeTypes;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;

/**
 * A reaction of gases with other materials, run by stirred or tray-equipped vessels. The JSON is Create's
 * processing format: item and fluid ingredients, item and fluid results, heat_requirement and processing_time.
 */
public class PressureReactionRecipe extends StandardProcessingRecipe<RecipeInput> {
    private ReactorModule module=ReactorModule.STIRRED;
    private static final Codec<ReactorModule> MODULE_CODEC=StringRepresentable.fromEnum(ReactorModule::values)
        .validate(value->value==ReactorModule.STIRRED || value==ReactorModule.ABSORBER ? DataResult.success(value) : DataResult.error(()->"Reaction requires stirred or absorber internals"));
    public PressureReactionRecipe(ProcessingRecipeParams params) { super(CERecipeTypes.PRESSURE_REACTION, params); }
    public ReactorModule module() { return module; }

    @Override protected int getMaxInputCount() { return 9; }
    @Override protected int getMaxOutputCount() { return 4; }
    @Override protected int getMaxFluidInputCount() { return 3; }
    @Override protected int getMaxFluidOutputCount() { return 2; }
    @Override protected boolean canRequireHeat() { return true; }
    @Override protected boolean canSpecifyDuration() { return true; }
    @Override public boolean matches(RecipeInput input, Level level) { return false; }

    public static final class Serializer implements RecipeSerializer<PressureReactionRecipe> {
        private final StandardProcessingRecipe.Serializer<PressureReactionRecipe> standard=new StandardProcessingRecipe.Serializer<>(PressureReactionRecipe::new);
        private final MapCodec<PressureReactionRecipe> codec=RecordCodecBuilder.mapCodec(instance->instance.group(
            standard.codec().forGetter((PressureReactionRecipe recipe)->recipe),
            MODULE_CODEC.optionalFieldOf("module",ReactorModule.STIRRED).forGetter(PressureReactionRecipe::module)
        ).apply(instance,(recipe,module)->{recipe.module=module;return recipe;}));
        private final StreamCodec<RegistryFriendlyByteBuf,PressureReactionRecipe> stream=StreamCodec.of(
            (buffer,recipe)->{standard.streamCodec().encode(buffer,recipe);buffer.writeEnum(recipe.module);},
            buffer->{var recipe=standard.streamCodec().decode(buffer);recipe.module=buffer.readEnum(ReactorModule.class);if(recipe.module!=ReactorModule.STIRRED && recipe.module!=ReactorModule.ABSORBER)throw new IllegalArgumentException("Invalid reaction module");return recipe;});
        @Override public MapCodec<PressureReactionRecipe> codec() { return codec; }
        @Override public StreamCodec<RegistryFriendlyByteBuf,PressureReactionRecipe> streamCodec() { return stream; }
    }
}
