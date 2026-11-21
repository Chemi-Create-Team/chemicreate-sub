package net.createplant.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.Stream;
import net.createplant.CreatePlant;
import net.createplant.thermal.FluidRequirement;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.FluidIngredientType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class StateFluidIngredient extends FluidIngredient {
    private static final MapCodec<StateFluidIngredient> CODEC=RecordCodecBuilder.mapCodec(i->i.group(
        BuiltInRegistries.FLUID.holderByNameCodec().fieldOf("fluid").forGetter(ingredient->ingredient.fluid),
        FluidRequirement.CODEC.forGetter(ingredient->ingredient.condition)
    ).apply(i,StateFluidIngredient::new));
    private static final DeferredRegister<FluidIngredientType<?>> TYPES=DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_INGREDIENT_TYPES,CreatePlant.MOD_ID);
    private static final java.util.function.Supplier<FluidIngredientType<StateFluidIngredient>> TYPE=TYPES.register("fluid_state",
        ()->new FluidIngredientType<>(CODEC,ByteBufCodecs.fromCodecWithRegistries(CODEC.codec())));

    private final Holder<Fluid> fluid;
    private final FluidRequirement condition;

    public StateFluidIngredient(Holder<Fluid> fluid,FluidRequirement condition) {
        this.fluid=fluid;
        this.condition=condition;
    }

    public static void register(IEventBus bus) { TYPES.register(bus); }
    @Override public boolean test(FluidStack stack) { return stack.is(fluid) && condition.matches(stack); }
    @Override protected Stream<FluidStack> generateStacks() {
        var example=condition.example(new FluidStack(fluid,1));
        return example.isEmpty() ? Stream.empty() : Stream.of(example);
    }
    @Override public boolean isSimple() { return false; }
    @Override public FluidIngredientType<?> getType() { return TYPE.get(); }
    @Override public boolean equals(Object other) {
        return other instanceof StateFluidIngredient ingredient && fluid.equals(ingredient.fluid) && condition.equals(ingredient.condition);
    }
    @Override public int hashCode() { return java.util.Objects.hash(fluid,condition); }
}
