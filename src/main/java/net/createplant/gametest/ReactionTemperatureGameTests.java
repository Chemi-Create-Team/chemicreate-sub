package net.createplant.gametest;

import net.createplant.thermal.FluidForm;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import java.util.Map;
import net.createplant.CreatePlant;
import net.createplant.block.entity.LiddedVesselBlockEntity;
import net.createplant.chemistry.ProcessRecipe;
import net.createplant.chemistry.ReactorModule;
import net.createplant.registry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public class ReactionTemperatureGameTests {
    private static final BlockPos BASE=new BlockPos(5,3,5);
    @GameTest(template="test_reactor")
    public static void recipesUseTypedFeedAndNativeHeatStages(GameTestHelper h){
        for(var entry:Map.of("crack_naphtha",HeatCondition.SUPERHEATED,"polymerize_ethylene",HeatCondition.HEATED,"polymerize_propylene",HeatCondition.HEATED,"hydrotreat_diesel",HeatCondition.HEATED,"hydrocrack_heavy_oil",HeatCondition.SUPERHEATED,"oxidize_ethylene",HeatCondition.HEATED).entrySet()){
            var recipe=ProcessRecipe.all().stream().filter(r->r.id().equals(entry.getKey())).findFirst().orElseThrow();
            h.assertTrue(recipe.requiredHeat()==entry.getValue() && !recipe.heatAllowed(HeatLevel.NONE) && recipe.heatAllowed(HeatLevel.SEETHING),"Native heat condition in "+entry.getKey());
            h.assertTrue(recipe.heatAllowed(HeatLevel.KINDLED)==(entry.getValue()==HeatCondition.HEATED),"Superheating boundary in "+entry.getKey());
            if(entry.getKey().startsWith("hydro"))h.assertTrue(FluidForm.COMPRESSED_HYDROGEN.matches(recipe.inputs().get(1)),"Hydrogen grade is part of fluid identity");
        }
        for(String name:new String[]{"heated_naphtha","heated_diesel","heated_heavy_oil","heated_ethylene","heated_propylene","heated_synthesis_feed"})h.assertTrue(!BuiltInRegistries.FLUID.containsKey(CreatePlant.location(name)),"No temperature-only fluid: "+name);
        h.succeed();
    }
    @GameTest(template="test_reactor")
    public static void removingHeatStopsAnUnpaidBatchAndReloadDoesNotDuplicateIt(GameTestHelper h){
        var owner=(LiddedVesselBlockEntity)ReactorGameTests.assemble(h.getLevel(),h.absolutePos(BASE),ReactorModule.STIRRED,1);ReactorGameTests.speed(owner,32);
        var input=owner.port(Direction.NORTH);input.fill(new FluidStack(net.createplant.registry.CEPetroFluids.PROPYLENE.get(),1000),EXECUTE);input.fill(new FluidStack(net.createplant.registry.CEProcessFluids.SYNTHESIS_GAS.get(),1000),EXECUTE);
        for(int i=0;i<120;i++)owner.tickProcess();
        h.assertTrue(owner.progress()==0 && input.getFluidInTank(0).getAmount()==1000,"No burner consumes no feed");
        h.setBlock(BASE.below(),AllBlocks.BLAZE_BURNER.getDefaultState().setValue(BlazeBurnerBlock.HEAT_LEVEL,HeatLevel.KINDLED));
        for(int i=0;i<30;i++)owner.tickProcess();
        // A fluid-only reaction runs continuously: part of the feed has already reacted, nothing more and nothing less.
        int feed=input.getFluidInTank(0).getAmount(), made=input.getFluidInTank(2).getAmount();
        h.assertTrue(owner.progress()>0 && feed<1000 && made>0 && feed+made<=1000,"Heating starts immediately without vessel warm-up: "+feed+" + "+made);
        h.setBlock(BASE.below(),net.minecraft.world.level.block.Blocks.AIR);
        for(int i=0;i<120;i++)owner.tickProcess();
        h.assertTrue(owner.status().equals("heat") && input.getFluidInTank(0).getAmount()==feed && input.getFluidInTank(2).getAmount()==made,"Loss of heat stops the reaction where it was");
        owner.loadWithComponents(owner.saveWithoutMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());owner.validateStructure();ReactorGameTests.speed(owner,32);
        h.setBlock(BASE.below(),AllBlocks.BLAZE_BURNER.getDefaultState().setValue(BlazeBurnerBlock.HEAT_LEVEL,HeatLevel.KINDLED));
        for(int i=0;i<320;i++)owner.tickProcess();input=owner.port(Direction.NORTH);
        h.assertTrue(input.getFluidInTank(0).isEmpty() && input.getFluidInTank(2).getAmount()==1000,"Reload and renewed heat complete exactly one paid batch");h.succeed();
    }
}
