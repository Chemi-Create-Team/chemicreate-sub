package net.createplant.thermal;

import net.createplant.CreatePlant;
import net.createplant.registry.CEThermal;
import net.createplant.transport.FluidHazards;
import net.createplant.mixture.MixtureHelper;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.fluids.FluidStack;

/** Discrete transport state. Fluid identity and recipes never depend on a continuous simulation. */
public final class FluidState {
    public static final int MAX = 16, AMBIENT = 8, NORMAL_PRESSURE = 4;
    private static final double[] KELVIN = scale(new double[]{4, 223.15, 298.15, 573.15, 1073.15}, false);
    private static final double[] BAR = scale(new double[]{.1, 1, 8, 60, 300}, true);
    private static final String TEXT = "fluid_state." + CreatePlant.MOD_ID + ".";

    private FluidState() { }

    private static double[] scale(double[] anchors, boolean logarithmic) {
        double[] levels = new double[MAX+1];
        for(int band=0;band<anchors.length-1;band++) {
            double first=anchors[band], next=anchors[band+1];
            for(int step=0;step<4;step++) {
                double fraction=step/4d;
                levels[band*4+step]=logarithmic ? first*Math.pow(next/first,fraction) : first+(next-first)*fraction;
            }
        }
        levels[MAX]=anchors[anchors.length-1];
        return levels;
    }

    public static int clamp(int level) { return Math.clamp(level,0,MAX); }
    public static double kelvin(int level) { return KELVIN[clamp(level)]; }
    public static double bar(int level) { return BAR[clamp(level)]; }
    public static int temperatureLevel(double kelvin) { return nearest(KELVIN,kelvin); }
    /** The kelvin range a level stands for: halfway to its neighbours. */
    public static double lowerBound(int level) { int l=clamp(level); return l==0 ? 0 : (KELVIN[l-1]+KELVIN[l])/2; }
    public static double upperBound(int level) { int l=clamp(level); return l==MAX ? KELVIN[MAX]*2 : (KELVIN[l]+KELVIN[l+1])/2; }
    public static int pressureLevel(double bar) { return nearest(BAR,bar); }

    private static int nearest(double[] scale,double value) {
        for(int i=0;i<MAX;i++)if(value<(scale[i]+scale[i+1])/2)return i;
        return MAX;
    }

    public static int temperature(FluidStack stack) {
        if(stack.isEmpty())return AMBIENT;
        Integer level=stack.get(CEThermal.TEMPERATURE.get());
        if(level!=null)return clamp(level);
        Integer legacy=stack.get(CEThermal.HEAT_GRADE.get());
        return legacy==null ? temperatureLevel(FluidHazards.nominal(stack).kelvin()) : clamp(legacy*4);
    }

    public static int pressure(FluidStack stack) {
        if(stack.isEmpty())return NORMAL_PRESSURE;
        Integer level=stack.get(CEThermal.PRESSURE.get());
        return level==null ? pressureLevel(FluidHazards.nominal(stack).bar()) : clamp(level);
    }

    public static int nominalTemperature(net.minecraft.world.level.material.Fluid fluid) { return temperatureLevel(FluidHazards.nominal(fluid).kelvin()); }
    public static int nominalPressure(net.minecraft.world.level.material.Fluid fluid) { return pressureLevel(FluidHazards.nominal(fluid).bar()); }

    public static void setTemperature(FluidStack stack,int level) { setTemperature(stack,stack.getFluid(),level); }
    public static void setPressure(FluidStack stack,int level) { setPressure(stack,stack.getFluid(),level); }

    /** A pure fluid at its substance's own state carries no state data, so other mods and plain transport see it unchanged. */
    static void setTemperature(FluidStack stack,net.minecraft.world.level.material.Fluid substance,int level) {
        if(stack.isEmpty())return;
        stack.remove(CEThermal.HEAT_GRADE.get());
        if(!MixtureHelper.isMixture(stack) && clamp(level)==nominalTemperature(substance))stack.remove(CEThermal.TEMPERATURE.get());
        else stack.set(CEThermal.TEMPERATURE.get(),clamp(level));
    }

    static void setPressure(FluidStack stack,net.minecraft.world.level.material.Fluid substance,int level) {
        if(stack.isEmpty())return;
        if(!MixtureHelper.isMixture(stack) && clamp(level)==nominalPressure(substance))stack.remove(CEThermal.PRESSURE.get());
        else stack.set(CEThermal.PRESSURE.get(),clamp(level));
    }

    public static FluidStack with(FluidStack stack,int temperature,int pressure) {
        if(stack.isEmpty())return FluidStack.EMPTY;
        var result=stack.copy();
        setTemperature(result,temperature);
        setPressure(result,pressure);
        return result;
    }

    public static void migrateGrade(FluidStack stack) {
        if(stack.isEmpty())return;
        Integer legacy=stack.get(CEThermal.HEAT_GRADE.get());
        if(legacy==null)return;
        if(!stack.has(CEThermal.TEMPERATURE.get()))setTemperature(stack,legacy*4);
        else stack.remove(CEThermal.HEAT_GRADE.get());
    }

    public static double boilingKelvin(FluidStack stack,int pressure) {
        double normal=FluidMaterials.boilingOrder(stack);
        var data=net.createplant.chemistry.FluidChemicalRegistry.base(stack.getFluid());
        double latent=data==null ? 0 : data.vaporizationHeat();
        if(!Double.isFinite(latent) || latent<=0)latent=88*normal;
        double inverse=1/normal-FluidMaterials.R*Math.log(bar(pressure))/latent;
        return inverse<=0 ? Double.POSITIVE_INFINITY : 1/inverse;
    }

    /** Whether the stack changes between liquid and gas as its temperature changes; a mixture or an unlisted fluid never does. */
    public static boolean phaseSensitive(FluidStack stack) {
        return !stack.isEmpty() && !MixtureHelper.isMixture(stack) && (FluidMaterials.known(stack.getFluid()) || FluidIdentity.controlled(stack.getFluid()));
    }

    /** Latent heat of vaporisation in J/mol. */
    public static double latentMolar(FluidStack stack) {
        var data=net.createplant.chemistry.FluidChemicalRegistry.base(stack.getFluid());
        double latent=data==null ? 0 : data.vaporizationHeat();
        return Double.isFinite(latent) && latent>0 ? latent : 88*FluidMaterials.boilingOrder(stack);
    }

    /** The level nearest to a real temperature that is in the wanted phase. */
    public static int levelFor(FluidStack stack,double kelvin,int pressure,boolean wantGas) {
        int level=temperatureLevel(kelvin);
        if(!phaseSensitive(stack))return level;
        while(wantGas && level<MAX && !gas(stack,level,pressure))level++;
        while(!wantGas && level>0 && gas(stack,level,pressure))level--;
        return level;
    }

    public static boolean liquidWater(FluidStack stack) {
        return stack.is(net.minecraft.world.level.material.Fluids.WATER) && !gas(stack);
    }

    public static boolean gas(FluidStack stack) { return gas(stack,temperature(stack),pressure(stack)); }

    public static boolean gas(FluidStack stack,int temperature,int pressure) {
        if(stack.isEmpty())return false;
        if(MixtureHelper.isMixture(stack))return MixtureHelper.gas(stack);
        if(!FluidMaterials.known(stack.getFluid()) && !FluidIdentity.controlled(stack.getFluid()))return FluidHazards.nominal(stack).gas() || stack.getFluidType().isLighterThanAir();
        return kelvin(temperature)>boilingKelvin(stack,pressure);
    }

    public static int average(int a,int amountA,int b,int amountB) {
        long total=(long)amountA+amountB;
        return total<=0 ? a : (int)(((long)a*amountA+(long)b*amountB+total/2)/total);
    }

    public static Component describe(FluidStack stack) {
        return Component.translatable(TEXT+"conditions",temperatureName(temperature(stack)),pressureName(pressure(stack)),
            phaseName(stack));
    }

    public static Component phaseName(FluidStack stack) {
        return Component.translatable(TEXT+(gas(stack) ? "gas" : "liquid"));
    }

    public static Component temperatureName(int value) {
        return bandName(value, band -> Grade.Heat.values()[band].label());
    }

    public static Component pressureName(int value) {
        return bandName(value,band -> Grade.Pressure.values()[band].label());
    }

    private static Component bandName(int value,java.util.function.IntFunction<Component> anchor) {
        int level=clamp(value), first=level/4, middle=level%4;
        if(middle==0)return anchor.apply(first);
        return Component.translatable(TEXT+"between."+middle,anchor.apply(first),anchor.apply(first+1));
    }
}
