package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pipes.valve.FluidValveBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import net.createplant.CreatePlant;
import net.createplant.chemistry.ProcessModule;
import net.createplant.registry.*;
import net.createplant.transport.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TransportQualityGameTests {
    private static final BlockPos SOURCE=new BlockPos(1,2,3),PUMP=new BlockPos(2,2,3),PIPE=new BlockPos(3,2,3),TARGET=new BlockPos(4,2,3);

    @GameTest(template="test_petrochemical")
    public static void chemicalRatingsDoNotInventForeignFluidHazards(GameTestHelper h) {
        for (var m:PipeMaterial.values()) for (var f:new Fluid[]{Fluids.WATER,Fluids.LAVA,CEPetroFluids.CRUDE_OIL.get(),CEPetroFluids.GASOLINE.get()})
            h.assertTrue(m.carries(new FluidStack(f,1000)),"Unclassified liquids have no invented hazard: "+m+" / "+f);
        var acid=FluidHazards.of(new FluidStack(CEFluids.NITRIC_ACID.get(),1000));
        h.assertTrue(PipeMaterial.COPPER.incompatibility(acid).equals("corrosion") && PipeMaterial.CERAMIC.incompatibility(acid).equals("safe"),"Cool nitric acid requires a corrosion-resistant material");
        h.assertTrue(PipeMaterial.COPPER.incompatibility(FluidHazards.named("pressurised_water")).equals("pressure"),"Liquid pressure still matters for copper");
        // Gases obey the same temperature, pressure and corrosion ratings as liquids.
        h.assertTrue(PipeMaterial.COPPER.carries(new FluidStack(CEFluids.STEAM.get(),1000)),"Ordinary steam fits copper's hot, atmospheric rating");
        h.assertTrue(PipeMaterial.COPPER.problem(new FluidStack(CEProcessFluids.SUPERHEATED_STEAM.get(),1000)).equals("overheat")
            && PipeMaterial.STAINLESS.carries(new FluidStack(CEProcessFluids.SUPERHEATED_STEAM.get(),1000)),"Superheated steam needs stainless steel");
        h.assertTrue(PipeMaterial.COPPER.problem(new FluidStack(CEProcessFluids.COMPRESSED_HYDROGEN.get(),1000)).equals("pressure"),"High-pressure hydrogen exceeds copper");
        h.assertTrue(PipeMaterial.STAINLESS.problem(new FluidStack(CEUranium.HYDROGEN_FLUORIDE.get(),1000)).equals("corrosion")
            && PipeMaterial.NICKEL_ALLOY.carries(new FluidStack(CEUranium.HYDROGEN_FLUORIDE.get(),1000)),"Hydrogen fluoride gas needs nickel alloy");
        h.succeed();
    }
    @GameTest(template="test_petrochemical")
    public static void theScaleMarksTheRatedRangeAndTheContentsLevel(GameTestHelper h) {
        var copper=net.createplant.thermal.GradeScale.temperature(PipeMaterial.COPPER.coldest,PipeMaterial.COPPER.hottest,13);
        String marks=copper.getString();
        h.assertTrue(marks.length()==17 && marks.charAt(13)=='●' && marks.charAt(0)=='◆' && marks.charAt(1)=='·' && marks.charAt(16)=='◆',"Seventeen marks, a diamond on each grade, with the contents' level filled in: "+marks);
        var styles=new java.util.ArrayList<net.minecraft.network.chat.TextColor>();
        copper.visit((style,text)->{styles.add(style.getColor());return java.util.Optional.empty();},net.minecraft.network.chat.Style.EMPTY);
        var green=net.minecraft.network.chat.TextColor.fromLegacyFormat(net.minecraft.ChatFormatting.GREEN);
        h.assertTrue(styles.get(8)==green && styles.get(12)==green && styles.get(4)!=green
            && styles.get(13)==net.minecraft.network.chat.TextColor.fromLegacyFormat(net.minecraft.ChatFormatting.WHITE),
            "Copper's ambient-to-hot range is green and a level inside it is white");
        var hot=net.createplant.thermal.GradeScale.temperature(PipeMaterial.COPPER.coldest,PipeMaterial.COPPER.hottest,16);
        var last=new net.minecraft.network.chat.TextColor[1];
        hot.visit((style,text)->{if(text.equals("●"))last[0]=style.getColor();return java.util.Optional.empty();},net.minecraft.network.chat.Style.EMPTY);
        h.assertTrue(last[0]==net.minecraft.network.chat.TextColor.fromLegacyFormat(net.minecraft.ChatFormatting.RED),"A level outside the range is red");
        h.succeed();
    }
    @GameTest(template="test_petrochemical")
    public static void plantTanksAcceptOnlyTheirOwnFluids(GameTestHelper h) {
        h.setBlock(SOURCE,AllBlocks.FLUID_TANK.get());h.setBlock(TARGET,AllBlocks.FLUID_TANK.get());
        var water=h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,h.absolutePos(SOURCE),Direction.UP);
        var liquid=h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,h.absolutePos(TARGET),Direction.UP);
        h.assertTrue(water.fill(new FluidStack(Fluids.WATER,1000),IFluidHandler.FluidAction.EXECUTE)==1000
            && water.fill(new FluidStack(CEFluids.HYDROGEN_GAS.get(),1000),IFluidHandler.FluidAction.EXECUTE)==0,"A tank holding water takes no gas on top of it");
        h.assertTrue(liquid.fill(new FluidStack(CEPetroFluids.CRUDE_OIL.get(),1000),IFluidHandler.FluidAction.EXECUTE)==1000
            && !net.createplant.thermal.TankGrade.LIQUID.accepts(new FluidStack(CEFluids.HYDROGEN_GAS.get(),1000)),"The liquid tank holds oil and is not rated for process gases");
        h.succeed();
    }
    @GameTest(template="test_petrochemical",timeoutTicks=300)
    public static void aPipeAndPumpCarryAFluidTheyAreNotRatedForAndWear(GameTestHelper h) {
        h.setBlock(SOURCE,CETransport.STAINLESS_STEEL_TANK.get());h.setBlock(TARGET,CETransport.STAINLESS_STEEL_TANK.get());
        h.setBlock(PIPE,straight(AllBlocks.FLUID_PIPE.getDefaultState()));
        h.setBlock(PUMP,AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(BlockStateProperties.FACING,Direction.EAST));
        h.setBlock(new BlockPos(2,2,4),AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS,Direction.Axis.X));
        drive(h,new BlockPos(1,2,4),Direction.EAST);
        IFluidHandler source=fluids(h,SOURCE),dest=fluids(h,TARGET);
        h.assertTrue(source.fill(new FluidStack(CEFluids.NITRIC_ACID.get(),1000),IFluidHandler.FluidAction.EXECUTE)==1000,"The acid-proof source takes nitric acid");
        h.succeedWhen(()->{
            var pipe=BlockEntityBehaviour.get(h.getBlockEntity(PIPE),ContainmentWear.TYPE);var pump=BlockEntityBehaviour.get(h.getBlockEntity(PUMP),ContainmentWear.TYPE);
            h.assertTrue(pipe!=null && pump!=null && !dest.getFluidInTank(0).isEmpty() && pipe.wear()>0 && pump.wear()>0,
                "Create's copper pipe and pump carry the acid and are worn by it: "+dest.getFluidInTank(0));
        });
    }
    @GameTest(template="test_petrochemical",timeoutTicks=250)
    public static void nativeCopperAndPumpFillAnAddonMachineWithoutLoss(GameTestHelper h) {
        transfer(h,straight(AllBlocks.FLUID_PIPE.getDefaultState()),CEPetroFluids.CRUDE_OIL.get(),true,false);
    }
    @GameTest(template="test_petrochemical",timeoutTicks=250)
    public static void ceramicPipeCarriesAcidAtAtmosphericPressure(GameTestHelper h) {
        var ceramic=PipeMaterial.CERAMIC;
        h.assertTrue(ceramic.incompatibility(FluidHazards.named("zirconium_sulfate_solution")).equals("safe"),"Fired clay takes hot sulfate liquors");
        h.assertTrue(ceramic.incompatibility(FluidHazards.named("pressurised_water")).equals("pressure"),"Ceramic still rejects high-pressure liquids");
        transfer(h,straight(CETransport.CERAMIC_PIPE.get().defaultBlockState()),CEUranium.SULFURIC_ACID.get(),false,false);
    }
    @GameTest(template="test_petrochemical",timeoutTicks=250)
    public static void stainlessPipeCarriesLikeCreatesPipe(GameTestHelper h) {
        transfer(h,straight(CETransport.STAINLESS_PIPE.get().defaultBlockState()),CEPetroFluids.CRUDE_OIL.get(),false,false);
    }
    @GameTest(template="test_petrochemical",timeoutTicks=250)
    public static void stainlessGlassPipeCarriesLikeCreatesGlassPipe(GameTestHelper h) {
        transfer(h,CETransport.STAINLESS_GLASS_PIPE.get().defaultBlockState().setValue(BlockStateProperties.AXIS,Direction.Axis.X),CEPetroFluids.CRUDE_OIL.get(),false,false);
    }
    @GameTest(template="test_petrochemical",timeoutTicks=250)
    public static void stainlessEncasedPipeCarriesLikeCreatesEncasedPipe(GameTestHelper h) {
        transfer(h,straight(CETransport.STAINLESS_ENCASED_PIPE.get().defaultBlockState()),CEPetroFluids.CRUDE_OIL.get(),false,false);
    }
    @GameTest(template="test_petrochemical")
    public static void stainlessPipeWrenchAndCasingRoundTripsStayStainless(GameTestHelper h) {
        var player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        BlockPos at=h.absolutePos(PIPE);
        var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(at).add(0,.5,0),Direction.UP,at,false);
        java.util.function.Supplier<BlockState> state=()->h.getBlockState(PIPE);
        // A placed pipe's behaviours initialise on its first tick, which Create's wrench relies on.
        Runnable wrench=()->{
            if(h.getBlockEntity(PIPE) instanceof SmartBlockEntity pipe) pipe.tick();
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,com.simibubi.create.AllItems.WRENCH.asStack());
            ((com.simibubi.create.content.equipment.wrench.IWrenchable)state.get().getBlock())
                .onWrenched(state.get(),new net.minecraft.world.item.context.UseOnContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,hit));
        };
        Runnable encase=()->{
            var casing=AllBlocks.COPPER_CASING.asStack();player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,casing);
            state.get().useItemOn(casing,h.getLevel(),player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
        };
        h.setBlock(PIPE,straight(CETransport.STAINLESS_PIPE.get().defaultBlockState()));
        wrench.run();
        h.assertTrue(state.get().is(CETransport.STAINLESS_GLASS_PIPE.get()) && state.get().getValue(BlockStateProperties.AXIS)==Direction.Axis.X,"A wrenched straight stainless pipe becomes the stainless glass pipe: "+state.get());
        wrench.run();
        h.assertTrue(state.get().is(CETransport.STAINLESS_PIPE.get()) && state.get().getValue(PipeBlock.EAST) && state.get().getValue(PipeBlock.WEST),"A wrenched stainless glass pipe returns to the stainless pipe: "+state.get());
        encase.run();
        h.assertTrue(state.get().is(CETransport.STAINLESS_ENCASED_PIPE.get()) && state.get().getValue(PipeBlock.EAST) && state.get().getValue(PipeBlock.WEST),"Copper casing encases the stainless pipe as the stainless encased pipe: "+state.get());
        wrench.run();
        h.assertTrue(state.get().is(CETransport.STAINLESS_PIPE.get()),"Wrenching off the casing returns the stainless pipe: "+state.get());
        wrench.run();encase.run();
        h.assertTrue(state.get().is(CETransport.STAINLESS_ENCASED_PIPE.get()),"Copper casing on the stainless glass pipe gives the stainless encased pipe: "+state.get());
        for(var block:new Block[]{CETransport.STAINLESS_PIPE.get(),CETransport.STAINLESS_GLASS_PIPE.get(),CETransport.STAINLESS_ENCASED_PIPE.get()})
            h.assertTrue(block.defaultBlockState().getCloneItemStack(hit,h.getLevel(),at,player).is(CETransport.STAINLESS_PIPE.get().asItem()),"Pick block gives the stainless pipe for "+block);
        h.succeed();
    }
    @GameTest(template="test_petrochemical",timeoutTicks=250)
    public static void nativeSmartFluidFilterAcceptsTheAddonBucket(GameTestHelper h) {
        transfer(h,AllBlocks.SMART_FLUID_PIPE.getDefaultState().setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.EAST),CEProcessFluids.COMPRESSED_HYDROGEN.get(),true,true);
    }
    @GameTest(template="test_petrochemical",timeoutTicks=250)
    public static void aReallyDrivenCreateValveOpensTheAddonLine(GameTestHelper h) {
        transfer(h,AllBlocks.FLUID_VALVE.getDefaultState().setValue(BlockStateProperties.FACING,Direction.UP)
            .setValue(com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE,false),CEPetroFluids.CRUDE_OIL.get(),true,false);
    }
    @GameTest(template="test_petrochemical",timeoutTicks=250)
    public static void reinforcedPipeCarriesCompressedGasIntoCreateStorage(GameTestHelper h) {
        transfer(h,straight(CETransport.STAINLESS_PIPE.get().defaultBlockState()),CEProcessFluids.COMPRESSED_SYNTHESIS_FEED.get(),false,false);
    }
    @GameTest(template="test_petrochemical",timeoutTicks=250)
    public static void stainlessMainCarriesHighPressureGasWithoutWear(GameTestHelper h) {
        transfer(h,straight(CETransport.STAINLESS_PIPE.get().defaultBlockState()),CEProcessFluids.COMPRESSED_HYDROGEN.get(),false,false);
    }
    @GameTest(template="test_petrochemical",timeoutTicks=250)
    public static void resinPipeCarriesNitricAcidWithNoUnnecessaryDamage(GameTestHelper h) {
        transfer(h,straight(CETransport.CERAMIC_PIPE.get().defaultBlockState()),CEFluids.NITRIC_ACID.get(),false,false);
    }
    private static BlockState straight(BlockState state) {
        for(var p:PipeBlock.PROPERTY_BY_DIRECTION.values())state=state.setValue(p,false);
        return state.setValue(PipeBlock.WEST,true).setValue(PipeBlock.EAST,true);
    }
    private static IFluidHandler fluids(GameTestHelper h,BlockPos p) {
        return h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,h.absolutePos(p),Direction.WEST);
    }
    private static void drive(GameTestHelper h,BlockPos p,Direction facing) {
        h.setBlock(p,AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,facing));
        h.runAfterDelay(5,()->((CreativeMotorBlockEntity)h.getBlockEntity(p)).generatedSpeed.setValue(256));
    }
    private static void transfer(GameTestHelper h,BlockState pipe,Fluid fluid,boolean addon,boolean filter) {
        boolean gas=FluidHazards.of(new FluidStack(fluid,1000)).gas();
        var storage=gas ? CETransport.GAS_RECEIVER.get().defaultBlockState().setValue(net.createplant.block.PulsationTubeBlock.AXIS,Direction.Axis.X) : CETransport.STAINLESS_STEEL_TANK.get().defaultBlockState();
        h.setBlock(SOURCE,storage);h.setBlock(TARGET,storage);
        h.setBlock(PIPE,pipe);h.setBlock(PUMP,AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(BlockStateProperties.FACING,Direction.EAST));
        h.setBlock(new BlockPos(2,2,4),AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS,Direction.Axis.X));
        drive(h,new BlockPos(1,2,4),Direction.EAST);
        if(pipe.getBlock() instanceof FluidValveBlock) h.runAfterDelay(40,()-> {
            h.assertTrue(fluids(h,TARGET).getFluidInTank(0).isEmpty(),"The undriven closed valve must stop addon fluid");
            drive(h,PIPE.north(),Direction.SOUTH);
        });
        if(filter) {
            var selection=BlockEntityBehaviour.get(h.getBlockEntity(PIPE),FilteringBehaviour.TYPE);
            selection.setFilter(new ItemStack(net.minecraft.world.item.Items.WATER_BUCKET));
            h.runAfterDelay(40,()->{
                h.assertTrue(fluids(h,TARGET).getFluidInTank(0).isEmpty(),"A mismatched native filter must reject the addon fluid");
                selection.setFilter(fluid.getFluidType().getBucket(new FluidStack(fluid,1000)));
            });
        }
        IFluidHandler source=fluids(h,SOURCE),dest=fluids(h,TARGET);
        h.assertTrue(source!=null && dest!=null && source.fill(new FluidStack(fluid,1000),IFluidHandler.FluidAction.EXECUTE)==1000,"A finite source and actual exposed destination must exist");
        h.succeedWhen(()->{
            var at=h.getBlockEntity(PIPE);
            String detail=at instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity kinetic?" speed="+kinetic.getSpeed():"";
            h.assertTrue(dest.getFluidInTank(0).getAmount()==1000 && dest.getFluidInTank(0).is(fluid) && source.getFluidInTank(0).isEmpty(),"Real transport must conserve 1000 mB: source="+source.getFluidInTank(0)+" target="+dest.getFluidInTank(0)+" pipe="+h.getBlockState(PIPE)+detail);
        });
    }
}
