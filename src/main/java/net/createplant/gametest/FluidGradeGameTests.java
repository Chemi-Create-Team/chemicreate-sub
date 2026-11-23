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
    public static void allLevelsSurviveStorageAndOneLevelHeating(GameTestHelper h) {
        var heater=new TankHeating();
        var lookup=h.getLevel().registryAccess();
        var fluid=new FluidStack(net.createplant.registry.CEPetroFluids.HEAVY_OIL.get(),300);
        for(int level=0;level<=16;level++) {
            var sample=FluidState.with(fluid,level,16-level);
            var restored=FluidStack.parseOptional(lookup,(net.minecraft.nbt.CompoundTag)sample.save(lookup));
            h.assertTrue(FluidStack.matches(sample,restored) && FluidState.temperature(restored)==level
                && FluidState.pressure(restored)==16-level,"All state levels survive save/reload");
            if(level>0)h.assertTrue(FluidState.kelvin(level)>FluidState.kelvin(level-1) && FluidState.bar(level)>FluidState.bar(level-1),"Both scales increase");
            h.assertTrue(heater.tick(java.util.List.of(sample),0)==TankHeating.NOTHING && FluidState.temperature(sample)==level,"Without a burner nothing warms");
            int before=FluidState.temperature(sample);
            for(int pass=0;pass<4;pass++)heater.tick(java.util.List.of(sample),TankHeating.LIT_WATTS);
            h.assertTrue(FluidState.temperature(sample)>=before && FluidState.temperature(sample)<=16,"Heating only raises the level and stops at sixteen");
        }
        h.assertTrue(Grade.Heat.of(FluidState.kelvin(13))==Grade.Heat.HOT && Grade.Heat.of(FluidState.kelvin(14))==Grade.Heat.VERY_HOT
            && Grade.Pressure.of(FluidState.bar(0))==Grade.Pressure.VACUUM && Grade.Pressure.of(FluidState.bar(16))==Grade.Pressure.ULTRA_HIGH,"Pipe ratings use representative values");
        h.succeed();
    }

    @GameTest(template="test_petrochemical")
    public static void mixingAveragesLevelsAndPreservesPhaseQuantities(GameTestHelper h){
        var a=FluidState.with(new FluidStack(Fluids.WATER,300),8,4);
        var b=FluidState.with(new FluidStack(Fluids.WATER,1000),10,8);
        var result=FluidThermal.mix(a,b,500);
        h.assertTrue(result.getAmount()==800 && a.getAmount()==300 && b.getAmount()==1000 && FluidState.temperature(result)==9
            && FluidState.pressure(result)==7,"Mixing weights both levels by the accepted quantities");
        var liquid=new FluidStack(net.createplant.registry.CEProcessFluids.LIQUID_NITROGEN.get(),1000);
        var gas=new FluidStack(net.createplant.registry.CEFluids.NITROGEN_GAS.get(),PhaseChange.ratio(liquid)+1);
        var tank=new ThermalFluidTank(2000,ignored->{});
        tank.fill(liquid,EXECUTE);
        var veryColdGas=FluidState.with(gas,2,4);
        int amount=tank.fill(veryColdGas,EXECUTE);
        h.assertTrue(amount==PhaseChange.ratio(liquid) && tank.getFluidAmount()==1001 && !FluidState.gas(tank.getFluid()),
            "When a cold liquid absorbs gas, indivisible gas units stay in the input");
        h.succeed();
    }

    @GameTest(template="test_petrochemical")
    public static void legacyStacksBucketsAndMixturesMigrateTheirData(GameTestHelper h) {
        var lookup=h.getLevel().registryAccess();
        var encoded=new net.minecraft.nbt.CompoundTag();
        encoded.putString("id",CreatePlant.location("steam").toString());
        encoded.putInt("amount",1001);
        var steam=FluidStack.parseOptional(lookup,encoded);
        h.assertTrue(steam.is(Fluids.WATER) && FluidState.gas(steam) && FluidState.temperature(steam)==12,"Old steam loads as gaseous water");
        var oldGrade=new net.minecraft.nbt.CompoundTag();
        oldGrade.putInt(CreatePlant.location("heat_grade").toString(),3);
        encoded.put("components",oldGrade);
        var graded=FluidStack.parseOptional(lookup,encoded);
        h.assertTrue(FluidState.temperature(graded)==12 && !graded.has(net.createplant.registry.CEThermal.HEAT_GRADE.get()),
            "The old heat-grade component is replaced by four temperature levels per grade");
        var oldBucket=new net.minecraft.nbt.CompoundTag();
        oldBucket.putString("id",CreatePlant.location("liquid_nitrogen_bucket").toString());
        oldBucket.putInt("count",1);
        var bucket=net.minecraft.world.item.ItemStack.parseOptional(lookup,oldBucket);
        var contents=net.neoforged.neoforge.fluids.FluidUtil.getFluidContained(bucket).orElseThrow();
        h.assertTrue(contents.is(net.createplant.registry.CEFluids.NITROGEN_GAS.get()) && !FluidState.gas(contents) && FluidState.temperature(contents)==1
            && bucket.is(net.createplant.registry.CEItems.NITROGEN_GAS_BUCKET.get()),"Old liquid bucket becomes the canonical nitrogen bucket");
        var carrier=new FluidStack(net.createplant.registry.CEMixtures.GAS_MIXTURE.get(),1200);
        carrier.set(net.createplant.registry.CEMixtures.MIXTURE_CONTENTS.get(),new net.createplant.mixture.MixtureContents(java.util.Map.of(
            CreatePlant.location("steam"),1000,CreatePlant.location("compressed_hydrogen"),200)));
        var mixture=FluidStack.parseOptional(lookup,(net.minecraft.nbt.CompoundTag)carrier.save(lookup));
        var parts=net.createplant.mixture.MixtureHelper.extractContents(mixture);
        h.assertTrue(parts.amounts().getOrDefault(net.minecraft.resources.ResourceLocation.withDefaultNamespace("water"),0)==1000
            && parts.amounts().getOrDefault(CreatePlant.location("hydrogen_gas"),0)==200,"Mixture ledger preserves both canonical materials and their quantities");
        var cooled=FluidState.with(mixture,0,4);
        h.assertTrue(FluidState.gas(cooled) && FluidState.temperature(cooled)==0,"A gaseous mixture does not selectively condense");
        for(int level=0;level<=16;level++) {
            var source=FluidState.with(new FluidStack(net.createplant.registry.CEFluids.HYDROGEN_GAS.get(),1000),8,level);
            var up=Compression.convert(source,true);
            var down=Compression.convert(source,false);
            h.assertTrue(FluidState.pressure(up)==Math.min(16,level+4) && FluidState.pressure(down)==Math.max(0,level-4)
                && up.getAmount()==1000 && down.getAmount()==1000 && FluidState.temperature(up)==8,"Gas pressure changes by four with conserved volume");
        }
        h.succeed();
    }
    @GameTest(template="test_petrochemical",timeoutTicks=100)
    public static void tankContentsKeepTheirGradeAcrossWeatherDaytimeAndSaveReload(GameTestHelper h){
        var at=new BlockPos(2,2,2);h.setBlock(at,AllBlocks.FLUID_TANK.get());var tank=(FluidTankBlockEntity)h.getBlockEntity(at);tank.getTankInventory().fill(new FluidStack(CEProcessFluids.WARM_WATER.get(),1700),EXECUTE);
        var saved=tank.saveWithoutMetadata(h.getLevel().registryAccess());long oldTime=h.getLevel().getDayTime();h.getLevel().setDayTime(18000);
        h.runAfterDelay(45,()-> {h.getLevel().setDayTime(oldTime);tank.loadWithComponents(saved,h.getLevel().registryAccess());var fluid=tank.getTankInventory().getFluid();h.assertTrue(fluid.is(CEProcessFluids.WARM_WATER.get()) && fluid.getAmount()==1700 && FluidState.temperature(fluid)==9 && FluidState.pressure(fluid)==4,"No ambient cooling or regenerated energy occurs in storage");h.succeed();});
    }
}
