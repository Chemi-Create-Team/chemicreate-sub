package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.kinetics.steamEngine.SteamEngineBlock;
import com.simibubi.create.content.logistics.vault.ItemVaultBlock;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import net.createplant.CreatePlant;
import net.createplant.block.HorizontalFluidTankBlock;
import net.createplant.block.entity.IndustrialDispatchBlockEntity;
import net.createplant.block.entity.HorizontalTankBlockEntity;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CEIndustrialExpansion;
import net.createplant.registry.CETransport;
import net.createplant.thermal.FluidThermal;
import net.createplant.thermal.SteamGrades;
import net.createplant.registry.CEProcessFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class IndustrialExpansionGameTests {
    private static final BlockPos BASE = new BlockPos(3, 2, 3);
    private IndustrialExpansionGameTests() { }
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

    private static int count(IItemHandler port) {
        int count = 0;for (int slot = 0; slot < port.getSlots(); slot++) count += port.getStackInSlot(slot).getCount();return count;
    }

    private static void runOrder(IndustrialDispatchBlockEntity board) {
        for (int i = 0; i < 10; i++) board.processOrder();
    }

    private static HorizontalTankBlockEntity boiler(GameTestHelper h, BlockPos origin, Direction.Axis axis, int length) {
        var state = CETransport.HORIZONTAL_FLUID_TANK.get().defaultBlockState().setValue(HorizontalFluidTankBlock.AXIS, axis);
        for (int i = 0; i < length; i++) h.setBlock(axis == Direction.Axis.X ? origin.east(i) : origin.south(i), state);
        return (HorizontalTankBlockEntity) ((HorizontalTankBlockEntity) h.getBlockEntity(origin)).getControllerBE();
    }

    private static void burner(GameTestHelper h, BlockPos pos, boolean superheated) {
        h.setBlock(pos.below(), AllBlocks.BLAZE_BURNER.getDefaultState().setValue(BlazeBurnerBlock.HEAT_LEVEL,
            superheated ? BlazeBurnerBlock.HeatLevel.SEETHING : BlazeBurnerBlock.HeatLevel.KINDLED));
    }

    private static IFluidHandler port(GameTestHelper h, BlockPos pos, Direction side) {
        var port = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(pos), side);
        h.assertTrue(port != null, "Create pipes can query the boiler fluid handler on " + side);return port;
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

    @GameTest(template = "test_fractionation")
    public static void horizontalTanksBoilAtAnyLengthAlongBothHorizontalAxes(GameTestHelper h) {
        var shortBoiler = boiler(h, BASE, Direction.Axis.X, 2);burner(h, BASE, true);burner(h, BASE.east(), true);
        port(h, BASE, Direction.NORTH).fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        var shortEnd = (HorizontalTankBlockEntity) h.getBlockEntity(BASE.east());shortEnd.generateSteam();
        h.assertTrue(shortBoiler.getHeight() == 2 && shortEnd.steam().getFluidAmount() > 0 && shortEnd.status().equals("running"), "Two heated cells already boil water");
        var z = boiler(h, new BlockPos(3, 2, 8), Direction.Axis.Z, 3);burner(h, new BlockPos(3, 2, 10), false);
        port(h, new BlockPos(3, 2, 8), Direction.WEST).fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        var zEnd = (HorizontalTankBlockEntity) h.getBlockEntity(new BlockPos(3, 2, 10));zEnd.generateSteam();
        h.assertTrue(z.getHeight() == 3 && zEnd.steam().getFluid().is(CEProcessFluids.HIGH_PRESSURE_STEAM.get()), "A tank along Z boils at its south end");h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void superheatedTubeSteamConservesWaterMassAndAddedEnergy(GameTestHelper h) {
        var root = boiler(h, BASE, Direction.Axis.X, 3);burner(h, BASE, true);burner(h, BASE.east(), true);burner(h, BASE.east(2), true);
        var boiler = (HorizontalTankBlockEntity) h.getBlockEntity(BASE.east(2));
        var feed = FluidThermal.at(new FluidStack(Fluids.WATER, 1000), 293.15);
        port(h, BASE, Direction.NORTH).fill(feed, FluidAction.EXECUTE);double initial = FluidThermal.energy(root.getTankInventory().getFluid());
        boiler.generateSteam();
        int water = root.getTankInventory().getFluidAmount(), steam = boiler.steam().getFluidAmount();
        h.assertTrue(steam > 0 && water + steam == 1000, "Tube steam keeps water-equivalent mass: " + water + "+" + steam);
        h.assertTrue(boiler.steam().getFluid().is(CEProcessFluids.SUPERHEATED_STEAM.get()), "Superheated burner produces the superheated grade");
        double credit=boiler.saveWithoutMetadata(h.getLevel().registryAccess()).getDouble("TubeHeat");
        h.assertTrue(Math.abs(steam*SteamGrades.SUPERHEATED.joulesPerMb()+credit-boiler.heatWatts()/20)<.1,"Fixed steam cost plus rounding credit equals paid heater heat");
        h.assertTrue(Math.abs(boiler.heatWatts() - 3 * 65.536 * SteamGrades.SUPERHEATED.joulesPerMb()) < .1,
            "Three superheated burners supply the calibrated heat for their rated steam rate");h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void tubeBoilerStopsAtFullOutputAndAfterLosingHeat(GameTestHelper h) {
        var root = boiler(h, BASE, Direction.Axis.X, 3);burner(h, BASE, true);burner(h, BASE.east(), true);burner(h, BASE.east(2), true);
        var boiler = (HorizontalTankBlockEntity) h.getBlockEntity(BASE.east(2));
        port(h, BASE, Direction.NORTH).fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        boiler.steam().fill(new FluidStack(CEProcessFluids.SUPERHEATED_STEAM.get(),Integer.MAX_VALUE),FluidAction.EXECUTE);
        boiler.generateSteam();h.assertTrue(boiler.status().equals("output") && root.getTankInventory().getFluidAmount() == 1000,
            "A full steam buffer keeps all feed water");
        boiler.steam().drain(Integer.MAX_VALUE, FluidAction.EXECUTE);boiler.generateSteam();
        h.assertTrue(boiler.steam().getFluidAmount() > 0, "Draining the outlet lets the boiler resume");
        int water = root.getTankInventory().getFluidAmount(), steam = boiler.steam().getFluidAmount();
        h.setBlock(BASE.east(2).below(), Blocks.AIR);boiler.generateSteam();
        h.assertTrue(boiler.status().equals("heat") && boiler.heatWatts() == 0 && root.getTankInventory().getFluidAmount() == water
            && boiler.steam().getFluidAmount() == steam, "Losing heat stops production without losing the existing contents");h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void tubePortsExposeSteamAndLiquidOnTheirActualFaces(GameTestHelper h) {
        var root = boiler(h, BASE, Direction.Axis.X, 3);burner(h, BASE, false);burner(h, BASE.east(), false);burner(h, BASE.east(2), false);
        var boiler = (HorizontalTankBlockEntity) h.getBlockEntity(BASE.east(2));
        var feed = port(h, BASE, Direction.NORTH);var output = port(h, BASE.east(2), Direction.UP);
        var level = h.getLevel();var absolute = h.absolutePos(BASE);
        h.assertTrue(FluidPipeBlock.canConnectTo(level, absolute, level.getBlockState(absolute), Direction.SOUTH)
            && FluidPipeBlock.canConnectTo(level, absolute, level.getBlockState(absolute), Direction.DOWN), "Stock Create pipes recognize both feed and steam ports");
        h.assertTrue(feed.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.SIMULATE) == 1000 && root.getTankInventory().isEmpty(), "Simulated feed fill cannot add water");
        feed.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);boiler.generateSteam();
        h.assertTrue(output.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE) == 0
            && feed.fill(new FluidStack(Fluids.LAVA, 1000), FluidAction.EXECUTE) == 0, "The boiling outlet rejects filling and the water keeps lava out");
        int held = boiler.steam().getFluidAmount();var preview = output.drain(held, FluidAction.SIMULATE);
        h.assertTrue(SteamGrades.of(preview)!=null && preview.getAmount() == held && boiler.steam().getFluidAmount() == held
            && preview.is(CEProcessFluids.HIGH_PRESSURE_STEAM.get()), "Normal burner steam is 350 C and simulated output retains its mass");
        h.assertTrue(feed.drain(1, FluidAction.SIMULATE).isEmpty() && port(h, BASE, Direction.DOWN).drain(1, FluidAction.SIMULATE).is(Fluids.WATER),
            "The side is the water inlet and the bottom is the water drain");
        h.assertTrue(output.drain(held, FluidAction.EXECUTE).getAmount() == held && output.drain(held, FluidAction.EXECUTE).isEmpty(),
            "An output charge can be extracted only once");
        var under = BASE.east(2).below();
        h.setBlock(under, AllBlocks.BLAZE_BURNER.getDefaultState().setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SMOULDERING));
        boiler.generateSteam();
        h.assertTrue(output.drain(1, FluidAction.EXECUTE).isEmpty() && boiler.steam().isEmpty(),
            "A fitted burner that runs out of fuel does not turn the outlet into a feedwater connection");
        h.setBlock(under, Blocks.AIR);
        h.assertTrue(output.drain(1, FluidAction.SIMULATE).is(Fluids.WATER), "Removing the terminal burner restores cold storage on the same connection");
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void localTubeSteamSurvivesReloadAndDoesNotDriveCreatesVirtualBoiler(GameTestHelper h) {
        var root = boiler(h, BASE, Direction.Axis.X, 3);BlockPos hot = BASE.east(2);burner(h, hot, true);burner(h, BASE, true);burner(h, BASE.east(), true);
        port(h, BASE, Direction.NORTH).fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        var part = (HorizontalTankBlockEntity) h.getBlockEntity(hot);part.generateSteam();
        h.assertTrue(root.steam().isEmpty() && part.steam().getFluidAmount() > 0, "Only the heated section owns this finite steam charge");
        h.assertTrue(part.steam().getFluid().is(CEProcessFluids.SUPERHEATED_STEAM.get()),
            "A non-controller steam section reports its own 20 bar pressure");
        int water = root.getTankInventory().getFluidAmount(), amount = part.steam().getFluidAmount();double energy = FluidThermal.energy(part.steam().getFluid());
        var saved = part.saveWithoutMetadata(h.getLevel().registryAccess());part.loadWithComponents(saved, h.getLevel().registryAccess());
        h.assertTrue(part.steam().getFluidAmount() == amount && Math.abs(FluidThermal.energy(part.steam().getFluid()) - energy) < .1
            && root.getTankInventory().getFluidAmount() == water, "Save/reload preserves steam mass, energy and shared feed water");
        h.setBlock(BASE.above(), AllBlocks.STEAM_ENGINE.getDefaultState().setValue(SteamEngineBlock.FACE, AttachFace.FLOOR).setValue(SteamEngineBlock.FACING, Direction.NORTH));
        root.updateBoilerState();int before = part.steam().getFluidAmount();part.generateSteam();
        h.assertTrue(root.boiler.attachedEngines == 1 && part.status().equals("engines") && part.steam().getFluidAmount() == before,
            "With a steam engine attached the tank is Create's boiler and raises no pipe steam");
        h.setBlock(BASE.above(), Blocks.AIR);root.updateBoilerState();
        h.setBlock(BASE.east(), Blocks.AIR);int remaining = part.steam().getFluidAmount();part.generateSteam();
        h.assertTrue(part.steam().getFluidAmount() >= remaining, "A split end keeps its steam, and boils on with any water it was given");h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void squareTubeBoilerExposesItsBottomSteamAtTheOuterEnd(GameTestHelper h) {
        var state = CETransport.HORIZONTAL_FLUID_TANK.get().defaultBlockState().setValue(HorizontalFluidTankBlock.AXIS, Direction.Axis.X);
        for (int x = 0; x < 3; x++) for (int y = 0; y < 2; y++) for (int z = 0; z < 2; z++) h.setBlock(BASE.offset(x, y, z), state);
        var root = (HorizontalTankBlockEntity) ((HorizontalTankBlockEntity) h.getBlockEntity(BASE)).getControllerBE();
        h.assertTrue(root.getWidth() == 2 && root.getHeight() == 3, "A genuine 2 by 2 cross-section forms one horizontal tank");
        BlockPos end = BASE.east(2);
        for (int x = 0; x < 3; x++) burner(h, BASE.east(x), true);
        port(h, BASE, Direction.NORTH).fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        var hot = (HorizontalTankBlockEntity) h.getBlockEntity(end);hot.generateSteam();
        h.assertTrue(h.getLevel().getBlockState(h.absolutePos(end.above())).is(CETransport.HORIZONTAL_FLUID_TANK.get()),
            "The upper tank row occupies the cell above the steam-producing lower row");
        var outlet = port(h, end, Direction.EAST);var preview = outlet.drain(1000, FluidAction.SIMULATE);
        h.assertTrue(SteamGrades.of(preview)!=null && preview.getAmount() > 0, "The outer end face exposes the lower row's steam outside the multiblock");
        var absolute = h.absolutePos(end);
        h.assertTrue(FluidPipeBlock.canConnectTo(h.getLevel(), absolute, h.getLevel().getBlockState(absolute), Direction.WEST),
            "An ordinary pipe can connect to the accessible outer steam outlet");
        h.assertTrue(outlet.drain(preview, FluidAction.EXECUTE).getAmount() == preview.getAmount(), "The accessible end outlet transfers exactly its previewed charge");
        h.succeed();
    }
}
