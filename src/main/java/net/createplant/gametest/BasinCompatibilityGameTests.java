package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import net.createplant.CreatePlant;
import net.createplant.registry.CEBlocks;
import net.createplant.registry.CEFluids;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Interoperability boundaries: ordinary Create machines retain Create semantics. */
@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BasinCompatibilityGameTests {
    @GameTest(template = "test_empty")
    public static void openBasinsTakeNoGas(GameTestHelper helper) {
        BlockPos at = new BlockPos(2, 1, 2);
        helper.setBlock(at, AllBlocks.BASIN.get());
        IFluidHandler port = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(at), Direction.NORTH);
        helper.assertTrue(port.fill(new FluidStack(CEFluids.HYDROGEN_GAS.get(), 750), IFluidHandler.FluidAction.EXECUTE) == 0, "An open basin takes no gas");
        helper.assertTrue(port.fill(new FluidStack(Fluids.WATER, 250), IFluidHandler.FluidAction.EXECUTE) == 250, "Liquids still go in");
        helper.succeed();
    }

    /** Open basins take no gas, so a mixing or compacting recipe that asks for one could never run (the old mixer AOD recipe did). */
    @GameTest(template = "test_empty")
    public static void noBasinRecipeAsksForAGas(GameTestHelper helper) {
        var unreachable = new java.util.ArrayList<String>();
        for (var type : List.of(com.simibubi.create.AllRecipeTypes.MIXING, com.simibubi.create.AllRecipeTypes.COMPACTING))
            for (var holder : helper.getLevel().getRecipeManager().getAllRecipesFor(type.<net.minecraft.world.item.crafting.RecipeInput, com.simibubi.create.content.processing.basin.BasinRecipe>getType()))
                for (var ingredient : holder.value().getFluidIngredients())
                    for (var fluid : ingredient.getFluids()) if (net.createplant.basin.BasinGases.gas(fluid)) unreachable.add(holder.id() + " (" + fluid.getFluid() + ")");
        helper.assertTrue(unreachable.isEmpty(), "Basin recipes that need a gas an open basin refuses: " + unreachable);
        helper.succeed();
    }





    @GameTest(template = "test_empty")
    public static void ordinaryAdjacentBasinsKeepCollision(GameTestHelper helper) {
        BlockPos left = new BlockPos(2, 1, 2);
        helper.setBlock(left, AllBlocks.BASIN.get());
        BlockPos absolute = helper.absolutePos(left);
        VoxelShape original = helper.getLevel().getBlockState(absolute).getCollisionShape(helper.getLevel(), absolute);
        helper.setBlock(left.east(), AllBlocks.BASIN.get());
        VoxelShape adjacent = helper.getLevel().getBlockState(absolute).getCollisionShape(helper.getLevel(), absolute);
        helper.assertTrue(!Shapes.joinIsNotEmpty(original, adjacent, BooleanOp.NOT_SAME),
            "Adjacency must not remove ordinary Create basin collision walls");
        helper.succeed();
    }

    private static BasinBlockEntity basin(GameTestHelper helper, BlockPos position) {
        return (BasinBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(position));
    }
    private static int inputCapacity(BasinBlockEntity basin) {
        IFluidHandler input = basin.inputTank.getCapability();
        int capacity = 0;
        for (int i = 0; i < input.getTanks(); i++) capacity += input.getTankCapacity(i);
        return capacity;
    }
    private static int fluidAmount(BasinBlockEntity basin, net.minecraft.world.level.material.Fluid fluid) {
        IFluidHandler input = basin.inputTank.getCapability();
        int amount = 0;
        for (int i = 0; i < input.getTanks(); i++) {
            FluidStack stack = input.getFluidInTank(i);
            if (!stack.isEmpty() && stack.getFluid() == fluid) amount += stack.getAmount();
        }
        return amount;
    }

    @GameTest(template = "test_empty", timeoutTicks = 400)
    public static void aMixerQuenchesMoltenPolymerIntoPelletsAndWarmWater(GameTestHelper helper) {
        BlockPos basin = new BlockPos(2, 1, 2), motor = new BlockPos(3, 2, 2);
        helper.setBlock(basin, AllBlocks.BASIN.get());
        helper.setBlock(new BlockPos(2, 3, 2), AllBlocks.MECHANICAL_MIXER.get());
        // The mixer meshes as a cogwheel only, so a vertical cogwheel beside it carries the drive.
        helper.setBlock(new BlockPos(3, 3, 2), AllBlocks.COGWHEEL.getDefaultState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, Direction.Axis.Y));
        helper.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.UP));
        IFluidHandler port = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(basin), Direction.NORTH);
        var melt = net.createplant.thermal.FluidThermal.at(new FluidStack(net.createplant.registry.CEProcessFluids.MOLTEN_POLYETHYLENE.get(), 250), 480);
        helper.assertTrue(port.fill(melt, IFluidHandler.FluidAction.EXECUTE) == 250 && port.fill(new FluidStack(Fluids.WATER, 100), IFluidHandler.FluidAction.EXECUTE) == 100, "The basin takes the melt and the quench water");
        helper.runAfterDelay(5, () -> ((com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity) helper.getBlockEntity(motor)).generatedSpeed.setValue(128));
        helper.succeedWhen(() -> {
            var items = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(basin), null);
            int pellets = 0;
            for (int slot = 0; slot < items.getSlots(); slot++) if (items.getStackInSlot(slot).is(net.createplant.registry.CEItems.POLYETHYLENE.get())) pellets += items.getStackInSlot(slot).getCount();
            int warm = 0, left = 0;
            for (int tank = 0; tank < port.getTanks(); tank++) {
                var stack = port.getFluidInTank(tank);
                if (stack.is(net.createplant.registry.CEProcessFluids.WARM_WATER.get())) warm += stack.getAmount();
                else if (stack.is(net.createplant.registry.CEProcessFluids.MOLTEN_POLYETHYLENE.get()) || stack.is(Fluids.WATER)) left += stack.getAmount();
            }
            helper.assertTrue(pellets == 2 && warm == 100 && left == 0, "250 mB melt and 100 mB water give two polyethylene and 100 mB warm water: " + pellets + " / " + warm + " / " + left);
        });
    }
}
