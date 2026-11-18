package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import net.createplant.CreatePlant;
import net.createplant.registry.CEProcessFluids;
import net.createplant.thermal.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public final class FluidGradeGameTests {
    @GameTest(template="test_petrochemical")
    public static void fluidTemperatureIsNominalAndAtNeverWritesAComponent(GameTestHelper h){
        var water=new FluidStack(Fluids.WATER,1000);var before=water.copy();
        h.assertTrue(FluidThermal.at(water,900)==water && FluidStack.matches(before,water),"at is a no-op on the original stack");
        h.assertTrue(FluidThermal.kelvin(water)==293 && !FluidThermal.tracked(water),"Ordinary water has a type temperature, not a stored measurement");
        h.assertTrue(BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(CreatePlant.location("fluid_temperature")).isEmpty() && BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(CreatePlant.location("specific_thermal_energy")).isEmpty(),"Neither retired thermal component is registered");h.succeed();
    }
    @GameTest(template="test_petrochemical")
    public static void mixingOnlyAddsCompatibleQuantitiesAndLeavesBothInputsUnchanged(GameTestHelper h){
        var a=FluidThermal.at(new FluidStack(Fluids.WATER,300),1000);var b=FluidThermal.at(new FluidStack(Fluids.WATER,1000),50);
        var result=FluidThermal.mix(a,b,500);
        h.assertTrue(result.getAmount()==800 && a.getAmount()==300 && b.getAmount()==1000 && FluidThermal.kelvin(result)==293,"Mixing quantities neither averages nor creates thermal state");
        h.assertTrue(!FluidThermal.compatible(result,new FluidStack(CEProcessFluids.WARM_WATER.get(),800)),"A separate warm-water type never merges implicitly");h.succeed();
    }
    @GameTest(template="test_petrochemical",timeoutTicks=100)
    public static void tankContentsKeepTheirGradeAcrossWeatherDaytimeAndSaveReload(GameTestHelper h){
        var at=new BlockPos(2,2,2);h.setBlock(at,AllBlocks.FLUID_TANK.get());var tank=(FluidTankBlockEntity)h.getBlockEntity(at);tank.getTankInventory().fill(new FluidStack(CEProcessFluids.WARM_WATER.get(),1700),EXECUTE);
        var saved=tank.saveWithoutMetadata(h.getLevel().registryAccess());long oldTime=h.getLevel().getDayTime();h.getLevel().setDayTime(18000);
        h.runAfterDelay(45,()-> {h.getLevel().setDayTime(oldTime);tank.loadWithComponents(saved,h.getLevel().registryAccess());var fluid=tank.getTankInventory().getFluid();h.assertTrue(fluid.is(CEProcessFluids.WARM_WATER.get()) && fluid.getAmount()==1700 && FluidThermal.kelvin(fluid)==330 && !FluidThermal.tracked(fluid),"No ambient cooling or regenerated energy occurs in storage");h.succeed();});
    }
}
