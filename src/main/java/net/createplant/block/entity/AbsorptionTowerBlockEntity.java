package net.createplant.block.entity;

import java.util.ArrayList;
import java.util.List;
import net.createplant.chemistry.ReactorModule;
import net.createplant.mixture.GasMixturePort;
import net.createplant.mixture.MixtureHelper;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CEProcessFluids;
import net.createplant.registry.CEReactor;
import net.createplant.registry.CEThermal;
import net.createplant.thermal.FluidThermal;
import net.createplant.thermal.Grade;
import net.createplant.thermal.SteamCondensate;
import net.createplant.thermal.SteamGrades;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * A counter-current absorption tower of four to eight storeys, a 3 × 3 octagon across. Gas rises from the bottom and water falls
 * from the top: the bottom storey takes the gas and gives the acid, the top storey takes the water and gives what the water did not hold.
 * Every storey runs one batch.
 */
public class AbsorptionTowerBlockEntity extends StackedReactorBlockEntity {
    public static final int MAX_STOREYS = 8;
    private long lastWash = Long.MIN_VALUE;

    public AbsorptionTowerBlockEntity(BlockPos pos, BlockState state) { super(CEReactor.ABSORPTION_ENTITY.get(), pos, state); }

    @Override public ReactorModule module() { return ReactorModule.TOWER; }
    @Override protected int minStoreys() { return ReactorModule.TOWER.minSections; }
    @Override protected int maxSections() { return MAX_STOREYS; }
    @Override public float rotationSpeed() { return formed() ? 32 : 0; }
    @Override public float calculateStressApplied() { return lastStressApplied = 0; }

    @Override public void tickProcess() {
        if (level!=null && !level.isClientSide && ready() && !level.hasNeighborSignal(worldPosition)
            && lastWash>=level.getGameTime()-5) thermalStatus("washing",true);
        else super.tickProcess();
    }

    /** Every storey shows the tower that its bottom storey runs. */
    @Override public boolean addToGoggleTooltip(List<Component> text, boolean sneaking) {
        var owner = bottomStorey();
        return owner == null || owner == this ? super.addToGoggleTooltip(text, sneaking) : owner.addToGoggleTooltip(text, sneaking);
    }

    @Override protected void structureTooltip(List<Component> text) {
        text.add(Component.translatable("reactor.create_refinery_n_reactors.tower_structure", sections(), MAX_STOREYS, CAPACITY, CAPACITY * 64));
    }

    @Override protected List<BlockPos> storeyCells(BlockPos centre) {
        List<BlockPos> cells = new ArrayList<>(9);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) cells.add(centre.offset(x, 0, z));
        return cells;
    }

    /**
     * Pipes join only the centre and the four straight-side blocks of the octagon, never its diagonal corners. At the bottom storey their sides
     * take gas and give liquid and their undersides give liquid; at the top storey their sides give spent gas and their tops take water.
     */
    @Override protected IFluidHandler storeyPort(StackedReactorBlockEntity bottom, BlockPos cell, Direction side) {
        int x = cell.getX() - worldPosition.getX(), z = cell.getZ() - worldPosition.getZ();
        if (x != 0 && z != 0) return null;
        if (side == Direction.DOWN) return this == bottom ? bottom.processPort(2, 1, false, true) : null;
        if (side == Direction.UP) return top() ? bottom.processPort(1, 1, true, false) : null;
        if (x == 0 && z == 0 || side != Direction.fromDelta(x, 0, z)) return null;
        if (this == bottom) return washingInlet(GasMixturePort.inlet(LiddedVesselBlockEntity.combined(
            bottom.processPort(0, 1, true, false), bottom.processPort(2, 1, false, true)), bottom));
        return top() ? GasMixturePort.outlet(bottom.processPort(3, 1, false, true), bottom) : null;
    }

    private static boolean washedFeed(FluidStack stack) {
        if (!MixtureHelper.gas(stack)) return false;
        var components=MixtureHelper.components(stack);
        if (!stack.getOrDefault(CEThermal.CONDENSATE.get(),false)
            && components.stream().noneMatch(part -> SteamGrades.of(part)!=null || part.getOrDefault(CEThermal.CONDENSATE.get(),false))) return false;
        return components.stream().anyMatch(part -> SteamGrades.of(part)==null) && components.stream().allMatch(part ->
            SteamGrades.of(part)!=null || part.is(CEProcessFluids.WATER_GAS.get()) || part.is(CEProcessFluids.CARBON_MONOXIDE.get()) || part.is(CEFluids.HYDROGEN_GAS.get()));
    }

    private SteamCondensate.Split washed(FluidStack feed, int amount) {
        var split=SteamCondensate.separate(MixtureHelper.portion(feed,amount),FluidThermal.kelvin(feed),Integer.MAX_VALUE);
        var product=split.gas().copy();
        if (product.is(CEProcessFluids.WATER_GAS.get())) {
            var clean=new FluidStack(CEProcessFluids.PURIFIED_SYNGAS.get(),product.getAmount());
            FluidThermal.processComponents(product,clean);
            product=SteamCondensate.withTemperature(clean,FluidThermal.kelvin(feed));
        }
        if (MixtureHelper.isMixture(product)) {
            product=FluidStack.EMPTY;
            for (var component:MixtureHelper.components(split.gas())) {
                component.remove(CEThermal.CONDENSATE.get());
                product=MixtureHelper.merge(product,component,true);
            }
        } else product.remove(CEThermal.CONDENSATE.get());
        return new SteamCondensate.Split(product,split.water());
    }

    private boolean hasWashCapacity(SteamCondensate.Split output) {
        return storeProcessOutput(2,output.water(),IFluidHandler.FluidAction.SIMULATE)==output.water().getAmount()
            && storeProcessOutput(3,output.gas(),IFluidHandler.FluidAction.SIMULATE)==output.gas().getAmount();
    }

    private IFluidHandler washingInlet(IFluidHandler legacy) {
        int generation=portGeneration();
        return new IFluidHandler() {
            @Override public int getTanks() { return legacy.getTanks(); }
            @Override public int getTankCapacity(int tank) { return legacy.getTankCapacity(tank); }
            @Override public FluidStack getFluidInTank(int tank) { return legacy.getFluidInTank(tank); }
            @Override public boolean isFluidValid(int tank, FluidStack stack) {
                return tank==0 && (SteamCondensate.water(stack) || washedFeed(stack)) ? fill(stack,FluidAction.SIMULATE)>0 : legacy.isFluidValid(tank,stack);
            }
            @Override public int fill(FluidStack feed, FluidAction action) {
                boolean water=SteamCondensate.water(feed);
                if (!water && !washedFeed(feed)) return legacy.fill(feed,action);
                if (level==null || action.execute() && level.isClientSide || generation!=portGeneration() || level.hasNeighborSignal(worldPosition)
                    || !water && FluidThermal.kelvin(feed)>SteamCondensate.BOILING_KELVIN) return 0;
                if (water) {
                    int accepted=storeProcessOutput(2,feed,action);
                    if (accepted>0 && action.execute()) lastWash=level.getGameTime();
                    return accepted;
                }
                int amount=feed.getAmount();
                var output=washed(feed,amount);
                while (!hasWashCapacity(output) && amount>1) {
                    amount/=2;output=washed(feed,amount);
                }
                if (!hasWashCapacity(output)) return 0;
                if (action.execute()) {
                    storeProcessOutput(2,output.water(),action);storeProcessOutput(3,output.gas(),action);
                    lastWash=level.getGameTime();
                }
                return amount;
            }
            @Override public FluidStack drain(int amount, FluidAction action) { return legacy.drain(amount,action); }
            @Override public FluidStack drain(FluidStack fluid, FluidAction action) { return legacy.drain(fluid,action); }
        };
    }
}
