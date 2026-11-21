package net.createplant.mixture;

import java.util.Map;
import java.util.TreeMap;
import net.createplant.thermal.FluidIdentity;
import net.createplant.thermal.FluidState;
import net.createplant.thermal.FluidThermal;
import net.createplant.registry.CEThermal;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.resources.ResourceLocation;
import net.createplant.registry.CEMixtures;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

public final class MixtureHelper {
    private MixtureHelper() { }
    public static boolean isMixture(FluidStack stack) { return stack.getFluid() instanceof MixtureFluid; }
    public static MixtureContents extractContents(FluidStack stack) {
        if (stack.isEmpty()) return MixtureContents.EMPTY;
        if (isAir(stack)) return airContents(stack.getAmount());
        if (!isMixture(stack)) return new MixtureContents(Map.of(BuiltInRegistries.FLUID.getKey(source(stack.getFluid())), stack.getAmount()));
        var contents = stack.get(CEMixtures.MIXTURE_CONTENTS.get());
        if (contents == null || contents.totalAmount() != stack.getAmount()) throw new IllegalArgumentException("Mixture component volume does not match its stack");
        return contents;
    }
    /** Air is a mixture too: 780 nitrogen, 210 oxygen and 10 other gases (argon and the rest) in every 1000 mB. */
    public static boolean composite(FluidStack stack) { return isMixture(stack) || isAir(stack); }
    private static boolean isAir(FluidStack stack) { return !stack.isEmpty() && source(stack.getFluid()).isSame(net.createplant.registry.CEFluids.AIR_GAS.get()); }
    public static MixtureContents airContents(int amount) {
        if (amount <= 0) return MixtureContents.EMPTY;
        return new MixtureContents(Map.of(BuiltInRegistries.FLUID.getKey(net.createplant.registry.CEFluids.NITROGEN_GAS.get()), 780,
            BuiltInRegistries.FLUID.getKey(net.createplant.registry.CEFluids.OXYGEN_GAS.get()), 210,
            BuiltInRegistries.FLUID.getKey(CEMixtures.OTHER_GASES.get()), 10)).resized(amount);
    }
    private static Fluid source(Fluid fluid) { return fluid instanceof FlowingFluid flowing ? flowing.getSource() : fluid; }
    public static FluidStack createStack(MixtureContents contents, boolean unwrapIfPure) {
        return createStack(contents,MixtureData.EMPTY,unwrapIfPure);
    }

    public static boolean gas(FluidStack stack) {
        if(stack.isEmpty())return false;
        return isMixture(stack) ? stack.getFluidType()==CEMixtures.GAS_TYPE.get() : FluidState.gas(stack);
    }

    public static boolean isMixableGas(FluidStack stack) {
        return gas(stack) && (!isMixture(stack) || validContents(stack));
    }

    private static boolean validContents(FluidStack stack) {
        try {
            return extractContents(stack).amounts().keySet().stream().allMatch(id ->
                BuiltInRegistries.FLUID.containsKey(id) && !(BuiltInRegistries.FLUID.get(id) instanceof MixtureFluid));
        } catch (IllegalArgumentException error) { return false; }
    }

    public static void migrateContents(FluidStack stack) {
        if(!isMixture(stack) || stack.isEmpty())return;
        var contents=stack.get(CEMixtures.MIXTURE_CONTENTS.get());
        if(contents==null)return;
        var patches=stack.getOrDefault(CEMixtures.MIXTURE_DATA.get(),MixtureData.EMPTY);
        boolean oldKey=contents.amounts().keySet().stream().anyMatch(id->FluidIdentity.legacy(BuiltInRegistries.FLUID.get(id)));
        if(!oldKey)return;
        var migrated=parts(contents,patches);
        writeComponents(stack,migrated);
        applyAverageState(stack,migrated);
    }

    private static Map<ResourceLocation,FluidStack> parts(MixtureContents contents,MixtureData data) {
        var parts=new TreeMap<ResourceLocation,FluidStack>();
        for(var entry:contents.amounts().entrySet()) {
            var id=entry.getKey();
            if(!BuiltInRegistries.FLUID.containsKey(id))throw new IllegalArgumentException("Unknown mixture component "+id);
            var fluid=BuiltInRegistries.FLUID.get(id);
            if(fluid instanceof MixtureFluid || fluid.defaultFluidState().isEmpty())throw new IllegalArgumentException("Invalid mixture component "+id);
            var part=new FluidStack(fluid.builtInRegistryHolder(),entry.getValue(),data.components().getOrDefault(id,DataComponentPatch.EMPTY));
            parts.merge(BuiltInRegistries.FLUID.getKey(source(part.getFluid())),part,(first,second)->{
                if(FluidThermal.mixable(first,second))return FluidThermal.mix(first,second,second.getAmount());
                return first.copyWithAmount(Math.addExact(first.getAmount(),second.getAmount()));
            });
        }
        return parts;
    }

    private static void writeComponents(FluidStack stack,Map<ResourceLocation,FluidStack> parts) {
        var amounts=new TreeMap<ResourceLocation,Integer>();
        var patches=new TreeMap<ResourceLocation,DataComponentPatch>();
        parts.forEach((id,part)->{
            amounts.put(id,part.getAmount());
            patches.put(id,part.getComponentsPatch());
        });
        stack.set(CEMixtures.MIXTURE_CONTENTS.get(),new MixtureContents(amounts));
        var data=new MixtureData(patches);
        if(!data.components().isEmpty())stack.set(CEMixtures.MIXTURE_DATA.get(),data);
        else stack.remove(CEMixtures.MIXTURE_DATA.get());
    }

    private static void applyAverageState(FluidStack stack,Map<ResourceLocation,FluidStack> parts) {
        long temperature=0,pressure=0,amount=0;
        for(var part:parts.values()) {
            temperature+=(long)FluidState.temperature(part)*part.getAmount();
            pressure+=(long)FluidState.pressure(part)*part.getAmount();
            amount+=part.getAmount();
        }
        if(amount==0)return;
        if(!stack.has(CEThermal.TEMPERATURE.get()))FluidState.setTemperature(stack,(int)((temperature+amount/2)/amount));
        if(!stack.has(CEThermal.PRESSURE.get()))stack.set(CEThermal.PRESSURE.get(),(int)((pressure+amount/2)/amount));
    }

    public static MixtureData data(FluidStack stack) {
        if (stack.isEmpty()) return MixtureData.EMPTY;
        if(isMixture(stack))return stack.getOrDefault(CEMixtures.MIXTURE_DATA.get(),MixtureData.EMPTY).forContents(extractContents(stack));
        var copy = stack.copy();net.createplant.nuclear.RadiationSources.stamp(copy);
        if (isAir(stack)) {
            var patches = new java.util.TreeMap<net.minecraft.resources.ResourceLocation, net.minecraft.core.component.DataComponentPatch>();
            for (var id : extractContents(stack).amounts().keySet()) patches.put(id, copy.getComponentsPatch());
            return new MixtureData(patches);
        }
        return new MixtureData(Map.of(BuiltInRegistries.FLUID.getKey(source(copy.getFluid())), copy.getComponentsPatch()));
    }
    public static java.util.List<FluidStack> components(FluidStack stack) {
        var contents = extractContents(stack);var data = data(stack);
        return contents.amounts().entrySet().stream().map(entry -> new FluidStack(BuiltInRegistries.FLUID.get(entry.getKey()).builtInRegistryHolder(), entry.getValue(),
            data.components().getOrDefault(entry.getKey(), net.minecraft.core.component.DataComponentPatch.EMPTY))).toList();
    }
    public static FluidStack createStack(MixtureContents contents,MixtureData data,boolean unwrap) {
        return createStack(contents,data,unwrap,null);
    }

    private static FluidStack createStack(MixtureContents contents,MixtureData data,boolean unwrap,Boolean gasCarrier) {
        int total=contents.totalAmount();
        if(total==0)return FluidStack.EMPTY;
        var parts=parts(contents,data);
        boolean gaseous=gasCarrier!=null ? gasCarrier : gas(parts.values().iterator().next());
        if(gasCarrier==null && parts.values().stream().anyMatch(part->gas(part)!=gaseous))
            throw new IllegalArgumentException("A mixture cannot hold both gases and liquids");
        if(unwrap && parts.size()==1) {
            var single=parts.values().iterator().next();
            if(gas(single)==gaseous)return single;
        }
        var result=new FluidStack(gaseous ? CEMixtures.GAS_MIXTURE.get() : CEMixtures.CHEMICAL_MIXTURE.get(),total);
        writeComponents(result,parts);
        applyAverageState(result,parts);
        return result;
    }

    public static FluidStack merge(FluidStack first, FluidStack second, boolean unwrap) {
        if(second.isEmpty())return first.copy();
        if(first.isEmpty())return isMixture(second) ? second.copy() : createStack(extractContents(second),data(second),unwrap,gas(second));
        if(gas(first)!=gas(second))throw new IllegalArgumentException("A mixture cannot hold both gases and liquids");
        var a = extractContents(first);var b = extractContents(second);var patches = new java.util.TreeMap<>(data(first).components());
        var incoming = data(second);
        for (var entry : b.amounts().entrySet()) {
            var id = entry.getKey();var patch = incoming.components().getOrDefault(id, net.minecraft.core.component.DataComponentPatch.EMPTY);
            if (a.amounts().containsKey(id)) {
                var original = new FluidStack(BuiltInRegistries.FLUID.get(id).builtInRegistryHolder(), a.amounts().get(id), patches.getOrDefault(id, net.minecraft.core.component.DataComponentPatch.EMPTY));
                var added = new FluidStack(BuiltInRegistries.FLUID.get(id).builtInRegistryHolder(), entry.getValue(), patch);
                if (!net.createplant.thermal.FluidThermal.mixable(original, added)) throw new IllegalArgumentException("Incompatible properties of mixture component " + id);
                patch = net.createplant.thermal.FluidThermal.mix(original, added, added.getAmount()).getComponentsPatch();
            }
            patches.put(id, patch);
        }
        var result=createStack(a.merge(b),new MixtureData(patches),unwrap,gas(first));
        int temperature=FluidState.average(FluidState.temperature(first),first.getAmount(),FluidState.temperature(second),second.getAmount());
        int pressure=FluidState.average(FluidState.pressure(first),first.getAmount(),FluidState.pressure(second),second.getAmount());
        if(!isMixture(result) && FluidState.gas(result,temperature,pressure)!=gas(first))
            result=createStack(a.merge(b),new MixtureData(patches),false,gas(first));
        return FluidState.with(result,temperature,pressure);
    }
    public static boolean isMixableLiquid(FluidStack stack) {
        return !stack.isEmpty() && !gas(stack) && (!isMixture(stack) || validContents(stack));
    }

    public static FluidStack portion(FluidStack stack, int amount) {
        return isMixture(stack) ? withContents(stack,extractContents(stack).split(amount).extracted()) : stack.copyWithAmount(amount);
    }

    public static FluidStack remainder(FluidStack stack, int removed) {
        return isMixture(stack) ? withContents(stack,extractContents(stack).split(removed).remaining()) : stack.copyWithAmount(stack.getAmount()-removed);
    }

    private static FluidStack withContents(FluidStack original,MixtureContents contents) {
        int amount=contents.totalAmount();
        if(amount==0)return FluidStack.EMPTY;
        var result=original.copyWithAmount(amount);
        result.set(CEMixtures.MIXTURE_CONTENTS.get(),contents);
        var patches=data(original).forContents(contents);
        result.set(CEMixtures.MIXTURE_DATA.get(),patches.components().isEmpty() ? null : patches);
        return result;
    }
}
