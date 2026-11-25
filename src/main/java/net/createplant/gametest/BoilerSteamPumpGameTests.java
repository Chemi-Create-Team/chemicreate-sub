package net.createplant.gametest;

import net.createplant.thermal.FluidForm;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import net.createplant.CreatePlant;
import net.createplant.block.HorizontalFluidTankBlock;
import net.createplant.block.entity.HorizontalTankBlockEntity;
import net.createplant.block.entity.TankBoiler;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CEProcessFluids;
import net.createplant.registry.CETransport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

/** Boiler steam is an ordinary fluid: a pump on the roof moves it through any pipe, even one lying on the boiler. */
@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BoilerSteamPumpGameTests {
    private BoilerSteamPumpGameTests() { }

    private static void firedBurner(GameTestHelper h, BlockPos under) {
        h.setBlock(under, AllBlocks.BLAZE_BURNER.getDefaultState().setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SMOULDERING));
        var burner = (com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity) h.getBlockEntity(under);
        var tag = burner.saveWithoutMetadata(h.getLevel().registryAccess());tag.putInt("fuelLevel", 1);tag.putInt("burnTimeRemaining", 4000);
        burner.loadWithComponents(tag, h.getLevel().registryAccess());burner.updateBlockState();
    }

    private static BlockState pipe(Block block, Direction a, Direction b) {
        var state = block.defaultBlockState();
        for (Direction side : Direction.values()) state = state.setValue(FluidPipeBlock.PROPERTY_BY_DIRECTION.get(side), side == a || side == b);
        return state;
    }

    /** A pump on the roof cell at {@code outlet}, then a line that climbs, turns back and runs west along the roof to a gas receiver. */
    private static FluidTank roofLine(GameTestHelper h, BlockPos outlet, Block pipe) {
        BlockPos pump = outlet.above();
        h.setBlock(pump, AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(BlockStateProperties.FACING, Direction.UP));
        h.setBlock(pump.east(), AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y));
        h.setBlock(pump.east().above(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.DOWN));
        ((CreativeMotorBlockEntity) h.getBlockEntity(pump.east().above())).generatedSpeed.setValue(128);
        h.setBlock(pump.above(), pipe(pipe, Direction.DOWN, Direction.WEST));
        h.setBlock(pump.above().west(), pipe(pipe, Direction.EAST, Direction.DOWN));
        h.setBlock(pump.west(), pipe(pipe, Direction.UP, Direction.WEST));
        h.setBlock(pump.west(2), pipe(pipe, Direction.EAST, Direction.WEST));
        BlockPos receiver = pump.west(3);
        h.setBlock(receiver, CETransport.GAS_RECEIVER.get().defaultBlockState().setValue(net.createplant.block.PulsationTubeBlock.AXIS, Direction.Axis.X));
        return ((FluidTankBlockEntity) h.getBlockEntity(receiver)).getControllerBE().getTankInventory();
    }

    @GameTest(template = "test_fractionation")
    public static void onlyTheCopperBoilersWearFromAnUnratedFluid(GameTestHelper h) {
        var state = CETransport.STAINLESS_BOILER.get().defaultBlockState().setValue(HorizontalFluidTankBlock.AXIS, Direction.Axis.X);
        h.setBlock(new BlockPos(2, 2, 2), state);
        h.setBlock(new BlockPos(2, 2, 6), CETransport.HORIZONTAL_FLUID_TANK.get().defaultBlockState().setValue(HorizontalFluidTankBlock.AXIS, Direction.Axis.X));
        h.runAfterDelay(2, () -> {
            var stainless = (HorizontalTankBlockEntity) h.getBlockEntity(new BlockPos(2, 2, 2));
            var copper = (HorizontalTankBlockEntity) h.getBlockEntity(new BlockPos(2, 2, 6));
            h.assertTrue(stainless.getBehaviour(net.createplant.transport.ContainmentWear.TYPE) == null
                && copper.getBehaviour(net.createplant.transport.ContainmentWear.TYPE) != null,
                "The stainless boiler never wears, even holding its own high-pressure steam; the copper horizontal tank still does");
            h.succeed();
        });
    }

    private static void horizontalBoiler(GameTestHelper h, Block pipe, boolean stainless) {
        BlockPos base = new BlockPos(6, 3, 8), hot = base.east(3);
        var block = stainless ? CETransport.STAINLESS_BOILER.get() : CETransport.HORIZONTAL_FLUID_TANK.get();
        var steam = stainless ? net.createplant.thermal.FluidForm.HIGH_PRESSURE_STEAM : net.createplant.thermal.FluidForm.STEAM;
        var boilerState = block.defaultBlockState().setValue(HorizontalFluidTankBlock.AXIS, Direction.Axis.X);
        for (int section = 0; section < 4; section++) { h.setBlock(base.east(section), boilerState);firedBurner(h, base.east(section).below()); }
        var root = (HorizontalTankBlockEntity) ((HorizontalTankBlockEntity) h.getBlockEntity(base)).getControllerBE();
        root.getTankInventory().fill(new FluidStack(Fluids.WATER, 8000), EXECUTE);
        var outlet = (HorizontalTankBlockEntity) h.getBlockEntity(hot);
        var stored = roofLine(h, hot, pipe);
        h.runAfterDelay(200, () -> {
            h.assertTrue(steam.matches(stored.getFluid()) && stored.getFluidAmount() > 0,
                "The roof pump sends the boiler's steam along a pipe lying on the boiler: received=" + stored.getFluidAmount() + " boiler=" + outlet.status());
            h.assertTrue(root.boiler.attachedEngines == 0 && !outlet.status().equals("engines"), "A pipe on the boiler is not counted as a steam engine");
            h.assertTrue(stored.getFluidAmount() + outlet.steam().getFluidAmount() + root.getTankInventory().getFluidAmount() == 8000, "The steam keeps the mass of the water it came from");
            h.succeed();
        });
    }

    @GameTest(template = "test_fractionation", timeoutTicks = 240)
    public static void copperPipeOnAHorizontalBoilerCarriesItsSteam(GameTestHelper h) { horizontalBoiler(h, AllBlocks.FLUID_PIPE.get(), true); }

    @GameTest(template = "test_fractionation", timeoutTicks = 240)
    public static void reinforcedPipeOnAHorizontalBoilerCarriesItsSteam(GameTestHelper h) { horizontalBoiler(h, CETransport.STAINLESS_PIPE.get(), false); }

    /** Create's own tank, {@code width} square and one block high, standing on burners and holding water. */
    private static FluidTankBlockEntity createBoiler(GameTestHelper h, BlockPos corner, int width, boolean burners, int water) {
        for (int x = 0; x < width; x++) for (int z = 0; z < width; z++) {
            h.setBlock(corner.offset(x, 0, z), AllBlocks.FLUID_TANK.get());
            if (burners) firedBurner(h, corner.offset(x, -1, z));
        }
        var tank = (FluidTankBlockEntity) h.getBlockEntity(corner);
        com.simibubi.create.api.connectivity.ConnectivityHandler.formMulti(tank);
        var controller = tank.getControllerBE();
        controller.getTankInventory().fill(new FluidStack(Fluids.WATER, water), EXECUTE);
        return controller;
    }

    @GameTest(template = "test_fractionation", timeoutTicks = 240)
    public static void aPumpOnACreateBoilerRoofSendsOrdinarySteamAlongTheRoof(GameTestHelper h) {
        BlockPos corner = new BlockPos(6, 3, 8);
        var controller = createBoiler(h, corner, 2, true, 8000);
        var boiler = TankBoiler.of(controller);
        var stored = roofLine(h, corner.offset(1, 0, 0), CETransport.STAINLESS_PIPE.get());
        h.runAfterDelay(200, () -> {
            h.assertTrue(FluidForm.STEAM.matches(stored.getFluid()) && stored.getFluidAmount() > 0,
                "A pump on the roof of Create's tank boiler moves ordinary steam through a gas pipe lying on the roof: received=" + stored.getFluidAmount());
            h.assertTrue(!controller.boiler.isActive(), "Pipes on the tank are never counted as steam engines");
            h.assertTrue(stored.getFluidAmount() + boiler.steam().getFluidAmount() + controller.getTankInventory().getFluidAmount() == 8000,
                "Feed water equals the steam in the boiler plus the steam received");
            h.succeed();
        });
    }

    @GameTest(template = "test_fractionation", timeoutTicks = 160)
    public static void aCreateBoilerKeepsItsSteamAndYieldsToASteamEngine(GameTestHelper h) {
        BlockPos at = new BlockPos(6, 3, 8);
        var controller = createBoiler(h, at, 1, true, 8000);
        var boiler = TankBoiler.of(controller);
        var roof = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(at), Direction.UP);
        h.runAfterDelay(60, () -> {
            int held = boiler.steam().getFluidAmount();
            h.assertTrue(held > 0 && FluidForm.STEAM.matches(roof.getFluidInTank(0)) && roof.fill(new FluidStack(Fluids.WATER, 100), SIMULATE) == 0,
                "While it boils the roof gives steam and takes no water: held=" + held);
            h.assertTrue(roof.drain(1, SIMULATE).getAmount() == 1 && boiler.steam().getFluidAmount() == held, "Simulated draining changes nothing");
            var saved = controller.saveWithoutMetadata(h.getLevel().registryAccess());
            boiler.steam().drain(Integer.MAX_VALUE, EXECUTE);
            controller.loadWithComponents(saved, h.getLevel().registryAccess());
            h.assertTrue(boiler.steam().getFluidAmount() == held, "Saving and loading keeps the boiler's steam");
            h.setBlock(at.east(), AllBlocks.STEAM_ENGINE.getDefaultState().setValue(com.simibubi.create.content.kinetics.steamEngine.SteamEngineBlock.FACE, AttachFace.WALL)
                .setValue(com.simibubi.create.content.kinetics.steamEngine.SteamEngineBlock.FACING, Direction.EAST));
            controller.updateBoilerState();
        });
        int[] water = {-1};
        h.runAfterDelay(70, () -> water[0] = controller.getTankInventory().getFluidAmount());
        h.runAfterDelay(140, () -> {
            h.assertTrue(controller.boiler.isActive() && controller.getTankInventory().getFluidAmount() == water[0],
                "A steam engine makes it Create's boiler, which boils no more water into pipe steam");
            h.succeed();
        });
    }

    @GameTest(template = "test_fractionation", timeoutTicks = 40)
    public static void anUnheatedCreateTankRoofIsAnOrdinaryTankFace(GameTestHelper h) {
        BlockPos at = new BlockPos(6, 3, 8);
        var controller = createBoiler(h, at, 1, false, 4000);
        var roof = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(at), Direction.UP);
        h.runAfterDelay(30, () -> {
            h.assertTrue(TankBoiler.of(controller).steam().isEmpty() && roof.fill(new FluidStack(Fluids.WATER, 1000), EXECUTE) == 1000
                && controller.getTankInventory().getFluidAmount() == 5000, "Without burners the roof fills the tank like any other face");
            h.succeed();
        });
    }
}
