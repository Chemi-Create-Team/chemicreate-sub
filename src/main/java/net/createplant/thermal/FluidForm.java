package net.createplant.thermal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.createplant.CreatePlant;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * A named state of a substance, such as steam or liquid nitrogen. There is one fluid for each substance (water, nitrogen gas, ...) and
 * its temperature and pressure are data on the stack (docs/fluid-states.md); a form is only the name a recipe or a machine gives to a
 * range of those states. It is not registered, so it has no bucket, block or item of its own.
 */
public enum FluidForm {
    WARM_WATER("minecraft:water",9,4,Condition.LIQUID),
    STEAM("minecraft:water",12,4,Condition.STEAM),
    HIGH_PRESSURE_STEAM("minecraft:water",12,12,Condition.STEAM),
    SUPERHEATED_STEAM("minecraft:water",16,12,Condition.STEAM),
    PRESSURISED_WATER("minecraft:water",10,8,Condition.PRESSURED_LIQUID),
    HOT_REACTOR_COOLANT("minecraft:water",11,12,Condition.HOT_LIQUID),
    LIQUID_NITROGEN("nitrogen_gas",1,4,Condition.LIQUID),
    LIQUID_OXYGEN("oxygen_gas",1,4,Condition.LIQUID),
    LIQUID_HELIUM("helium_gas",0,4,Condition.LIQUID),
    LIQUID_AMMONIA("ammonia_gas",4,4,Condition.LIQUID),
    LIQUID_CHLORINE("chlorine",4,4,Condition.LIQUID),
    LIQUID_SULFUR_DIOXIDE("sulfur_dioxide",6,4,Condition.LIQUID),
    LIQUID_VINYL_CHLORIDE("vinyl_chloride",5,4,Condition.LIQUID),
    LIQUID_PROPYLENE("propylene",4,4,Condition.LIQUID),
    COMPRESSED_AIR("air_gas",8,8,Condition.PRESSURE),
    PRESSURISED_HYDROGEN("hydrogen_gas",8,8,Condition.PRESSURE),
    COMPRESSED_HYDROGEN("hydrogen_gas",8,12,Condition.PRESSURE),
    PRESSURISED_CARBON_MONOXIDE("carbon_monoxide",8,8,Condition.PRESSURE),
    COMPRESSED_SYNTHESIS_FEED("synthesis_gas",8,8,Condition.PRESSURE),
    HOT_SYNTHESIS_MIXTURE("cold_synthesis_mixture",12,8,Condition.HOT),
    HOT_NITRIC_OXIDE_MIXTURE("cold_nitric_oxide_mixture",12,4,Condition.HOT),
    HOT_OLEFINS("cooled_olefins",12,4,Condition.HOT),
    HOT_HYDROTREATER_EFFLUENT("cooled_hydrotreater_effluent",12,8,Condition.HOT),
    HOT_HYDROCRACKER_EFFLUENT("cooled_hydrocracker_effluent",12,8,Condition.HOT),
    WARM_SLIME_COOLANT("slime_coolant",9,4,Condition.HOT),
    PREHEATED_CRUDE_OIL("createdieselgenerators:crude_oil",12,4,Condition.HOT);

    private enum Condition { LIQUID, STEAM, HOT, PRESSURE, HOT_LIQUID, PRESSURED_LIQUID }

    private final ResourceLocation substance;
    private final int temperature, pressure;
    private final Condition condition;

    FluidForm(String substance,int temperature,int pressure,Condition condition) {
        this.substance=substance.contains(":") ? ResourceLocation.parse(substance) : CreatePlant.location(substance);
        this.temperature=temperature;
        this.pressure=pressure;
        this.condition=condition;
    }

    /** The name of the state, which also names its rows in the material tables (for example {@code liquid_nitrogen}). */
    public String id() { return name().toLowerCase(Locale.ROOT); }

    /** The one fluid of the substance. */
    public Fluid fluid() { return BuiltInRegistries.FLUID.get(substance); }

    public int temperature() { return temperature; }
    public int pressure() { return pressure; }

    /** {@code amount} mB of the substance at this state's own temperature and pressure. */
    public FluidStack stack(int amount) {
        var stack=new FluidStack(fluid(),amount);
        FluidState.setTemperature(stack,stack.getFluid(),temperature);
        FluidState.setPressure(stack,stack.getFluid(),pressure);
        return stack;
    }

    public FluidRequirement requirement() {
        return switch(condition) {
            case LIQUID -> new FluidRequirement(temperature<8 ? 0 : temperature,16,0,16,FluidRequirement.Phase.LIQUID);
            case STEAM -> new FluidRequirement(temperature==16 ? 16 : 0,16,pressure==4 ? 0 : pressure,pressure==4 ? 7 : 16,FluidRequirement.Phase.GAS);
            case HOT -> new FluidRequirement(temperature,16,0,16,FluidRequirement.Phase.ANY);
            case PRESSURE -> new FluidRequirement(0,16,pressure,16,FluidRequirement.Phase.GAS);
            case HOT_LIQUID, PRESSURED_LIQUID -> new FluidRequirement(temperature,16,pressure,16,FluidRequirement.Phase.LIQUID);
        };
    }

    /** Whether the stack is this substance in this state. */
    public boolean matches(FluidStack stack) {
        return !stack.isEmpty() && stack.getFluid()==fluid() && requirement().matches(stack);
    }

    /** Every form of a substance, in declaration order. */
    public static List<FluidForm> of(Fluid substance) {
        var forms=new ArrayList<FluidForm>();
        for(var form:values())if(form.fluid()==substance)forms.add(form);
        return forms;
    }

    /** The liquid form of a gas that can be liquefied, or {@code null}. */
    public static FluidForm liquidOf(Fluid substance) {
        for(var form:values())if(form.condition==Condition.LIQUID && form.name().startsWith("LIQUID_") && form.fluid()==substance)return form;
        return null;
    }

    /** Whether the fluid is a substance that has named forms, so its state is data on the stack. */
    public static boolean isSubstance(Fluid fluid) {
        for(var form:values())if(form.fluid()==fluid)return true;
        return false;
    }
}
