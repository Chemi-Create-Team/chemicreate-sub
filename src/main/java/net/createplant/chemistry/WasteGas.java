package net.createplant.chemistry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.createplant.registry.CEGasBell;
import net.createplant.safety.FlammableGas;
import net.createplant.safety.ToxicGas;
import net.createplant.thermal.FluidMaterials;
import net.createplant.thermal.FluidThermal;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.fluids.FluidStack;

/** Reference-volume properties survive dilution, draining and transfer of a contaminated stream. */
public record WasteGas(double density,double heatCapacity,double molarMass,int hazards) {
    public static final int FUEL=1,OXIDIZER=2;
    public static final WasteGas DEFAULT=new WasteGas(.00125,1040,.028014,0);
    public static final Codec<WasteGas> CODEC=RecordCodecBuilder.create(i->i.group(
        Codec.doubleRange(1e-9,100).fieldOf("density").forGetter(WasteGas::density),
        Codec.doubleRange(1,100000).fieldOf("heat_capacity").forGetter(WasteGas::heatCapacity),
        Codec.doubleRange(.0001,10).fieldOf("molar_mass").forGetter(WasteGas::molarMass),
        Codec.intRange(0,15).fieldOf("hazards").forGetter(WasteGas::hazards)).apply(i,WasteGas::new));
    public static final StreamCodec<RegistryFriendlyByteBuf,WasteGas> STREAM=StreamCodec.composite(
        ByteBufCodecs.DOUBLE,WasteGas::density,ByteBufCodecs.DOUBLE,WasteGas::heatCapacity,
        ByteBufCodecs.DOUBLE,WasteGas::molarMass,ByteBufCodecs.VAR_INT,WasteGas::hazards,WasteGas::new);
    public WasteGas {
        if (!Double.isFinite(density) || density<1e-9 || density>100 || !Double.isFinite(heatCapacity) || heatCapacity<1 || heatCapacity>100000
            || !Double.isFinite(molarMass) || molarMass<.0001 || molarMass>10 || hazards<0 || hazards>15) throw new IllegalArgumentException("Invalid waste gas properties");
    }
    public static boolean contaminated(FluidStack gas) { if (gas.isEmpty()) return false;var id=BuiltInRegistries.FLUID.getKey(gas.getFluid());return id!=null && id.getNamespace().equals(net.createplant.CreatePlant.MOD_ID) && id.getPath().replace("flowing_","").equals("contaminated_gas"); }
    public static WasteGas of(FluidStack gas) {
        if (contaminated(gas)) return gas.getOrDefault(CEGasBell.GAS_CONTENTS.get(),DEFAULT);
        var thermal=FluidThermal.properties(gas);
        String id=BuiltInRegistries.FLUID.getKey(gas.getFluid()).getPath().replace("flowing_","");
        int flags=(FlammableGas.fuel(gas) ? FUEL : 0) | (id.equals("oxygen_gas") || id.equals("air_gas") ? OXIDIZER : 0)
            | (Math.max(0,ToxicGas.strength(gas)+1)<<2);
        return new WasteGas(thermal.kilogramsPerLitre(),thermal.joulesPerKilogramKelvin(),FluidMaterials.molarMass(gas),flags);
    }
    public static boolean reacts(FluidStack a,FluidStack b) { return !b.isEmpty() && (((a.isEmpty() ? 0 : of(a).hazards)|of(b).hazards)&3)==3; }
    public static FluidStack mix(FluidStack stored,FluidStack incoming,int amount) {
        if (stored.isEmpty() || incoming.isEmpty() || amount<=0 || amount>incoming.getAmount()) throw new IllegalArgumentException("Empty contaminated mixture");
        var a=of(stored);var b=of(incoming);int total=Math.addExact(stored.getAmount(),amount);
        double ma=stored.getAmount()/1000d*a.density,mb=amount/1000d*b.density,mass=ma+mb;
        var properties=new WasteGas(mass/(total/1000d),(ma*a.heatCapacity+mb*b.heatCapacity)/mass,
            mass/(ma/a.molarMass+mb/b.molarMass),(a.hazards|b.hazards)&3 | Math.max(a.hazards&12,b.hazards&12));
        var result=new FluidStack(CEGasBell.CONTAMINATED_GAS.get(),total);result.set(CEGasBell.GAS_CONTENTS.get(),properties);
        return result;
    }
}
