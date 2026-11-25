package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.createplant.CreatePlant;
import net.createplant.block.*;
import net.createplant.block.entity.*;
import net.createplant.chemistry.ReactorModule;
import net.createplant.registry.*;
import net.createplant.thermal.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public class ThermalControlGameTests {
    /** A shell-and-tube exchanger at least two slices long running to the right of {@code front}. */
    public static ReactorMachineBlockEntity build(net.minecraft.world.level.Level level,BlockPos base,Direction front,int length) {
        return exchanger(level,base,front.getClockWise(),Math.max(2,length),ExchangerStructure.Kind.SHELL);
    }
    /** Places a run of exchanger casings lying along {@code along}, as a player would; its controller is then at {@code first}. */
    public static ReactorMachineBlockEntity exchanger(net.minecraft.world.level.Level level,BlockPos first,Direction along,int length,ExchangerStructure.Kind kind) {
        for (int index=0;index<length;index++) for (int across=0;across<kind.width;across++) for (int up=0;up<kind.width;up++)
            level.setBlock(first.relative(along,index).relative(along.getClockWise(),across).above(up),kind.casing().defaultBlockState().setValue(ThermalCasingBlock.FACING,along),3);
        var be=(ReactorMachineBlockEntity)level.getBlockEntity(first);
        be.validateStructure();return be;
    }
    /** The tube side (a jacketed pipe's core) at the first or last end face. */
    public static net.neoforged.neoforge.fluids.capability.IFluidHandler tube(ReactorMachineBlockEntity be,int end) {
        Direction along=be.getBlockState().getValue(ThermalMachineBlock.FACING);int cells=be.exchanger().width*be.exchanger().width;
        return end==0 ? be.horizontalPort(be.members().getFirst(),along.getOpposite()) : be.horizontalPort(be.members().get(be.members().size()-cells),along);
    }
    /** The shell side (a jacketed pipe's jacket) on the top of the first end, and the top (the jacket: bottom) of the last. */
    public static net.neoforged.neoforge.fluids.capability.IFluidHandler shell(ReactorMachineBlockEntity be,int end) {
        var kind=be.exchanger();int top=kind.width-1,cells=kind.width*kind.width;
        return end==0 ? be.horizontalPort(be.members().get(top),Direction.UP)
            : be.horizontalPort(be.members().get(be.members().size()-cells+top),kind==ExchangerStructure.Kind.JACKET ? Direction.DOWN : Direction.UP);
    }
    @GameTest(template="test_reactor")
    public static void automaticExchangeConvertsBothFiniteStreamsWithoutASetting(GameTestHelper h) {
        var be=build(h.getLevel(),h.absolutePos(new BlockPos(5,2,5)),Direction.NORTH,3);
        be.thermal().tank(0).fill(new FluidStack(Fluids.WATER,1000),EXECUTE);be.thermal().tank(2).fill(FluidState.with(new FluidStack(CEPetroFluids.HEAVY_OIL.get(),300),11,4),EXECUTE);
        for(int i=0;i<5;i++)be.thermal().tick();
        h.assertTrue(be.thermal().tank(0).getFluidAmount()==700 && FluidState.temperature(be.thermal().tank(1).getFluid())==9 && be.thermal().tank(1).getFluidAmount()==300 && be.thermal().tank(2).isEmpty() && be.thermal().tank(3).getFluidAmount()==300,"Both rows convert the same finite batch count");h.succeed();
    }
    @GameTest(template="test_fractionation")
    public static void parallelAndCounterflowLevelsAndRate(GameTestHelper h) {
        for(int heat=0;heat<=16;heat++)for(int cold=0;cold<heat;cold++) {
            // Two liquid streams of the same substance: parallel flow ends at the mean, counterflow moves 0.9 of the difference.
            var a=FluidState.with(new FluidStack(net.createplant.registry.CEMetals.ZIRCONIUM_SULFATE_SOLUTION.get(),1000),heat,4);var b=FluidState.with(new FluidStack(net.createplant.registry.CEMetals.ZIRCONIUM_SULFATE_SOLUTION.get(),1000),cold,4);
            double kelvinHot=FluidState.kelvin(heat),kelvinCold=FluidState.kelvin(cold);
            var parallel=ThermalEquipment.exchange(a,kelvinHot,b,kelvinCold,false);
            h.assertTrue(parallel.firstHot() && Math.abs(parallel.first().kelvin()-parallel.second().kelvin())<.01 && parallel.first().kelvin()<=kelvinHot && parallel.second().kelvin()>=kelvinCold,"Parallel flow ends both streams at one temperature without gaining heat");
            var counter=ThermalEquipment.exchange(a,kelvinHot,b,kelvinCold,true);
            h.assertTrue(Math.abs(counter.first().kelvin()-(kelvinHot-.9*(kelvinHot-kelvinCold)))<.01 && Math.abs(counter.second().kelvin()-(kelvinCold+.9*(kelvinHot-kelvinCold)))<.01,"Opposing inlets move 0.9 of the temperature difference");
        }
        var be=build(h.getLevel(),h.absolutePos(new BlockPos(3,2,2)),Direction.NORTH,2);
        var hot=FluidState.with(new FluidStack(CEPetroFluids.HEAVY_OIL.get(),500),11,4);
        var cold=FluidState.with(new FluidStack(Fluids.WATER,500),5,8);
        tube(be,0).fill(hot,EXECUTE);
        shell(be,1).fill(cold,EXECUTE);
        for(int i=0;i<5;i++)be.tickProcess();
        var hotResult=tube(be,1).drain(500,EXECUTE);
        var coldResult=shell(be,0).drain(500,EXECUTE);
        // Counterflow moves 0.9 of the difference times the smaller capacity, the oil's: the oil falls to about -5 C, the water rises to about 71 C.
        h.assertTrue(hotResult.getAmount()==200 && coldResult.getAmount()==200 && FluidState.temperature(hotResult)==6
            && FluidState.temperature(coldResult)==9 && FluidState.pressure(coldResult)==8,"Actual opposing ports move equal batches with no rate bonus: "
            +FluidState.temperature(hotResult)+"/"+FluidState.temperature(coldResult));
        h.succeed();
    }

    @GameTest(template="test_fractionation")
    public static void exchangerCondensesWholeUnitsAndRetainsBothRemainders(GameTestHelper h) {
        var be=build(h.getLevel(),h.absolutePos(new BlockPos(3,2,2)),Direction.NORTH,2);
        var gas=new FluidStack(CEChlorAlkali.CHLORINE.get(),1);
        int ratio=PhaseChange.ratio(gas), amount=2*ratio+17;
        tube(be,0).fill(gas.copyWithAmount(amount),EXECUTE);
        shell(be,0).fill(FluidState.with(new FluidStack(Fluids.WATER,amount),0,4),EXECUTE);
        for(int i=0;i<100;i++)be.tickProcess();
        double difference=Math.abs(be.thermal().tank(1).kelvin()-be.thermal().tank(3).kelvin());
        var liquid=tube(be,1).drain(amount,EXECUTE);
        var coolant=shell(be,1).drain(amount,EXECUTE);
        h.assertTrue(liquid.getAmount()==2 && !FluidState.gas(liquid) && coolant.getAmount()==2*ratio
            && be.thermal().tank(0).getFluidAmount()==17 && be.thermal().tank(2).getFluidAmount()==17,
            "Pairwise phase conversion retains both remainders and consumes only complete batches: "+liquid+" / "+coolant);
        h.assertTrue(FluidState.temperature(liquid)==0 && difference<.01,
            "The large cold stream cools the two condensed units past boiling to the common outlet temperature: level="
                +FluidState.temperature(liquid)+", delta="+difference);
        h.succeed();
    }

    @GameTest(template="test_fractionation")
    public static void exchangerCasingsLieAlongTheWayThePlayerLooks(GameTestHelper h) {
        var player=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
        var pos=h.absolutePos(new BlockPos(3,2,3));
        for(var front:Direction.Plane.HORIZONTAL) for(var kind:ExchangerStructure.Kind.values()) {
            player.setYRot(front.toYRot());
            var stack=new net.minecraft.world.item.ItemStack(kind.casing());player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,stack);
            var ctx=new net.minecraft.world.item.context.BlockPlaceContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,stack,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),Direction.UP,pos,false));
            var state=kind.casing().getStateForPlacement(ctx);
            h.assertTrue(state.getValue(ThermalCasingBlock.FACING)==front,"A casing lies along the player's view");
            h.assertTrue(state.rotate(Rotation.CLOCKWISE_90).getValue(ThermalCasingBlock.FACING)==front.getClockWise(),"Schematic rotation turns the run with it");
        }
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,net.minecraft.world.item.ItemStack.EMPTY);h.succeed();
    }
    @GameTest(template="test_fractionation")
    public static void rowDirectionSimulationReloadAndReversalPreserveFluid(GameTestHelper h) {
        var be=build(h.getLevel(),h.absolutePos(new BlockPos(5,2,5)),Direction.NORTH,2);var near=tube(be,0);var far=tube(be,1);var fluid=new FluidStack(Fluids.WATER,100);
        var before=be.saveWithoutMetadata(h.getLevel().registryAccess());h.assertTrue(near.fill(fluid,SIMULATE)==100 && before.equals(be.saveWithoutMetadata(h.getLevel().registryAccess())),"Simulation does not claim direction");
        h.assertTrue(far.fill(fluid,EXECUTE)==100 && near.fill(fluid,EXECUTE)==0,"An executed fill claims only this row");shell(be,0).fill(FluidForm.WARM_WATER.stack(100),EXECUTE);
        for(int i=0;i<5;i++)be.tickProcess();h.assertTrue(near.drain(17,EXECUTE).getAmount()==17,"Partial withdrawal returns its exact debit");
        var saved=be.saveWithoutMetadata(h.getLevel().registryAccess());be.loadWithComponents(saved,h.getLevel().registryAccess());be.validateStructure();near=tube(be,0);far=tube(be,1);
        h.assertTrue(near.drain(100,EXECUTE).getAmount()==83,"Reload retains direction and remaining output");shell(be,1).drain(100,EXECUTE);
        h.assertTrue(near.fill(fluid,EXECUTE)==100,"An empty row can reverse");shell(be,0).fill(FluidForm.WARM_WATER.stack(100),EXECUTE);for(int i=0;i<5;i++)be.tickProcess();
        var reversed=far.drain(100,EXECUTE);h.assertTrue(reversed.getAmount()==100 && FluidState.temperature(reversed)==8,
            "Reversal keeps the same recipe and amount: "+reversed+" level="+FluidState.temperature(reversed)+" status="+be.status());h.succeed();
    }
    @GameTest(template="test_fractionation",timeoutTicks=600)
    public static void realPumpsOperateAllFourHorizontalExchangerPorts(GameTestHelper h) { rowPumpPlant(h,false,false,true); }
    @GameTest(template="test_fractionation",timeoutTicks=600)
    public static void pipesPlacedBeforeTheStackedExchangerReconnectAfterAssembly(GameTestHelper h) { rowPumpPlant(h,true,false,true); }
    @GameTest(template="test_fractionation",timeoutTicks=600)
    public static void exchangerBothRowsFlowFromNearEnd(GameTestHelper h) { rowPumpPlant(h,false,false,false); }
    @GameTest(template="test_fractionation",timeoutTicks=600)
    public static void exchangerBothRowsFlowFromFarEnd(GameTestHelper h) { rowPumpPlant(h,false,true,true); }
    @GameTest(template="test_fractionation",timeoutTicks=600)
    public static void exchangerLowerReverseUpperForward(GameTestHelper h) { rowPumpPlant(h,false,true,false); }
    /**
     * Real pumps and encased pipes on all four ports of a three-slice shell-and-tube exchanger along east: the tube side through the
     * two end faces at the bottom, the shell side up from the top of each end and away at the height of the pipes above it.
     */
    private static void rowPumpPlant(GameTestHelper h,boolean pipesFirst,boolean tubeReverse,boolean shellReverse) {
        var level=h.getLevel();var base=h.absolutePos(new BlockPos(7,2,8));
        if (!pipesFirst) exchanger(level,base,Direction.EAST,3,ExchangerStructure.Kind.SHELL);
        // Until the exchanger is built, blocks in its place keep the pipe ends closed.
        else for(var p:BlockPos.betweenClosed(base,base.offset(2,1,1))) level.setBlock(p,Blocks.IRON_BLOCK.defaultBlockState(),3);
        java.util.Map<BlockPos,Integer> motors=new java.util.HashMap<>();
        BlockPos coldSource=base.offset(-5,0,0),coldTarget=base.offset(7,0,0),hotSource=base.offset(8,2,0),hotTarget=base.offset(-6,2,0);
        if (tubeReverse) { var swap=coldSource;coldSource=coldTarget;coldTarget=swap; }
        if (!shellReverse) { var swap=hotSource;hotSource=hotTarget;hotTarget=swap; }
        final var coldFeed=coldSource;final var coldProduct=coldTarget;final var hotFeed=hotSource;final var hotProduct=hotTarget;
        for(var p:new BlockPos[]{coldSource,coldTarget,hotSource,hotTarget}) level.setBlock(p,com.simibubi.create.AllBlocks.FLUID_TANK.get().defaultBlockState(),3);
        for(int row=0;row<2;row++) {
            int yy=row*2;
            for(int dx=-5;dx<=7;dx++) {
                if(dx>=1 && dx<=1 || row==0 && (dx==-5 || dx==7) || row==0 && dx>=0 && dx<=2) continue;
                var p=base.offset(dx,yy,0);boolean pump=dx==-3 || dx==5;
                if(pump) {
                    Direction flow=(row==0 ? tubeReverse : shellReverse) ? Direction.WEST : Direction.EAST;
                    level.setBlock(p,AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(BlockStateProperties.FACING,flow),3);
                    var cog=p.north();level.setBlock(cog,AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS,Direction.Axis.X),3);
                    var motor=cog.east();level.setBlock(motor,AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,Direction.WEST),3);motors.put(motor,row==1 ? -128 : 128);
                } else {
                    // Above each end the shell pipe turns down into the top of the exchanger.
                    boolean down=row==1 && (dx==0 || dx==2);
                    var state=AllBlocks.ENCASED_FLUID_PIPE.getDefaultState();
                    for(var d:Direction.values()) state=state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(d),down ? d==Direction.DOWN || d==(dx==0 ? Direction.WEST : Direction.EAST) : d.getAxis()==Direction.Axis.X);
                    level.setBlock(p,state,3);
                }
            }
        }
        h.runAfterDelay(5,()->{
            for(var entry:motors.entrySet()) ((CreativeMotorBlockEntity)level.getBlockEntity(entry.getKey())).generatedSpeed.setValue(entry.getValue());
            ((FluidTankBlockEntity)level.getBlockEntity(coldFeed)).getTankInventory().fill(FluidThermal.at(new FluidStack(Fluids.WATER,1000),300),EXECUTE);
            ((FluidTankBlockEntity)level.getBlockEntity(hotFeed)).getTankInventory().fill(FluidForm.WARM_WATER.stack(1000),EXECUTE);
        });
        if (pipesFirst) h.runAfterDelay(30,()->exchanger(level,base,Direction.EAST,3,ExchangerStructure.Kind.SHELL));
        h.runAfterDelay(400,()->{
            var be=(ReactorMachineBlockEntity)level.getBlockEntity(base);
            var cold=((FluidTankBlockEntity)level.getBlockEntity(coldProduct)).getTankInventory().getFluid();var hot=((FluidTankBlockEntity)level.getBlockEntity(hotProduct)).getTankInventory().getFluid();
            h.assertTrue(cold.is(Fluids.WATER) && cold.getAmount()==1000 && hot.is(Fluids.WATER) && hot.getAmount()==1000
                && FluidState.temperature(cold)>=8 && FluidState.temperature(hot)<=9,"Native pump networks use the four real ports without mixing: "
                +cold.getAmount()+" at "+FluidState.temperature(cold)+" / "+hot.getAmount()+" at "+FluidState.temperature(hot)+" status="+be.status());
            h.succeed();
        });
    }
}
