package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import net.createplant.CreatePlant;
import net.createplant.block.*;
import net.createplant.block.entity.*;
import net.createplant.registry.*;
import net.createplant.thermal.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public final class TurbineThermalGameTests {
    private static FluidStack steam(int n,double ignored){return new FluidStack(CEProcessFluids.HIGH_PRESSURE_STEAM.get(),n);}
    private static void near(GameTestHelper h,double a,double b,String why){h.assertTrue(Double.isFinite(a) && Math.abs(a-b)<Math.max(.001,Math.abs(b)*1e-9),why+": "+a+" / "+b);}

    @GameTest(template="test_fractionation")
    public static void pressureRoutesFollowVisiblePipeFaces(GameTestHelper h){
        var level=h.getLevel();var first=h.absolutePos(new BlockPos(2,2,2));var second=first.east();
        var pipe=CETransport.STAINLESS_PIPE.get().defaultBlockState();
        level.setBlock(first,pipe.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(Direction.EAST),true),2);
        level.setBlock(second,pipe.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(Direction.WEST),true),2);
        h.assertTrue(FluidPipeTopology.joins(level,first,Direction.EAST),"Two visibly joined pressure-pipe faces form one route");
        level.setBlock(first,level.getBlockState(first).setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(Direction.EAST),false),2);
        h.assertTrue(!FluidPipeTopology.joins(level,first,Direction.EAST),"Adjacent pressure pipes with a closed face are separate routes");
        h.succeed();
    }

    public static SteamTurbineBlockEntity[] build(Level l,BlockPos origin){return build(l,origin,new int[]{5,6,7,8,9,13,14,15});}
    /** Rotors tick in the order they are placed. */
    private static SteamTurbineBlockEntity[] build(Level l,BlockPos origin,int[] order){
        for(int x:order){
            boolean high=x>=13;var p=origin.offset(x,4,10);var state=(high ? CEBlocks.HIGH_PRESSURE_STEAM_TURBINE : CEBlocks.STEAM_TURBINE).get().defaultBlockState().setValue(SteamTurbineBlock.AXIS,Direction.Axis.X).setValue(SteamTurbineBlock.BLADES,high ? 6 : 3).setValue(SteamTurbineBlock.CASING,true);
            if(!high)state=state.setValue(SteamTurbineBlock.SIZE_1,4).setValue(SteamTurbineBlock.SIZE_2,4).setValue(SteamTurbineBlock.SIZE_3,4);
            l.setBlock(p,state,3);HighPressureTurbinePartBlock.placeCasingRing(l,p,Direction.Axis.X,high ? 1 : 2);
        }
        l.setBlock(origin.offset(11,4,10),com.simibubi.create.AllBlocks.SHAFT.get().defaultBlockState().setValue(BlockStateProperties.AXIS,Direction.Axis.X),3);
        for(int x:new int[]{4,10,12,16})HighPressureTurbineEndBlock.placeEnd(l,origin.offset(x,4,10),Direction.Axis.X,x==10 || x==16,x<=10 ? 2 : 1);
        for(int x=5;x<=13;x++)l.setBlock(origin.offset(x,7,10),CETransport.STAINLESS_PIPE.get().defaultBlockState(),3);
        l.setBlock(origin.offset(13,6,10),CETransport.STAINLESS_PIPE.get().defaultBlockState(),3);
        return new SteamTurbineBlockEntity[]{(SteamTurbineBlockEntity)l.getBlockEntity(origin.offset(13,4,10)),(SteamTurbineBlockEntity)l.getBlockEntity(origin.offset(5,4,10))};
    }
    @GameTest(template="test_fractionation")
    public static void expansionConservesEnergyAndRespondsToVacuumAndTemperature(GameTestHelper h){
        var pair=build(h.getLevel(),h.absolutePos(BlockPos.ZERO));
        for(var grade:SteamGrades.values()){
            var high=pair[0].thermodynamics();var low=pair[1].thermodynamics();high.clear();low.clear();
            high.input().fill(new FluidStack(grade.fluid(),100),EXECUTE);high.step(true,100);
            near(h,high.work(),100*grade.workPerMb(false),"High stage work is fixed by grade");
            low.input().fill(high.exhaust().drain(100,EXECUTE),EXECUTE);low.step(true,100);
            near(h,high.work()+low.work()+low.condenserJoules(),100*grade.joulesPerMb(),"Both stages and condenser balance");
            h.assertTrue(low.exhaust().getFluid().is(Fluids.WATER) && low.exhaust().getFluidAmount()==100,"Steam returns equal ordinary water");
        }
        h.succeed();
    }
    @GameTest(template="test_fractionation")
    public static void bothStagesKeepEveryMillibucketAndJouleIncludingSavedExhaust(GameTestHelper h){
        var pair=build(h.getLevel(),h.absolutePos(BlockPos.ZERO));var high=pair[0].thermodynamics();var low=pair[1].thermodynamics();
        high.input().fill(new FluidStack(CEProcessFluids.SUPERHEATED_STEAM.get(),1000),EXECUTE);high.step(true,1000);
        low.input().fill(high.exhaust().drain(1000,EXECUTE),EXECUTE);low.step(true,1000);
        near(h,high.work()+low.work(),1250000,"Fixed two-stage superheated work");
        near(h,low.pendingCondenserJoules(),1758000,"Finite condenser heat");
        var lookup=h.getLevel().registryAccess();var saved=pair[1].saveWithoutMetadata(lookup);double wear=low.wear();
        pair[1].loadWithComponents(saved,lookup);
        h.assertTrue(low.exhaust().getFluidAmount()==1000 && low.input().isEmpty() && low.work()==0 && low.wear()==wear,"Reload keeps stock and wear without replaying work");
        near(h,low.pendingCondenserJoules(),1758000,"Reload retains pending rejection");
        h.assertTrue(!low.service(),"A filled rotor cannot be serviced");low.exhaust().drain(1000,EXECUTE);
        h.assertTrue(low.service() && low.wear()==0 && !low.service(),"Empty stopped rotor is serviced once");h.succeed();
    }
    @GameTest(template="test_fractionation")
    public static void fullOutletAndSimulationNeverSpendFeedOrCreateWork(GameTestHelper h){
        var pair=build(h.getLevel(),h.absolutePos(BlockPos.ZERO));var be=pair[0];var handler=be.getSteamCapability();
        h.assertTrue(handler.fill(steam(1000,700),SIMULATE)==1000 && be.thermodynamics().input().isEmpty(),"Native preview cannot prime the rotor");handler.fill(steam(1000,700),EXECUTE);be.thermodynamics().exhaust().setFluid(new FluidStack(CEFluids.STEAM.get(),TurbineThermal.CAPACITY));double before=FluidThermal.energy(be.thermodynamics().input().getFluid());
        h.assertTrue(be.thermodynamics().step(true,64)==0 && be.thermodynamics().work()==0,"A full exhaust stops work");near(h,FluidThermal.energy(be.thermodynamics().input().getFluid()),before,"Blocked exhaust retains input energy");
        h.getLevel().destroyBlock(be.getBlockPos().above(),false);h.assertTrue(handler.fill(steam(1,700),EXECUTE)==0,"A removed casing immediately disables cached inlet use");h.succeed();
    }
    private static int transfer(net.neoforged.neoforge.fluids.capability.IFluidHandler from,net.neoforged.neoforge.fluids.capability.IFluidHandler to,int maximum){var preview=from.drain(maximum,SIMULATE);int n=to.fill(preview,SIMULATE);if(n==0)return 0;var parcel=from.drain(n,EXECUTE);int accepted=to.fill(parcel,EXECUTE);if(accepted!=parcel.getAmount())throw new IllegalStateException("Transfer promise changed");return accepted;}
    @GameTest(template="test_fractionation",timeoutTicks=250)
    public static void nativePumpTransfersFullHotExhaustBetweenExistingCasings(GameTestHelper h){
        var l=h.getLevel();var base=h.absolutePos(BlockPos.ZERO);var pair=build(l,base);pair[0].thermodynamics().input().fill(steam(1000,750),EXECUTE);
        var pump=base.offset(10,7,10);l.setBlock(pump,AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(BlockStateProperties.FACING,Direction.WEST),3);l.setBlock(pump.above(),AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS,Direction.Axis.X),3);var motor=pump.above().east();l.setBlock(motor,AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,Direction.WEST),3);
        h.runAfterDelay(10,()->((com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity)l.getBlockEntity(motor)).generatedSpeed.setValue(128));
        h.onEachTick(()->{int mass=0;for(int x:new int[]{5,6,7,8,9,13,14,15}){var be=(SteamTurbineBlockEntity)l.getBlockEntity(base.offset(x,4,10));mass+=be.thermodynamics().input().getFluidAmount()+be.thermodynamics().exhaust().getFluidAmount();}h.assertTrue(mass==1000,"Native pumping does not halve or lose steam mass: "+mass);});
        h.runAfterDelay(200,()->{int low=0;for(int x=5;x<=9;x++){var rotor=(SteamTurbineBlockEntity)l.getBlockEntity(base.offset(x,4,10));low+=rotor.thermodynamics().input().getFluidAmount()+rotor.thermodynamics().exhaust().getFluidAmount();}h.assertTrue(low>0,"A real Create pump reaches the low-pressure casing; received="+low+" high="+pair[0].thermodynamics().status());h.succeed();});
    }

    @GameTest(template="test_fractionation",timeoutTicks=250)
    public static void lowPressureFacilityReturnsThirtyDegreeWaterThroughACreatePump(GameTestHelper h){
        var level=h.getLevel();var base=h.absolutePos(BlockPos.ZERO);var pair=build(level,base);var low=pair[1];
        // Leave the x=4 end-cap hub free for the output shaft and drain condensate from its upper outer ring.
        var outlet=base.offset(3,6,10);var pump=base.offset(2,6,10);var tankPos=base.offset(0,6,10);
        level.setBlock(outlet,CETransport.STAINLESS_PIPE.get().defaultBlockState(),3);
        level.setBlock(pump,AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(BlockStateProperties.FACING,Direction.WEST),3);
        level.setBlock(base.offset(1,6,10),CETransport.STAINLESS_PIPE.get().defaultBlockState(),3);
        level.setBlock(tankPos,CETransport.STAINLESS_STEEL_TANK.get().defaultBlockState(),3);
        level.setBlock(pump.above(),AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS,Direction.Axis.X),3);
        level.setBlock(pump.above().east(),AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,Direction.WEST),3);
        ((com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity)level.getBlockEntity(pump.above().east())).generatedSpeed.setValue(128);

        var steam=reactorSteam(16000);
        h.runAfterDelay(20,()->low.thermodynamics().input().fill(steam,EXECUTE));
        h.runAfterDelay(200,()->{
            var tank=((com.simibubi.create.content.fluids.tank.FluidTankBlockEntity)level.getBlockEntity(tankPos)).getControllerBE();var water=tank.getTankInventory().getFluid();var terminal=low.thermodynamics().exhaust().getFluid();
            h.assertTrue(low.thermodynamics().totalWork()>0 && low.thermodynamics().totalCondenserJoules()>0,"The low-pressure terminal section expands and condenses steam");
            h.assertTrue(water.is(Fluids.WATER) && water.getAmount()>0
                    && (terminal.isEmpty() || terminal.is(Fluids.WATER)),
                "A Create pump returns 30 C liquid from the terminal turbine to the pressure-rated tank: status="+low.thermodynamics().status()
                    +", exhaust="+terminal+(terminal.isEmpty() ? "" : " at "+(FluidThermal.kelvin(terminal)-273.15)+" C")
                    +", tank="+water+(water.isEmpty() ? "" : " at "+(FluidThermal.kelvin(water)-273.15)+" C")
                    +", pump="+((com.simibubi.create.content.kinetics.base.KineticBlockEntity)level.getBlockEntity(pump)).getSpeed());
            h.assertTrue(low.thermodynamics().exhaust().getFluidAmount()<TurbineThermal.CAPACITY,
                "The condensate pump prevents the low-pressure output buffer from blocking");
            h.succeed();
        });
    }

    @GameTest(template="test_fractionation",timeoutTicks=250)
    public static void everyLowPressureRotorDrainsThroughASideManifold(GameTestHelper h){
        var level=h.getLevel();var base=h.absolutePos(BlockPos.ZERO);build(level,base);
        // Match the full plant: a pipe one block outside the south casing drains every rotor through a horizontal face.
        for(int x=5;x<=9;x++){
            var state=CETransport.STAINLESS_PIPE.get().defaultBlockState()
                .setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(Direction.NORTH),true)
                .setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(Direction.WEST),true);
            if(x<9)state=state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(Direction.EAST),true);
            level.setBlock(base.offset(x,4,13),state,3);
        }
        var pump=base.offset(4,4,13);var tankPos=base.offset(1,4,13);
        level.setBlock(pump,AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(BlockStateProperties.FACING,Direction.WEST),3);
        level.setBlock(base.offset(3,4,13),CETransport.STAINLESS_PIPE.get().defaultBlockState(),3);
        level.setBlock(base.offset(2,4,13),CETransport.STAINLESS_PIPE.get().defaultBlockState(),3);
        level.setBlock(tankPos,AllBlocks.FLUID_TANK.get().defaultBlockState(),3);
        level.setBlock(pump.above(),AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS,Direction.Axis.X),3);
        var motor=pump.above().east();level.setBlock(motor,AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,Direction.WEST),3);
        ((com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity)level.getBlockEntity(motor)).generatedSpeed.setValue(128);

        h.runAfterDelay(20,()->{for(int x=5;x<=9;x++)((SteamTurbineBlockEntity)level.getBlockEntity(base.offset(x,4,10))).thermodynamics().input()
            .fill(reactorSteam(1000),EXECUTE);});
        h.runAfterDelay(200,()->{
            var tank=((com.simibubi.create.content.fluids.tank.FluidTankBlockEntity)level.getBlockEntity(tankPos)).getControllerBE();
            int exhaust=0;for(int x=5;x<=9;x++)exhaust+=((SteamTurbineBlockEntity)level.getBlockEntity(base.offset(x,4,10))).thermodynamics().exhaust().getFluidAmount();
            h.assertTrue(tank.getTankInventory().getFluidAmount()==5000 && exhaust==0,
                "The side manifold returns every rotor's condensate: tank="+tank.getTankInventory().getFluidAmount()+", out="+exhaust
                    +", pump="+((com.simibubi.create.content.kinetics.base.KineticBlockEntity)level.getBlockEntity(pump)).getSpeed());
            h.succeed();
        });
    }

    @GameTest(template="test_fractionation",timeoutTicks=100)
    public static void ultraHighPressureMainDrainsRadiatorAtStationFlow(GameTestHelper h){
        var level=h.getLevel();var base=h.absolutePos(BlockPos.ZERO);var fan=base.offset(5,5,2);
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=0;y<4;y++)
            level.setBlock(fan.offset(x,y-3,z),y<3 ? CEProcessBlocks.COOLER_RADIATOR.get().defaultBlockState()
                : x==0 && z==0 ? CEProcessBlocks.COOLING_FAN.get().defaultBlockState() : CEProcessBlocks.COOLING_FAN_SHROUD.get().defaultBlockState(),3);
        var radiator=(RadiatorBlockEntity)level.getBlockEntity(fan.below(3));radiator.lazyTick();
        level.setBlock(fan.below(4),AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,Direction.UP),3);
        ((com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity)level.getBlockEntity(fan.below(4))).generatedSpeed.setValue(128);
        var outletPort=fan.below(2).south();var pipe=outletPort.south();var pump=pipe.south();var tankPos=pump.south(2);
        level.setBlock(pipe,CETransport.STAINLESS_PIPE.get().defaultBlockState(),3);
        level.setBlock(pump,AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(BlockStateProperties.FACING,Direction.SOUTH),3);
        level.setBlock(pump.south(),CETransport.STAINLESS_PIPE.get().defaultBlockState(),3);
        for(int dx=0;dx<2;dx++)for(int dy=0;dy<2;dy++)for(int dz=0;dz<2;dz++)
            level.setBlock(tankPos.offset(dx,dy,dz),AllBlocks.FLUID_TANK.getDefaultState(),3);
        com.simibubi.create.api.connectivity.ConnectivityHandler.formMulti(
            (com.simibubi.create.content.fluids.tank.FluidTankBlockEntity)level.getBlockEntity(tankPos));
        level.setBlock(pump.above(),AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS,Direction.Axis.Z),3);
        level.setBlock(pump.above().south(),AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,Direction.NORTH),3);
        ((com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity)level.getBlockEntity(pump.above().south())).generatedSpeed.setValue(128);

        var charge=reactorSteam(16000);
        h.runAfterDelay(20,()->{
            var inlet=((RadiatorBlockEntity)level.getBlockEntity(fan.below(2).north())).port(Direction.NORTH);
            h.assertTrue(inlet!=null && inlet.fill(charge,EXECUTE)==charge.getAmount(),"The formed condenser accepts its throughput-test charge");
        });
        h.runAfterDelay(60,()->{
            var tank=((com.simibubi.create.content.fluids.tank.FluidTankBlockEntity)level.getBlockEntity(tankPos)).getControllerBE();
            h.assertTrue(tank.getTankInventory().getFluidAmount()==1600,
                "A 128 RPM pump on an ultra-high-pressure main returns condenser water at the fixed radiator throughput: "
                    +tank.getTankInventory().getFluidAmount());
            h.succeed();
        });
    }

    @GameTest(template="test_fractionation",timeoutTicks=300)
    public static void aPumpOnACreateBoilerFeedsTheTurbineThroughCopperPipesAtOneToOneMass(GameTestHelper h){
        var l=h.getLevel();var base=h.absolutePos(BlockPos.ZERO);build(l,base);var at=base.offset(21,4,10);var pump=at.above();
        l.setBlock(at,AllBlocks.FLUID_TANK.getDefaultState(),3);
        l.setBlock(at.below(),AllBlocks.BLAZE_BURNER.getDefaultState().setValue(com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HEAT_LEVEL,com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.KINDLED),3);
        var burner=(com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity)l.getBlockEntity(at.below());var tag=burner.saveWithoutMetadata(l.registryAccess());tag.putInt("fuelLevel",1);tag.putInt("burnTimeRemaining",4000);burner.loadWithComponents(tag,l.registryAccess());burner.updateBlockState();
        l.setBlock(pump,AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(BlockStateProperties.FACING,Direction.UP),3);
        l.setBlock(pump.south(),AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS,Direction.Axis.Y),3);
        l.setBlock(pump.south().above(),AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,Direction.DOWN),3);
        ((com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity)l.getBlockEntity(pump.south().above())).generatedSpeed.setValue(128);
        l.setBlock(pump.above(),copper(Direction.DOWN,Direction.WEST),3);
        for(int x=16;x<=20;x++)l.setBlock(base.offset(x,6,10),copper(Direction.EAST,Direction.WEST),3);
        l.setBlock(base.offset(15,6,10),copper(Direction.EAST,Direction.DOWN),3);
        var tank=(com.simibubi.create.content.fluids.tank.FluidTankBlockEntity)l.getBlockEntity(at);tank.getTankInventory().fill(new FluidStack(Fluids.WATER,200),EXECUTE);
        h.runAfterDelay(230,()->{int steamMass=0;for(int x:new int[]{5,6,7,8,9,13,14,15}){var be=(SteamTurbineBlockEntity)l.getBlockEntity(base.offset(x,4,10));steamMass+=be.thermodynamics().input().getFluidAmount()+be.thermodynamics().exhaust().getFluidAmount();}
            int held=TankBoiler.of(tank).steam().getFluidAmount();
            h.assertTrue(steamMass>0,"A pump on the roof of Create's boiler sends its steam through copper pipes to the turbine");
            h.assertTrue(steamMass+held+tank.getTankInventory().getFluidAmount()==200,"Boiling creates no extra water-equivalent mass: turbines="+steamMass+" boiler="+held+" water="+tank.getTankInventory().getFluidAmount());h.succeed();});
    }
    private static net.minecraft.world.level.block.state.BlockState copper(Direction a,Direction b){
        var state=AllBlocks.FLUID_PIPE.getDefaultState();
        for(var side:Direction.values())state=state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side),side==a || side==b);
        return state;
    }
    @GameTest(template="test_fractionation",timeoutTicks=100)
    public static void wetSteamCanBeDrainedAndOverspeedStopsExtraction(GameTestHelper h){
        var be=build(h.getLevel(),h.absolutePos(BlockPos.ZERO))[0];
        h.assertTrue(be.getSteamCapability().fill(new FluidStack(Fluids.WATER,1000),EXECUTE)==0,"Steam inlet rejects liquid water");
        be.thermodynamics().input().fill(reactorSteam(1000),EXECUTE);be.setSpeed(300);
        h.assertTrue(be.thermodynamics().capacityWork()==0 && be.thermodynamics().input().getFluidAmount()==1000 && be.thermodynamics().work()==0,"Overrunning shaft publishes no capacity and a preview spends no feed");h.succeed();
    }

    @GameTest(template="test_fractionation",timeoutTicks=180)
    public static void turbinePublishesCapacityBeforeStartingAgainstAnAttachedLoad(GameTestHelper h){
        var level=h.getLevel();var base=h.absolutePos(BlockPos.ZERO);var pair=build(level,base);var hp=pair[0];
        // Keep a real Create load on the output before steam arrives; startup must not depend on an unloaded test shaft.
        level.setBlock(base.offset(17,4,10),AllBlocks.ENCASED_FAN.getDefaultState().setValue(BlockStateProperties.FACING,Direction.EAST),3);
        h.runAfterDelay(40,()->{
            hp.thermodynamics().input().fill(steam(4000,750),EXECUTE);
            var facility=TurbineFacility.find(level,hp.getBlockPos(),hp.getBlockState());
            h.assertTrue(hp.thermodynamics().work()==0 && TurbineFacility.totalSU(level,facility)>0,
                "Stored steam publishes prospective stress capacity before the first expansion tick");
        });
        h.runAfterDelay(130,()->{
            h.assertTrue(hp.thermodynamics().totalWork()>0 && hp.thermodynamics().input().getFluidAmount()<4000,
                "Steam expansion bootstraps the generator even when a load was already attached");
            h.succeed();
        });
    }


    private static FluidStack reactorSteam(int n){return new FluidStack(CEProcessFluids.HIGH_PRESSURE_STEAM.get(),n);}
    /** Joins the HP end ring to the first LP rotor alone, as a set is piped when each stage has one working rotor. */
    private static void singleInterstageMain(Level l,BlockPos base){
        var pipe=CETransport.STAINLESS_PIPE.get().defaultBlockState();
        for(int x=5;x<=13;x++)l.setBlock(base.offset(x,7,10),Blocks.AIR.defaultBlockState(),3);
        for(int y=6;y<=8;y++)l.setBlock(base.offset(12,y,10),pipe,3);
        for(int x=5;x<=11;x++)l.setBlock(base.offset(x,8,10),pipe,3);
        l.setBlock(base.offset(5,7,10),pipe,3);
        var pump=base.offset(10,8,10);l.setBlock(pump,AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(BlockStateProperties.FACING,Direction.WEST),3);
        l.setBlock(pump.above(),AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS,Direction.Axis.X),3);
        l.setBlock(pump.above().east(),AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,Direction.WEST),3);
        ((com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity)l.getBlockEntity(pump.above().east())).generatedSpeed.setValue(128);
    }
    private static void keepFed(SteamTurbineBlockEntity high,SteamTurbineBlockEntity low){
        int room=8192-high.thermodynamics().input().getFluidAmount();
        if(room>0)high.thermodynamics().input().fill(reactorSteam(room),EXECUTE);
        low.thermodynamics().exhaust().drain(Integer.MAX_VALUE,EXECUTE);low.thermodynamics().removeCondenserHeat(Double.MAX_VALUE);
    }

    @GameTest(template="test_fractionation",timeoutTicks=200)
    public static void aPumpLineOnTwoCasingFacesLosesNoSteam(GameTestHelper h){
        var l=h.getLevel();var base=h.absolutePos(BlockPos.ZERO);var hp=build(l,base)[0];var tankPos=base.offset(18,2,8);var pump=base.offset(16,6,8);
        for(int y=0;y<3;y++)l.setBlock(tankPos.above(y),AllBlocks.FLUID_TANK.getDefaultState(),3);
        var pipe=CETransport.STAINLESS_PIPE.get().defaultBlockState();
        for(var cell:new BlockPos[]{base.offset(18,5,8),base.offset(18,6,8),base.offset(17,6,8),base.offset(15,6,8),base.offset(14,6,8),base.offset(13,6,8),base.offset(13,5,8),base.offset(13,4,8)})l.setBlock(cell,pipe,3);
        l.setBlock(pump,AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(BlockStateProperties.FACING,Direction.WEST),3);
        l.setBlock(pump.above(),AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS,Direction.Axis.X),3);
        l.setBlock(pump.above().east(),AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,Direction.WEST),3);
        ((com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity)l.getBlockEntity(pump.above().east())).generatedSpeed.setValue(128);
        int[] tankMass={-1};
        hp.thermodynamics().exhaust().setFluid(new FluidStack(CEFluids.STEAM.get(),TurbineThermal.CAPACITY));
        h.runAfterDelay(20,()->{
            var tank=(com.simibubi.create.content.fluids.tank.FluidTankBlockEntity)l.getBlockEntity(tankPos);
            com.simibubi.create.api.connectivity.ConnectivityHandler.formMulti(tank);
            tank.getControllerBE().getTankInventory().fill(new FluidStack(CEFluids.STEAM.get(),16000),EXECUTE);
            tankMass[0]=16000;
        });
        // Held ten mB short of full at a low pressure, the rotor takes ten mB a tick however many faces offer it.
        h.onEachTick(()->{
            if(tankMass[0]<0)return;
            int now=((com.simibubi.create.content.fluids.tank.FluidTankBlockEntity)l.getBlockEntity(tankPos)).getControllerBE().getTankInventory().getFluidAmount();
            h.assertTrue(tankMass[0]-now<=10,"A line touching two casing faces of one rotor drains only what the rotor takes: "+(tankMass[0]-now)+" mB");
            tankMass[0]=now;
            hp.thermodynamics().input().setFluid(new FluidStack(CEFluids.STEAM.get(),hp.thermodynamics().input().getCapacity()-10));
        });
        h.runAfterDelay(150,()->{
            h.assertTrue(tankMass[0]<16000,"The rotor did take steam through the line");
            h.succeed();
        });
    }

    @GameTest(template="test_fractionation",timeoutTicks=200)
    public static void steamBelowTheHighPressureBackPressureCrossesToTheLowPressureStage(GameTestHelper h){
        var level=h.getLevel();var base=h.absolutePos(BlockPos.ZERO);var pair=build(level,base);var hp=pair[0];var lp=pair[1];
        singleInterstageMain(level,base);
        h.onEachTick(()->h.assertTrue(!hp.thermodynamics().status().equals("wet"),"Dry steam is never reported as wet"));
        h.runAfterDelay(20,()->hp.thermodynamics().input().fill(new FluidStack(CEFluids.STEAM.get(),TurbineThermal.CAPACITY),EXECUTE));
        h.runAfterDelay(80,()->{
            h.assertTrue(hp.thermodynamics().input().isEmpty() && hp.thermodynamics().totalWork()==0 && lp.thermodynamics().totalWork()>0,
                "5 bar steam does no work in the HP stage, crosses it and works in the LP stage: "+hp.thermodynamics().status()+" in="+hp.thermodynamics().input().getFluidAmount()+" lp="+lp.thermodynamics().totalWork());
            lp.thermodynamics().exhaust().drain(TurbineThermal.CAPACITY,EXECUTE);
            hp.thermodynamics().input().fill(reactorSteam(4000),EXECUTE);
        });
        h.runAfterDelay(120,()->{
            h.assertTrue(hp.thermodynamics().totalWork()>0,"The same stage works again as soon as the pressure returns");
            h.succeed();
        });
    }

    @GameTest(template="test_fractionation",timeoutTicks=500)
    public static void aSetCarriesItsGeneratorThroughStartAndRestart(GameTestHelper h){
        var level=h.getLevel();var base=h.absolutePos(BlockPos.ZERO);var pair=build(level,base);var hp=pair[0];var lp=pair[1];
        singleInterstageMain(level,base);
        for(int x=17;x<=24;x++){
            var rotor=base.offset(x,4,10);
            level.setBlock(rotor,com.george_vi.electroenergetics.CEEBlocks.ALTERNATOR_ROTOR.getDefaultState().setValue(BlockStateProperties.AXIS,Direction.Axis.X),3);
            for(var side:new Direction[]{Direction.NORTH,Direction.SOUTH,Direction.UP,Direction.DOWN})level.setBlock(rotor.relative(side),com.george_vi.electroenergetics.CEEBlocks.STATOR.getDefaultState()
                .setValue(com.george_vi.electroenergetics.content.rotor.StatorBlock.FACING,side.getOpposite()).setValue(com.george_vi.electroenergetics.content.rotor.StatorBlock.ROLL,true),3);
        }
        boolean[] feeding={false};
        h.onEachTick(()->{if(feeding[0])keepFed(hp,lp);});
        h.runAfterDelay(40,()->feeding[0]=true);
        h.runAfterDelay(160,()->{
            var generator=(com.simibubi.create.content.kinetics.base.KineticBlockEntity)level.getBlockEntity(base.offset(17,4,10));
            h.assertTrue(Math.abs(generator.getSpeed())==256 && !generator.isOverStressed() && hp.thermodynamics().totalWork()>0,
                "Steam starts a set against a generator coupled before it: "+generator.getSpeed()+" RPM, "+hp.thermodynamics().status());
            feeding[0]=false;
        });
        h.runAfterDelay(260,()->{
            var generator=(com.simibubi.create.content.kinetics.base.KineticBlockEntity)level.getBlockEntity(base.offset(17,4,10));
            h.assertTrue(generator.isOverStressed() && hp.thermodynamics().input().isEmpty(),"With the steam spent the generator stalls: "+hp.thermodynamics().status());
            feeding[0]=true;
        });
        h.runAfterDelay(380,()->{
            var generator=(com.simibubi.create.content.kinetics.base.KineticBlockEntity)level.getBlockEntity(base.offset(17,4,10));
            h.assertTrue(Math.abs(generator.getSpeed())==256 && !generator.isOverStressed() && hp.thermodynamics().status().equals("running"),
                "Steam restarts the stalled set under the same load: "+generator.getSpeed()+" RPM, "+hp.thermodynamics().status());
            h.succeed();
        });
    }

    @GameTest(template="test_fractionation",timeoutTicks=200)
    public static void theInterstageMainCarriesAFullRotorFlow(GameTestHelper h){
        var level=h.getLevel();var base=h.absolutePos(BlockPos.ZERO);var pair=build(level,base,new int[]{13,14,15,5,6,7,8,9});var hp=pair[0];var lp=pair[1];
        singleInterstageMain(level,base);
        long[] start={0};
        h.onEachTick(()->keepFed(hp,lp));
        // One surge is enough: the LP inlet no longer empties between transfers.
        h.runAfterDelay(30,()->lp.thermodynamics().input().fill(new FluidStack(CEFluids.STEAM.get(),14000),EXECUTE));
        h.runAfterDelay(60,()->start[0]=lp.thermodynamics().totalCondensate());
        h.runAfterDelay(140,()->{
            double flow=(lp.thermodynamics().totalCondensate()-start[0])/80d;
            h.assertTrue(flow>=TurbineThermal.MAX_FLOW-1,"One HP rotor feeds one LP rotor at a full rotor flow: "+flow+" mB/t");
            h.succeed();
        });
    }

    @GameTest(template="test_fractionation")
    public static void lowPressureFacilityUsesSurfaceCondenserVacuumAndThirtyDegreeDischarge(GameTestHelper h){
        var pair=build(h.getLevel(),h.absolutePos(BlockPos.ZERO));var low=pair[1].thermodynamics();
        low.input().fill(new FluidStack(CEFluids.STEAM.get(),1000),EXECUTE);low.step(true,10);
        h.assertTrue(low.exhaust().getFluid().is(Fluids.WATER) && low.exhaust().getFluidAmount()==10 && low.work()==2500 && low.condenserJoules()==17580,"Terminal stage returns ordinary water and fixed work and rejection");h.succeed();
    }
}
