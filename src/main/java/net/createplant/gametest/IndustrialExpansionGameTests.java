package net.createplant.gametest;

import net.createplant.thermal.FluidForm;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.kinetics.steamEngine.SteamEngineBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import net.createplant.CreatePlant;
import net.createplant.block.HorizontalFluidTankBlock;
import net.createplant.block.entity.HorizontalTankBlockEntity;
import net.createplant.registry.CETransport;
import net.createplant.thermal.FluidState;
import net.createplant.thermal.FluidThermal;
import net.createplant.thermal.SteamGrades;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class IndustrialExpansionGameTests {
    static final BlockPos BASE = new BlockPos(3, 2, 3);
    private IndustrialExpansionGameTests() { }
    static int count(IItemHandler port) {
        int count = 0;for (int slot = 0; slot < port.getSlots(); slot++) count += port.getStackInSlot(slot).getCount();return count;
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

    @GameTest(template = "test_fractionation")
    public static void horizontalTanksBoilAtAnyLengthAlongBothHorizontalAxes(GameTestHelper h) {
        var shortBoiler = boiler(h, BASE, Direction.Axis.X, 2);burner(h, BASE, true);burner(h, BASE.east(), true);
        port(h, BASE, Direction.NORTH).fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        var shortEnd = (HorizontalTankBlockEntity) h.getBlockEntity(BASE.east());shortEnd.generateSteam();
        h.assertTrue(shortBoiler.getHeight() == 2 && shortEnd.steam().getFluidAmount() > 0 && shortEnd.status().equals("running"), "Two heated cells already boil water");
        var z = boiler(h, new BlockPos(3, 2, 8), Direction.Axis.Z, 3);burner(h, new BlockPos(3, 2, 10), false);
        port(h, new BlockPos(3, 2, 8), Direction.WEST).fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        var zEnd = (HorizontalTankBlockEntity) h.getBlockEntity(new BlockPos(3, 2, 10));zEnd.generateSteam();
        h.assertTrue(z.getHeight() == 3 && SteamGrades.of(zEnd.steam().getFluid()) == SteamGrades.ORDINARY, "A tank along Z boils at its south end");h.succeed();
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
        h.assertTrue(SteamGrades.of(boiler.steam().getFluid()) == SteamGrades.ORDINARY, "Superheated burners retain the horizontal tank’s atmospheric steam grade");
        double credit=boiler.saveWithoutMetadata(h.getLevel().registryAccess()).getDouble("TubeHeat");
        h.assertTrue(Math.abs(steam*SteamGrades.ORDINARY.joulesPerMb()+credit-boiler.heatWatts()/20)<.1,"Fixed steam cost plus rounding credit equals paid heater heat");
        h.assertTrue(Math.abs(boiler.heatWatts() - 3 * 131.072 * SteamGrades.ORDINARY.joulesPerMb()) < .1,
            "Three superheated burners supply the calibrated heat for their rated steam rate");h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void tubeBoilerMixesLeftoverSteamAndStopsAfterLosingHeat(GameTestHelper h) {
        var root = boiler(h, BASE, Direction.Axis.X, 3);burner(h, BASE, true);burner(h, BASE.east(), true);burner(h, BASE.east(2), true);
        var boiler = (HorizontalTankBlockEntity) h.getBlockEntity(BASE.east(2));
        port(h, BASE, Direction.NORTH).fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        boiler.steam().fill(FluidForm.SUPERHEATED_STEAM.stack(1000),FluidAction.EXECUTE);
        boiler.generateSteam();
        var mixed = boiler.steam().getFluid();
        // Steam is water in its gas state, so leftover superheated steam and new steam share the buffer at their weighted average.
        h.assertTrue(mixed.is(Fluids.WATER) && mixed.getAmount() > 1000 && root.getTankInventory().getFluidAmount() < 1000
            && FluidState.gas(mixed), "Leftover steam mixes with the new steam instead of blocking the boiler: "
            + mixed.getAmount() + " at " + FluidState.temperature(mixed) + " " + boiler.status());
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
            && SteamGrades.of(preview) == SteamGrades.ORDINARY, "Simulated atmospheric steam output retains its mass");
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
        h.assertTrue(SteamGrades.of(part.steam().getFluid()) == SteamGrades.ORDINARY,
            "A non-controller steam section reports its own atmospheric-grade steam");
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
