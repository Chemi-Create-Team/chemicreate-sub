package net.createplant.gametest;

import java.util.Map;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import net.createplant.CreatePlant;
import net.createplant.mixture.GasMixturePort;
import net.createplant.mixture.MixtureContents;
import net.createplant.mixture.MixtureHelper;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CEMixtures;
import net.createplant.registry.CETransport;
import net.createplant.registry.CEUranium;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

/** Gases kept as one mixture by the volume of each component: in tanks, through machine inlets and out of machine outlets. */
@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class GasMixtureGameTests {
    private static final BlockPos AT = new BlockPos(3, 2, 3);

    private static int amountOf(FluidStack stack, Fluid fluid) {
        return MixtureHelper.extractContents(stack).amounts().getOrDefault(BuiltInRegistries.FLUID.getKey(fluid), 0);
    }

    private static FluidStack mixture(Fluid first, int a, Fluid second, int b) {
        return MixtureHelper.createStack(new MixtureContents(Map.of(BuiltInRegistries.FLUID.getKey(first), a, BuiltInRegistries.FLUID.getKey(second), b)), false);
    }

    @GameTest(template = "test_fractionation")
    public static void differentGasesInOneTankBecomeOneMixtureThatDrainsInProportion(GameTestHelper h) {
        h.setBlock(AT, CETransport.GAS_RECEIVER.get());
        var tank = ((FluidTankBlockEntity) h.getBlockEntity(AT)).getTankInventory();
        h.assertTrue(tank.fill(new FluidStack(CEFluids.NITROGEN_GAS.get(), 1000), EXECUTE) == 1000
            && tank.fill(new FluidStack(CEFluids.OXYGEN_GAS.get(), 500), EXECUTE) == 500, "The gas receiver takes nitrogen, then oxygen");
        var stored = tank.getFluid();
        h.assertTrue(stored.is(CEMixtures.GAS_MIXTURE.get()) && amountOf(stored, CEFluids.NITROGEN_GAS.get()) == 1000 && amountOf(stored, CEFluids.OXYGEN_GAS.get()) == 500,
            "The two gases share the tank as one gas mixture: " + stored.getComponentsPatch());
        var drained = tank.drain(300, EXECUTE);
        h.assertTrue(amountOf(drained, CEFluids.NITROGEN_GAS.get()) == 200 && amountOf(drained, CEFluids.OXYGEN_GAS.get()) == 100
            && tank.getFluidAmount() == 1200, "Draining keeps the two-to-one ratio");
        h.assertTrue(tank.fill(new FluidStack(Fluids.WATER, 100), SIMULATE) == 0, "A liquid never joins a gas mixture");
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void anOutletGivesEveryGasAsOneMixtureAndAPureGasStaysPure(GameTestHelper h) {
        var dioxide = new FluidTank(1000);
        var nitrogen = new FluidTank(4000);
        dioxide.fill(new FluidStack(CEUranium.SULFUR_DIOXIDE.get(), 500), EXECUTE);
        IFluidHandler port = new IFluidHandler() {
            private final FluidTank[] tanks = {dioxide, nitrogen};
            @Override public int getTanks() { return 2; }
            @Override public FluidStack getFluidInTank(int tank) { return tanks[tank].getFluid(); }
            @Override public int getTankCapacity(int tank) { return tanks[tank].getCapacity(); }
            @Override public boolean isFluidValid(int tank, FluidStack stack) { return false; }
            @Override public int fill(FluidStack resource, FluidAction action) { return 0; }
            @Override public FluidStack drain(FluidStack resource, FluidAction action) {
                for (var tank : tanks) if (FluidStack.isSameFluidSameComponents(tank.getFluid(), resource)) return tank.drain(resource, action);
                return FluidStack.EMPTY;
            }
            @Override public FluidStack drain(int maxDrain, FluidAction action) { return dioxide.isEmpty() ? nitrogen.drain(maxDrain, action) : dioxide.drain(maxDrain, action); }
        };
        var outlet = GasMixturePort.outlet(port, null);
        h.assertTrue(outlet.getFluidInTank(0).is(CEUranium.SULFUR_DIOXIDE.get()), "A single gas product leaves as that pure gas");
        nitrogen.fill(new FluidStack(CEFluids.NITROGEN_GAS.get(), 1900), EXECUTE);
        var shown = outlet.getFluidInTank(0);
        h.assertTrue(shown.is(CEMixtures.GAS_MIXTURE.get()) && shown.getAmount() == 2400, "Two gas products show as one 2400 mB mixture");
        var taken = outlet.drain(shown.copyWithAmount(480), EXECUTE);
        h.assertTrue(amountOf(taken, CEUranium.SULFUR_DIOXIDE.get()) == 100 && amountOf(taken, CEFluids.NITROGEN_GAS.get()) == 380
            && dioxide.getFluidAmount() == 400 && nitrogen.getFluidAmount() == 1520, "Draining takes each product in proportion to its stock");
        h.succeed();
    }

    @GameTest(template = "test_reactor", timeoutTicks = 300)
    public static void theAbsorptionTowerTakesTheSulfurDioxideOutOfAMixtureAndLetsTheNitrogenOutAtTheTop(GameTestHelper h) {
        var tower = AbsorptionTowerGameTests.tower(h.getLevel(), h.absolutePos(new BlockPos(5, 2, 5)), 4, true);
        var bottom = tower.getBlockPos();var top = bottom.above(3);var level = h.getLevel();
        // Create attaches the pass-through buffer when the block entity first ticks.
        h.runAfterDelay(2, () -> {
            var feed = mixture(CEUranium.SULFUR_DIOXIDE.get(), 250, CEFluids.NITROGEN_GAS.get(), 950);
            var inlet = level.getCapability(Capabilities.FluidHandler.BLOCK, bottom.east(), Direction.EAST);
            h.assertTrue(inlet.fill(feed, EXECUTE) == 1200, "The bottom takes the whole 1200 mB of burner gas");
            h.assertTrue(level.getCapability(Capabilities.FluidHandler.BLOCK, top, Direction.UP).fill(new FluidStack(Fluids.WATER, 250), EXECUTE) == 250, "Water enters at the top");
            var offgas = level.getCapability(Capabilities.FluidHandler.BLOCK, top.west(), Direction.WEST);
            h.assertTrue(offgas.getFluidInTank(0).is(CEFluids.NITROGEN_GAS.get()) && offgas.getFluidInTank(0).getAmount() == 950,
                "The nitrogen the tower does not absorb waits at the top outlet as pure nitrogen");
            h.succeedWhen(() -> {
                var acid = level.getCapability(Capabilities.FluidHandler.BLOCK, bottom, Direction.DOWN).drain(new FluidStack(CEUranium.SULFUROUS_ACID.get(), 1000), SIMULATE);
                h.assertTrue(acid.getAmount() == 250, "The sulfur dioxide taken from the mixture becomes sulfurous acid: " + tower.status());
                h.assertTrue(offgas.drain(2000, EXECUTE).getAmount() == 950, "All the nitrogen leaves at the top");
            });
        });
    }
}
