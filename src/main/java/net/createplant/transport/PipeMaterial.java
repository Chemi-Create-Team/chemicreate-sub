package net.createplant.transport;

import java.util.EnumSet;
import java.util.Set;
import net.createplant.block.MaterialPipeBlock;
import net.createplant.thermal.Grade;
import net.createplant.transport.FluidHazards.Corrosion;
import net.minecraft.world.level.block.state.BlockState;

public enum PipeMaterial {
    COPPER(Grade.Heat.HOT,Grade.Heat.AMBIENT,Grade.Pressure.ATMOSPHERIC),
    /** Austenitic stainless stays tough down to cryogenic temperatures and resists acids and alkalis, but not chlorine. */
    STAINLESS(Grade.Heat.VERY_HOT,Grade.Heat.CRYOGENIC,Grade.Pressure.HIGH,Corrosion.ACID,Corrosion.BASE),
    NICKEL_ALLOY(Grade.Heat.VERY_HOT,Grade.Heat.COLD,Grade.Pressure.HIGH,Corrosion.ACID,Corrosion.FLUORIDE,Corrosion.HALOGEN,Corrosion.BASE),
    ZIRCONIUM(Grade.Heat.VERY_HOT,Grade.Heat.COLD,Grade.Pressure.HIGH,Corrosion.ACID,Corrosion.HOT_ACID,Corrosion.BASE),
    /** Fired clay stoneware: proof against acids, hot ones included, and chlorine, but too brittle for pressure and etched by alkalis. */
    CERAMIC(Grade.Heat.HOT,Grade.Heat.COLD,Grade.Pressure.ATMOSPHERIC,Corrosion.ACID,Corrosion.HOT_ACID,Corrosion.HALOGEN),
    /** The pressure valves: an alloy body with acid-proof trim. */
    VALVE(Grade.Heat.VERY_HOT,Grade.Heat.CRYOGENIC,Grade.Pressure.ULTRA_HIGH,Corrosion.ACID,Corrosion.BASE);

    /** The hottest and coldest grades and the highest pressure grade the material carries. */
    public final Grade.Heat hottest, coldest;
    public final Grade.Pressure strongest;
    /**
     * How many parallel bores a stainless power-station main represents. Four 256 RPM reactor outlet pumps can
     * therefore carry the station flow through the native pump network.
     */
    public static final int MAIN_FLOW=512;
    private final Set<Corrosion> resists=EnumSet.of(Corrosion.NONE);
    PipeMaterial(Grade.Heat hottest,Grade.Heat coldest,Grade.Pressure strongest,Corrosion... resists) {
        this.hottest=hottest;this.coldest=coldest;this.strongest=strongest;this.resists.addAll(Set.of(resists));
    }
    public boolean resists(Corrosion corrosion) { return resists.contains(corrosion); }
    /** Create's own pipe shapes: the copper ones and the stainless steel family built on them. */
    public boolean createPipe() { return this==COPPER || this==STAINLESS; }
    /** What patches a worn pipe of this material: a plate, an ingot or the resin of it. */

    public String incompatibility(FluidHazards.Properties fluid) {
        if (!resists(fluid.corrosion())) return "corrosion";
        if (fluid.heat().compareTo(hottest)>0) return "overheat";
        if (fluid.heat().compareTo(coldest)<0) return "brittle";
        if (fluid.pressure().compareTo(strongest)>0) return "pressure";
        return "safe";
    }
    public boolean carries(net.neoforged.neoforge.fluids.FluidStack stack) {
        return problem(stack).equals("safe");
    }
    public boolean carries(FluidHazards.Properties fluid) { return incompatibility(fluid).equals("safe"); }
    /** Why a fluid, liquid or gas, wears this material, or "safe"; a mixture wears it as its worst constituent does. */
    public String problem(net.neoforged.neoforge.fluids.FluidStack stack) {
        if(stack.isEmpty())return "safe";
        if(net.createplant.mixture.MixtureHelper.isMixture(stack)) {
            if(!net.createplant.mixture.MixtureHelper.isMixableGas(stack) && !net.createplant.mixture.MixtureHelper.isMixableLiquid(stack))return "fluid";
            for(var part:net.createplant.mixture.MixtureHelper.components(stack)) { String reason=problem(part);if(!reason.equals("safe"))return reason; }
            return "safe";
        }
        return incompatibility(FluidHazards.of(stack));
    }
    /** The material of a pipe block, Create's own or this mod's; null for anything that is not a pipe, such as a pump or a tank. */
    public static PipeMaterial pipe(BlockState state) {
        var block=state.getBlock();
        boolean pipe=block instanceof com.simibubi.create.content.fluids.pipes.FluidPipeBlock || block instanceof com.simibubi.create.content.fluids.pipes.EncasedPipeBlock
            || block instanceof com.simibubi.create.content.fluids.pipes.AxisPipeBlock;
        return pipe ? of(state) : null;
    }
    /** The material of a pipe or of a pump rated like its pipe: Create's mechanical pump is copper. Other pumps carry no rating. */
    public static PipeMaterial carrier(BlockState state) {
        var material=pipe(state);
        if (material!=null) return material;
        if (state.getBlock() instanceof net.createplant.block.MaterialPumpBlock pump) return pump.material();
        return state.is(com.simibubi.create.AllBlocks.MECHANICAL_PUMP.get()) ? COPPER : null;
    }
    public void describe(java.util.List<net.minecraft.network.chat.Component> lines) { describe(lines, net.neoforged.neoforge.fluids.FluidStack.EMPTY, false); }

    /** The rated range as a scale with the carried fluid's level on it, or with Shift as the grade names and their ranges. */
    public void describe(java.util.List<net.minecraft.network.chat.Component> lines, net.neoforged.neoforge.fluids.FluidStack contents, boolean sneaking) {
        if (sneaking) lines.add(net.minecraft.network.chat.Component.translatable("transport.create_refinery_n_reactors.carries",coldest.detail(),hottest.detail(),strongest.detail()));
        else lines.add(net.createplant.thermal.GradeScale.line(coldest, hottest, strongest, contents.isEmpty() ? -1 : net.createplant.thermal.FluidState.temperature(contents),
            contents.isEmpty() ? -1 : net.createplant.thermal.FluidState.pressure(contents)));
        lines.add(net.minecraft.network.chat.Component.translatable("transport.create_refinery_n_reactors.resistance",
            resists(Corrosion.ACID) ? "○" : "×",resists(Corrosion.HOT_ACID) ? "○" : "×",resists(Corrosion.FLUORIDE) ? "○" : "×",
            resists(Corrosion.HALOGEN) ? "○" : "×",resists(Corrosion.BASE) ? "○" : "×"));
    }
    /** The fluid a pipe or pump is carrying now, from the flows its transport keeps on each side; empty when it carries none. */
    public static net.neoforged.neoforge.fluids.FluidStack contents(net.minecraft.world.level.block.entity.BlockEntity pipe) {
        if (!(pipe instanceof com.simibubi.create.foundation.blockEntity.SmartBlockEntity smart)) return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
        var transport = smart.getBehaviour(com.simibubi.create.content.fluids.FluidTransportBehaviour.TYPE);
        if (transport != null) for (var side : net.minecraft.core.Direction.values()) {
            var flow = transport.getFlow(side);
            if (flow != null && !flow.fluid.isEmpty()) return flow.fluid;
        }
        return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
    }
    public static PipeMaterial of(BlockState state) {
        if (state.getBlock() instanceof MaterialPipeBlock pipe) return pipe.material;
        if (state.getBlock() instanceof net.createplant.block.PressureValveBlock) return VALVE;
        if (state.getBlock() instanceof net.createplant.block.StainlessPipeBlock || state.getBlock() instanceof net.createplant.block.StainlessGlassPipeBlock
            || state.getBlock() instanceof net.createplant.block.StainlessEncasedPipeBlock) return STAINLESS;
        return COPPER;
    }
}
