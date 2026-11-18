package net.createplant.gametest;

import com.mojang.logging.LogUtils;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import net.createplant.CreatePlant;
import net.createplant.chemistry.ElectrolysisProcess;
import net.createplant.block.entity.*;
import net.createplant.chemistry.DistillationProfile;
import net.createplant.chemistry.ReactorModule;
import net.createplant.registry.*;
import net.createplant.thermal.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

/** One material ledger from water and atmospheric air through Haber-Bosch and Ostwald to nitric acid. */
@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NitricPlantIntegrationGameTests {
    private static final Logger LOGGER=LogUtils.getLogger();
    private static final int SYNTHESIS=40000,HYDROGEN=30000,NITROGEN=10000,AIR_BATCHES=14;
    @GameTest(template="test_fractionation",timeoutTicks=200)
    public static void waterAndAtmosphereRunThroughTheWholeNitricAcidPlant(GameTestHelper h){
        var level=h.getLevel();var hydrogen=new ThermalFluidTank(8000,f->{});var oxygen=new ThermalFluidTank(4000,f->{});
        int electrolysisTicks=electrolyse(h,hydrogen,oxygen);
        h.assertTrue(hydrogen.getFluidAmount()==HYDROGEN && oxygen.getFluidAmount()==HYDROGEN/2,"Paid water electrolysis gives the finite 2:1 gas feed");
        var airCompressor=compressor(level,h.absolutePos(new BlockPos(9,3,3)));
        BlockPos intake=h.absolutePos(new BlockPos(13,2,2));
        level.setBlock(intake,CEGasPipes.GAS_PIPE.get().defaultBlockState(),3);
        level.setBlock(intake.above(),Blocks.AIR.defaultBlockState(),3);
        var openEnd=new com.simibubi.create.content.fluids.OpenEndedPipe(new net.createmod.catnip.math.BlockFace(intake,Direction.UP));
        openEnd.manageSource(level,null);
        var atmosphere=openEnd.provideHandler().getCapability();
        var air=FluidStack.EMPTY;
        for(int i=0;i<AIR_BATCHES;i++){var parcel=atmosphere.drain(1000,EXECUTE);if(air.isEmpty())air=parcel;else air.grow(parcel.getAmount());}
        var compressedAir=compress(h,airCompressor,air);
        h.assertTrue(compressedAir.is(CEProcessFluids.COMPRESSED_AIR.get()) && compressedAir.getAmount()==14000,"One compressor raises air by one grade without a pressure reserve");
        var column=DistillationGameTests.build(level,h.absolutePos(new BlockPos(2,2,2)),2,14);
        h.assertTrue(DistillationGameTests.inlet(column,2).fill(compressedAir,EXECUTE)==14000,"The air column accepts compressed air");
        for(int i=0;i<5;i++)DistillationGameTests.run(column,105);
        var nitrogen=DistillationGameTests.outlet(column,13).drain(Integer.MAX_VALUE,EXECUTE);
        var liquidOxygen=DistillationGameTests.outlet(column,0).drain(Integer.MAX_VALUE,EXECUTE);
        var argon=DistillationGameTests.outlet(column,9).drain(Integer.MAX_VALUE,EXECUTE);
        h.assertTrue(nitrogen.getAmount()==10920 && argon.getAmount()==140 && liquidOxygen.getAmount()*DistillationProfile.gasPerLiquid(DistillationProfile.OXYGEN)+column.stock(DistillationProfile.OXYGEN)==2940,"Air separation preserves the 78:1:21 product ledger");
        var mixer=ReactorGameTests.assemble(level,h.absolutePos(new BlockPos(11,2,20)),ReactorModule.STIRRED,4);var mixing=mixer.port(Direction.NORTH);
        h.assertTrue(mixing.fill(hydrogen.drain(HYDROGEN,EXECUTE),EXECUTE)==HYDROGEN && mixing.fill(nitrogen.copyWithAmount(NITROGEN),EXECUTE)==NITROGEN,"The real stirred vessel accepts hydrogen and nitrogen");nitrogen.shrink(NITROGEN);
        var synthesis=new ThermalFluidTank(8000,f->{});
        for(int i=0;i<10;i++){run(mixer,105);var parcel=mixing.drain(new FluidStack(CEProcessFluids.SYNTHESIS_GAS.get(),4000),EXECUTE);h.assertTrue(parcel.getAmount()==4000 && synthesis.fill(parcel,EXECUTE)==4000,"Four stages mix four real batches");}
        var feedCompressor=compressor(level,h.absolutePos(new BlockPos(15,3,3)));
        var feed=compress(h,feedCompressor,synthesis.drain(SYNTHESIS,EXECUTE));
        h.assertTrue(feed.is(CEProcessFluids.COMPRESSED_SYNTHESIS_FEED.get()) && feed.getAmount()==SYNTHESIS,"Synthesis compression preserves amount and changes grade");
        var tower=ReactionTowerGameTests.tower(level,h.absolutePos(new BlockPos(16,2,18)),4,CEItems.IRON_CATALYST.toStack());ReactionTowerGameTests.heat(tower);
        h.assertTrue(ReactionTowerGameTests.port(tower).fill(feed,EXECUTE)==40000,"The reaction tower accepts four compressed batches");
        tower.process(100);
        var hot=ReactionTowerGameTests.port(tower).drain(Integer.MAX_VALUE,EXECUTE);
        h.assertTrue(hot.is(CEProcessFluids.HOT_SYNTHESIS_MIXTURE.get()) && hot.getAmount()==36000 && tower.layer(0).catalystStack().getDamageValue()==1 && tower.layer(3).catalystStack().getDamageValue()==1,"Four layers run four fixed reactions, each paying one use of its catalyst");
        var radiator=radiator(level,h.absolutePos(new BlockPos(17,5,3)));
        var cold=cool(h,radiator,hot);h.assertTrue(cold.is(CEProcessFluids.COLD_SYNTHESIS_MIXTURE.get()) && cold.getAmount()==36000,"Fan cooling converts the full hot stream to its cold type");
        var separator=HaberBoschGameTests.separator(level,h.absolutePos(new BlockPos(22,2,18)));
        var sepIn=capability(level,separator.getBlockPos().above(),Direction.NORTH,"separator inlet");
        h.assertTrue(sepIn.fill(cold,EXECUTE)==36000,"The separator stores the cooled finite feed");for(int i=0;i<24;i++)separator.process(.25);
        var liquid=capability(level,separator.getBlockPos(),Direction.DOWN,"liquid ammonia").drain(Integer.MAX_VALUE,EXECUTE);
        var recycle=capability(level,separator.getBlockPos().above(),Direction.UP,"recycle gas").drain(Integer.MAX_VALUE,EXECUTE);
        h.assertTrue(liquid.is(CEProcessFluids.LIQUID_AMMONIA.get()) && liquid.getAmount()==4 && recycle.is(CEProcessFluids.COMPRESSED_SYNTHESIS_FEED.get()) && recycle.getAmount()==32000,"The separator yields four liquid ammonia and 32000 recycle, without equilibrium calculations");
        var evaporator=ReactorGameTests.assemble(level,h.absolutePos(new BlockPos(11,2,25)),ReactorModule.STIRRED,1);ReactorGameTests.heat(evaporator,com.simibubi.create.content.processing.recipe.HeatCondition.HEATED);var evaporation=evaporator.port(Direction.NORTH);
        h.assertTrue(evaporation.fill(liquid,EXECUTE)==4,"Liquid ammonia enters the native heated mixing recipe");run(evaporator,420);
        var ammonia=evaporation.drain(new FluidStack(CEFluids.AMMONIA_GAS.get(),Integer.MAX_VALUE),EXECUTE);
        h.assertTrue(ammonia.getAmount()==3736,"Heated evaporation produces four finite 934 mB parcels");
        var oxidizer=ReactionTowerGameTests.tower(level,h.absolutePos(new BlockPos(26,2,19)),3,CEItems.OXIDATION_CATALYST.toStack());var port=ReactionTowerGameTests.port(oxidizer);
        h.assertTrue(port.fill(ammonia.copyWithAmount(300),EXECUTE)==300 && port.fill(oxygen.drain(375,EXECUTE),EXECUTE)==375,"Ostwald oxidation accepts real ammonia and electrolytic oxygen");ammonia.shrink(300);oxidizer.process(100);
        var hotNitric=port.drain(new FluidStack(CEProcessFluids.HOT_NITRIC_OXIDE_MIXTURE.get(),750),EXECUTE);h.assertTrue(hotNitric.getAmount()==750,"Three oxidation batches produce 750 hot mixture");
        var coldNitric=cool(h,radiator,hotNitric);h.assertTrue(coldNitric.is(CEProcessFluids.COLD_NITRIC_OXIDE_MIXTURE.get()),"The same radiator converts the next stream after draining");
        var nitricColumn=DistillationGameTests.build(level,h.absolutePos(new BlockPos(27,2,27)),2,14);DistillationGameTests.inlet(nitricColumn,2).fill(coldNitric,EXECUTE);DistillationGameTests.run(nitricColumn,65);
        var nitricOxide=DistillationGameTests.outlet(nitricColumn,13).drain(300,EXECUTE);var reactionWater=DistillationGameTests.outlet(nitricColumn,9).drain(450,EXECUTE);
        h.assertTrue(nitricOxide.getAmount()==300 && reactionWater.getAmount()==450,"Separation preserves all 750 mB");
        port.fill(nitricOxide,EXECUTE);port.fill(oxygen.drain(150,EXECUTE),EXECUTE);oxidizer.process(100);var dioxide=port.drain(new FluidStack(CEOstwaldFluids.NITROGEN_DIOXIDE.get(),300),EXECUTE);
        var absorber=ReactorGameTests.assemble(level,h.absolutePos(new BlockPos(21,2,25)),ReactorModule.TOWER,4);var absorption=absorber.port(Direction.NORTH);
        h.assertTrue(absorption.fill(dioxide,EXECUTE)==300 && absorption.fill(new FluidStack(Fluids.WATER,100),EXECUTE)==100,"Absorption consumes real dioxide and water");for(int i=0;i<101;i++)absorber.tickProcess();
        var acid=absorption.drain(new FluidStack(CEFluids.NITRIC_ACID.get(),Integer.MAX_VALUE),EXECUTE);var monoxide=absorber.port(Direction.UP).drain(new FluidStack(CEOstwaldFluids.NITRIC_OXIDE.get(),Integer.MAX_VALUE),EXECUTE);
        h.assertTrue(acid.getAmount()==200 && monoxide.getAmount()==100,"Absorption makes 200 nitric acid and 100 nitric oxide for recycle");
        LOGGER.info("NITRIC_PLANT_GRADE_DATA electrolysis_ticks={} water_mB=45000 hydrogen_mB=30000 oxygen_mB=15000 air_mB=14000 nitrogen_mB=10920 nitrogen_excess_mB={} synthesis_mB=40000 hot_mix_mB=36000 liquid_ammonia_mB=4 synthesis_recycle_mB={} ammonia_mB=3736 ammonia_excess_mB={} reaction_water_mB={} nitric_acid_mB={} nitric_oxide_recycle_mB={} oxygen_excess_mB={}",electrolysisTicks,nitrogen.getAmount(),recycle.getAmount(),ammonia.getAmount(),reactionWater.getAmount(),acid.getAmount(),monoxide.getAmount(),oxygen.getFluidAmount());h.succeed();
    }
    private static FluidStack compress(GameTestHelper h,CompressionStage stage,FluidStack feed){
        ((com.simibubi.create.content.kinetics.base.KineticBlockEntity)h.getLevel().getBlockEntity(stage.engine().shaftPos())).setSpeed(256);
        var input=capability(h.getLevel(),stage.cylinder().getBlockPos(),Direction.UP,"compressor inlet");var output=capability(h.getLevel(),stage.cylinder().getBlockPos(),Direction.DOWN,"compressor outlet");
        var result=FluidStack.EMPTY;int accepted=0;
        for(int i=0;i<10000 && (accepted<feed.getAmount() || !stage.cylinder().inlet().isEmpty() || !stage.cylinder().outlet().isEmpty());i++){
            accepted+=input.fill(feed.copyWithAmount(feed.getAmount()-accepted),EXECUTE);stage.engine().tick();var parcel=output.drain(Integer.MAX_VALUE,EXECUTE);
            if(!parcel.isEmpty()){if(result.isEmpty())result=parcel;else{h.assertTrue(FluidThermal.compatible(result,parcel),"Compressor output type stays consistent");result.grow(parcel.getAmount());}}
        }
        h.assertTrue(accepted==feed.getAmount() && result.getAmount()==feed.getAmount(),"Compression closes its entire finite ledger");return result;
    }
    private static FluidStack cool(GameTestHelper h,RadiatorPlant radiator,FluidStack input){
        h.assertTrue(radiator.input().fill(input,EXECUTE)==input.getAmount(),"Radiator accepts the complete hot batch");
        for(int i=0;i<1000 && !radiator.input().getFluidInTank(0).isEmpty();i++)radiator.owner().tick();
        var result=radiator.output().drain(Integer.MAX_VALUE,EXECUTE);h.assertTrue(result.getAmount()==input.getAmount(),"Cooling preserves the complete quantity");return result;
    }

    private static int electrolyse(GameTestHelper helper, ThermalFluidTank hydrogen, ThermalFluidTank oxygen) {
        var level = helper.getLevel();
        var owner = ElectrolysisCellGameTests.assemble(level, helper.absolutePos(new BlockPos(2, 2, 20)), 3, Direction.NORTH);
        ElectrolysisCellGameTests.load(owner, 40);
        owner.setRunning(true);ElectrolysisCellGameTests.lower(owner);
        var low = (ElectrolysisCellBlockEntity)level.getBlockEntity(owner.structure().minimum());var high = low;
        var h2port = low.port(Direction.WEST);var o2port = high.port(Direction.EAST);
        int ticks = HYDROGEN / 80, waterFed = 0;
        for (int tick = 0; tick < ticks; tick++) {
            waterFed += ElectrolysisCellGameTests.feed(owner, new FluidStack(Fluids.WATER, 120));
            ElectrolysisCellGameTests.run(owner, 1, 400);
            helper.assertTrue(hydrogen.fill(h2port.drain(80, EXECUTE), EXECUTE) == 80 && oxygen.fill(o2port.drain(40, EXECUTE), EXECUTE) == 40, "Forty paid plate cycles give exactly 80 mB H2 and 40 mB O2");
        }
        helper.assertTrue(waterFed == HYDROGEN * 3 / 2, "Electrolysis water ledger closes");return ticks;
    }

    private static RadiatorPlant radiator(ServerLevel level, BlockPos fan) {
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int y = 0; y < 4; y++)
            level.setBlock(fan.offset(x, y - 3, z), y < 3 ? CEProcessBlocks.COOLER_RADIATOR.get().defaultBlockState()
                : x == 0 && z == 0 ? CEProcessBlocks.COOLING_FAN.get().defaultBlockState() : CEProcessBlocks.COOLING_FAN_SHROUD.get().defaultBlockState(), 3);
        var owner = (RadiatorBlockEntity)level.getBlockEntity(fan.below(3));owner.lazyTick();
        ((CoolingFanBlockEntity)level.getBlockEntity(fan)).setSpeed(128);
        return new RadiatorPlant(owner,
            capability(level, fan.below(2).north(), Direction.NORTH, "radiator inlet"),
            capability(level, fan.below(2).south(), Direction.SOUTH, "radiator outlet"));
    }


    private record CompressionStage(ReciprocatingCylinderBlockEntity cylinder,ReciprocatingCompressorBlockEntity engine) { }

    private static CompressionStage compressor(ServerLevel level,BlockPos cylinder) {
        level.setBlock(cylinder,CEReciprocating.CYLINDER.get().defaultBlockState(),3);
        level.setBlock(cylinder.south(),CEReciprocating.COMPRESSOR.get().defaultBlockState()
            .setValue(net.createplant.block.ReciprocatingCompressorBlock.FACE,net.minecraft.world.level.block.state.properties.AttachFace.WALL)
            .setValue(net.createplant.block.ReciprocatingCompressorBlock.FACING,Direction.SOUTH),3);
        var engine=(ReciprocatingCompressorBlockEntity)level.getBlockEntity(cylinder.south());
        level.setBlock(engine.shaftPos(),AllBlocks.SHAFT.getDefaultState().setValue(com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock.AXIS,Direction.Axis.X),3);
        engine.attachShaft();
        level.setBlock(engine.shaftPos().west(),AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(com.simibubi.create.content.kinetics.base.DirectionalKineticBlock.FACING,Direction.EAST),3);
        ((com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity)level.getBlockEntity(engine.shaftPos().west())).generatedSpeed.setValue(256);
        return new CompressionStage((ReciprocatingCylinderBlockEntity)level.getBlockEntity(cylinder),engine);
    }

    private static IFluidHandler capability(ServerLevel level, BlockPos pos, Direction side, String name) {
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
        if (handler == null) throw new IllegalStateException("Missing " + name + " capability at " + pos + " on " + side);
        return handler;
    }

    private static void run(ReactorMachineBlockEntity machine, int ticks) {
        ReactorGameTests.speed(machine, 128);for (int i = 0; i < ticks; i++) machine.tickProcess();
    }

    private record RadiatorPlant(RadiatorBlockEntity owner, IFluidHandler input, IFluidHandler output) { }
}
