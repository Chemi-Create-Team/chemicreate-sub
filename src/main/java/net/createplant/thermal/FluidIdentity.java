package net.createplant.thermal;

import net.createplant.registry.CEThermal;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/** What a fluid substance is allowed to be: which fluids carry a state, and which states a recipe asks for (see {@link FluidForm}). */
public final class FluidIdentity {
    private FluidIdentity() { }

    /** Whether the fluid is a substance with named states, such as water or nitrogen, whose temperature and pressure are data on the stack. */
    public static boolean controlled(Fluid fluid) { return FluidForm.isSubstance(fluid); }

    /** Called once after a stack is built or decoded: a stack from an old save may still carry a heat grade. */
    public static void migrate(FluidStack stack) {
        if(stack.isEmpty() || !CEThermal.HEAT_GRADE.isBound())return;
        FluidState.migrateGrade(stack);
    }

    /** The states a recipe ingredient of this fluid accepts when it names no condition of its own. */
    public static FluidRequirement requirement(Fluid type) {
        var id=BuiltInRegistries.FLUID.getKey(type);
        if(id.getPath().startsWith("cold_") || id.getPath().startsWith("cooled_"))
            return new FluidRequirement(0,11,0,16,FluidRequirement.Phase.ANY);
        if(controlled(type) || FluidMaterials.known(type))return new FluidRequirement(0,16,0,16,
            FluidState.gas(new FluidStack(type,1)) ? FluidRequirement.Phase.GAS : FluidRequirement.Phase.LIQUID);
        return FluidRequirement.ANY;
    }
}
