package net.createplant.gametest;

import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import net.createplant.CreatePlant;
import net.createplant.block.*;
import net.createplant.block.entity.*;
import net.createplant.chemistry.DistillationProfile;
import net.createplant.registry.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.*;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public class DistillationGameTests {
    public static FractionatingColumnBlockEntity build(ServerLevel level,BlockPos origin,int width,int height) {
        for (int y=0;y<height;y++) for (int x=0;x<width;x++) for (int z=0;z<width;z++) level.setBlock(origin.offset(x,y,z),CEDistillation.COLUMN.get().defaultBlockState(),3);
        var last=(FractionatingColumnBlockEntity)level.getBlockEntity(origin.offset(width-1,height-1,width-1));last.rebuild();
        return last.getControllerBE();
    }
    private static IFluidHandler port(FractionatingColumnBlockEntity owner,int x,int y,int z,Direction side) {
        return ((FractionatingColumnBlockEntity)owner.getLevel().getBlockEntity(owner.getBlockPos().offset(x,y,z))).port(side);
    }
    /** The inlet on the east face: layer 3 takes compressed, cooled air. */
    public static IFluidHandler inlet(FractionatingColumnBlockEntity owner,int layer) { return port(owner,owner.getWidth()-1,layer,(owner.getWidth()-1)/2,Direction.EAST); }
    /** An outlet on the north face: layer 1 gives liquid oxygen, layer 10 crude argon and layer 14 nitrogen. */
    public static IFluidHandler outlet(FractionatingColumnBlockEntity owner,int layer) { return port(owner,(owner.getWidth()-1)/2,layer,0,Direction.NORTH); }
    public static void run(FractionatingColumnBlockEntity owner,int ticks) { for (int t=0;t<ticks;t++) owner.tick(); }
    /** Fills compressed, cooled air as a pump from an 8 bar vessel would. */
    private static int fill(IFluidHandler feed,int amount,IFluidHandler.FluidAction action) {
        try {
            return feed.fill(net.createplant.thermal.FluidThermal.at(new FluidStack(net.createplant.registry.CEProcessFluids.COMPRESSED_AIR.get(),amount),300),action);
        } finally { }
    }
    @GameTest(template="test_fractionation",timeoutTicks=100)
    public static void onlyTwoByTwoCrossSectionsFormWithTheSpecifiedPorts(GameTestHelper h) {
        for (int w=1;w<=3;w++) {
            BlockPos origin=h.absolutePos(new BlockPos(w*8,2,4));var owner=build(h.getLevel(),origin,w,13);
            h.assertTrue(owner.ready()==(w==2) && (w!=2 || fill(inlet(owner,2),1000,EXECUTE)==0),"Thirteen layers can hold chemical fractions but must reject air in a "+w+"x"+w+" column");
            for (int x=0;x<w;x++) for (int z=0;z<w;z++) h.getLevel().setBlock(origin.offset(x,13,z),CEDistillation.COLUMN.get().defaultBlockState(),3);
            ((FractionatingColumnBlockEntity)h.getLevel().getBlockEntity(origin.offset(w-1,13,w-1))).rebuild();
            owner=((FractionatingColumnBlockEntity)h.getLevel().getBlockEntity(origin)).getControllerBE();
            if(w!=2) {
                h.assertTrue(!owner.ready() && owner.status().equals("width"),"Only 2×2 may operate: "+w);
                for(int y=0;y<14;y++)for(int x=0;x<w;x++)for(int z=0;z<w;z++)for(var side:Direction.values())
                    h.assertTrue(port(owner,x,y,z,side)==null,"An unsupported cross-section exposes no fluid ports");
                run(owner,200);h.assertTrue(!owner.ready(),"Ticking cannot reactivate an unsupported cross-section");continue;
            }
            h.assertTrue(owner.getWidth()==w && owner.getHeight()==14 && owner.ready(),"The fourteenth layer forms the "+w+"x"+w+" column: "+owner.status());
            h.assertTrue(owner.batchCapacity()==w*w,"Area determines simultaneous batches");
            BlockPos air=origin.offset(w-1,2,(w-1)/2);
            h.assertTrue(FluidPipeBlock.canConnectTo(h.getLevel(),air,h.getLevel().getBlockState(air),Direction.WEST)
                && !FluidPipeBlock.canConnectTo(h.getLevel(),air,h.getLevel().getBlockState(air),Direction.EAST)
                && !FluidPipeBlock.canConnectTo(h.getLevel(),air,h.getLevel().getBlockState(air),Direction.DOWN),"Create pipes recognize the east inlet and reject the other faces of its layer");
            for (int layer=0;layer<14;layer++) {
                boolean in=layer==2,out=layer==0 || layer==9 || layer==13;
                h.assertTrue((inlet(owner,layer)!=null)==in && (outlet(owner,layer)!=null)==out,"Layer "+(layer+1)+" has the expected ports");
                h.assertTrue(port(owner,(w-1)/2,layer,w-1,Direction.SOUTH)==null && port(owner,0,layer,(w-1)/2,Direction.WEST)==null && port(owner,(w-1)/2,layer,(w-1)/2,Direction.UP)==null,"No other face exposes a port");
            }
            run(owner,20);long scans=owner.structureScans();run(owner,200);h.assertTrue(owner.structureScans()==scans,"Waiting ticks reuse the structure cache");
        }
        h.succeed();
    }
    @GameTest(template="test_fractionation",timeoutTicks=150)
    public static void anExtraLayerStopsTheColumnAndRemovingItRecovers(GameTestHelper h) {
        int w=2;
        BlockPos origin=h.absolutePos(new BlockPos(w*8,2,4));var owner=build(h.getLevel(),origin,w,14);
        h.assertTrue(owner.ready(),"A complete column is valid for width "+w);
        IFluidHandler stale=inlet(owner,2);
        for (int x=0;x<w;x++) for (int z=0;z<w;z++) h.getLevel().setBlock(origin.offset(x,14,z),CEDistillation.COLUMN.get().defaultBlockState(),3);
        ((FractionatingColumnBlockEntity)h.getLevel().getBlockEntity(origin.offset(w-1,14,w-1))).rebuild();
        h.assertTrue(!owner.ready() && fill(stale,100,EXECUTE)==0,"An excessive connected layer stops the column and cached ports");
        for (int x=0;x<w;x++) for (int z=0;z<w;z++) h.getLevel().setBlock(origin.offset(x,14,z),Blocks.AIR.defaultBlockState(),3);
        ((FractionatingColumnBlockEntity)h.getLevel().getBlockEntity(origin.offset(w-1,13,w-1))).rebuild();
        var restored=((FractionatingColumnBlockEntity)h.getLevel().getBlockEntity(origin)).getControllerBE();h.assertTrue(restored.ready() && restored.getHeight()==14,"Removing the excessive layer restores the column");
        h.succeed();
    }
    @GameTest(template="test_fractionation",timeoutTicks=600)
    public static void airSplitsIntoTheAirCompositionAtThreeOutletsAndKeepsItsMass(GameTestHelper h) {
        var owner=build(h.getLevel(),h.absolutePos(new BlockPos(5,2,5)),2,14);
        var feed=inlet(owner,2);
        h.assertTrue(fill(feed,1000,SIMULATE)==1000 && owner.stock(DistillationProfile.AIR)==0,"Simulating the feed does not fill the hold-up");
        h.assertTrue(feed.fill(new FluidStack(CEFluids.NITROGEN_GAS.get(),1000),EXECUTE)==0,"The air inlet takes only air");
        h.assertTrue(fill(feed,4000,EXECUTE)==4000,"Compressed air enters at layer 3");
        run(owner,4*DistillationProfile.CYCLE_TICKS+5);
        h.assertTrue(owner.stock(DistillationProfile.AIR)==0,"Four batches consume exactly 4000 mB of air");
        // 4 x 210 mB of oxygen is held as gas; liquid oxygen leaves 1 mB for every gasPerLiquid mB, and the rest waits.
        int per=DistillationProfile.gasPerLiquid(DistillationProfile.OXYGEN);
        var oxygen=outlet(owner,0).drain(1000,EXECUTE);
        h.assertTrue(oxygen.is(CEFluids.LIQUID_OXYGEN.get()) && oxygen.getAmount()==840/per && owner.stock(DistillationProfile.OXYGEN)==840%per,
            "Layer 1 gives liquid oxygen with the mass of the oxygen in the air: "+oxygen+", "+owner.stock(DistillationProfile.OXYGEN)+" mB of gas left");
        var argon=outlet(owner,9).drain(1000,EXECUTE);
        h.assertTrue(argon.is(CEFluids.ARGON_GAS.get()) && argon.getAmount()==40,"Layer 10 gives crude argon: "+argon);
        var nitrogen=outlet(owner,13).drain(10000,EXECUTE);
        h.assertTrue(nitrogen.is(CEFluids.NITROGEN_GAS.get()) && nitrogen.getAmount()==3120,"Layer 14 gives high-purity nitrogen: "+nitrogen);
        h.assertTrue(outlet(owner,0).drain(new FluidStack(CEFluids.NITROGEN_GAS.get(),100),EXECUTE).isEmpty(),"An outlet gives only its own fluid");
        h.succeed();
    }
    @GameTest(template="test_fractionation",timeoutTicks=100)
    public static void compressedCooledAirIsLiquefiedInTheColdBox(GameTestHelper h) {
        var owner=build(h.getLevel(),h.absolutePos(new BlockPos(5,2,5)),2,14);var feed=inlet(owner,2);
        h.assertTrue(feed.fill(new FluidStack(CEFluids.AIR_GAS.get(),1000),EXECUTE)==0,"Atmospheric air is rejected");
        h.assertTrue(fill(feed,1000,EXECUTE)==1000,"Typed compressed air is accepted");run(owner,DistillationProfile.CYCLE_TICKS+5);
        h.assertTrue(owner.stock(DistillationProfile.AIR)==0 && outlet(owner,13).drain(1000,EXECUTE).getAmount()==780,"One fixed air batch is separated");h.succeed();
    }
    @GameTest(template="test_fractionation",timeoutTicks=200)
    public static void aPumpFeedsCompressedAirFromATankIntoTheColdBox(GameTestHelper h) {
        var owner=build(h.getLevel(),h.absolutePos(new BlockPos(5,2,5)),2,14);
        BlockPos pump=new BlockPos(7,4,5),tank=new BlockPos(8,4,5),motor=new BlockPos(8,4,6);
        h.setBlock(tank,com.simibubi.create.AllBlocks.FLUID_TANK.get());
        h.setBlock(pump,com.simibubi.create.AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,Direction.WEST));
        h.setBlock(new BlockPos(7,4,6),com.simibubi.create.AllBlocks.COGWHEEL.getDefaultState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,Direction.Axis.X));
        h.setBlock(motor,com.simibubi.create.AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,Direction.WEST));
        var stored=((com.simibubi.create.content.fluids.tank.FluidTankBlockEntity)h.getBlockEntity(tank)).getTankInventory();
        stored.fill(net.createplant.thermal.FluidThermal.at(new FluidStack(net.createplant.registry.CEProcessFluids.COMPRESSED_AIR.get(),80000),300),EXECUTE);
        h.runAfterDelay(5,()->((com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity)h.getBlockEntity(motor)).generatedSpeed.setValue(64));
        h.succeedWhen(()->{
            var nitrogen=outlet(owner,13);
            h.assertTrue(owner.stock(DistillationProfile.AIR)>0 || nitrogen!=null && nitrogen.getFluidInTank(0).getAmount()>0,"Pumped compressed air reaches the column: "+stored.getFluidAmount());
        });
    }
    @GameTest(template="test_fractionation",timeoutTicks=150)
    public static void fourParallelAirBatchesConserveVolume(GameTestHelper h) {
        var owner=build(h.getLevel(),h.absolutePos(new BlockPos(5,2,5)),2,14);
        h.assertTrue(owner.batchCapacity()==4 && fill(inlet(owner,2),4000,EXECUTE)==4000,"The 2×2 tower admits four parallel batches");
        run(owner,DistillationProfile.CYCLE_TICKS+5);
        int total=owner.stock(DistillationProfile.OXYGEN)+owner.stock(DistillationProfile.ARGON)+owner.stock(DistillationProfile.NITROGEN);
        h.assertTrue(total==4000 && owner.stock(DistillationProfile.AIR)==0,"Four parallel batches conserve all supplied air");h.succeed();
    }
    @GameTest(template="test_fractionation",timeoutTicks=100)
    public static void fullOutputsAndRedstoneConsumeNothing(GameTestHelper h) {
        var owner=build(h.getLevel(),h.absolutePos(new BlockPos(5,2,5)),2,14);
        int maximum=owner.capacity()/780;
        for (int batch=0;batch<=maximum;batch++) { fill(inlet(owner,2),1000,EXECUTE);run(owner,DistillationProfile.CYCLE_TICKS+5); }
        int nitrogen=owner.stock(DistillationProfile.NITROGEN);
        h.assertTrue(nitrogen==maximum*780 && owner.stock(DistillationProfile.AIR)==1000 && owner.status().equals("products_full"),"A buffer that cannot take a whole batch stops it without consuming air: "+nitrogen);
        run(owner,400);h.assertTrue(owner.stock(DistillationProfile.AIR)==1000 && owner.stock(DistillationProfile.NITROGEN)==nitrogen,"Waiting with a full output changes nothing");
        outlet(owner,13).drain(1000,EXECUTE);run(owner,DistillationProfile.CYCLE_TICKS+5);
        h.assertTrue(owner.stock(DistillationProfile.AIR)==0,"Draining the product allows the next batch");
        fill(inlet(owner,2),1000,EXECUTE);h.getLevel().setBlock(owner.getBlockPos().west(),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);run(owner,200);
        h.assertTrue(owner.status().equals("redstone") && owner.stock(DistillationProfile.AIR)==1000,"Redstone pauses the column and keeps its stock");
        h.succeed();
    }
    @GameTest(template="test_fractionation",timeoutTicks=100)
    public static void reloadKeepsStockAndProgressWithoutDuplicatingABatch(GameTestHelper h) {
        var owner=build(h.getLevel(),h.absolutePos(new BlockPos(5,2,5)),2,14);
        fill(inlet(owner,2),1200,EXECUTE);run(owner,50);
        var saved=owner.saveWithoutMetadata(h.getLevel().registryAccess());owner.loadWithComponents(saved,h.getLevel().registryAccess());owner.rebuild();
        h.assertTrue(owner.stock(DistillationProfile.AIR)==1200,"NBT restore retains the feed");
        run(owner,60);
        h.assertTrue(owner.stock(DistillationProfile.AIR)==200 && owner.stock(DistillationProfile.OXYGEN)==210 && owner.stock(DistillationProfile.NITROGEN)==780,"Reload retains the progress and processes one batch once");
        h.succeed();
    }
    @GameTest(template="test_fractionation")
    public static void rectangularColumnsCannotHideAnOperatingTwoByTwoSubset(GameTestHelper h) {
        int index=0;
        for(int[] size:new int[][]{{1,2},{2,3},{3,2}}) {
            var at=h.absolutePos(new BlockPos(3+index++*8,2,5));
            for(int y=0;y<5;y++)for(int x=0;x<size[0];x++)for(int z=0;z<size[1];z++)
                h.getLevel().setBlock(at.offset(x,y,z),CEDistillation.COLUMN.get().defaultBlockState(),3);
            for(int y=0;y<5;y++)for(int x=0;x<size[0];x++)for(int z=0;z<size[1];z++)
                ((FractionatingColumnBlockEntity)h.getLevel().getBlockEntity(at.offset(x,y,z))).rebuild();
            for(int y=0;y<5;y++)for(int x=0;x<size[0];x++)for(int z=0;z<size[1];z++) {
                var part=(FractionatingColumnBlockEntity)h.getLevel().getBlockEntity(at.offset(x,y,z));
                h.assertTrue(!part.getControllerBE().ready(),"A connected rectangular extension cannot leave an operating 2×2 subset");
            }
        }
        h.succeed();
    }
}
