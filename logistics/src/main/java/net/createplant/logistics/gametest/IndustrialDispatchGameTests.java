package net.createplant.logistics.gametest;

import net.createplant.gametest.IndustrialExpansionGameTests;
import net.createplant.gametest.SurvivalReachabilityGameTests;
import net.createplant.logistics.block.entity.IndustrialDispatchBlockEntity;
import net.createplant.logistics.registry.CEIndustrialExpansion;
import com.simibubi.create.AllBlocks;
import net.createplant.CreatePlant;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.items.IItemHandler;
import static net.createplant.gametest.IndustrialExpansionGameTests.*;
import com.simibubi.create.content.logistics.vault.ItemVaultBlock;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import static net.createplant.gametest.SurvivalReachabilityGameTests.has;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public class IndustrialDispatchGameTests {
    private record Dispatch(IndustrialDispatchBlockEntity board, IItemHandler warehouse, BlockPos relativePos) { }

    private static Dispatch dispatch(GameTestHelper h, String order) {
        h.setBlock(BASE, AllBlocks.ITEM_VAULT.getDefaultState().setValue(ItemVaultBlock.HORIZONTAL_AXIS, Direction.Axis.X));
        var vault = (ItemVaultBlockEntity) h.getBlockEntity(BASE);vault.tick();
        BlockPos at = BASE.north();
        h.setBlock(at, CEIndustrialExpansion.DISPATCH.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        var board = (IndustrialDispatchBlockEntity) h.getBlockEntity(at);
        for (int i = 0; i < board.orders().size() && !board.order().id().equals(CreatePlant.location("industrial_delivery/" + order)); i++) board.nextOrder(1);
        h.assertTrue(board.order() != null && board.order().id().equals(CreatePlant.location("industrial_delivery/" + order)), "The requested delivery is loaded: " + order);
        var warehouse = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(BASE), Direction.NORTH);
        h.assertTrue(warehouse != null, "A standard Create vault exposes the warehouse capability");
        return new Dispatch(board, warehouse, at);
    }

    private static void supply(GameTestHelper h, Dispatch setup) {
        for (var ingredient : setup.board().order().value().getIngredients())
            h.assertTrue(ItemHandlerHelper.insertItemStacked(setup.warehouse(), ingredient.getItems()[0].copyWithCount(1), false).isEmpty(), "The vault accepts the delivery goods");
    }

    private static void runOrder(IndustrialDispatchBlockEntity board) {
        for (int i = 0; i < 10; i++) board.processOrder();
    }

    @GameTest(template = "test_fractionation", timeoutTicks = 260)
    public static void actualDispatchTicksConsumeOneFiniteVaultCharge(GameTestHelper h) {
        var setup = dispatch(h, "alloy");supply(h, setup);
        h.runAfterDelay(220, () -> {
            h.assertTrue(setup.board().completed() == 1 && count(setup.warehouse()) == 0,
                "Ten seconds of real server ticks deliver exactly one finite vault charge");
            var output = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(setup.relativePos()), Direction.NORTH);
            h.assertTrue(output != null && output.extractItem(0, 64, true).getCount() == 5 && count(setup.board().rewards()) == 5,
                "A funnel's simulated reward extraction leaves all five emeralds stored");
            h.assertTrue(output.extractItem(0, 64, false).getCount() == 5 && output.extractItem(0, 64, false).isEmpty(), "A delivered reward can be extracted only once");
            h.succeed();
        });
    }

    @GameTest(template = "test_fractionation")
    public static void dispatchFullOutputAndRedstonePauseRetainTheShipment(GameTestHelper h) {
        var setup = dispatch(h, "alloy");supply(h, setup);var board = setup.board();
        board.rewards().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 64));runOrder(board);
        h.assertTrue(board.status().equals("output") && board.completed() == 0 && count(setup.warehouse()) == 64,
            "A full reward outlet retains the entire shipment");
        board.rewards().setStackInSlot(0, ItemStack.EMPTY);
        h.setBlock(setup.relativePos().west(), Blocks.REDSTONE_BLOCK);runOrder(board);
        h.assertTrue(board.status().equals("paused") && board.completed() == 0 && count(setup.warehouse()) == 64,
            "Redstone pauses delivery without consuming the goods");
        h.setBlock(setup.relativePos().west(), Blocks.AIR);runOrder(board);
        h.assertTrue(board.completed() == 1 && count(setup.warehouse()) == 0 && count(board.rewards()) == 5,
            "Removing the signal completes exactly the retained shipment");h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void dispatchSaveReloadAndOrderChangesDoNotDuplicateRewards(GameTestHelper h) {
        var setup = dispatch(h, "alloy");supply(h, setup);var board = setup.board();
        for (int i = 0; i < 3; i++) board.processOrder();
        h.assertTrue(board.progress() == 60 && count(setup.warehouse()) == 64, "A partial order has not consumed its goods");
        var saved = board.saveWithoutMetadata(h.getLevel().registryAccess());board.loadWithComponents(saved, h.getLevel().registryAccess());
        h.assertTrue(board.progress() == 60, "Reload retains finite delivery progress");
        for (int i = 0; i < 7; i++) board.processOrder();
        h.assertTrue(board.completed() == 1 && count(board.rewards()) == 5 && count(setup.warehouse()) == 0, "Reloaded progress commits once");
        var completed = board.saveWithoutMetadata(h.getLevel().registryAccess());board.loadWithComponents(completed, h.getLevel().registryAccess());runOrder(board);
        h.assertTrue(board.completed() == 1 && count(board.rewards()) == 5, "Reloading a completed shipment does not pay twice");
        board.nextOrder(1);h.assertTrue(board.progress() == 0, "Selecting a different order starts its own progress");h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void liquidOrdersUseTheActualPipePortWithoutFreeCargo(GameTestHelper h) {
        var setup = dispatch(h, "fuel");var board = setup.board();
        var input = board.order().value().getFluidIngredients().getFirst().getFluids()[0].copy();
        var pipe = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(setup.relativePos()), Direction.EAST);
        h.assertTrue(pipe != null && pipe.fill(input, FluidAction.SIMULATE) == input.getAmount() && board.cargo().isEmpty(), "Simulating a fuel delivery does not create cargo");
        h.assertTrue(pipe.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE) == 0, "Water cannot substitute for ordered industrial fuel");
        h.assertTrue(pipe.fill(input, FluidAction.EXECUTE) == input.getAmount(), "The delivery board receives fuel through a standard fluid capability");
        runOrder(board);h.assertTrue(board.completed() == 1 && board.cargo().isEmpty() && count(board.rewards()) == 4,
            "One finite liquid shipment pays once and consumes exactly the ordered fuel");h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void dispatchStopsIfItsWarehouseIsRemoved(GameTestHelper h) {
        var setup = dispatch(h, "alloy");supply(h, setup);var board = setup.board();board.processOrder();
        h.setBlock(BASE, Blocks.AIR);runOrder(board);
        h.assertTrue(board.status().equals("vault") && board.completed() == 0 && count(board.rewards()) == 0 && board.progress() == 0,
            "An absent warehouse invalidates progress before any reward is created");h.succeed();
    }

}
