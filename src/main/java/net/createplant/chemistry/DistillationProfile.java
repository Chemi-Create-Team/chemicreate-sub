package net.createplant.chemistry;

import net.createplant.registry.CEFluids;
import net.createplant.thermal.FluidThermal;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Air separation column of 14 layers. Air enters at layer 3; the lower column, the condenser between the columns and the upper column
 * split it inside the column into liquid oxygen (layer 1), crude argon (layer 10) and nitrogen (layer 14). Amounts are mB per batch and,
 * so that mass is kept, every slot counts gas mB: liquid oxygen leaving is converted at its density ratio.
 */
public final class DistillationProfile {
    // Detect three-wide groups as a whole so they cannot hide an operating two-wide subset.
    public static final int MAX_WIDTH=3, REQUIRED_WIDTH=2, HEIGHT=14, BUFFER_PER_BLOCK=1000, CYCLE_TICKS=100, AIR_BATCH=1000;
    public static final int AIR=0, OXYGEN=1, ARGON=2, NITROGEN=3, SLOTS=6;
    public static final int[] PRODUCT={OXYGEN,ARGON,NITROGEN}, PRODUCT_AMOUNT={210,10,780};
    private static final int[] OUTLET_SLOTS={OXYGEN,ARGON,NITROGEN}, OUTLET_LAYERS={0,9,13};
    private static final int[] INLET_SLOTS={AIR}, INLET_LAYERS={2};
    private DistillationProfile() { }
    public record Fraction(String id,int ticks,FluidStack input,java.util.List<FluidStack> outputs) { }
    private static final java.util.List<Fraction> REGISTERED=new java.util.ArrayList<>();
    private static boolean resolved;
    private static final class Fractions {
        static final java.util.Map<String,Fraction> ALL=resolve();
    }
    private static synchronized java.util.Map<String,Fraction> resolve() {
        resolved=true;
        return java.util.stream.Stream.concat(ProcessRecipe.all().stream().filter(r->r.module()==ProcessModule.FRACTION_SEPARATOR).map(r->new Fraction(r.id(),r.ticks(),r.inputs().getFirst(),r.outputs())),REGISTERED.stream())
            .collect(java.util.stream.Collectors.toUnmodifiableMap(Fraction::id,r->new Fraction(r.id(),r.ticks(),r.input(),r.outputs().stream().sorted(java.util.Comparator.comparingDouble(f->net.createplant.thermal.FluidMaterials.boilingOrder(f))).toList())));
    }
    /** A fractionation of another mod: one feed and up to five cuts. Register during common setup, before any column runs. */
    public static synchronized void register(Fraction fraction) {
        if (resolved) throw new IllegalStateException("Register fractionations before the first column runs");
        if (fraction.outputs().isEmpty() || fraction.outputs().size()>SLOTS-1 || fraction.ticks()<=0 || fraction.input().isEmpty()) throw new IllegalArgumentException("Invalid fractionation "+fraction.id());
        REGISTERED.add(fraction);
    }
    public static synchronized java.util.List<Fraction> registered() { return java.util.List.copyOf(REGISTERED); }
    public static Fraction fraction(String id) { return Fractions.ALL.get(id); }
    public static Fraction fraction(FluidStack feed) { return Fractions.ALL.values().stream().filter(r->net.createplant.thermal.ThermalStreams.accepts(feed,r.input())).findFirst().orElse(null); }
    private static final int[] FRACTION_LAYERS={13,9,6,3,0};
    public static int requiredHeight(String recipe) {
        var fraction=fraction(recipe);
        return fraction==null ? HEIGHT : Math.max(2,fraction.outputs().size());
    }
    public static int feedLayer(int height) { return Math.min(2,height-1); }
    /** Short towers pack the cuts together; a fourteen-layer tower keeps its original connections. */
    public static int fractionLayer(int slot,int height,int cuts) {
        if(slot<1 || slot>cuts || cuts>FRACTION_LAYERS.length || height<Math.max(2,cuts) || height>HEIGHT)return -1;
        return Math.max(cuts-slot,FRACTION_LAYERS[slot-1]*(height-1)/(HEIGHT-1));
    }
    public static int fractionSlot(int layer,int height,int cuts) {
        for(int slot=1;slot<=cuts;slot++)if(layer==fractionLayer(slot,height,cuts))return slot;
        return 0;
    }
    /** The liquid that crosses a port of the slot in place of its gas: liquid oxygen leaves. */
    public static net.createplant.thermal.FluidForm liquid(int slot) {
        return slot==OXYGEN ? net.createplant.thermal.FluidForm.LIQUID_OXYGEN : null;
    }
    /** Gas mB that carry the mass of one mB of the slot's liquid. */
    public static int gasPerLiquid(int slot) {
        double liquid=FluidThermal.properties(liquid(slot).stack(1)).kilogramsPerLitre(),gas=FluidThermal.properties(stack(slot,1)).kilogramsPerLitre();
        return (int)Math.round(liquid/gas);
    }
    /** The 1-based outlet number of a layer, or 0 when it has none. */
    public static int outlet(int layer) { return number(OUTLET_LAYERS,layer); }
    public static int inlet(int layer) { return number(INLET_LAYERS,layer); }
    public static int outletSlot(int number) { return OUTLET_SLOTS[number-1]; }
    public static int inletSlot(int number) { return INLET_SLOTS[number-1]; }
    private static int number(int[] layers,int layer) {
        for (int i=0;i<layers.length;i++) if (layers[i]==layer) return i+1;
        return 0;
    }
    /** {@code amount} mB of what the slot holds in air mode; oxygen is the liquid. */
    public static FluidStack stack(int slot,int amount) {
        return slot==OXYGEN ? net.createplant.thermal.FluidForm.LIQUID_OXYGEN.stack(amount) : new FluidStack(fluid(slot),amount);
    }
    public static Fluid fluid(int slot) {
        return switch (slot) {
            case AIR -> CEFluids.AIR_GAS.get();
            case OXYGEN -> CEFluids.OXYGEN_GAS.get();
            case ARGON -> CEFluids.ARGON_GAS.get();
            case NITROGEN -> CEFluids.NITROGEN_GAS.get();
            default -> net.minecraft.world.level.material.Fluids.EMPTY;
        };
    }
}
