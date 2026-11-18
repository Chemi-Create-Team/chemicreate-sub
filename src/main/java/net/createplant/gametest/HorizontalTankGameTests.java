package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.steamEngine.SteamEngineBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import net.createplant.CreatePlant;
import net.createplant.block.HorizontalFluidTankBlock;
import net.createplant.registry.CETransport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class HorizontalTankGameTests {
    private static final BlockPos BASE = new BlockPos(3, 2, 3);

    private static BlockState tank(Direction.Axis axis) { return CETransport.HORIZONTAL_FLUID_TANK.get().defaultBlockState().setValue(HorizontalFluidTankBlock.AXIS, axis); }

    private static FluidTankBlockEntity at(GameTestHelper h, BlockPos pos) { return (FluidTankBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(pos)); }

    @GameTest(template = "test_fractionation")
    public static void tanksLyingAlongTheSameAxisFormOneMultiblockWithEndsMarked(GameTestHelper h) {
        for (int i = 0; i < 4; i++) h.setBlock(BASE.east(i), tank(Direction.Axis.X));
        var root = at(h, BASE).getControllerBE();
        h.assertTrue(root.getHeight() == 4 && root.getWidth() == 1 && root.getTotalTankSize() == 4, "Four tanks in a row are one tank of length four: " + root.getHeight() + "x" + root.getWidth());
        var first = h.getLevel().getBlockState(h.absolutePos(BASE));
        var middle = h.getLevel().getBlockState(h.absolutePos(BASE.east()));
        var last = h.getLevel().getBlockState(h.absolutePos(BASE.east(3)));
        h.assertTrue(first.getValue(FluidTankBlock.BOTTOM) && !first.getValue(FluidTankBlock.TOP), "The west end is the bottom end");
        h.assertTrue(!middle.getValue(FluidTankBlock.BOTTOM) && !middle.getValue(FluidTankBlock.TOP), "The middle is neither end");
        h.assertTrue(last.getValue(FluidTankBlock.TOP) && !last.getValue(FluidTankBlock.BOTTOM), "The east end is the top end");
        h.assertTrue(root.getTankInventory().getCapacity() == 4 * FluidTankBlockEntity.getCapacityMultiplier(), "Capacity grows with every block");
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void squareSectionsAndMixedAxesConnectCorrectly(GameTestHelper h) {
        for (int i = 0; i < 2; i++) for (int y = 0; y < 2; y++) for (int z = 0; z < 2; z++) h.setBlock(BASE.offset(i, y, z), tank(Direction.Axis.X));
        var root = at(h, BASE).getControllerBE();
        h.assertTrue(root.getHeight() == 2 && root.getWidth() == 2, "A 2x2x2 block forms a section of two across: " + root.getHeight() + "x" + root.getWidth());
        h.setBlock(BASE.offset(5, 0, 0), tank(Direction.Axis.X));
        h.setBlock(BASE.offset(5, 0, 1), tank(Direction.Axis.Z));
        h.assertTrue(at(h, BASE.offset(5, 0, 0)).getControllerBE().getHeight() == 1 && at(h, BASE.offset(5, 0, 1)).getControllerBE().getHeight() == 1, "Tanks lying along different axes do not join");
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void fluidPipesAndBreakingBehaveLikeCreatesTank(GameTestHelper h) {
        for (int i = 0; i < 3; i++) h.setBlock(BASE.east(i), tank(Direction.Axis.X));
        var level = h.getLevel();
        var port = level.getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(BASE.east(2)), Direction.UP);
        h.assertTrue(port.fill(new FluidStack(Fluids.LAVA, 3000), FluidAction.EXECUTE) == 3000, "Any block of the tank takes fluid");
        h.assertTrue(at(h, BASE).getControllerBE().getTankInventory().getFluidAmount() == 3000, "The fluid is in the shared inventory");
        level.destroyBlock(h.absolutePos(BASE.east()), false);
        h.assertTrue(at(h, BASE).getControllerBE().getHeight() == 1 && at(h, BASE.east(2)).getControllerBE().getHeight() == 1, "Breaking the middle splits the tank");
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void burnersUnderTheWholeLengthAndEnginesOnTheSideMakeItABoiler(GameTestHelper h) {
        for (int i = 0; i < 3; i++) h.setBlock(BASE.east(i), tank(Direction.Axis.X));
        var root = at(h, BASE).getControllerBE();
        root.getTankInventory().fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        h.setBlock(BASE.east(2).below(), AllBlocks.BLAZE_BURNER.get().defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SEETHING));
        h.setBlock(BASE.east().below(), AllBlocks.BLAZE_BURNER.get().defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SEETHING));
        h.setBlock(BASE.above(), AllBlocks.STEAM_ENGINE.get().defaultBlockState().setValue(SteamEngineBlock.FACE, AttachFace.FLOOR).setValue(SteamEngineBlock.FACING, Direction.NORTH));
        root.updateBoilerState();
        root.boiler.updateTemperature(root);
        h.assertTrue(root.boiler.activeHeat >= 2, "Burners under the length of the tank count: " + root.boiler.activeHeat);
        h.assertTrue(root.boiler.attachedEngines == 1, "An engine on top of the tank is attached: " + root.boiler.attachedEngines);
        h.succeed();
    }
}
