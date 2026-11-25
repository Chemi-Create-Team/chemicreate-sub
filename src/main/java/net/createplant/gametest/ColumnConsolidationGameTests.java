package net.createplant.gametest;

import net.createplant.thermal.FluidForm;
import net.createplant.CreatePlant;
import net.createplant.block.FractionatingColumnBlock;
import net.createplant.block.entity.FractionatingColumnBlockEntity;
import net.createplant.chemistry.DistillationProfile;
import net.createplant.chemistry.ProcessModule;
import net.createplant.chemistry.ProcessRecipe;
import net.createplant.registry.CEDistillation;
import net.createplant.registry.CEPetroFluids;
import net.createplant.registry.CEProcessFluids;
import net.createplant.thermal.FluidThermal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.fluids.FluidStack;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class ColumnConsolidationGameTests {
    private static final int[] LAYERS={13,9,6,3,0};
    private static FluidStack feed(DistillationProfile.Fraction recipe,int batches) {
        var input=recipe.input().copyWithAmount(recipe.input().getAmount()*batches);
        return input.is(CEPetroFluids.CRUDE_OIL.get()) ? FluidThermal.at(input,650) : input;
    }
    @GameTest(template="test_fractionation",timeoutTicks=240)
    public static void aNativePumpCarriesHotCrudeThroughTheModeChange(GameTestHelper h) {
        var owner=DistillationGameTests.build(h.getLevel(),h.absolutePos(new BlockPos(5,2,5)),2,5);
        var pump=new BlockPos(8,4,5);var source=new BlockPos(9,4,5);var cog=pump.south();var motor=cog.west();
        h.setBlock(source,net.createplant.registry.CETransport.STAINLESS_STEEL_TANK.get());
        var pipe=net.createplant.registry.CETransport.STAINLESS_PIPE.get().defaultBlockState();
        for(var side:Direction.values())pipe=pipe.setValue(com.simibubi.create.content.fluids.pipes.FluidPipeBlock.PROPERTY_BY_DIRECTION.get(side),side.getAxis()==Direction.Axis.X);
        h.setBlock(new BlockPos(7,4,5),pipe);
        h.setBlock(pump,com.simibubi.create.AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,Direction.WEST));
        h.setBlock(cog,com.simibubi.create.AllBlocks.COGWHEEL.getDefaultState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,Direction.Axis.X));
        h.setBlock(motor,com.simibubi.create.AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,Direction.EAST));
        var tank=((com.simibubi.create.content.fluids.tank.FluidTankBlockEntity)h.getBlockEntity(source)).getTankInventory();
        tank.fill(FluidForm.PREHEATED_CRUDE_OIL.stack(2000),EXECUTE);
        h.runAfterDelay(5,()->((com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity)h.getBlockEntity(motor)).generatedSpeed.setValue(64));
        h.succeedWhen(()->{
            h.assertTrue(owner.recipeId().equals("distill_crude") && owner.stock(1)>0,"Native pumped crude selects chemical mode and completes a real batch");
            int total=tank.getFluidAmount();for(int i=0;i<6;i++)total+=owner.stock(i);
            h.assertTrue(total==2000,"Refreshing the actual Create pipe targets through a mode change neither loses nor duplicates crude or its fractions");
        });
    }
    @GameTest(template="test_fractionation",timeoutTicks=360)
    public static void compactStoryboardPumpsKeepAllFiveProductsSeparate(GameTestHelper h) throws Exception {
        var origin=new BlockPos(1,1,1);
        try(var stream=ColumnConsolidationGameTests.class.getResourceAsStream("/assets/"+CreatePlant.MOD_ID+"/ponder/plant/fractionating_column.nbt")) {
            var structure=net.minecraft.nbt.NbtIo.readCompressed(stream,net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            var palette=structure.getList("palette",net.minecraft.nbt.Tag.TAG_COMPOUND);
            var blocks=structure.getList("blocks",net.minecraft.nbt.Tag.TAG_COMPOUND);
            for(int i=0;i<blocks.size();i++) {
                var block=blocks.getCompound(i);var coordinates=block.getList("pos",net.minecraft.nbt.Tag.TAG_INT);
                h.setBlock(origin.offset(coordinates.getInt(0),coordinates.getInt(1),coordinates.getInt(2)),
                    net.minecraft.nbt.NbtUtils.readBlockState(h.getLevel().holderLookup(net.minecraft.core.registries.Registries.BLOCK),palette.getCompound(block.getInt("state"))));
            }
        }
        var owner=(FractionatingColumnBlockEntity)h.getBlockEntity(origin.offset(8,1,13));owner.rebuild();
        var recipe=DistillationProfile.fraction("distill_crude");
        var source=((com.simibubi.create.content.fluids.tank.FluidTankBlockEntity)h.getBlockEntity(origin.offset(13,3,13))).getTankInventory();
        source.fill(feed(recipe,4),EXECUTE);
        h.runAfterDelay(5,()->{
            ((com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity)h.getBlockEntity(origin.offset(12,2,13))).generatedSpeed.setValue(64);
            for(int i=0;i<5;i++)((com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity)h.getBlockEntity(origin.offset(9,5-i,12-2*i))).generatedSpeed.setValue(64);
        });
        h.succeedWhen(()->{
            h.assertTrue(source.getFluid().isEmpty(),"The illustrated feed pump empties its source");
            for(int i=0;i<5;i++) {
                var target=((com.simibubi.create.content.fluids.tank.FluidTankBlockEntity)h.getBlockEntity(origin.offset(8,5-i,9-2*i))).getTankInventory().getFluid();
                var expected=recipe.outputs().get(i).copyWithAmount(4*recipe.outputs().get(i).getAmount());
                h.assertTrue(target.is(expected.getFluid()) && target.getAmount()==expected.getAmount(),"The illustrated outlet "+(i+1)+" collects only its paid fraction: "+target+"; recipe="+owner.recipeId()+" input="+owner.stock(0)+" cut="+owner.stock(i+1)+" pump="+((com.simibubi.create.content.fluids.pump.PumpBlockEntity)h.getBlockEntity(origin.offset(8,5-i,11-2*i))).getSpeed());
            }
            for(int i=0;i<6;i++)h.assertTrue(owner.stock(i)==0,"No paid fluid remains stranded in the tower");
        });
    }
    @GameTest(template="test_fractionation")
    public static void everyFormerTrayRecipeRunsOnTheFractionationTower(GameTestHelper h) {
        int index=0;
        for(var process:ProcessRecipe.all())if(process.module()==ProcessModule.FRACTION_SEPARATOR) {
            var recipe=DistillationProfile.fraction(process.id());
            var owner=DistillationGameTests.build(h.getLevel(),h.absolutePos(fractionPosition(index++)),2,14);
            var input=feed(recipe,1);var inlet=DistillationGameTests.inlet(owner,2);
            h.assertTrue(inlet.fill(input,SIMULATE)==input.getAmount() && owner.recipeId().equals("air") && owner.stock(0)==0,"A chemical feed preview does not change column mode or stocks");
            h.assertTrue(inlet.fill(input,EXECUTE)==input.getAmount() && owner.recipeId().equals(recipe.id()),"The existing tower accepts the former tray process: "+recipe.id());
            h.assertTrue(inlet.fill(input,EXECUTE)==0,"Changing mode invalidates the cached inlet");
            DistillationGameTests.run(owner,recipe.ticks()+5);
            h.assertTrue(owner.stock(0)==0,"Exactly one paid batch is consumed: "+recipe.id());
            double previousBoiling=0;
            for(int i=0;i<recipe.outputs().size();i++) {
                var expected=recipe.outputs().get(i);var out=DistillationGameTests.outlet(owner,LAYERS[i]);
                var preview=out.drain(Integer.MAX_VALUE,SIMULATE);var actual=out.drain(Integer.MAX_VALUE,EXECUTE);
                h.assertTrue(actual.is(expected.getFluid()) && actual.getAmount()==expected.getAmount() && preview.getAmount()==actual.getAmount(),"The actual cut retains the old recipe's product and quantity: "+recipe.id()+" / "+i);
                double boiling=net.createplant.thermal.FluidMaterials.boilingOrder(actual);h.assertTrue(boiling>=previousBoiling,"Lighter cuts leave above heavier cuts");previousBoiling=boiling;
                h.assertTrue(out.drain(1,EXECUTE).isEmpty(),"A cut cannot be recovered twice");
            }
        }
        h.succeed();
    }
    @GameTest(template="test_fractionation")
    public static void chemicalCutsRunAtTheirMinimumHeightAtTwoByTwo(GameTestHelper h) {
        int index=0;
        for(var process:ProcessRecipe.all())if(process.module()==ProcessModule.FRACTION_SEPARATOR) {
            var recipe=DistillationProfile.fraction(process.id());int height=DistillationProfile.requiredHeight(recipe.id());
            int width=2;
            var owner=DistillationGameTests.build(h.getLevel(),h.absolutePos(fractionPosition(index++)),width,height);
            var inlet=DistillationGameTests.inlet(owner,DistillationProfile.feedLayer(height));var input=feed(recipe,width*width);
            h.assertTrue(inlet.fill(input,SIMULATE)==input.getAmount() && owner.recipeId().equals("air"),"Preview leaves the empty short tower unselected");
            h.assertTrue(inlet.fill(input,EXECUTE)==input.getAmount(),"A "+height+"-layer tower accepts "+recipe.id());
            DistillationGameTests.run(owner,recipe.ticks()+10);
            for(int slot=1;slot<=recipe.outputs().size();slot++) {
                int layer=DistillationProfile.fractionLayer(slot,height,recipe.outputs().size());
                var expected=recipe.outputs().get(slot-1).copyWithAmount(recipe.outputs().get(slot-1).getAmount()*width*width);
                var output=DistillationGameTests.outlet(owner,layer);
                h.assertTrue(output!=null && output.drain(expected,SIMULATE).getAmount()==expected.getAmount(),"Each compact cut has its own real output port");
                h.assertTrue(output.drain(expected,EXECUTE).getAmount()==expected.getAmount() && owner.stock(slot)==0,"A cut is emitted exactly once");
            }
            h.assertTrue(owner.stock(0)==0,"All parallel input batches are consumed exactly once");
        }
        h.succeed();
    }
    @GameTest(template="test_fractionation")
    public static void shorterThanTheCutCountRejectsFeedAndOutletLayersNeverCollide(GameTestHelper h) {
        int index=0;
        for(var process:ProcessRecipe.all())if(process.module()==ProcessModule.FRACTION_SEPARATOR) {
            var recipe=DistillationProfile.fraction(process.id());int minimum=DistillationProfile.requiredHeight(recipe.id());
            var owner=DistillationGameTests.build(h.getLevel(),h.absolutePos(fractionPosition(index++)),2,minimum-1);
            var inlet=DistillationGameTests.inlet(owner,DistillationProfile.feedLayer(minimum-1));
            h.assertTrue(inlet==null || inlet.fill(feed(recipe,1),EXECUTE)==0,"An undersized tower cannot accept this recipe");
            for(int height=minimum;height<=14;height++) {
                int previous=height;
                for(int slot=1;slot<=recipe.outputs().size();slot++) {
                    int layer=DistillationProfile.fractionLayer(slot,height,recipe.outputs().size());
                    h.assertTrue(layer>=0 && layer<previous,"Every cut is reachable and strictly below the previous cut at height "+height);previous=layer;
                    h.assertTrue(DistillationProfile.fractionSlot(layer,height,recipe.outputs().size())==slot,"Port lookup agrees with the model");
                }
            }
        }
        h.succeed();
    }

    private static BlockPos fractionPosition(int index) {
        return new BlockPos(3 + 4 * (index % 6), 2, 4 + 4 * (index / 6));
    }
    @GameTest(template="test_fractionation")
    public static void hotCrudeIsRequiredAndResidualProductsPreventChangingFeed(GameTestHelper h) {
        var owner=DistillationGameTests.build(h.getLevel(),h.absolutePos(new BlockPos(5,2,5)),2,14);
        var inlet=DistillationGameTests.inlet(owner,2);
        h.assertTrue(inlet.fill(new FluidStack(CEPetroFluids.CRUDE_OIL.get(),1000),EXECUTE)==0,"Cold crude cannot bypass its existing 620 K heating requirement");
        h.assertTrue(inlet.fill(FluidThermal.at(FluidForm.PREHEATED_CRUDE_OIL.stack(1000),650),EXECUTE)==1000,"Heated crude selects distillation");
        DistillationGameTests.run(owner,110);
        h.assertTrue(DistillationGameTests.inlet(owner,2).fill(new FluidStack(CEProcessFluids.COOLED_OLEFINS.get(),200),EXECUTE)==0,"Residual fractions keep another feed from changing the outputs");
        for(int layer:LAYERS)DistillationGameTests.outlet(owner,layer).drain(Integer.MAX_VALUE,EXECUTE);
        h.assertTrue(DistillationGameTests.inlet(owner,2).fill(new FluidStack(CEProcessFluids.COOLED_OLEFINS.get(),200),EXECUTE)==200,"A completely drained tower can run another separation process");
        h.assertTrue(DistillationGameTests.outlet(owner,13).getFluidInTank(0).isEmpty(),"Changing feed does not manufacture a new product");h.succeed();
    }
    @GameTest(template="test_fractionation")
    public static void chemicalStockAndProgressSurviveARealControllerNbtReload(GameTestHelper h) {
        var at=h.absolutePos(new BlockPos(5,2,5));var owner=DistillationGameTests.build(h.getLevel(),at,2,5);var recipe=DistillationProfile.fraction("distill_crude");
        DistillationGameTests.inlet(owner,2).fill(feed(recipe,2),EXECUTE);DistillationGameTests.run(owner,50);
        // The column distils continuously, so halfway through the cycle part of the feed is already a product.
        int feedLeft=owner.stock(0), made=owner.stock(1);
        var tag=owner.saveWithoutMetadata(h.getLevel().registryAccess());h.getLevel().removeBlockEntity(at);
        var restored=new FractionatingColumnBlockEntity(at,h.getLevel().getBlockState(at));restored.loadWithComponents(tag,h.getLevel().registryAccess());h.getLevel().setBlockEntity(restored);restored.rebuild();
        h.assertTrue(restored.recipeId().equals("distill_crude") && restored.stock(0)==feedLeft && restored.stock(1)==made,
            "Local cell stocks and active process survive the controller's replacement: "+restored.stock(0)+"/"+feedLeft);
        DistillationGameTests.run(restored,55);
        h.assertTrue(restored.stock(0)==0 && restored.stock(1)==2*recipe.outputs().getFirst().getAmount(),"Reload preserves progress and finishes the two parallel batches once: "+restored.stock(0)+" / "+restored.stock(1));
        DistillationGameTests.run(restored,100);
        h.assertTrue(restored.stock(0)==0 && restored.stock(1)==2*recipe.outputs().getFirst().getAmount(),"Further ticking cannot repeat either paid batch");h.succeed();
    }
    @GameTest(template="test_fractionation")
    public static void breakingAndRepairingAnEmptyTopCellKeepsChemicalFeedInItsCells(GameTestHelper h) {
        var at=h.absolutePos(new BlockPos(5,2,5));var owner=DistillationGameTests.build(h.getLevel(),at,2,14);
        DistillationGameTests.inlet(owner,2).fill(feed(DistillationProfile.fraction("distill_crude"),2),EXECUTE);
        h.getLevel().removeBlock(at.offset(1,13,1),false);
        int total=0;
        for(int y=0;y<14;y++)for(int x=0;x<2;x++)for(int z=0;z<2;z++)if(h.getLevel().getBlockEntity(at.offset(x,y,z)) instanceof FractionatingColumnBlockEntity part) {
            var data=part.saveWithoutMetadata(h.getLevel().registryAccess());for(int n:data.getIntArray("Held"))total+=n;
        }
        h.assertTrue(total==2000,"Structure splitting keeps the feed in the cells that actually contain it");
        h.getLevel().setBlock(at.offset(1,13,1),CEDistillation.COLUMN.get().defaultBlockState(),3);
        ((FractionatingColumnBlockEntity)h.getLevel().getBlockEntity(at.offset(1,13,1))).rebuild();
        var restored=((FractionatingColumnBlockEntity)h.getLevel().getBlockEntity(at)).getControllerBE();
        h.assertTrue(restored.ready() && restored.stock(0)==2000 && restored.recipeId().equals("distill_crude"),"Repair regroups the same finite chemical inventory and process");h.succeed();
    }
    @GameTest(template="test_fractionation")
    public static void unsupportedExpansionAndReloadPreserveFeedUntilTwoByTwoIsRestored(GameTestHelper h) {
        var at=h.absolutePos(new BlockPos(5,2,5));var owner=DistillationGameTests.build(h.getLevel(),at,2,5);
        var recipe=DistillationProfile.fraction("distill_crude");
        DistillationGameTests.inlet(owner,2).fill(feed(recipe,2),EXECUTE);
        var stale=DistillationGameTests.inlet(owner,2);var outlet=DistillationGameTests.outlet(owner,4);
        for(int y=0;y<5;y++)for(int x=0;x<3;x++)for(int z=0;z<3;z++)if(x==2 || z==2)
            h.getLevel().setBlock(at.offset(x,y,z),CEDistillation.COLUMN.get().defaultBlockState(),3);
        ((FractionatingColumnBlockEntity)h.getLevel().getBlockEntity(at.offset(2,4,2))).rebuild();
        owner=((FractionatingColumnBlockEntity)h.getLevel().getBlockEntity(at)).getControllerBE();
        h.assertTrue(!owner.ready() && owner.getWidth()==3 && stale.fill(feed(recipe,1),EXECUTE)==0 && outlet.drain(1000,EXECUTE).isEmpty(),"Expanding to 3×3 disables processing and stale ports");
        var lookup=h.getLevel().registryAccess();owner.loadWithComponents(owner.saveWithoutMetadata(lookup),lookup);owner.rebuild();
        DistillationGameTests.run(owner,300);int total=0;
        for(int y=0;y<5;y++)for(int x=0;x<3;x++)for(int z=0;z<3;z++) {
            var part=(FractionatingColumnBlockEntity)h.getLevel().getBlockEntity(at.offset(x,y,z));
            for(int amount:part.saveWithoutMetadata(lookup).getIntArray("Held"))total+=amount;
        }
        h.assertTrue(total==2000 && !owner.ready(),"Reloading an unsupported tower keeps all paid feed without running it");
        for(int y=0;y<5;y++)for(int x=0;x<3;x++)for(int z=0;z<3;z++)if(x==2 || z==2)h.getLevel().removeBlock(at.offset(x,y,z),false);
        ((FractionatingColumnBlockEntity)h.getLevel().getBlockEntity(at.offset(1,4,1))).rebuild();
        var restored=((FractionatingColumnBlockEntity)h.getLevel().getBlockEntity(at)).getControllerBE();
        h.assertTrue(restored.ready() && restored.getWidth()==2 && restored.stock(0)==2000,"Removing the empty extension restores the same finite feed");
        DistillationGameTests.run(restored,110);int products=0;for(int slot=1;slot<6;slot++)products+=restored.stock(slot);
        h.assertTrue(restored.stock(0)==0 && products==2000,"The restored 2×2 tower processes each paid batch once");h.succeed();
    }
}
