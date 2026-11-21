package net.createplant.transport;

import java.util.EnumSet;
import java.util.Set;
import net.createplant.block.MaterialPipeBlock;
import net.createplant.block.ResinPipeBlock;
import net.createplant.thermal.Grade;
import net.createplant.transport.FluidHazards.Corrosion;
import net.minecraft.world.level.block.state.BlockState;

public enum PipeMaterial {
    COPPER(Grade.Heat.HOT,Grade.Heat.AMBIENT,Grade.Pressure.ATMOSPHERIC),
    RESIN(Grade.Heat.AMBIENT,Grade.Heat.COLD,Grade.Pressure.ATMOSPHERIC,Corrosion.ACID,Corrosion.HOT_ACID),
    REINFORCED(Grade.Heat.VERY_HOT,Grade.Heat.COLD,Grade.Pressure.PRESSURISED),
    STAINLESS(Grade.Heat.VERY_HOT,Grade.Heat.COLD,Grade.Pressure.HIGH,Corrosion.ACID),
    NICKEL_ALLOY(Grade.Heat.VERY_HOT,Grade.Heat.COLD,Grade.Pressure.HIGH,Corrosion.ACID,Corrosion.FLUORIDE),
    ZIRCONIUM(Grade.Heat.VERY_HOT,Grade.Heat.COLD,Grade.Pressure.HIGH,Corrosion.ACID,Corrosion.HOT_ACID),
    /** A stainless line inside a vacuum jacket: the one pipe, with the valves, that carries cryogenic liquids. */
    CRYOGENIC(Grade.Heat.AMBIENT,Grade.Heat.CRYOGENIC,Grade.Pressure.PRESSURISED,Corrosion.ACID),
    /** Rigid PVC: cheap and acid-proof, but soft when hot. */
    PVC(Grade.Heat.AMBIENT,Grade.Heat.COLD,Grade.Pressure.PRESSURISED,Corrosion.ACID),
    /** Fired clay stoneware: proof against acids, hot ones included, but too brittle for pressure. */
    CERAMIC(Grade.Heat.HOT,Grade.Heat.COLD,Grade.Pressure.ATMOSPHERIC,Corrosion.ACID,Corrosion.HOT_ACID),
    /** The pressure valves: an alloy body with acid-proof trim. */
    VALVE(Grade.Heat.VERY_HOT,Grade.Heat.CRYOGENIC,Grade.Pressure.HIGH,Corrosion.ACID);

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
        if (fluid.gas() && this!=VALVE) return "safe";
        if (!resists(fluid.corrosion())) return "corrosion";
        if (fluid.heat().compareTo(hottest)>0) return this==RESIN || this==PVC?"softening":"overheat";
        if (fluid.heat().compareTo(coldest)<0) return "brittle";
        if (fluid.pressure().compareTo(strongest)>0) return "pressure";
        return "safe";
    }
    public boolean carries(net.neoforged.neoforge.fluids.FluidStack stack) {
        if (!net.createplant.mixture.MixtureHelper.isMixture(stack))
            return this!=VALVE && !stack.isEmpty() && stack.getFluidType().isLighterThanAir() || carries(FluidHazards.of(stack));
        return net.createplant.mixture.MixtureHelper.isMixableLiquid(stack) && net.createplant.mixture.MixtureHelper.components(stack).stream().allMatch(this::carries);
    }
    public boolean carries(FluidHazards.Properties fluid) { return incompatibility(fluid).equals("safe"); }
    /** Why a fluid wears this material, or "safe"; a mixture wears it as its worst constituent does. */
    public String problem(net.neoforged.neoforge.fluids.FluidStack stack) {
        if (stack.isEmpty()) return "safe";
        if (net.createplant.mixture.MixtureHelper.isMixture(stack)) {
            for (var part : net.createplant.mixture.MixtureHelper.components(stack)) { String reason=problem(part);if (!reason.equals("safe")) return reason; }
            return "safe";
        }
        return this!=VALVE && stack.getFluidType().isLighterThanAir() ? "safe" : incompatibility(FluidHazards.of(stack));
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
    /** Liquid material ratings, alongside the shared gas behaviour. */
    public void describe(java.util.List<net.minecraft.network.chat.Component> lines) {
        lines.add(net.minecraft.network.chat.Component.translatable("transport.create_refinery_n_reactors.carries",coldest.detail(),hottest.detail(),strongest.detail()));
        lines.add(net.minecraft.network.chat.Component.translatable("transport.create_refinery_n_reactors.resistance",
            resists(Corrosion.ACID) ? "○" : "×",resists(Corrosion.HOT_ACID) ? "○" : "×",resists(Corrosion.FLUORIDE) ? "○" : "×"));
        if (this!=VALVE) lines.add(net.minecraft.network.chat.Component.translatable("transport.create_refinery_n_reactors.gases"));
    }
    public static PipeMaterial of(BlockState state) {
        if (state.getBlock() instanceof MaterialPipeBlock pipe) return pipe.material;
        if (state.getBlock() instanceof net.createplant.block.GasPipeBlock) return REINFORCED;
        if (state.getBlock() instanceof net.createplant.block.PressureValveBlock) return VALVE;
        if (state.getBlock() instanceof ResinPipeBlock) return RESIN;
        if (state.getBlock() instanceof net.createplant.block.StainlessPipeBlock || state.getBlock() instanceof net.createplant.block.StainlessGlassPipeBlock
            || state.getBlock() instanceof net.createplant.block.StainlessEncasedPipeBlock) return STAINLESS;
        return COPPER;
    }
}
