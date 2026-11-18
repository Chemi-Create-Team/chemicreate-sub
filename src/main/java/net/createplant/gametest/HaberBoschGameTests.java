package net.createplant.gametest;

import com.simibubi.create.api.connectivity.ConnectivityHandler;
import net.createplant.CreatePlant;
import net.createplant.block.entity.AmmoniaSeparatorBlockEntity;
import net.createplant.registry.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.*;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class HaberBoschGameTests {
    public static AmmoniaSeparatorBlockEntity separator(ServerLevel level,BlockPos at){level.setBlock(at,CETransport.AMMONIA_SEPARATOR.get().defaultBlockState(),3);level.setBlock(at.above(),CETransport.AMMONIA_SEPARATOR.get().defaultBlockState(),3);var be=(AmmoniaSeparatorBlockEntity)level.getBlockEntity(at);ConnectivityHandler.formMulti(be);return (AmmoniaSeparatorBlockEntity)be.getControllerBE();}
    private static IFluidHandler port(ServerLevel level,BlockPos at,Direction side){var port=level.getCapability(Capabilities.FluidHandler.BLOCK,at,side);if(port==null)throw new AssertionError("Missing separator port at "+at+" "+side);return port;}
    @GameTest(template="test_empty")
    public static void theCatalystPileCanBeMadeInAHeatedMixer(GameTestHelper h){h.assertTrue(h.getLevel().getRecipeManager().byKey(CreatePlant.location("mixing/triiron_tetroxide_pile")).isPresent(),"The existing heated mixer produces the iron catalyst");h.succeed();}
    @GameTest(template="test_fractionation")
    public static void coldMixtureProducesFixedLiquidAmmoniaAndRecycleWithoutASimulationMutation(GameTestHelper h){
        var at=h.absolutePos(new BlockPos(4,2,4));var be=separator(h.getLevel(),at);var inlet=port(h.getLevel(),at.above(),Direction.NORTH);var before=be.saveWithoutMetadata(h.getLevel().registryAccess());var feed=new FluidStack(CEProcessFluids.COLD_SYNTHESIS_MIXTURE.get(),9000);
        h.assertTrue(inlet.fill(feed,SIMULATE)==9000 && before.equals(be.saveWithoutMetadata(h.getLevel().registryAccess())),"A separator simulation has no pressure or inventory side effects");
        h.assertTrue(inlet.fill(new FluidStack(CEProcessFluids.HOT_SYNTHESIS_MIXTURE.get(),9000),EXECUTE)==0,"Hot gas must pass a cooler first");
        inlet.fill(feed,EXECUTE);for(int i=0;i<12;i++)be.process(.25);
        var liquid=port(h.getLevel(),at,Direction.DOWN).drain(100000,EXECUTE);var recycle=port(h.getLevel(),at.above(),Direction.UP).drain(100000,EXECUTE);
        h.assertTrue(liquid.is(CEProcessFluids.LIQUID_AMMONIA.get()) && liquid.getAmount()==1 && recycle.is(CEProcessFluids.COMPRESSED_SYNTHESIS_FEED.get()) && recycle.getAmount()==8000 && be.feed().isEmpty(),"9000 mB cold mixture produces 1 mB liquid ammonia and 8000 mB reusable pressurised feed");h.succeed();
    }
    @GameTest(template="test_fractionation")
    public static void savedPartialSeparationKeepsItsFeedAndCannotDuplicateProducts(GameTestHelper h){
        var at=h.absolutePos(new BlockPos(4,2,4));var be=separator(h.getLevel(),at);port(h.getLevel(),at.above(),Direction.NORTH).fill(new FluidStack(CEProcessFluids.COLD_SYNTHESIS_MIXTURE.get(),9000),EXECUTE);for(int i=0;i<6;i++)be.process(.25);
        var saved=be.saveWithoutMetadata(h.getLevel().registryAccess());be.loadWithComponents(saved,h.getLevel().registryAccess());for(int i=0;i<6;i++)be.process(.25);
        h.assertTrue(be.feed().isEmpty() && be.liquid().getAmount()==1 && be.recycle().getAmount()==8000,"Partial batch survives reload once");for(int i=0;i<20;i++)be.process(.25);h.assertTrue(be.liquid().getAmount()==1 && be.recycle().getAmount()==8000,"An empty inlet cannot create products");h.succeed();
    }
    @GameTest(template="test_fractionation")
    public static void resizingAndReloadRetireOldPortsWithoutMovingOrCreatingStock(GameTestHelper h){
        var at=h.absolutePos(new BlockPos(4,2,4));var be=separator(h.getLevel(),at);var oldIn=port(h.getLevel(),at.above(),Direction.NORTH);var oldOut=port(h.getLevel(),at.above(),Direction.UP);
        oldIn.fill(new FluidStack(CEProcessFluids.COLD_SYNTHESIS_MIXTURE.get(),9000),EXECUTE);
        h.getLevel().setBlock(at.above(2),CETransport.AMMONIA_SEPARATOR.get().defaultBlockState(),3);ConnectivityHandler.formMulti(be);
        h.assertTrue(be.isController() && be.getHeight()==3 && be.feed().getAmount()==9000,"Growing upward keeps the original stock owner");
        h.assertTrue(oldIn.fill(new FluidStack(CEProcessFluids.COLD_SYNTHESIS_MIXTURE.get(),9000),EXECUTE)==0 && oldOut.drain(1,EXECUTE).isEmpty(),"Geometry changes retire held capability objects");
        var newIn=port(h.getLevel(),at.above(2),Direction.NORTH);var saved=be.saveWithoutMetadata(h.getLevel().registryAccess());be.loadWithComponents(saved,h.getLevel().registryAccess());
        h.assertTrue(newIn.fill(new FluidStack(CEProcessFluids.COLD_SYNTHESIS_MIXTURE.get(),9000),SIMULATE)==0,"Reload retires the previous lease");
        for(int i=0;i<12;i++)be.process(.25);
        h.assertTrue(be.feed().isEmpty() && be.liquid().getAmount()==1 && be.recycle().getAmount()==8000,"The retained parcel is converted once");h.succeed();
    }
}
