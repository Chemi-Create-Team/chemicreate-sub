package net.createplant.atomics.gametest;

import net.createplant.gametest.ReactionTowerGameTests;
import net.createplant.gametest.ReactorGameTests;
import java.util.List;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import io.netty.buffer.Unpooled;
import net.createplant.CreatePlant;
import net.createplant.block.entity.LiddedVesselBlockEntity;
import net.createplant.block.entity.ReactionTowerBlockEntity;
import net.createplant.chemistry.ReactorModule;
import net.createplant.recipe.PressureReactionRecipe;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CEItems;
import net.createplant.registry.CESharedChemicals;
import net.createplant.atomics.registry.CEUranium;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public final class UraniumFuelGameTests {
    private static final BlockPos BASE=new BlockPos(4,3,4);

    private static LiddedVesselBlockEntity vessel(GameTestHelper h,BlockPos base,ReactorModule module,int layers) {
        var result=(LiddedVesselBlockEntity)ReactorGameTests.assemble(h.getLevel(),h.absolutePos(base),module,layers);
        h.assertTrue(result.formed(),"The vessel forms: "+result.status());
        return result;
    }
    private static void run(LiddedVesselBlockEntity vessel,int ticks) {
        if(vessel.module().stress>0)ReactorGameTests.speed(vessel,32);
        for(int i=0;i<ticks;i++)vessel.tickProcess();
    }
    private static void burner(GameTestHelper h,BlockPos base,HeatLevel heat) {
        h.setBlock(base.below(),AllBlocks.BLAZE_BURNER.getDefaultState().setValue(BlazeBurnerBlock.HEAT_LEVEL,heat));
    }
    private static IFluidHandler pipe(GameTestHelper h,LiddedVesselBlockEntity vessel) {
        var result=h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,vessel.getBlockPos(),Direction.NORTH);
        h.assertTrue(result!=null,"Native pipe capability exists");return result;
    }
    private static IItemHandler funnel(GameTestHelper h,LiddedVesselBlockEntity vessel) {
        var result=h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,vessel.getBlockPos(),Direction.NORTH);
        h.assertTrue(result!=null,"Native funnel capability exists");return result;
    }
    private static int count(IItemHandler port,Item item) {
        int count=0;for(int i=0;i<port.getSlots();i++)if(port.getStackInSlot(i).is(item))count+=port.getStackInSlot(i).getCount();return count;
    }
    private static int amount(LiddedVesselBlockEntity vessel,net.minecraft.world.level.material.Fluid fluid) {
        int amount=0;for(int i=0;i<vessel.displayedFluidSlots();i++){var stack=vessel.displayedFluid(i);if(stack.is(fluid))amount+=stack.getAmount();}return amount;
    }
    private static void reload(GameTestHelper h,LiddedVesselBlockEntity vessel) {
        var saved=vessel.saveWithoutMetadata(h.getLevel().registryAccess());
        vessel.loadWithComponents(saved.copy(),h.getLevel().registryAccess());vessel.validateStructure();
        h.assertTrue(vessel.formed(),"A reloaded vessel reforms");
    }
    private static PressureReactionRecipe recipe(GameTestHelper h,String id) {
        return (PressureReactionRecipe)h.getLevel().getRecipeManager().byKey(CreatePlant.location("pressure_reaction/"+id)).orElseThrow().value();
    }

    @GameTest(template="test_fractionation")
    public static void lavaRoastsOreAndBurnerFansFireBothPressedPellets(GameTestHelper h) {
        var fan=AllFanProcessingTypes.BLASTING;
        h.setBlock(BASE,Blocks.LAVA);h.assertTrue(fan.isValidAt(h.getLevel(),h.absolutePos(BASE)),"Lava enables native Create blasting");
        var roasted=fan.process(CEUranium.URANINITE.toStack(8),h.getLevel());
        h.assertTrue(roasted!=null && roasted.size()==1 && roasted.getFirst().is(CEUranium.YELLOWCAKE.get()) && roasted.getFirst().getCount()==8,"Lava fan roasts eight raw uranium into eight oxide feeds");
        var createUranium=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create","crushed_raw_uranium"));
        var compatible=fan.process(new ItemStack(createUranium,8),h.getLevel());
        h.assertTrue(compatible!=null && compatible.size()==1 && compatible.getFirst().is(CEUranium.YELLOWCAKE.get()) && compatible.getFirst().getCount()==8,
            "Create's own crushing result also roasts into yellowcake");
        h.setBlock(BASE,AllBlocks.BLAZE_BURNER.getDefaultState().setValue(BlazeBurnerBlock.HEAT_LEVEL,HeatLevel.KINDLED));
        h.assertTrue(fan.isValidAt(h.getLevel(),h.absolutePos(BASE)),"An ordinary lit Blaze Burner enables the same hot-air firing");
        for(var pair:List.of(List.of(CEUranium.GREEN_URANIUM_PELLET.get(),CEUranium.URANIUM_PELLET.get()),List.of(CEUranium.GREEN_COMPACT_URANIUM_PELLET.get(),CEUranium.COMPACT_URANIUM_PELLET.get()))) {
            var fired=fan.process(new ItemStack(pair.getFirst(),2),h.getLevel());
            h.assertTrue(fired!=null && fired.size()==1 && fired.getFirst().is(pair.getLast()) && fired.getFirst().getCount()==2,"The native burner fan uses the furnace recipe without losing pellets");
        }
        h.assertTrue(h.getLevel().getRecipeManager().byKey(CreatePlant.location("pressure_reaction/yellowcake")).isEmpty(),"The replaced wet ore route is absent");h.succeed();
    }

    /** A three-layer reaction tower with an empty tray in every layer, its north-west bottom corner at base. */
    private static ReactionTowerBlockEntity tower(GameTestHelper h,BlockPos base) { return ReactionTowerGameTests.tower(h.getLevel(),h.absolutePos(base),3,ItemStack.EMPTY); }
    /** The solid feed and products at the west side of a tower's bottom layer. */
    private static IItemHandler solids(GameTestHelper h,ReactionTowerBlockEntity tower) {
        var result=h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,tower.getBlockPos().west(),Direction.WEST);
        h.assertTrue(result!=null,"The tower's bottom side takes and gives solids");return result;
    }
    private static void burner(GameTestHelper h,ReactionTowerBlockEntity tower,HeatLevel heat) {
        h.getLevel().setBlock(tower.getBlockPos().below(),AllBlocks.BLAZE_BURNER.getDefaultState().setValue(BlazeBurnerBlock.HEAT_LEVEL,heat),3);
    }
    /** Runs the tower's batches of 100 ticks, one per call, as its own tick would. */
    private static void run(ReactionTowerBlockEntity tower,int batches) { for(int i=0;i<batches;i++)tower.process(100); }
    private static void reload(GameTestHelper h,ReactionTowerBlockEntity tower) {
        var lookup=h.getLevel().registryAccess();
        for(int y=0;y<tower.layers();y++){var layer=tower.layer(y);layer.loadWithComponents(layer.saveWithoutMetadata(lookup).copy(),lookup);}
    }

    @GameTest(template="test_fractionation")
    public static void towerTraysNeedHeatForReductionAndSuperheatForGasification(GameTestHelper h) {
        var tray=tower(h,BASE);var items=solids(h,tray);var input=ReactionTowerGameTests.port(tray);
        h.assertTrue(items.insertItem(0,CEUranium.YELLOWCAKE.toStack(),true).isEmpty() && items.getStackInSlot(0).isEmpty(),"Simulated funnel insertion is inert");
        items.insertItem(0,CEUranium.YELLOWCAKE.toStack(),false);
        h.assertTrue(input.fill(new FluidStack(CEFluids.HYDROGEN_GAS.get(),250),EXECUTE)==250,"The trays' solid lets the tower take hydrogen");
        tray.process(110);
        h.assertTrue(tray.status().equals("heat") && tray.layer(0).load().is(CEUranium.YELLOWCAKE.get()) && tray.tank(0).getAmount()==250,"Unheated trays retain both feeds");
        burner(h,tray,HeatLevel.KINDLED);tray.process(45);reload(h,tray);tray.process(70);
        h.assertTrue(tray.layer(0).made().is(CEUranium.URANIUM_DIOXIDE.get()) && tray.layer(0).made().getCount()==1 && tray.layer(0).load().isEmpty() && tray.tank(0).isEmpty(),
            "Reduction survives reload and consumes exactly one batch, leaving the dioxide on the tray");
        h.assertTrue(items.extractItem(tray.layers(),1,false).is(CEUranium.URANIUM_DIOXIDE.get()) && tray.layer(0).made().isEmpty(),"A funnel takes the product from the bottom side");
        var gasBase=BASE.offset(8,0,0);var gas=tower(h,gasBase);
        solids(h,gas).insertItem(0,CEUranium.URANIUM_TETRAFLUORIDE.toStack(),false);ReactionTowerGameTests.port(gas).fill(new FluidStack(CESharedChemicals.FLUORINE.get(),250),EXECUTE);
        burner(h,gas,HeatLevel.KINDLED);run(gas,2);
        h.assertTrue(gas.status().equals("heat") && gas.tank(2).isEmpty() && gas.layer(0).load().is(CEUranium.URANIUM_TETRAFLUORIDE.get()),"Ordinary heat cannot gasify green salt");
        burner(h,gas,HeatLevel.SEETHING);run(gas,1);
        h.assertTrue(gas.tank(2).is(CEUranium.URANIUM_HEXAFLUORIDE.get()) && gas.tank(2).getAmount()==250 && gas.tank(0).isEmpty(),"Superheated trays make the UF6 gas");
        var catalysed=tower(h,BASE.offset(16,0,0));catalysed.insert(CEItems.OXIDATION_CATALYST.toStack());
        h.assertTrue(!solids(h,catalysed).isItemValid(0,CEUranium.YELLOWCAKE.toStack()) && !solids(h,catalysed).insertItem(0,CEUranium.YELLOWCAKE.toStack(),false).isEmpty(),
            "A tower holding a catalyst takes no solid feed");
        var stirred=vessel(h,BASE.offset(24,0,0),ReactorModule.STIRRED,2);var stirItems=funnel(h,stirred);
        stirItems.insertItem(0,CEUranium.YELLOWCAKE.toStack(),false);pipe(h,stirred).fill(new FluidStack(CEFluids.HYDROGEN_GAS.get(),250),EXECUTE);burner(h,BASE.offset(24,0,0),HeatLevel.SEETHING);run(stirred,150);
        h.assertTrue(count(stirItems,CEUranium.URANIUM_DIOXIDE.get())==0,"A stirred tank cannot bypass the tray requirement");h.succeed();
    }

    @GameTest(template="test_fractionation")
    public static void allThreeReconversionFeedsWorkInEveryArrivalOrderAndAfterReload(GameTestHelper h) {
        int[][] orders={{0,1,2},{0,2,1},{1,0,2},{1,2,0},{2,0,1},{2,1,0}};
        for(int grade=0;grade<2;grade++) {
            var be=vessel(h,BASE.offset(grade*8,0,0),ReactorModule.STIRRED,1);
            var uranium=grade==0 ? CEUranium.ENRICHED_URANIUM_HEXAFLUORIDE.get() : CEUranium.COMPACT_ENRICHED_URANIUM_HEXAFLUORIDE.get();
            var dioxide=grade==0 ? CEUranium.ENRICHED_URANIUM_DIOXIDE.get() : CEUranium.COMPACT_URANIUM_DIOXIDE.get();
            var feeds=new FluidStack[]{new FluidStack(uranium,250),new FluidStack(Fluids.WATER,250),new FluidStack(CEFluids.HYDROGEN_GAS.get(),250)};
            for(int index=0;index<orders.length;index++) {
                var input=pipe(h,be);var items=funnel(h,be);int[] order=orders[index];
                for(int part=0;part<2;part++)h.assertTrue(input.fill(feeds[order[part]],EXECUTE)==250,"The first two fluids enter through native piping");
                run(be,110);h.assertTrue(count(items,dioxide)==0 && amount(be,feeds[order[0]].getFluid())==250 && amount(be,feeds[order[1]].getFluid())==250,"Missing third fluid consumes nothing");
                h.assertTrue(input.fill(feeds[order[2]],SIMULATE)==250 && amount(be,feeds[order[2]].getFluid())==0,"Simulating the third fluid changes nothing");
                h.assertTrue(input.fill(feeds[order[2]],EXECUTE)==250,"The third fluid enters the third process feed buffer");
                run(be,40);reload(h,be);items=funnel(h,be);run(be,65);
                h.assertTrue(count(items,dioxide)==1 && amount(be,uranium)==0 && amount(be,Fluids.WATER)==0 && amount(be,CEFluids.HYDROGEN_GAS.get())==0,"Every arrival order consumes all three feeds exactly once: grade="+grade+", order="+index+", status="+be.status());
                h.assertTrue(amount(be,CESharedChemicals.HYDROGEN_FLUORIDE.get())==750,"Both grades return the combined 750 mB HF byproduct");
                h.assertTrue(items.extractItem(9,1,false).is(dioxide),"The product is available to standard funnel extraction");
                be.port(Direction.NORTH).drain(new FluidStack(CESharedChemicals.HYDROGEN_FLUORIDE.get(),750),EXECUTE);
                run(be,105);h.assertTrue(count(items,dioxide)==0,"No additional batch appears after reload");
            }
        }
        h.succeed();
    }

    @GameTest(template="test_fractionation")
    public static void blockedReconversionProductsRetainEveryInput(GameTestHelper h) {
        var be=vessel(h,BASE,ReactorModule.STIRRED,1);var input=pipe(h,be);
        for(var stack:List.of(new FluidStack(CEUranium.ENRICHED_URANIUM_HEXAFLUORIDE.get(),250),new FluidStack(CEFluids.HYDROGEN_GAS.get(),250),new FluidStack(Fluids.WATER,250)))input.fill(stack,EXECUTE);
        var saved=be.saveWithoutMetadata(h.getLevel().registryAccess());
        // Fill the vessel's volume with hydrogen fluoride beside the gaseous feed, so no further gas fits.
        int full=((net.createplant.thermal.ThermalFluidTank)be.getTankInventory()).gasCapacity()-500;
        for(int tank=2;tank<7 && full>0;tank++){int part=Math.min(full,8000*64);saved.put("Tank"+tank,new FluidStack(CESharedChemicals.HYDROGEN_FLUORIDE.get(),part).save(h.getLevel().registryAccess()));full-=part;}
        be.loadWithComponents(saved,h.getLevel().registryAccess());be.validateStructure();run(be,150);
        h.assertTrue(amount(be,CEUranium.ENRICHED_URANIUM_HEXAFLUORIDE.get())==250 && amount(be,CEFluids.HYDROGEN_GAS.get())==250 && amount(be,Fluids.WATER)==250 && count(funnel(h,be),CEUranium.ENRICHED_URANIUM_DIOXIDE.get())==0,"A full product buffer preserves every input");
        be.port(Direction.NORTH).drain(new FluidStack(CESharedChemicals.HYDROGEN_FLUORIDE.get(),750),EXECUTE);run(be,105);
        h.assertTrue(count(funnel(h,be),CEUranium.ENRICHED_URANIUM_DIOXIDE.get())==1 && amount(be,Fluids.WATER)==0,"Freeing exactly one batch's space resumes once");h.succeed();
    }

    @GameTest(template="test_fractionation")
    public static void oreToPelletRunsThroughTheRequestedExistingMachines(GameTestHelper h) {
        var tray=tower(h,BASE);var stirBase=BASE.offset(8,0,0);var stir=vessel(h,stirBase,ReactorModule.STIRRED,2);var gasBase=BASE.offset(16,0,0);var gas=tower(h,gasBase);
        var roast=AllFanProcessingTypes.BLASTING.process(CEUranium.URANINITE.toStack(8),h.getLevel());
        solids(h,tray).insertItem(0,roast.getFirst(),false);ReactionTowerGameTests.port(tray).fill(new FluidStack(CEFluids.HYDROGEN_GAS.get(),2000),EXECUTE);burner(h,tray,HeatLevel.KINDLED);run(tray,8);
        var reduced=solids(h,tray).extractItem(tray.layers(),8,false);h.assertTrue(reduced.is(CEUranium.URANIUM_DIOXIDE.get()) && reduced.getCount()==8,"Eight roasted feeds reduce on the trays");
        funnel(h,stir).insertItem(0,reduced,false);pipe(h,stir).fill(new FluidStack(CESharedChemicals.HYDROGEN_FLUORIDE.get(),4000),EXECUTE);burner(h,stirBase,HeatLevel.KINDLED);run(stir,820);
        var salt=funnel(h,stir).extractItem(9,8,false);h.assertTrue(salt.is(CEUranium.URANIUM_TETRAFLUORIDE.get()) && salt.getCount()==8,"The heated stirred tank makes green salt");
        solids(h,gas).insertItem(0,salt,false);ReactionTowerGameTests.port(gas).fill(new FluidStack(CESharedChemicals.FLUORINE.get(),2000),EXECUTE);burner(h,gas,HeatLevel.SEETHING);run(gas,8);
        var feed=ReactionTowerGameTests.port(gas).drain(new FluidStack(CEUranium.URANIUM_HEXAFLUORIDE.get(),2000),EXECUTE);h.assertTrue(feed.getAmount()==2000,"Superheated trays provide the centrifuge feed");
        var centrifuge=GasCentrifugeGameTests.build(h,BASE.offset(24,0,0),4);centrifuge.port(0).fill(feed,EXECUTE);GasCentrifugeGameTests.run(centrifuge,410);
        var enriched=centrifuge.port(1).drain(250,EXECUTE);h.assertTrue(enriched.is(CEUranium.ENRICHED_URANIUM_HEXAFLUORIDE.get()) && enriched.getAmount()==250,"The unchanged enrichment stage separates 250 mB product");
        h.setBlock(stirBase.below(),Blocks.AIR);var input=pipe(h,stir);input.fill(enriched,EXECUTE);input.fill(new FluidStack(CEFluids.HYDROGEN_GAS.get(),250),EXECUTE);input.fill(new FluidStack(Fluids.WATER,250),EXECUTE);run(stir,105);
        var powder=funnel(h,stir).extractItem(9,1,false);h.assertTrue(powder.is(CEUranium.ENRICHED_URANIUM_DIOXIDE.get()),"The three-fluid stirred step produces enriched dioxide powder");
        var press=(com.simibubi.create.content.kinetics.press.PressingRecipe)h.getLevel().getRecipeManager().byKey(CreatePlant.location("pressing/green_uranium_pellet")).orElseThrow().value();
        h.assertTrue(press.getIngredients().getFirst().test(powder),"The standard press accepts that powder");var green=press.getResultItem(h.getLevel().registryAccess());var fired=AllFanProcessingTypes.BLASTING.process(green,h.getLevel());
        h.assertTrue(green.getCount()==2 && fired.getFirst().is(CEUranium.URANIUM_PELLET.get()) && fired.getFirst().getCount()==2,"One dioxide powder presses and fires into two fuel pellets");h.succeed();
    }

    @GameTest(template="test_fractionation")
    public static void thirdFeedGasDrainIsFiniteAndSurvivesReload(GameTestHelper h) {
        var be=vessel(h,BASE,ReactorModule.STIRRED,1);var input=pipe(h,be);
        input.fill(new FluidStack(CEUranium.ENRICHED_URANIUM_HEXAFLUORIDE.get(),250),EXECUTE);
        input.fill(new FluidStack(Fluids.WATER,250),EXECUTE);
        h.assertTrue(input.fill(new FluidStack(CEFluids.HYDROGEN_GAS.get(),1000),EXECUTE)==1000,"A third gas uses its separate finite process buffer");
        var gas=be.processGasPort(net.createplant.block.entity.ReactorMachineBlockEntity.THIRD_FEED);
        var before=be.saveWithoutMetadata(h.getLevel().registryAccess());
        h.assertTrue(gas.drain(250,SIMULATE).getAmount()==250 && before.equals(be.saveWithoutMetadata(h.getLevel().registryAccess())),"Simulated gas removal preserves all inventory");
        h.assertTrue(gas.drain(250,EXECUTE).getAmount()==250,"Actual gas removal returns exactly its debit");reload(h,be);
        h.assertTrue(amount(be,CEFluids.HYDROGEN_GAS.get())==750 && amount(be,Fluids.WATER)==250,"Reload retains gas and untouched liquid");h.succeed();
    }

    @GameTest(template="test_empty")
    public static void reactionModulesAndThreeFluidsSurviveNetworkSerialization(GameTestHelper h) {
        var serializer=new PressureReactionRecipe.Serializer();
        for(String id:List.of("uranium_dioxide","uranium_tetrafluoride","uranium_hexafluoride","enriched_uranium_dioxide","compact_uranium_dioxide")) {
            var original=recipe(h,id);var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
            try {
                serializer.streamCodec().encode(buffer,original);var decoded=serializer.streamCodec().decode(buffer);
                h.assertTrue(decoded.module()==original.module() && decoded.getFluidIngredients().size()==original.getFluidIngredients().size() && decoded.getRequiredHeat()==original.getRequiredHeat(),"Client recipe synchronization preserves module, fluids and heat for "+id);
            } finally { buffer.release(); }
        }
        h.succeed();
    }
}
