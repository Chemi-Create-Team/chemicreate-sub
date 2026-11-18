package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import net.createplant.CreatePlant;
import net.createplant.block.ReactionTowerBlock;
import net.createplant.block.entity.ReactionTowerBlockEntity;
import net.createplant.registry.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.items.IItemHandler;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class ReactionTowerGameTests {
    private static final BlockPos BASE=new BlockPos(21,2,4),CENTRE=BASE.offset(1,0,1);
    private static void build(GameTestHelper h,int layers){for(int y=0;y<layers;y++)for(int x=0;x<3;x++)for(int z=0;z<3;z++)h.setBlock(BASE.offset(x,y,z),CETransport.REACTION_TOWER.get());}
    private static ReactionTowerBlockEntity owner(GameTestHelper h){return ((ReactionTowerBlockEntity)h.getBlockEntity(CENTRE)).owner();}
    private static IFluidHandler fluid(GameTestHelper h,BlockPos relative,Direction side){return h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,h.absolutePos(relative),side);}
    private static IItemHandler items(GameTestHelper h,BlockPos relative,Direction side){return h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,h.absolutePos(relative),side);}
    private static void heat(GameTestHelper h){h.setBlock(BASE.offset(0,-1,0),AllBlocks.BLAZE_BURNER.get().defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL,BlazeBurnerBlock.HeatLevel.KINDLED));}
    private static ItemStack basket(){return new ItemStack(CEReactor.CATALYST_BASKET.get());}
    /** Builds and forms a 3x3 tower at once from its north-west bottom corner, with a basket and, unless empty, the catalyst in every layer; returns its owner. */
    public static ReactionTowerBlockEntity tower(ServerLevel level,BlockPos corner,int layers,ItemStack catalyst){
        for(int y=0;y<layers;y++)for(int x=0;x<3;x++)for(int z=0;z<3;z++)level.setBlock(corner.offset(x,y,z),CETransport.REACTION_TOWER.get().defaultBlockState(),3);
        com.simibubi.create.api.connectivity.ConnectivityHandler.formMulti((ReactionTowerBlockEntity)level.getBlockEntity(corner));
        var owner=((ReactionTowerBlockEntity)level.getBlockEntity(corner.offset(1,0,1))).owner();
        load(owner,layers,catalyst);
        return owner;
    }
    /** The feed and product port on the west side of a tower's bottom layer. */
    public static IFluidHandler port(ReactionTowerBlockEntity owner){return owner.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,owner.getBlockPos().west(),Direction.WEST);}
    /** Kindles a blaze burner under the tower's bottom corner. */
    public static void heat(ReactionTowerBlockEntity owner){owner.getLevel().setBlockAndUpdate(owner.getBlockPos().offset(-1,-1,-1),AllBlocks.BLAZE_BURNER.get().defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL,BlazeBurnerBlock.HeatLevel.KINDLED));}
    /** Fits a basket and the given catalyst into each of the first layers. */
    private static void load(ReactionTowerBlockEntity owner,int layers,ItemStack catalyst){for(int i=0;i<layers;i++){owner.insert(basket());if(!catalyst.isEmpty())owner.insert(catalyst.copy());}}

    @GameTest(template="test_fractionation")
    public static void aThreeByThreeTowerFormsWithAShaftAndPortsOnItsOuterSides(GameTestHelper h){
        build(h,3);
        h.runAfterDelay(10,()->{
            var owner=owner(h);
            h.assertTrue(owner!=null && owner.layers()==3,"Three 3x3 layers form a tower owned by the bottom centre");
            h.assertTrue(ReactionTowerBlock.mask(h.getBlockState(CENTRE))==0 && ReactionTowerBlock.mask(h.getBlockState(BASE))==(1|8) && ReactionTowerBlock.mask(h.getBlockState(BASE.offset(1,1,0)))==1,"Only the outer faces are walls, so the inside is hollow: "+ReactionTowerBlock.mask(h.getBlockState(CENTRE))+" "+ReactionTowerBlock.mask(h.getBlockState(BASE))+" "+ReactionTowerBlock.mask(h.getBlockState(BASE.offset(1,1,0))));
            h.assertTrue(h.getBlockState(BASE).getValue(com.simibubi.create.content.fluids.tank.FluidTankBlock.SHAPE)==com.simibubi.create.content.fluids.tank.FluidTankBlock.Shape.PLAIN,"The shell has no windows");
            h.assertTrue(fluid(h,BASE.offset(0,0,1),Direction.WEST)!=null && fluid(h,CENTRE,Direction.DOWN)!=null && fluid(h,BASE.offset(2,2,1),Direction.EAST)!=null,"The bottom layer's sides, the middle of the underside and the top layer's sides connect");
            h.assertTrue(fluid(h,BASE.offset(0,1,1),Direction.WEST)==null && fluid(h,BASE,Direction.DOWN)==null && fluid(h,BASE.offset(0,0,1),Direction.EAST)==null,"The middle layer, the rest of the underside and inner faces connect nothing");
            h.assertTrue(items(h,CENTRE.above(2),Direction.UP)!=null && items(h,CENTRE,Direction.UP)==null,"Catalysts go in through the top");
            owner.process(5);
            h.assertTrue(owner.status().equals("no_feed") && fluid(h,BASE.offset(0,0,1),Direction.WEST).fill(new FluidStack(CEProcessFluids.COMPRESSED_SYNTHESIS_FEED.get(),1000),SIMULATE)==0,"Without baskets the tower takes only what it can separate: "+owner.status());
            h.setBlock(BASE.offset(0,2,0),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            h.runAfterDelay(10,()->{h.assertTrue(owner(h)==null && fluid(h,BASE.offset(0,0,1),Direction.WEST)==null,"A broken tower closes its ports");h.succeed();});
        });
    }
    @GameTest(template="test_fractionation")
    public static void basketsAndOneKindOfCatalystGoInFromTheBottomUp(GameTestHelper h){
        build(h,3);
        h.runAfterDelay(10,()->{
            var owner=owner(h);
            for(int i=0;i<3;i++)owner.insert(basket());
            h.assertTrue(!owner.canInsert(basket()) && owner.layer(0).hasBasket() && owner.layer(2).hasBasket(),"One basket per layer");
            owner.insert(new ItemStack(CEItems.OXIDATION_CATALYST.get()));
            h.assertTrue(owner.layer(0).catalystStack().is(CEItems.OXIDATION_CATALYST.get()) && !owner.canInsert(new ItemStack(CEItems.IRON_CATALYST.get())),"The lowest basket fills first and every basket holds the same catalyst");
            var top=items(h,CENTRE.above(2),Direction.UP);
            h.assertTrue(top.insertItem(1,new ItemStack(CEItems.OXIDATION_CATALYST.get()),false).isEmpty() && top.extractItem(1,1,false).isEmpty(),"A funnel adds catalyst and cannot take a working one out");
            h.assertTrue(owner.takeTop().is(CEItems.OXIDATION_CATALYST.get()) && owner.takeTop().is(CEItems.OXIDATION_CATALYST.get()) && ReactionTowerBlockEntity.basket(owner.takeTop()),"The wrench takes the highest catalyst first, then a basket");
            h.succeed();
        });
    }
    @GameTest(template="test_fractionation")
    public static void eachWorkingLayerRunsOneSynthesisBatchWhenHeated(GameTestHelper h){
        build(h,3);
        h.runAfterDelay(10,()->{
            var owner=owner(h);load(owner,3,new ItemStack(CEItems.IRON_CATALYST.get()));
            var feed=fluid(h,BASE.offset(2,0,1),Direction.EAST);var product=fluid(h,BASE.offset(0,0,1),Direction.WEST);
            h.assertTrue(fluid(h,BASE.offset(2,2,1),Direction.EAST).fill(new FluidStack(CEProcessFluids.COMPRESSED_SYNTHESIS_FEED.get(),1000),SIMULATE)==0 && fluid(h,CENTRE,Direction.DOWN).fill(new FluidStack(CEProcessFluids.COMPRESSED_SYNTHESIS_FEED.get(),1000),SIMULATE)==0,"A reactor takes its feed only at the bottom layer's sides");
            h.assertTrue(feed.fill(new FluidStack(CEProcessFluids.COMPRESSED_SYNTHESIS_FEED.get(),30000),EXECUTE)==30000,"Three batches of feed fit");
            owner.process(100);h.assertTrue(owner.status().equals("heat") && owner.tank(0).getAmount()==30000,"Without heat nothing reacts: "+owner.status());
            heat(h);owner.process(99);h.assertTrue(owner.status().equals("running") && owner.tank(0).getAmount()==30000 && owner.tank(2).isEmpty(),"A batch settles only when it completes: "+owner.status());
            owner.process(1);var made=product.drain(1000000,EXECUTE);
            h.assertTrue(owner.tank(0).isEmpty() && made.is(CEProcessFluids.HOT_SYNTHESIS_MIXTURE.get()) && made.getAmount()==27000,"Three layers run three batches, drawn from another bottom side: "+made.getAmount());
            h.assertTrue(owner.layer(0).catalystStack().getDamageValue()==1 && owner.layer(2).catalystStack().getDamageValue()==1,"Each layer wears its own catalyst");
            feed.fill(new FluidStack(CEProcessFluids.COMPRESSED_SYNTHESIS_FEED.get(),15000),EXECUTE);owner.process(100);
            h.assertTrue(owner.tank(0).getAmount()==5000 && fluid(h,CENTRE,Direction.DOWN).drain(1000000,SIMULATE).getAmount()==9000 && owner.layer(2).catalystStack().getDamageValue()==2 && owner.layer(1).catalystStack().getDamageValue()==1,"Only whole batches run, the upper layer first, where the gas meets the beds");
            owner.process(100);h.assertTrue(owner.status().equals("no_feed"),"Less than one batch waits: "+owner.status());
            h.succeed();
        });
    }
    @GameTest(template="test_fractionation")
    public static void otherCatalystsRunTheirOwnTwoFeedReactions(GameTestHelper h){
        build(h,3);
        h.runAfterDelay(10,()->{
            var owner=owner(h);load(owner,2,new ItemStack(CEItems.OXIDATION_CATALYST.get()));
            var feed=fluid(h,BASE.offset(1,0,0),Direction.NORTH);
            h.assertTrue(feed.fill(new FluidStack(CEProcessFluids.COMPRESSED_SYNTHESIS_FEED.get(),1000),SIMULATE)==0,"A feed the oxidation catalyst cannot use is refused");
            h.assertTrue(feed.fill(new FluidStack(CEFluids.AMMONIA_GAS.get(),200),EXECUTE)==200 && feed.fill(new FluidStack(CEFluids.OXYGEN_GAS.get(),250),EXECUTE)==250,"Both feeds of the reaction share one port");
            owner.process(100);
            var made=fluid(h,BASE.offset(1,0,2),Direction.SOUTH).drain(1000000,EXECUTE);
            h.assertTrue(made.is(CEProcessFluids.HOT_NITRIC_OXIDE_MIXTURE.get()) && made.getAmount()==500 && owner.tank(0).isEmpty() && owner.tank(1).isEmpty(),"Two baskets oxidise two batches of ammonia: "+made.getAmount()+" "+owner.status());
            h.succeed();
        });
    }
    @GameTest(template="test_fractionation")
    public static void aFullOutletStopsTheTowerWithoutUsingFeed(GameTestHelper h){
        build(h,3);
        h.runAfterDelay(10,()->{
            heat(h);var owner=owner(h);load(owner,3,new ItemStack(CEItems.IRON_CATALYST.get()));var feed=fluid(h,BASE.offset(2,0,1),Direction.EAST);
            for(int i=0;i<40 && !owner.status().equals("output");i++){feed.fill(new FluidStack(CEProcessFluids.COMPRESSED_SYNTHESIS_FEED.get(),30000-owner.tank(0).getAmount()),EXECUTE);owner.process(100);}
            int product=owner.tank(2).getAmount(),left=owner.tank(0).getAmount();owner.process(100);
            h.assertTrue(owner.status().equals("output") && left>=10000 && owner.tank(0).getAmount()==left && owner.tank(2).getAmount()==product,"A full outlet keeps the feed: "+owner.status()+" "+left+" "+product);
            h.succeed();
        });
    }
    @GameTest(template="test_fractionation")
    public static void aSpentCatalystStopsItsLayerAndComesOutThroughTheTop(GameTestHelper h){
        build(h,3);
        h.runAfterDelay(10,()->{
            heat(h);var owner=owner(h);var worn=new ItemStack(CEItems.IRON_CATALYST.get());worn.setDamageValue(worn.getMaxDamage()-1);load(owner,1,worn);
            fluid(h,BASE.offset(2,0,1),Direction.EAST).fill(new FluidStack(CEProcessFluids.COMPRESSED_SYNTHESIS_FEED.get(),20000),EXECUTE);
            owner.process(100);owner.process(100);
            h.assertTrue(owner.tank(0).getAmount()==10000 && owner.status().equals("catalyst"),"The last batch spends the catalyst and the tower stops: "+owner.status());
            var spent=items(h,CENTRE.above(2),Direction.UP).extractItem(0,1,false);
            h.assertTrue(spent.is(CEItems.IRON_CATALYST.get()) && owner.layer(0).catalystStack().isEmpty() && owner.layer(0).hasBasket(),"A funnel takes the spent catalyst out and leaves the basket");
            h.assertTrue(worn.getMaxDamage()==CEItems.CATALYST_BATCHES && h.getLevel().getRecipeManager().byKey(CreatePlant.location("iron_catalyst_regeneration")).isPresent() && !spent.isRepairable(),"A catalyst lasts "+CEItems.CATALYST_BATCHES+" batches and comes back with metal, not by joining two worn ones");
            var saved=owner.saveWithoutMetadata(h.getLevel().registryAccess());owner.loadWithComponents(saved,h.getLevel().registryAccess());
            h.assertTrue(owner.tank(0).getAmount()==10000,"The feed survives a reload");
            h.succeed();
        });
    }
    @GameTest(template="test_fractionation")
    public static void withoutBasketsTheTowerSeparatesBehindOpenSightGlasses(GameTestHelper h){
        build(h,3);
        h.runAfterDelay(10,()->{
            var owner=owner(h);var bottom=fluid(h,BASE.offset(0,0,1),Direction.WEST);var top=fluid(h,BASE.offset(2,2,1),Direction.EAST);
            h.assertTrue(bottom.fill(new FluidStack(CEProcessFluids.COLD_SYNTHESIS_MIXTURE.get(),27000),EXECUTE)==27000,"The cold synthesis mixture goes in at the bottom");
            owner.process(59);
            h.assertTrue(owner.status().equals("running") && owner.tank(0).getAmount()==27000,"The separation settles only when the batch completes: "+owner.status());
            h.assertTrue(h.getBlockState(BASE.offset(1,1,0)).getValue(ReactionTowerBlock.WINDOW) && h.getBlockState(BASE.offset(0,2,1)).getValue(ReactionTowerBlock.WINDOW) && !h.getBlockState(BASE).getValue(ReactionTowerBlock.WINDOW),"The sight glasses in the middle of each side open while it separates");
            h.assertTrue(!owner.canInsert(basket()),"No basket goes into a separator that holds fluid");
            owner.process(1);
            h.assertTrue(owner.tank(0).isEmpty() && owner.tank(2).getAmount()==3 && owner.tank(3).getAmount()==24000,"Three layers separate three batches: "+owner.tank(2).getAmount()+" "+owner.tank(3).getAmount());
            h.assertTrue(top.drain(1,SIMULATE).is(CEProcessFluids.COMPRESSED_SYNTHESIS_FEED.get()) && bottom.drain(1000,SIMULATE).is(CEProcessFluids.LIQUID_AMMONIA.get()),"The gas leaves at the top and the liquid at the bottom");
            h.assertTrue(bottom.drain(1000,EXECUTE).getAmount()==3 && top.drain(1000000,EXECUTE).getAmount()==24000 && bottom.drain(1000,SIMULATE).isEmpty(),"Each outlet gives only its own phase");
            owner.process(5);
            h.assertTrue(!h.getBlockState(BASE.offset(1,1,0)).getValue(ReactionTowerBlock.WINDOW) && owner.canInsert(basket()),"Empty, it closes its sight glasses and takes a basket again");
            h.succeed();
        });
    }
    @GameTest(template="test_fractionation")
    public static void basketsAndCatalystGoInOnlyThroughTheOpenHatchWhichStopsTheTower(GameTestHelper h){
        build(h,3);
        h.runAfterDelay(10,()->{
            var owner=owner(h);var player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);var top=h.absolutePos(CENTRE.above(2));
            var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(top).add(0,.5,0),Direction.UP,top,false);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,basket());
            h.getLevel().getBlockState(top).useItemOn(player.getMainHandItem(),h.getLevel(),player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
            h.assertTrue(!owner.hatchOpen() && !owner.hasBaskets() && player.getMainHandItem().getCount()==1,"A shut hatch keeps baskets out");
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            h.getLevel().getBlockState(top).useWithoutItem(h.getLevel(),player,hit);
            h.assertTrue(owner.hatchOpen(),"An empty hand on the roof opens the hatch");
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,basket());
            h.getLevel().getBlockState(top).useItemOn(player.getMainHandItem(),h.getLevel(),player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
            h.assertTrue(owner.layer(0).hasBasket() && player.getMainHandItem().isEmpty(),"Through the open hatch the basket drops into the lowest layer");
            for(int i=1;i<3;i++)owner.insert(basket());
            var feed=fluid(h,BASE.offset(2,0,1),Direction.EAST);
            h.assertTrue(feed.fill(new FluidStack(CEOstwaldFluids.NITRIC_OXIDE.get(),100),EXECUTE)==100 && feed.fill(new FluidStack(CEFluids.OXYGEN_GAS.get(),50),EXECUTE)==50,"Baskets without catalyst take the feed of a reaction that needs none");
            owner.process(200);
            h.assertTrue(owner.status().equals("hatch") && owner.tank(0).getAmount()==100,"Nothing reacts while the hatch is open: "+owner.status());
            owner.toggleHatch();owner.process(200);
            h.assertTrue(owner.tank(0).isEmpty() && owner.tank(1).isEmpty() && fluid(h,CENTRE,Direction.DOWN).drain(1000,SIMULATE).is(CEOstwaldFluids.NITROGEN_DIOXIDE.get()),"Shut, the empty baskets oxidise nitric oxide: "+owner.status()+" "+owner.tank(2).getFluid());
            feed.fill(new FluidStack(CEOstwaldFluids.NITRIC_OXIDE.get(),100),EXECUTE);owner.toggleHatch();
            h.assertTrue(owner.tank(0).isEmpty() && owner.tank(2).isEmpty(),"Opening the hatch lets the gas out");
            h.succeed();
        });
    }
    /** Runs the synthesis long enough for the gauges to settle, keeping the feed topped up and the product drawn off; returns the temperatures from the top layer down. */
    private static float[] settle(ReactionTowerBlockEntity owner,IFluidHandler feed,IFluidHandler product){
        for(int i=0;i<12;i++){feed.fill(new FluidStack(CEProcessFluids.COMPRESSED_SYNTHESIS_FEED.get(),30000),EXECUTE);owner.process(100);product.drain(1000000,EXECUTE);}
        return new float[]{owner.layerTemperature(2),owner.layerTemperature(1),owner.layerTemperature(0)};
    }
    @GameTest(template="test_fractionation")
    public static void theGaugesShowTheLoopPressureAndTheHeatMovingDownAsTheCatalystWears(GameTestHelper h){
        build(h,3);
        h.runAfterDelay(10,()->{
            heat(h);var owner=owner(h);load(owner,3,new ItemStack(CEItems.IRON_CATALYST.get()));
            var feed=fluid(h,BASE.offset(2,0,1),Direction.EAST);var product=fluid(h,BASE.offset(0,0,1),Direction.WEST);
            var fresh=settle(owner,feed,product);
            h.assertTrue(fresh[0]>fresh[1] && fresh[1]>fresh[2] && fresh[2]>=380,"Fresh catalyst runs hottest in the upper stage: "+java.util.Arrays.toString(fresh));
            feed.fill(new FluidStack(CEProcessFluids.COMPRESSED_SYNTHESIS_FEED.get(),ReactionTowerBlockEntity.VOLUME_PER_LAYER*64*3*4/5),EXECUTE);
            for(int i=0;i<6;i++)owner.process(5);
            h.assertTrue(owner.pressure()>=15 && owner.pressure()<=25,"A well-filled loop reads 15 to 25 MPa: "+owner.pressure());
            for(int i=0;i<3;i++)owner.takeTop();
            var worn=new ItemStack(CEItems.IRON_CATALYST.get());worn.setDamageValue(worn.getMaxDamage()/2);
            for(int i=0;i<3;i++)owner.insert(worn.copy());
            var aged=settle(owner,feed,product);
            h.assertTrue(aged[0]<fresh[0]-20 && aged[2]>fresh[2]+5 && aged[1]>fresh[1],"Worn catalyst leaves the upper stage cooler and heats the lower ones: "+java.util.Arrays.toString(fresh)+" -> "+java.util.Arrays.toString(aged));
            h.succeed();
        });
    }
}
