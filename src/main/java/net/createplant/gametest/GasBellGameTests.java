package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.createplant.CreatePlant;
import net.createplant.block.entity.GasHolderBlockEntity;
import net.createplant.chemistry.WasteGas;
import net.createplant.contraptions.GasHolderSleeveContraption;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CEGasBell;
import net.createplant.registry.CEGasPipes;
import net.createplant.thermal.FluidMaterials;
import net.createplant.thermal.FluidThermal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public final class GasBellGameTests {
    public static final BlockPos BASE=new BlockPos(5,5,5);
    public static GasHolderBlockEntity build(net.minecraft.server.level.ServerLevel level,BlockPos at,int width,int height) {
        for (int x=0;x<width;x++) for (int z=0;z<width;z++) level.setBlock(at.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);
        for (int y=0;y<height;y++) for (int x=0;x<width;x++) for (int z=0;z<width;z++) level.setBlock(at.offset(x,y,z),CEGasBell.TANK.get().defaultBlockState(),3);
        var owner=((GasHolderBlockEntity)level.getBlockEntity(at)).getControllerBE();ConnectivityHandler.formMulti(owner);owner.tick();return owner.getControllerBE();
    }
    private static GasHolderBlockEntity holder(GameTestHelper h) { return build(h.getLevel(),h.absolutePos(BASE),3,3); }
    private static IFluidHandler port(GameTestHelper h,BlockPos at) { return h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,at,Direction.DOWN); }
    private static FluidStack hydrogen(int amount) { return FluidThermal.at(new FluidStack(CEFluids.HYDROGEN_GAS.get(),amount),293.15); }
    private static FluidStack nitrogen(int amount) { return FluidThermal.at(new FluidStack(CEFluids.NITROGEN_GAS.get(),amount),310); }

    @GameTest(template="test_rbmk",timeoutTicks=120)
    public static void nativeTankWidthsJoinWithoutRailsAnchorsOrGlue(GameTestHelper h) {
        for (int width=1;width<=3;width++) {
            var be=build(h.getLevel(),h.absolutePos(new BlockPos(1+(width-1)*6,5,3)),width,2);
            h.assertTrue(be.getWidth()==width && be.getHeight()==2 && be.capacity()==width*width*2*8000,"Native tank dimensions and finite capacity: "+width+" / "+be.getWidth()+" / "+be.capacity());
            for (int i=0;i<3;i++) h.assertTrue(be.sleeve(i)!=null && be.sleeve(i).getContraption() instanceof GasHolderSleeveContraption
                && be.sleeve(i).getContraption().getSimplifiedEntityColliders().size>0,"Every sleeve has a native collision body");
        }
        h.assertTrue(!BuiltInRegistries.BLOCK.containsKey(CreatePlant.location("gas_holder_anchor")) && !BuiltInRegistries.BLOCK.containsKey(CreatePlant.location("gas_holder_guide_rail")),"The obsolete anchor and rail are not registered");h.succeed();
    }
    @GameTest(template="test_rbmk",timeoutTicks=160)
    public static void theInnerBellRisesFirstThenTheMiddleThenTheOuter(GameTestHelper h) {
        var be=holder(h);var inlet=port(h,be.getBlockPos());int third=be.capacity()/3;
        h.assertTrue(inlet.fill(hydrogen(third),SIMULATE)==third && be.gas().isEmpty() && be.offset()==0,"Simulation moves no gas or metal");
        inlet.fill(hydrogen(third),EXECUTE);
        h.runAfterDelay(30,()->{
            h.assertTrue(Math.abs(be.offset()-2.5)<1e-6 && Math.abs(be.geometry().offset(be.offset(),0))<1e-6
                && Math.abs(be.geometry().offset(be.offset(),1))<1e-6 && Math.abs(be.geometry().offset(be.offset(),2)-2.5)<1e-6,"At one third only the inner bell lifts");inlet.fill(hydrogen(third),EXECUTE);
        });
        h.runAfterDelay(60,()->{
            h.assertTrue(Math.abs(be.offset()-5)<1e-6 && be.geometry().offset(be.offset(),0)==0 && Math.abs(be.geometry().offset(be.offset(),1)-2.5)<1e-6,"The middle bell follows the fully extended inner one");inlet.fill(hydrogen(third),EXECUTE);
        });
        h.runAfterDelay(90,()->{
            h.assertTrue(Math.abs(be.offset()-7.5)<1e-6 && Math.abs(be.geometry().offset(be.offset(),0)-2.5)<1e-6,"At full charge all three strokes are extended");
            h.assertTrue(inlet.drain(be.capacity(),EXECUTE).getAmount()==216000,"All 216000 mB are recoverable exactly once");
        });
        h.runAfterDelay(155,()->{h.assertTrue(be.offset()==0 && be.gas().isEmpty(),"The three bells retract completely after pumping out");h.succeed();});
    }
    @GameTest(template="test_rbmk",timeoutTicks=100)
    public static void theNativeTankItemPlacesACompleteThreeByThreeLayer(GameTestHelper h) {
        var be=build(h.getLevel(),h.absolutePos(BASE),3,2);var at=be.getBlockPos().offset(1,1,1);
        var player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);player.setShiftKeyDown(false);
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(CEGasBell.TANK.get(),9));
        var result=player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(at).add(0,.5,0),Direction.UP,at,false)));
        h.assertTrue(result.consumesAction(),"The actual tank item accepts the top placement");
        h.runAfterDelay(25,()->{
            var owner=((GasHolderBlockEntity)h.getLevel().getBlockEntity(be.getBlockPos())).getControllerBE();
            h.assertTrue(owner.getWidth()==3 && owner.getHeight()==3 && player.getMainHandItem().isEmpty(),"Nine survival items place exactly one complete 3x3 layer: "+owner.getWidth()+" / "+owner.getHeight()+" / "+player.getMainHandItem());h.succeed();
        });
    }
    @GameTest(template="test_rbmk",timeoutTicks=220)
    public static void fourByFourLayersAndTelescopicStrokesWork(GameTestHelper h) { wideHolder(h,4); }
    @GameTest(template="test_rbmk",timeoutTicks=220)
    public static void fiveByFiveLayersAndTelescopicStrokesWork(GameTestHelper h) { wideHolder(h,5); }
    @GameTest(template="test_rbmk",timeoutTicks=220)
    public static void sixBySixLayersAndTelescopicStrokesWork(GameTestHelper h) { wideHolder(h,6); }
    @GameTest(template="test_rbmk",timeoutTicks=220)
    public static void sevenBySevenLayersAndTelescopicStrokesWork(GameTestHelper h) { wideHolder(h,7); }
    private static void wideHolder(GameTestHelper h,int width) {
        var initial=build(h.getLevel(),h.absolutePos(BASE),width,2);var at=initial.getBlockPos();
        h.assertTrue(initial.getWidth()==width && initial.getHeight()==2,"Every requested square width forms with native connectivity: "+width);
        var player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);player.setShiftKeyDown(false);
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(CEGasBell.TANK.get(),width*width));
        var top=at.offset(width/2,1,width/2);
        h.assertTrue(player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(top).add(0,.5,0),Direction.UP,top,false))).consumesAction(),"The actual tank item accepts a complete wide layer");
        h.runAfterDelay(25,()->{
            var be=((GasHolderBlockEntity)h.getLevel().getBlockEntity(at)).getControllerBE();
            h.assertTrue(be.getWidth()==width && be.getHeight()==3 && be.capacity()==width*width*24000 && player.getMainHandItem().isEmpty(),"Exactly one wide layer is placed and paid for");
            be.toggleWindows();be.toggleWindows();int low=(width-1)/2,high=width/2;
            for(int y=0;y<3;y++)for(int x=0;x<width;x++)for(int z=0;z<width;z++) {
                var member=(GasHolderBlockEntity)h.getLevel().getBlockEntity(at.offset(x,y,z));
                boolean window=(x==0 || x==width-1) && z>=low && z<=high || (z==0 || z==width-1) && x>=low && x<=high;
                h.assertTrue(member.getControllerBE()==be && member.getBlockState().getValue(com.simibubi.create.content.fluids.tank.FluidTankBlock.SHAPE)==(window ? com.simibubi.create.content.fluids.tank.FluidTankBlock.Shape.WINDOW : com.simibubi.create.content.fluids.tank.FluidTankBlock.Shape.PLAIN),"Every member shares the owner and toggles symmetric windows");
            }
            var inlet=port(h,at.offset(width-1,0,width-1));int charge=be.capacity()/3;
            h.assertTrue(inlet.fill(nitrogen(charge),SIMULATE)==charge && be.gas().isEmpty(),"Simulation through the far member is read-only");
            h.assertTrue(inlet.fill(nitrogen(charge),EXECUTE)==charge,"The far member accepts the first finite charge");
        });
        for(int stage=1;stage<=3;stage++) {
            int step=stage;
            h.runAfterDelay(25+stage*30,()->{
                var be=((GasHolderBlockEntity)h.getLevel().getBlockEntity(at)).getControllerBE();
                h.assertTrue(Math.abs(be.offset()-step*2.5)<1e-6 && be.gas().getFluidAmount()==step*width*width*8000,"Wide holders complete the same three strokes: "+width+" / "+step);
                for(int i=0;i<3;i++)h.assertTrue(be.sleeve(i).isAlive() && be.sleeve(i).getContraption() instanceof GasHolderSleeveContraption sleeve && sleeve.matches(width,3)
                    && Math.abs(be.sleeve(i).getY()-at.getY()-.125-be.geometry().offset(be.offset(),i))<1e-6,"Every physical sleeve follows the wide geometry");
                var inlet=port(h,at.offset(width-1,0,width-1));
                if(step<3)h.assertTrue(inlet.fill(nitrogen(be.capacity()/3),EXECUTE)==be.capacity()/3,"The next charge is conserved");
                else h.assertTrue(inlet.fill(nitrogen(1000),EXECUTE)==0 && inlet.drain(Integer.MAX_VALUE,EXECUTE).getAmount()==be.capacity(),"A full wide holder refuses overflow and returns the complete charge once");
            });
        }
        h.runAfterDelay(185,()->{
            var be=((GasHolderBlockEntity)h.getLevel().getBlockEntity(at)).getControllerBE();
            h.assertTrue(be.gas().isEmpty() && be.offset()==0 && port(h,at).drain(1000,EXECUTE).isEmpty(),"A wide holder retracts completely without duplicating gas");h.succeed();
        });
    }
    @GameTest(template="test_rbmk",timeoutTicks=430)
    public static void maximumWideHolderReloadsItsFullSizeAndFiniteGas(GameTestHelper h) {
        var be=build(h.getLevel(),h.absolutePos(BASE),7,8);var at=be.getBlockPos();var level=h.getLevel();
        h.assertTrue(be.getWidth()==7 && be.getHeight()==8 && be.capacity()==3136000,"The 7x7x8 maximum forms with its exact finite capacity");
        h.assertTrue(port(h,at.offset(6,0,6)).fill(nitrogen(3136000),EXECUTE)==3136000,"The maximum holder accepts its complete charge");
        h.runAfterDelay(190,()->{
            h.assertTrue(Math.abs(be.offset()-22.5)<1e-6,"The maximum height extends all three 7.5 m strokes");
            CompoundTag[] entities=new CompoundTag[3];java.util.UUID[] ids=new java.util.UUID[3];
            for(int i=0;i<3;i++){entities[i]=new CompoundTag();be.sleeve(i).save(entities[i]);ids[i]=be.sleeve(i).getUUID();be.sleeve(i).discard();}
            var saved=be.saveWithoutMetadata(level.registryAccess());level.removeBlockEntity(at);var loaded=new GasHolderBlockEntity(at,level.getBlockState(at));loaded.loadWithComponents(saved,level.registryAccess());level.setBlockEntity(loaded);
            for(var tag:entities)level.addFreshEntity(EntityType.loadEntityRecursive(tag,level,e->e));
            h.runAfterDelay(15,()->{
                h.assertTrue(loaded.getWidth()==7 && loaded.getHeight()==8 && loaded.capacity()==3136000 && loaded.gas().getFluidAmount()==3136000,"NBT preserves maximum dimensions and all gas");
                for(int i=0;i<3;i++)h.assertTrue(loaded.sleeve(i)!=null && loaded.sleeve(i).getUUID().equals(ids[i]) && loaded.sleeve(i).getContraption() instanceof GasHolderSleeveContraption sleeve
                    && sleeve.matches(7,8) && sleeve.bounds.maxX==7 && sleeve.bounds.maxZ==7,"Reload retains seven-block sleeve collisions and the original entity IDs");
                h.assertTrue(port(h,at.offset(6,0,6)).drain(Integer.MAX_VALUE,EXECUTE).getAmount()==3136000,"All saved gas is recoverable once");
                h.runAfterDelay(190,()->{h.assertTrue(loaded.gas().isEmpty() && loaded.offset()==0,"The saved maximum holder retracts completely");h.succeed();});
            });
        });
    }
    @GameTest(template="test_rbmk",timeoutTicks=80)
    public static void eightWideDoesNotBecomeOneOversizedHolder(GameTestHelper h) {
        var be=build(h.getLevel(),h.absolutePos(BASE),8,2);
        h.runAfterDelay(25,()->{
            for(int x=0;x<8;x++)for(int z=0;z<8;z++) {
                var owner=((GasHolderBlockEntity)h.getLevel().getBlockEntity(be.getBlockPos().offset(x,0,z))).getControllerBE();
                h.assertTrue(owner!=null && owner.getWidth()<=7,"Native connectivity splits widths above the supported limit");
            }
            h.succeed();
        });
    }
    @GameTest(template="test_rbmk",timeoutTicks=100)
    public static void shrinkingRetiresACachedUpperFaceHandler(GameTestHelper h) {
        var be=holder(h);var at=be.getBlockPos();var member=at.above();
        var cached=h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,member,Direction.UP);h.assertTrue(cached!=null,"An internal top-facing handler initially exists");
        for(int x=0;x<3;x++)for(int z=0;z<3;z++)h.getLevel().removeBlock(at.offset(x,2,z),false);
        h.runAfterDelay(25,()->{
            var owner=((GasHolderBlockEntity)h.getLevel().getBlockEntity(at)).getControllerBE();
            h.assertTrue(owner.getHeight()==2 && port(h,at).fill(nitrogen(1000),EXECUTE)==1000,"The shorter tank is usable from below");
            h.assertTrue(h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,member,Direction.UP)==null && cached.fill(nitrogen(1000),EXECUTE)==0
                && cached.drain(1000,EXECUTE).isEmpty(),"A cached handler cannot bypass the newly sealed top face");h.succeed();
        });
    }
    @GameTest(template="test_rbmk")
    public static void aSingleTankLayerDoesNotOperate(GameTestHelper h) {
        var be=build(h.getLevel(),h.absolutePos(BASE),3,1);
        h.assertTrue(!be.formed() && be.status().equals("height") && port(h,be.getBlockPos()).fill(hydrogen(1000),EXECUTE)==0,"At least two structural layers are required");h.succeed();
    }
    @GameTest(template="test_rbmk")
    public static void incompatibleGasIsRefusedWithoutContamination(GameTestHelper h) {
        var be=holder(h);var inlet=port(h,be.getBlockPos());inlet.fill(hydrogen(10000),EXECUTE);
        h.assertTrue(inlet.fill(nitrogen(4000),SIMULATE)==0 && inlet.fill(nitrogen(4000),EXECUTE)==0,"Different gas is refused without consuming it");
        h.assertTrue(be.gas().getFluid().is(CEFluids.HYDROGEN_GAS.get()) && be.gas().getFluidAmount()==10000,"Stored gas is unchanged");h.succeed();
    }
    @GameTest(template="test_rbmk")
    public static void incompatibleOxygenCannotExplodeTheHolder(GameTestHelper h) {
        var be=holder(h);var inlet=port(h,be.getBlockPos());inlet.fill(hydrogen(10000),EXECUTE);var roof=be.sleeve(2);var oxygen=new FluidStack(CEFluids.OXYGEN_GAS.get(),1000);
        h.assertTrue(inlet.fill(oxygen,SIMULATE)==0 && inlet.fill(oxygen,EXECUTE)==0,"Oxygen cannot mix into stored hydrogen");
        h.assertTrue(roof.isAlive() && be.gas().getFluidAmount()==10000,"Refusal cannot destroy the holder");h.succeed();
    }
    @GameTest(template="test_rbmk",timeoutTicks=100)
    public static void gasRefusalRemainsSafeAfterReload(GameTestHelper h) {
        var be=holder(h);var inlet=port(h,be.getBlockPos());inlet.fill(hydrogen(10000),EXECUTE);inlet.fill(nitrogen(5000),EXECUTE);
        var saved=be.saveWithoutMetadata(h.getLevel().registryAccess());be.loadWithComponents(saved,h.getLevel().registryAccess());
        h.runAfterDelay(5,()->{h.assertTrue(be.gas().getFluid().is(CEFluids.HYDROGEN_GAS.get()) && be.gas().getFluidAmount()==10000,"Refused inflow and reload preserve the original gas");h.assertTrue(inlet.fill(new FluidStack(CEFluids.OXYGEN_GAS.get(),1000),EXECUTE)==0 && be.sleeve(2).isAlive(),"Refusal stays safe after reload");h.succeed();});
    }
    @GameTest(template="test_rbmk")
    public static void gasTypeChangesOnlyAfterTheHolderIsEmpty(GameTestHelper h) {
        var be=holder(h);var inlet=port(h,be.getBlockPos());inlet.fill(hydrogen(1000),EXECUTE);inlet.drain(999,EXECUTE);
        h.assertTrue(inlet.fill(nitrogen(1000),EXECUTE)==0 && be.gas().getFluidAmount()==1,"Different gas is refused while any previous gas remains");
        inlet.drain(1,EXECUTE);h.assertTrue(inlet.fill(nitrogen(1000),EXECUTE)==1000,"An empty holder accepts the next gas");h.succeed();
    }
    @GameTest(template="test_rbmk")
    public static void nativeShiftWrenchPurgesThroughAnyMember(GameTestHelper h) {
        var be=holder(h);port(h,be.getBlockPos()).fill(nitrogen(1000),EXECUTE);var at=be.getBlockPos().offset(2,1,0);
        var player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);player.setItemInHand(InteractionHand.MAIN_HAND,AllItems.WRENCH.asStack());player.setShiftKeyDown(true);
        player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(at),Direction.NORTH,at,false)));player.setShiftKeyDown(false);
        h.assertTrue(h.getLevel().getBlockState(at).is(CEGasBell.TANK.get()) && be.gas().isEmpty(),"A member purges the shared gas instead of being picked up while charged");h.succeed();
    }
    @GameTest(template="test_rbmk",timeoutTicks=160)
    public static void anObstacleStopsTheRoofAndRemovalRestartsIt(GameTestHelper h) {
        var be=holder(h);var at=be.getBlockPos();var obstruction=at.offset(1,5,1);h.getLevel().setBlock(obstruction,Blocks.STONE.defaultBlockState(),3);
        port(h,at).fill(hydrogen(be.capacity()/2),EXECUTE);
        h.runAfterDelay(40,()->{
            h.assertTrue(be.status().equals("blocked") && be.offset()<2 && be.gas().getFluidAmount()==108000,"A real block stops expansion without deleting gas: "+be.offset()+" / "+be.status());
            h.assertTrue(port(h,at).fill(hydrogen(1000),EXECUTE)==0,"The obstructed holder refuses more gas");h.getLevel().removeBlock(obstruction,false);
        });
        h.runAfterDelay(95,()->{h.assertTrue(Math.abs(be.offset()-3.75)<1e-6 && be.status().equals("ready"),"The same three bells resume after clearing the path");h.succeed();});
    }
    @GameTest(template="test_rbmk",timeoutTicks=180)
    public static void aNativeMechanicalPumpKeepsIncompatibleGasAtTheSource(GameTestHelper h) {
        var be=holder(h);var at=be.getBlockPos();var level=h.getLevel();port(h,at).fill(hydrogen(10000),EXECUTE);
        level.setBlock(at.below(),AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(BlockStateProperties.FACING,Direction.UP),3);level.setBlock(at.below(2),net.createplant.registry.CETransport.GAS_RECEIVER.get().defaultBlockState().setValue(net.createplant.block.PulsationTubeBlock.AXIS,Direction.Axis.Y),3);
        var source=((FluidTankBlockEntity)level.getBlockEntity(at.below(2))).getTankInventory();source.fill(nitrogen(16000),EXECUTE);
        h.onEachTick(()->((KineticBlockEntity)level.getBlockEntity(at.below())).setSpeed(32));h.runAfterDelay(3,()->((KineticBlockEntity)level.getBlockEntity(at.below())).onSpeedChanged(0));
        h.runAfterDelay(120,()->{h.assertTrue(source.getFluidAmount()==16000 && be.gas().getFluidAmount()==10000,"Refused inflow stays at the source without loss");h.succeed();});
    }
    @GameTest(template="test_rbmk",timeoutTicks=80)
    public static void anEmptyObstructedHolderDoesNotLatchAfterPurging(GameTestHelper h) {
        var be=holder(h);var at=be.getBlockPos();var obstruction=at.offset(1,3,1);var inlet=port(h,at);
        h.getLevel().setBlock(obstruction,Blocks.STONE.defaultBlockState(),3);inlet.fill(nitrogen(4000),EXECUTE);
        h.runAfterDelay(8,()->{
            h.assertTrue(be.status().equals("blocked") && be.offset()==0,"The initial roof stroke is obstructed");
            h.assertTrue(inlet.drain(4000,EXECUTE).getAmount()==4000,"The stationary blocked holder can be emptied");h.getLevel().removeBlock(obstruction,false);
        });
        h.runAfterDelay(20,()->{h.assertTrue(be.status().equals("ready") && inlet.fill(nitrogen(1000),EXECUTE)==1000,"Clearing the obstruction resumes an empty holder without a stuck interlock");h.succeed();});
    }
    @GameTest(template="test_rbmk",timeoutTicks=160)
    public static void allThreeNativeCollisionBodiesSurviveNbtExactlyOnce(GameTestHelper h) {
        var be=holder(h);var at=be.getBlockPos();var level=h.getLevel();port(h,at).fill(nitrogen(108000),EXECUTE);
        h.runAfterDelay(40,()->{
            CompoundTag[] entities=new CompoundTag[3];java.util.UUID[] ids=new java.util.UUID[3];
            for(int i=0;i<3;i++) {entities[i]=new CompoundTag();be.sleeve(i).save(entities[i]);ids[i]=be.sleeve(i).getUUID();be.sleeve(i).discard();}
            var data=be.saveWithoutMetadata(level.registryAccess());level.removeBlockEntity(at);var loaded=new GasHolderBlockEntity(at,level.getBlockState(at));loaded.loadWithComponents(data,level.registryAccess());level.setBlockEntity(loaded);
            for (var tag:entities) level.addFreshEntity(EntityType.loadEntityRecursive(tag,level,e->e));
            h.runAfterDelay(15,()->{
                for(int i=0;i<3;i++)h.assertTrue(loaded.sleeve(i)!=null && loaded.sleeve(i).getUUID().equals(ids[i]),"Each saved sleeve reattaches by its original UUID");
                long count=level.getEntitiesOfClass(ControlledContraptionEntity.class,new AABB(at).inflate(10)).stream().filter(e->e.getContraption() instanceof GasHolderSleeveContraption).count();
                h.assertTrue(count==3 && loaded.gas().getFluidAmount()==108000 && Math.abs(loaded.offset()-3.75)<1e-6,"Reload restores exactly three bodies and the finite gas");h.succeed();
            });
        });
    }
    @GameTest(template="test_rbmk",timeoutTicks=150)
    public static void addingATankLayerPreservesTheGasWithoutCloningBodies(GameTestHelper h) {
        var be=build(h.getLevel(),h.absolutePos(BASE),1,2);var at=be.getBlockPos();port(h,at).fill(nitrogen(8000),EXECUTE);
        h.runAfterDelay(30,()->h.getLevel().setBlock(at.above(2),CEGasBell.TANK.get().defaultBlockState(),3));
        h.runAfterDelay(95,()->{
            var owner=((GasHolderBlockEntity)h.getLevel().getBlockEntity(at)).getControllerBE();
            h.assertTrue(owner.getHeight()==3 && owner.capacity()==24000 && owner.gas().getFluidAmount()==8000,"Native layer expansion preserves all gas and changes only capacity");
            long count=h.getLevel().getEntitiesOfClass(ControlledContraptionEntity.class,new AABB(at).inflate(9)).stream().filter(e->e.getContraption() instanceof GasHolderSleeveContraption).count();
            h.assertTrue(count==3 && Math.abs(owner.offset()-2.5)<1e-6,"Resizing recreates exactly three correctly positioned bodies: "+count+" / "+owner.offset());h.succeed();
        });
    }
    @GameTest(template="test_rbmk",timeoutTicks=140)
    public static void theMovingRoofCarriesARealEntity(GameTestHelper h) {
        var be=holder(h);var at=be.getBlockPos();var pig=EntityType.PIG.create(h.getLevel());pig.setNoAi(true);pig.setPos(at.getX()+1.5,at.getY()+3.4,at.getZ()+1.5);h.getLevel().addFreshEntity(pig);
        h.runAfterDelay(8,()->port(h,at).fill(nitrogen(be.capacity()/2),EXECUTE));
        h.runAfterDelay(80,()->{
            h.assertTrue(pig.isAlive() && pig.getY()>at.getY()+6.7,"Create's native roof collision lifts an actual entity: "+pig.getY()+" base="+at.getY());h.succeed();
        });
    }
    @GameTest(template="test_rbmk")
    public static void cachedPortsAndRealRecipesCannotCreateFreeGasOrWater(GameTestHelper h) {
        var be=holder(h);var at=be.getBlockPos();var inlet=port(h,at);int capacity=be.capacity();h.assertTrue(inlet.fill(nitrogen(capacity+1000),EXECUTE)==capacity,"No generic compression multiplier");
        h.getLevel().removeBlock(at.east(),false);h.assertTrue(inlet.fill(nitrogen(1000),EXECUTE)==0 && inlet.drain(1000,EXECUTE).isEmpty(),"A broken structure retires its cached port");
        var grid=java.util.List.of(ItemStack.EMPTY,AllItems.IRON_SHEET.asStack(),ItemStack.EMPTY,AllItems.ZINC_INGOT.asStack(),new ItemStack(Items.WATER_BUCKET),AllItems.ZINC_INGOT.asStack(),ItemStack.EMPTY,AllBlocks.FLUID_TANK.asStack(),ItemStack.EMPTY);
        var input=net.minecraft.world.item.crafting.CraftingInput.of(3,3,grid);var recipe=h.getLevel().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING,input,h.getLevel()).orElseThrow().value();
        h.assertTrue(recipe.assemble(input,h.getLevel().registryAccess()).is(CEGasBell.TANK.get().asItem()) && recipe.getRemainingItems(input).get(4).is(Items.BUCKET),"The water seal consumes a water bucket and returns the empty bucket");h.succeed();
    }
}
