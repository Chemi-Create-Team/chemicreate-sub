package net.createplant.gametest;

import net.createplant.CreatePlant;
import net.createplant.block.GasTankContainerBlock;
import net.createplant.block.entity.FuelChannelBlockEntity;
import net.createplant.registry.CETransport;
import net.createplant.thermal.FluidState;
import net.createplant.thermal.TwoPhase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class HighPressureSeparatorGameTests {
    private static BlockPos lay(GameTestHelper h, int width) {
        var first = new BlockPos(2, 2, 2);
        var state = CETransport.HIGH_PRESSURE_SEPARATOR.get().defaultBlockState().setValue(GasTankContainerBlock.AXIS, Direction.Axis.X);
        for (int x = 0; x < GasTankContainerBlock.length(width); x++) for (int y = 0; y < width; y++) for (int z = 0; z < width; z++) h.setBlock(first.offset(x, y, z), state);
        return first;
    }

    @GameTest(template = "test_fractionation")
    public static void theSeparatorSplitsTheReactorsWetStreamIntoWaterAndSteam(GameTestHelper h) {
        var first = lay(h, 1);
        var level = h.getLevel();
        var at = h.absolutePos(first);
        var inlet = level.getCapability(Capabilities.FluidHandler.BLOCK, at, Direction.NORTH);
        var gas = level.getCapability(Capabilities.FluidHandler.BLOCK, at, Direction.UP);
        var liquid = level.getCapability(Capabilities.FluidHandler.BLOCK, at, Direction.DOWN);
        h.assertTrue(inlet != null && gas != null && liquid != null, "A finished separator exposes a side inlet, a top outlet and a bottom outlet");
        var wet = FuelChannelBlockEntity.outletStack(1000, .5, 0);
        h.assertTrue(TwoPhase.is(wet) && inlet.fill(wet, SIMULATE) == 1000 && gas.getFluidInTank(0).isEmpty() && liquid.getFluidInTank(0).isEmpty(),
            "The inlet previews the whole wet parcel without storing it");
        h.assertTrue(inlet.fill(wet, EXECUTE) == 1000, "The inlet takes the whole parcel");
        var steam = gas.drain(2000, EXECUTE);
        var water = liquid.drain(2000, EXECUTE);
        h.assertTrue(steam.getAmount() > 0 && FluidState.gas(steam) && !TwoPhase.is(steam) && water.getAmount() > 0 && !FluidState.gas(water) && !TwoPhase.is(water)
            && steam.getAmount() + water.getAmount() == 1000, "Steam leaves the top and water the bottom, one mB of each phase for each mB of the parcel: " + steam + " " + water);
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void onlyAFinishedSeparatorTakesAStream(GameTestHelper h) {
        var state = CETransport.HIGH_PRESSURE_SEPARATOR.get().defaultBlockState().setValue(GasTankContainerBlock.AXIS, Direction.Axis.X);
        var at = new BlockPos(2, 2, 2);
        for (int x = 0; x < 3; x++) for (int y = 0; y < 2; y++) for (int z = 0; z < 2; z++) h.setBlock(at.offset(x, y, z), state);
        h.assertTrue(h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(at), Direction.NORTH) == null, "A 2 x 2 run three long is no separator and takes nothing");
        h.succeed();
    }
}
