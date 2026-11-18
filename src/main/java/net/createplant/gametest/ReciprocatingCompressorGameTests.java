package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import net.createplant.CreatePlant;
import net.createplant.block.ReciprocatingCompressorBlock;
import net.createplant.block.ReciprocatingShaftBlock;
import net.createplant.block.entity.ReciprocatingCompressorBlockEntity;
import net.createplant.block.entity.ReciprocatingCylinderBlockEntity;
import net.createplant.block.entity.ReciprocatingShaftBlockEntity;
import net.createplant.registry.CEProcessFluids;
import net.createplant.registry.CEReciprocating;
import net.createplant.thermal.FluidThermal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class ReciprocatingCompressorGameTests {
    private static final BlockPos BASE = new BlockPos(4, 2, 4);

    private static FluidStack gas(int amount) { return FluidThermal.at(new FluidStack(CEProcessFluids.SYNTHESIS_GAS.get(), amount), 300); }

    /** A cylinder with an engine on top facing north, so the crank shaft runs along X three blocks above the cylinder. */
    private static ReciprocatingCompressorBlockEntity build(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos cylinder = h.absolutePos(BASE);
        level.setBlock(cylinder, CEReciprocating.CYLINDER.get().defaultBlockState(), 3);
        var state = CEReciprocating.COMPRESSOR.get().defaultBlockState().setValue(ReciprocatingCompressorBlock.FACE, AttachFace.FLOOR).setValue(ReciprocatingCompressorBlock.FACING, Direction.NORTH);
        level.setBlock(cylinder.above(), state, 3);
        level.setBlock(cylinder.above(3), AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.X), 3);
        var engine = (ReciprocatingCompressorBlockEntity) level.getBlockEntity(cylinder.above());
        engine.attachShaft();
        return engine;
    }

    /** A creative motor beside the shaft, so the kinetic network is real and the shaft keeps its speed. */
    private static void drive(GameTestHelper h, ReciprocatingCompressorBlockEntity engine) {
        BlockPos motor = engine.shaftPos().west();
        h.getLevel().setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(com.simibubi.create.content.kinetics.base.DirectionalKineticBlock.FACING, Direction.EAST), 3);
        ((com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity) h.getLevel().getBlockEntity(motor)).generatedSpeed.setValue(128);
    }

    @GameTest(template = "test_fractionation")
    public static void engineStandsOnlyOnACylinderAndTakesOverTheShaft(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos floating = h.absolutePos(new BlockPos(9, 2, 9));
        var state = CEReciprocating.COMPRESSOR.get().defaultBlockState();
        h.assertTrue(!state.canSurvive(level, floating), "An engine cannot stand without a cylinder");
        var engine = build(h);
        h.assertTrue(level.getBlockState(engine.shaftPos()).getBlock() instanceof ReciprocatingShaftBlock, "The shaft at the end of the crank is taken over");
        h.assertTrue(level.getBlockEntity(engine.shaftPos()) instanceof ReciprocatingShaftBlockEntity shaft && engine.getBlockPos().equals(shaft.engine()), "The shaft knows its engine");
        level.destroyBlock(engine.getBlockPos(), false);
        h.assertTrue(AllBlocks.SHAFT.has(level.getBlockState(h.absolutePos(BASE).above(3))), "The shaft is a Create shaft again once the engine is gone");
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void wrongShaftAxisIsNotTakenOver(GameTestHelper h) {
        var level = h.getLevel();
        var engine = build(h);
        level.setBlock(engine.shaftPos(), AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.Z), 3);
        engine.attachShaft();
        h.assertTrue(AllBlocks.SHAFT.has(level.getBlockState(engine.shaftPos())), "A shaft across the crank stays a plain shaft");
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void cylinderPortsAreSuctionOnTopAndDischargeBelow(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos pos = h.absolutePos(BASE);
        level.setBlock(pos, CEReciprocating.CYLINDER.get().defaultBlockState(), 3);
        var cylinder = (ReciprocatingCylinderBlockEntity) level.getBlockEntity(pos);
        var up = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, Direction.UP);
        var down = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, Direction.DOWN);
        h.assertTrue(level.getCapability(Capabilities.FluidHandler.BLOCK, pos, Direction.EAST) == null, "The sides have no port");
        h.assertTrue(up.fill(gas(1000), FluidAction.SIMULATE) == 1000 && cylinder.inlet().isEmpty(), "Suction takes gas and a simulation changes nothing");
        h.assertTrue(up.fill(gas(1000), FluidAction.EXECUTE) == 1000 && cylinder.inlet().getFluidAmount() == 1000, "The top feeds the suction chamber");
        h.assertTrue(down.fill(gas(1000), FluidAction.EXECUTE) == 0 && up.drain(1000, FluidAction.EXECUTE).isEmpty(), "The bottom takes no gas and the top gives none");
        cylinder.outlet().fill(gas(500), FluidAction.EXECUTE);
        h.assertTrue(down.drain(500, FluidAction.EXECUTE).getAmount() == 500, "The bottom gives the compressed gas");
        h.succeed();
    }


    @GameTest(template = "test_fractionation", timeoutTicks = 100)
    public static void stoppedShaftDoesNotCompressOrLoad(GameTestHelper h) {
        var engine = build(h);
        var shaft = (ReciprocatingShaftBlockEntity) h.getLevel().getBlockEntity(engine.shaftPos());
        var cylinder = (ReciprocatingCylinderBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(BASE));
        cylinder.inlet().fill(gas(1000), FluidAction.EXECUTE);
        h.runAfterDelay(30, () -> {
            h.assertTrue(cylinder.outlet().isEmpty() && cylinder.inlet().getFluidAmount() == 1000, "No rotation, no compression");
            h.assertTrue(shaft.calculateStressApplied() == 0 && engine.status().equals("speed"), "A stopped engine adds no load");
            h.succeed();
        });
    }




    @GameTest(template="test_fractionation",timeoutTicks=120)
    public static void realShaftMovesExactlyOneCompressionGradeAndRestartsAfterAFullOutlet(GameTestHelper h) {
        var engine=build(h);var cylinder=(ReciprocatingCylinderBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(BASE));
        cylinder.inlet().fill(gas(1000),FluidAction.EXECUTE);drive(h,engine);
        h.runAfterDelay(60,()-> {
            h.assertTrue(cylinder.outlet().getFluid().is(CEProcessFluids.COMPRESSED_SYNTHESIS_FEED.get()),"Native shaft produces the mapped gas type");
            h.assertTrue(cylinder.inlet().getFluidAmount()+cylinder.outlet().getFluidAmount()==1000,"No gas is lost to compression");
            cylinder.inlet().fill(gas(1000),FluidAction.EXECUTE);
            cylinder.outlet().setFluid(new FluidStack(CEProcessFluids.COMPRESSED_SYNTHESIS_FEED.get(),cylinder.outlet().getCapacity()));engine.tick();
            h.assertTrue(engine.flow()==0 && engine.status().equals("output"),"A full outlet pauses without locking the engine");
            cylinder.outlet().drain(cylinder.outlet().getFluidAmount(),FluidAction.EXECUTE);
        });
        h.runAfterDelay(85,()-> {h.assertTrue(engine.flow()>0 && engine.work()>0 && !cylinder.outlet().isEmpty(),"Clearing outlet restores flow and shaft load automatically");h.succeed();});
    }
    @GameTest(template = "test_fractionation", timeoutTicks = 110)
    public static void realCompressorReducesAirVolumeWithoutLosingAir(GameTestHelper h) {
        var engine = build(h);
        var cylinder = (ReciprocatingCylinderBlockEntity) h.getBlockEntity(BASE);
        var raw = new FluidStack(net.createplant.registry.CEFluids.AIR_GAS.get(), 1600);
        h.assertTrue(cylinder.inlet().fill(raw, FluidAction.EXECUTE) == 1000, "Atmospheric air fills the suction chamber at its physical capacity");
        drive(h, engine);
        h.runAfterDelay(90, () -> {
            var output = cylinder.outlet().getFluid();
            h.assertTrue(cylinder.inlet().isEmpty() && output.is(CEProcessFluids.COMPRESSED_AIR.get())
                && output.getAmount() == 1000 && net.createplant.thermal.Compression.airVolume(output) == 62.5,
                "The real shaft converts all 1000 mB of raw air into 62.5 mB of physical compressed volume");
            h.succeed();
        });
    }
}
