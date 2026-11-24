package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.createplant.CreatePlant;
import net.createplant.block.ExchangerStructure;
import net.createplant.thermal.FluidState;
import net.createplant.thermal.HeatContent;
import net.createplant.thermal.ThermalEquipment;
import net.createplant.thermal.ThermalFluidTank;
import net.createplant.thermal.TwoPhase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public class TwoPhaseGameTests {
    @GameTest(template="test_reactor")
    public static void aPartlyBoiledStreamLeavesAsGasAndLiquidAndSplits(GameTestHelper h) {
        var tank=new ThermalFluidTank(16000,stack->{ });
        tank.fillAt(water(1000),373.15,.5,EXECUTE);
        var out=tank.drain(400,EXECUTE);
        h.assertTrue(out.getAmount()==400 && TwoPhase.is(out) && TwoPhase.share(out)==.5,"A half-boiled content leaves with half its share as gas: "+out);
        var split=TwoPhase.split(out,400);
        h.assertTrue(split.gas().getAmount()+split.liquid().getAmount()==400 && FluidState.gas(split.gas()) && !FluidState.gas(split.liquid())
            && split.gas().getAmount()==200,"The separator gets 200 mB of gas and 200 mB of liquid, none made or lost: "+split);
        h.succeed();
    }

    @GameTest(template="test_reactor")
    public static void toppingUpBoilingWaterPreservesLatentHeatAndThePipeView(GameTestHelper h) {
        var tank=new ThermalFluidTank(3000,stack->{ });
        var first=TwoPhase.mark(water(1000),.25);
        var second=TwoPhase.mark(water(1000),.75);
        h.assertTrue(tank.fill(first,EXECUTE)==1000 && tank.fill(second,SIMULATE)==1000
            && tank.getFluidAmount()==1000 && TwoPhase.share(tank.getFluidInTank(0))==.25,
            "Previewing the second parcel leaves the original amount and quality unchanged");
        h.assertTrue(tank.fill(second,EXECUTE)==1000 && tank.getFluidAmount()==2000
            && Math.abs(tank.heat().quality(tank.getFluid())-.5)<1e-7,"Equal boiling parcels retain the amount-weighted vapour share");
        var reading=tank.getFluidInTank(0).copyWithAmount(400);
        h.assertTrue(TwoPhase.share(reading)==.5 && tank.drain(first,EXECUTE).isEmpty()
            && FluidStack.matches(reading,tank.drain(reading,SIMULATE))
            && FluidStack.matches(reading,tank.drain(reading,EXECUTE)),"Create's typed and simulated drains use the advertised stream state");
        h.assertTrue(TwoPhase.is(TwoPhase.mark(water(100),.01)) && TwoPhase.is(TwoPhase.mark(water(100),.99)),
            "Partially changed phases at the edge of the encoded range stay marked");
        h.succeed();
    }

    @GameTest(template="test_reactor")
    public static void coolingABoilingStreamCondensesItBeforeTheTemperatureFalls(GameTestHelper h) {
        var sample=water(1000);
        double boil=FluidState.boilingKelvin(sample,4);
        double latent=HeatContent.latent(sample);
        var wet=TwoPhase.mark(sample,.5);
        var cooled=HeatContent.shift(wet,boil,-latent/4);
        h.assertTrue(Math.abs(cooled.kelvin()-boil)<1e-6 && Math.abs(cooled.quality()-.25)<1e-6,
            "Cooling liquid-basis wet water spends the removed energy on condensation first: "+cooled);
        var warmed=ThermalEquipment.exchange(wet,boil,new FluidStack(Fluids.WATER,1000),300,false);
        h.assertTrue(warmed.first().quality()>0 && warmed.first().quality()<.5
            && Math.abs(warmed.first().kelvin()-warmed.second().kelvin())<1e-4,
            "Parallel flow moves one shared heat quantity even when the hot side condenses at its boiling point: "+warmed);
        h.succeed();
    }

    @GameTest(template="test_reactor")
    public static void parallelWaterStreamsStopExchangingAtTheirSharedBoilingPoint(GameTestHelper h) {
        var steam=FluidState.with(water(1000),10,4);
        var coldWater=FluidState.with(water(1000),7,4);
        double hot=FluidState.kelvin(10), cold=FluidState.kelvin(7), boil=FluidState.boilingKelvin(steam,4);
        double expectedQuality=1-(HeatContent.capacity(coldWater)*(boil-cold)-HeatContent.capacity(steam)*(hot-boil))/HeatContent.latent(steam);
        var exchange=ThermalEquipment.exchange(steam,hot,coldWater,cold,false);
        h.assertTrue(expectedQuality>0 && expectedQuality<1 && Math.abs(exchange.first().quality()-expectedQuality)<1e-6
            && exchange.second().quality()==0 && Math.abs(exchange.second().kelvin()-boil)<1e-4,
            "The steam partially condenses and the coolant stays liquid when their temperatures first match: "+exchange);
        h.succeed();
    }

    @GameTest(template="test_fractionation")
    public static void theSeparatorReturnsBothPhasesThroughItsNativePorts(GameTestHelper h) {
        var level=h.getLevel();
        var at=h.absolutePos(new BlockPos(4,2,4));
        var separator=HaberBoschGameTests.separator(level,at);
        var inlet=level.getCapability(Capabilities.FluidHandler.BLOCK,at.above(),Direction.NORTH);
        var fluid=TwoPhase.mark(water(1200),.25);
        h.assertTrue(inlet!=null && inlet.fill(fluid,SIMULATE)==1200 && separator.water().isEmpty() && separator.steam().isEmpty(),
            "The formed inlet previews both outputs without mutating them");
        h.assertTrue(inlet.fill(fluid,EXECUTE)==1200,"The formed inlet accepts the complete parcel");
        var liquid=level.getCapability(Capabilities.FluidHandler.BLOCK,at,Direction.DOWN);
        var gas=level.getCapability(Capabilities.FluidHandler.BLOCK,at.above(),Direction.UP);
        h.assertTrue(liquid!=null && gas!=null,"The separator exposes a bottom and top outlet");
        var drainedLiquid=liquid.drain(2000,EXECUTE);
        var drainedGas=gas.drain(2000,EXECUTE);
        h.assertTrue(drainedLiquid.getAmount()==900 && FluidState.liquidWater(drainedLiquid)
            && drainedGas.getAmount()==300 && FluidState.gas(drainedGas)
            && !TwoPhase.is(drainedGas) && !TwoPhase.is(drainedLiquid),"Both single phases leave their own outlet");
        h.succeed();
    }

    @GameTest(template="test_fractionation",timeoutTicks=180)
    public static void createPumpCarriesAnExchangerStreamToTheSeparator(GameTestHelper h) {
        var level=h.getLevel();
        var at=h.absolutePos(new BlockPos(6,3,6));
        var exchanger=ThermalControlGameTests.exchanger(level,at,Direction.EAST,2,ExchangerStructure.Kind.SHELL);
        var separator=HaberBoschGameTests.separator(level,at.east(5).below());
        var pump=at.east(3);
        level.setBlock(pump,AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(BlockStateProperties.FACING,Direction.EAST),3);
        for(var pos:new BlockPos[]{pump.west(),pump.east()}) {
            var pipe=AllBlocks.ENCASED_FLUID_PIPE.getDefaultState();
            for(var direction:Direction.values())pipe=pipe.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(direction),direction.getAxis()==Direction.Axis.X);
            level.setBlock(pos,pipe,3);
        }
        level.setBlock(pump.north(),AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS,Direction.Axis.X),3);
        var motor=pump.north().east();
        level.setBlock(motor,AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,Direction.WEST),3);
        h.runAfterDelay(10,()->{
            var drive=(CreativeMotorBlockEntity)level.getBlockEntity(motor);
            drive.generatedSpeed.setValue(128);
            h.assertTrue(ThermalControlGameTests.tube(exchanger,0).fill(TwoPhase.mark(water(1200),.5),EXECUTE)==1200
                && ThermalControlGameTests.shell(exchanger,0).fill(new FluidStack(Fluids.WATER,1200),EXECUTE)==1200,
                "Both real exchanger inlets accept the test streams");
        });
        h.runAfterDelay(150,()->{
            int liquid=separator.water().getAmount(), gas=separator.steam().getAmount();
            h.assertTrue(liquid+gas==1200 && gas>0 && liquid>gas,
                "A Create pump preserves mass and both phases after partial cooling: "+liquid+" liquid, "+gas+" steam");
            h.succeed();
        });
    }

    private static FluidStack water(int amount) {
        return FluidState.with(new FluidStack(Fluids.WATER,amount),9,4);
    }
}
