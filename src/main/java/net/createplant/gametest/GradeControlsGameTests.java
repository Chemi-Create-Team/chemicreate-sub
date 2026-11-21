package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import net.createplant.CreatePlant;
import net.createplant.block.PressureValveBlock;
import net.createplant.block.entity.*;
import net.createplant.registry.*;
import net.createplant.thermal.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public final class GradeControlsGameTests {
    @GameTest(template = "test_petrochemical")
    public static void expandedAirFillsItsAvailableVolumeAndRetainsTheRest(GameTestHelper h) {
        var at = new BlockPos(2, 2, 2);
        h.setBlock(at, CEPressure.REDUCER.get().defaultBlockState().setValue(PressureValveBlock.FACING, Direction.EAST)
            .setValue(PressureValveBlock.AXIS, Direction.Axis.X));
        var valve = (PressureValveBlockEntity) h.getBlockEntity(at);
        var in = valve.port(Direction.WEST);
        var out = valve.port(Direction.EAST);
        var compressed = new FluidStack(CEProcessFluids.COMPRESSED_AIR.get(), 3200);
        FluidState.setTemperature(compressed, 12);
        h.assertTrue(in.fill(compressed, EXECUTE) == 3200 && Compression.airVolume(compressed) == 200, "Compressed stock occupies 200 mB");
        valve.tick();
        h.assertTrue(in.fill(compressed, EXECUTE) == 3200, "Replace the first input batch");
        valve.tick();
        h.assertTrue(out.getFluidInTank(0).getAmount() == 4000 && in.getFluidInTank(0).getAmount() == 2400,
            "Only the amount that fits expanded moves");
        var expanded = out.drain(4000, EXECUTE);
        h.assertTrue(expanded.is(CEFluids.AIR_GAS.get()) && Compression.airVolume(expanded) == 4000
            && FluidState.temperature(expanded) == 12, "Expansion retains the air and its existing temperature");
        valve.tick();
        h.assertTrue(in.getFluidInTank(0).isEmpty() && out.drain(4000, EXECUTE).getAmount() == 2400, "Draining the outlet recovers every remaining unit");
        h.succeed();
    }

    @GameTest(template="test_petrochemical")
    public static void compressionAndExpansionChangeExactlyOneGradeAndKeepQuantity(GameTestHelper h) {
        var stack=new FluidStack(CEFluids.HYDROGEN_GAS.get(),173);
        for(int i=0;i<2;i++) {stack=Compression.convert(stack,true);h.assertTrue(FluidState.pressure(stack)==FluidState.NORMAL_PRESSURE+4*(i+1) && stack.getAmount()==173,"One compression stage raises one grade; no mass creation");}
        for(int i=1;i>=0;i--) {stack=Compression.convert(stack,false);h.assertTrue(FluidState.pressure(stack)==FluidState.NORMAL_PRESSURE+4*i && stack.getAmount()==173,"One reducing stage");}
        var unknown=new FluidStack(Fluids.WATER,173);h.assertTrue(FluidStack.matches(unknown,Compression.convert(unknown,false)),"Unlisted fluid passes unchanged");h.succeed();
    }
    @GameTest(template="test_petrochemical")
    public static void reducingValveHasSeparateDirectionalPortsAndRetainsSavedContents(GameTestHelper h) {
        var at=new BlockPos(2,2,2);h.setBlock(at,CEPressure.REDUCER.get().defaultBlockState().setValue(PressureValveBlock.FACING,Direction.EAST).setValue(PressureValveBlock.AXIS,Direction.Axis.X));
        var valve=(PressureValveBlockEntity)h.getBlockEntity(at);var feed=new FluidStack(CEProcessFluids.COMPRESSED_HYDROGEN.get(),1300);
        var before=valve.saveWithoutMetadata(h.getLevel().registryAccess());
        h.assertTrue(valve.port(Direction.WEST).fill(feed,SIMULATE)==1300 && before.equals(valve.saveWithoutMetadata(h.getLevel().registryAccess())),"Simulation never changes stock");
        h.assertTrue(valve.port(Direction.EAST).fill(feed,EXECUTE)==0 && valve.port(Direction.UP)==null,"No reverse filling or side port");
        h.assertTrue(valve.port(Direction.WEST).fill(feed,EXECUTE)==1300,"Receive high pressure gas");valve.tick();
        var tag=valve.saveWithoutMetadata(h.getLevel().registryAccess());valve.loadWithComponents(tag,h.getLevel().registryAccess());
        var out=valve.port(Direction.EAST).drain(4000,EXECUTE);
        h.assertTrue(out.is(CEProcessFluids.PRESSURISED_HYDROGEN.get()) && out.getAmount()==1300 && valve.port(Direction.WEST).drain(1,EXECUTE).isEmpty(),"Reload and conversion preserve amount and direction");h.succeed();
    }
    @GameTest(template="test_petrochemical")
    public static void reducingValveWaitsWhileItsOutletIsFullAndResumesWhenDrained(GameTestHelper h) {
        var at=new BlockPos(2,2,2);h.setBlock(at,CEPressure.REDUCER.get().defaultBlockState().setValue(PressureValveBlock.FACING,Direction.EAST).setValue(PressureValveBlock.AXIS,Direction.Axis.X));
        var valve=(PressureValveBlockEntity)h.getBlockEntity(at);var inlet=valve.port(Direction.WEST);var outlet=valve.port(Direction.EAST);
        inlet.fill(new FluidStack(CEProcessFluids.COMPRESSED_HYDROGEN.get(),4000),EXECUTE);valve.tick();
        inlet.fill(new FluidStack(CEProcessFluids.COMPRESSED_HYDROGEN.get(),1000),EXECUTE);valve.tick();valve.tick();
        h.assertTrue(valve.waiting() && outlet.getFluidInTank(0).getAmount()==4000 && inlet.getFluidInTank(0).getAmount()==1000,"A full outlet parks the valve");
        h.assertTrue(outlet.drain(600,EXECUTE).getAmount()==600 && !valve.waiting(),"Draining the outlet wakes it");valve.tick();
        h.assertTrue(outlet.getFluidInTank(0).getAmount()==4000 && inlet.getFluidInTank(0).getAmount()==400,"It converts again without losing gas");h.succeed();
    }
    @GameTest(template="test_petrochemical")
    public static void gaugeThresholdAndComparatorUseFluidGradeInsteadOfStoredAmount(GameTestHelper h) {
        var at=new BlockPos(2,2,2);h.setBlock(at,CETransport.PIPE_PRESSURE_GAUGE.get().defaultBlockState().setValue(BlockStateProperties.AXIS,Direction.Axis.X));
        var gauge=(PipeGaugeBlockEntity)h.getBlockEntity(at);gauge.threshold.setValue(1);
        var fluids=java.util.List.of(CEFluids.HYDROGEN_GAS,CEProcessFluids.PRESSURISED_HYDROGEN,CEProcessFluids.COMPRESSED_HYDROGEN);
        int[] signals={4,8,11};
        for(int i=0;i<3;i++){gauge.observe(new FluidStack(fluids.get(i).get(),1));gauge.tick();h.assertTrue(gauge.reading()==i+1 && gauge.analogSignal()==signals[i] && gauge.alarm()==(i>0),"Grade controls needle, comparator and alarm across the five pressure grades");}
        h.succeed();
    }
    @GameTest(template="test_petrochemical")
    public static void scrubberDirectlyAcceptsToxicGasAndOnlyConsumesMatchingCaustic(GameTestHelper h) {
        var at=new BlockPos(2,2,2);h.setBlock(at,CECompressor.RELIEF_SCRUBBER.get());var scrubber=(ReliefScrubberBlockEntity)h.getBlockEntity(at);
        scrubber.port(Direction.NORTH).fill(new FluidStack(CEChlorAlkali.CAUSTIC_SODA.get(),700),EXECUTE);
        var gas=new FluidStack(CEChlorAlkali.CHLORINE.get(),1000);var port=scrubber.port(Direction.DOWN);
        h.assertTrue(port.fill(gas,SIMULATE)==700 && scrubber.amount()==700,"Simulation observes available caustic only");
        h.assertTrue(port.fill(gas,EXECUTE)==700 && scrubber.amount()==0 && port.fill(gas,EXECUTE)==0,"Direct piped disposal is bounded by caustic");h.succeed();
    }
}
