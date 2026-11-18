package net.createplant.gametest;

import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;
import net.createplant.CreatePlant;
import net.createplant.block.entity.*;
import net.createplant.registry.*;
import net.createplant.thermal.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public class RadiatorTemperatureGameTests {
    public static RadiatorBlockEntity cell(GameTestHelper h,BlockPos fan){
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=0;y<4;y++)h.setBlock(fan.offset(x,y-3,z),y<3 ? CEProcessBlocks.COOLER_RADIATOR.get().defaultBlockState() : x==0 && z==0 ? CEProcessBlocks.COOLING_FAN.get().defaultBlockState() : CEProcessBlocks.COOLING_FAN_SHROUD.get().defaultBlockState());
        var be=(RadiatorBlockEntity)h.getBlockEntity(fan.below(3));be.lazyTick();return be.owner();
    }
    private static void run(RadiatorBlockEntity be){for(int i=0;i<5;i++)be.tick();}
    @GameTest(template="test_fractionation")
    public static void allHotStreamsUseTheSameFixedCoolingTableAndStoppedFansKeepTheirFeed(GameTestHelper h){
        var fan=new BlockPos(5,5,5);var be=cell(h,fan);var in=((RadiatorBlockEntity)h.getBlockEntity(fan.below(2).north())).port(Direction.NORTH);var out=((RadiatorBlockEntity)h.getBlockEntity(fan.below(2).south())).port(Direction.SOUTH);
        h.assertTrue(in.fill(new FluidStack(CEFluids.HYDROGEN_GAS.get(),100),EXECUTE)==0,"Unmapped gases cannot block the radiator");
        for(var pair:ThermalStreams.cooledStreams().entrySet()){
            ((CoolingFanBlockEntity)h.getBlockEntity(fan)).setSpeed(0);var feed=new FluidStack(pair.getKey(),100);var saved=be.saveWithoutMetadata(h.getLevel().registryAccess());
            h.assertTrue(in.fill(feed,SIMULATE)==100 && saved.equals(be.saveWithoutMetadata(h.getLevel().registryAccess())),"Simulating the inlet does not claim a direction or create state");in.fill(feed,EXECUTE);run(be);
            h.assertTrue(in.getFluidInTank(0).getAmount()==100 && out.drain(100,SIMULATE).isEmpty(),"Stopped fans retain the full hot batch");
            ((CoolingFanBlockEntity)h.getBlockEntity(fan)).setSpeed(64);run(be);var result=out.drain(100,EXECUTE);
            h.assertTrue(result.is(pair.getValue()) && result.getAmount()==100 && in.getFluidInTank(0).isEmpty(),"A powered fan converts the exact fixed amount");
        }h.succeed();
    }
    @GameTest(template="test_fractionation")
    public static void fanSpeedAndAreaScaleThroughputWhileFullOutputsKeepAllInventory(GameTestHelper h){
        var fan=new BlockPos(5,5,5);var be=cell(h,fan);var inletPart=(RadiatorBlockEntity)h.getBlockEntity(fan.below(2).north());var in=inletPart.port(Direction.NORTH);var out=((RadiatorBlockEntity)h.getBlockEntity(fan.below(2).south())).port(Direction.SOUTH);
        ((CoolingFanBlockEntity)h.getBlockEntity(fan)).setSpeed(64);in.fill(new FluidStack(CEProcessFluids.WARM_WATER.get(),1000),EXECUTE);run(be);h.assertTrue(out.drain(1000,EXECUTE).getAmount()==100,"One fan at 64 RPM converts 100 per step");
        ((CoolingFanBlockEntity)h.getBlockEntity(fan)).setSpeed(128);run(be);h.assertTrue(out.drain(1000,EXECUTE).getAmount()==200,"Doubling fan speed doubles throughput");
        var second=fan.east(3);cell(h,second);be.lazyTick();be=be.owner();((CoolingFanBlockEntity)h.getBlockEntity(fan)).setSpeed(64);((CoolingFanBlockEntity)h.getBlockEntity(second)).setSpeed(64);
        h.assertTrue(be.fanCount()==2 && in.fill(new FluidStack(CEProcessFluids.WARM_WATER.get(),1),EXECUTE)==0,"Expanding the bank retires the old port geometry");
        var left=(RadiatorBlockEntity)h.getBlockEntity(fan.below(2).west());var right=(RadiatorBlockEntity)h.getBlockEntity(second.below(2).east());in=(left.isInlet() ? left.port(Direction.WEST) : right.port(Direction.EAST));out=(left.isInlet() ? right.port(Direction.EAST) : left.port(Direction.WEST));run(be);h.assertTrue(out.drain(1000,EXECUTE).getAmount()==200,"Two fans give two conversion units");
        for(int i=0;i<1000 && !in.getFluidInTank(0).isEmpty();i++){run(be);out.drain(1000,EXECUTE);}
        h.assertTrue(in.getFluidInTank(0).isEmpty(),"The first feed drains before the capacity check");
        in.fill(new FluidStack(CEProcessFluids.WARM_WATER.get(),32000),EXECUTE);for(int i=0;i<160;i++)run(be);
        in.fill(new FluidStack(CEProcessFluids.WARM_WATER.get(),100),EXECUTE);run(be);
        h.assertTrue(in.getFluidInTank(0).getAmount()==100 && out.getFluidInTank(1).getAmount()==32000,"Full output retains every unprocessed input mB");
        var old=out;be.loadWithComponents(be.saveWithoutMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());be.lazyTick();
        h.assertTrue(old.drain(1,EXECUTE).isEmpty(),"Reload invalidates a held output capability");h.succeed();
    }
    @GameTest(template="test_fractionation")
    public static void aFanCondensesOrdinarySteamIntoOrdinaryWater(GameTestHelper h){
        var fan=new BlockPos(5,5,5);var be=cell(h,fan);((CoolingFanBlockEntity)h.getBlockEntity(fan)).setSpeed(128);
        var in=((RadiatorBlockEntity)h.getBlockEntity(fan.below(2).north())).port(Direction.NORTH);var out=((RadiatorBlockEntity)h.getBlockEntity(fan.below(2).south())).port(Direction.SOUTH);
        in.fill(new FluidStack(CEFluids.STEAM.get(),1000),EXECUTE);run(be);var water=out.drain(1000,EXECUTE);
        h.assertTrue(water.is(Fluids.WATER) && water.getAmount()==200 && in.getFluidInTank(0).getAmount()==800,"A fixed conversion preserves the remaining steam and completed condensate");h.succeed();
    }
}
