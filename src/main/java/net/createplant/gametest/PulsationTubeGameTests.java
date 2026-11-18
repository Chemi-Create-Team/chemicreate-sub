package net.createplant.gametest;

import java.util.*;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.createplant.CreatePlant;
import net.createplant.block.PulsationTubeBlock;
import net.createplant.block.entity.PulsationTubeBlockEntity;
import net.createplant.registry.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class PulsationTubeGameTests {
    private static final BlockPos AT=new BlockPos(2,2,2);
    private static net.neoforged.neoforge.fluids.capability.IFluidHandler port(GameTestHelper h,BlockPos at,Direction side){return h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,h.absolutePos(at),side);}
    private static PulsationTubeBlockEntity row(GameTestHelper h,BlockPos at,Direction.Axis axis){
        var along=Direction.fromAxisAndDirection(axis,Direction.AxisDirection.POSITIVE);
        for(int n=0;n<3;n++)h.setBlock(at.relative(along,n),CETransport.GAS_RECEIVER.get().defaultBlockState().setValue(PulsationTubeBlock.AXIS,axis));
        var be=(PulsationTubeBlockEntity)h.getBlockEntity(at);ConnectivityHandler.formMulti(be);return (PulsationTubeBlockEntity)be.getControllerBE();
    }
    @GameTest(template = "test_fractionation")
    public static void theSameTubeFitsSixteenTimesAsMuchCompressedAir(GameTestHelper h) {
        var root = row(h, AT, Direction.Axis.X);
        var pipe = port(h, AT, Direction.WEST);
        var tank = (net.createplant.thermal.GradeFluidTank) root.getTankInventory();
        int volume = tank.volumeCapacity();
        var raw = new FluidStack(CEFluids.AIR_GAS.get(), volume * 16);
        var compressed = net.createplant.thermal.Compression.convert(raw, true);
        h.assertTrue(pipe.fill(raw, EXECUTE) == volume && tank.gasVolume() == volume, "Ambient air uses the whole tube at its physical capacity");
        pipe.drain(volume, EXECUTE);
        h.assertTrue(pipe.fill(compressed.copyWithAmount(volume), EXECUTE) == volume && tank.gasVolume() == volume / 16,
            "The same quantity fills one sixteenth of the tube when compressed");
        h.assertTrue(pipe.fill(compressed, SIMULATE) == volume * 15 && tank.getFluidAmount() == volume,
            "Simulation reports spare capacity without changing stock");
        h.assertTrue(pipe.fill(compressed, EXECUTE) == volume * 15 && tank.gasVolume() == volume,
            "A full tube holds sixteen times as much air at the higher pressure");
        tank.setFluid(raw.copyWithAmount(volume * 2));
        var lookup = h.getLevel().registryAccess();
        root.loadWithComponents(root.saveWithoutMetadata(lookup), lookup);
        var restored = port(h, AT, Direction.WEST);
        h.assertTrue(restored.fill(raw, EXECUTE) == 0 && restored.drain(volume * 2, EXECUTE).getAmount() == volume * 2,
            "An old over-capacity ambient stock stays recoverable after loading");
        h.succeed();
    }

    @GameTest(template="test_fractionation")
    public static void standardCreateTanksRefuseEveryRegisteredGasButKeepLiquids(GameTestHelper h){
        h.setBlock(AT,AllBlocks.FLUID_TANK.get());var be=(FluidTankBlockEntity)h.getBlockEntity(AT);var handler=port(h,AT,Direction.UP);int count=0;
        for(var fluid:BuiltInRegistries.FLUID){
            var stack=new FluidStack(fluid,1000);if(stack.isEmpty() || net.createplant.mixture.MixtureHelper.isMixture(stack) || !CETransport.gas(stack))continue;count++;
            h.assertTrue(!handler.isFluidValid(0,stack) && handler.fill(stack,SIMULATE)==0 && handler.fill(stack,EXECUTE)==0 && be.getTankInventory().isEmpty(),"Create liquid tanks reject gas through actual ports: "+fluid);
        }
        h.assertTrue(count>=10 && handler.fill(new FluidStack(Fluids.WATER,1000),EXECUTE)==1000,"Water remains accepted after all rejected gases");
        for(String id:List.of("liquid_tank","water_tank")){
            var name=CreatePlant.location(id);h.assertTrue(!BuiltInRegistries.BLOCK.containsKey(name) && !BuiltInRegistries.ITEM.containsKey(name),"Removed tank has no block or item registration: "+id);
            h.assertTrue(h.getLevel().getRecipeManager().byKey(name).isEmpty(),"Removed tank has no recipe: "+id);
        }
        h.succeed();
    }
    @GameTest(template="test_fractionation")
    public static void tubesConnectOnEveryAxisWithSharedGasAndOnlyEndPorts(GameTestHelper h){
        int index=0;
        for(var axis:Direction.Axis.values()){
            var at=AT.offset(index++*4,0,0);var root=row(h,at,axis);var along=Direction.fromAxisAndDirection(axis,Direction.AxisDirection.POSITIVE);
            var inlet=port(h,at,along.getOpposite());var outlet=port(h,at.relative(along,2),along);
            h.assertTrue(root.getWidth()==1 && root.getHeight()==3 && inlet!=null && outlet!=null,"A three-part tube forms on "+axis);
            var gas=new FluidStack(CEFluids.NITROGEN_GAS.get(),24000);var before=root.saveWithoutMetadata(h.getLevel().registryAccess());
            h.assertTrue(inlet.fill(gas,SIMULATE)==24000 && before.equals(root.saveWithoutMetadata(h.getLevel().registryAccess())),"Simulation leaves shared gas unchanged");
            h.assertTrue(inlet.fill(gas,EXECUTE)==24000 && outlet.getFluidInTank(0).getAmount()==24000 && inlet.fill(new FluidStack(Fluids.WATER,1000),EXECUTE)==0,"Gas is shared, liquids rejected");
            for(var side:Direction.values())if(side.getAxis()!=axis)h.assertTrue(port(h,at.relative(along),side)==null,"The tube wall has no false side port");
            var saved=root.saveWithoutMetadata(h.getLevel().registryAccess());root.loadWithComponents(saved,h.getLevel().registryAccess());
            h.assertTrue(outlet.drain(7000,EXECUTE).getAmount()==7000 && root.getTankInventory().getFluidAmount()==17000,"Reload and partial draining conserve gas");
        }
        h.succeed();
    }
    @GameTest(template="test_fractionation")
    public static void rotatingAFilledTubeKeepsEveryParcelAndRetiresWrongAxisPorts(GameTestHelper h){
        var root=row(h,AT,Direction.Axis.X);port(h,AT,Direction.WEST).fill(new FluidStack(CEFluids.NITROGEN_GAS.get(),36000),EXECUTE);
        var old=port(h,AT,Direction.WEST);var world=h.getLevel();var absolute=h.absolutePos(AT);
        var player=FakePlayerFactory.getMinecraft(world);var context=new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(absolute),Direction.UP,absolute,false));
        CETransport.GAS_RECEIVER.get().onWrenched(world.getBlockState(absolute),context);
        h.assertTrue(world.getBlockState(absolute).getValue(PulsationTubeBlock.AXIS)==Direction.Axis.Y && old.drain(1,EXECUTE).isEmpty(),"Wrench changes axis and old side stops flowing");
        int total=0;var counted=new HashSet<BlockPos>();
        for(int x=0;x<3;x++){var part=(PulsationTubeBlockEntity)h.getBlockEntity(AT.east(x));var owner=part.getControllerBE();if(counted.add(owner.getBlockPos()))total+=owner.getTankInventory().getFluidAmount();}
        h.assertTrue(total==36000,"Splitting for rotation preserves all compressed gas");h.succeed();
    }
    @GameTest(template="test_fractionation",timeoutTicks=250)
    public static void twoNativePumpsMoveFiniteGasThroughTheTubeWithoutLoss(GameTestHelper h){
        var source=new BlockPos(1,2,5);var tube=new BlockPos(4,2,5);var target=new BlockPos(9,2,5);row(h,tube,Direction.Axis.X);
        h.setBlock(source,CETransport.GAS_RECEIVER.get().defaultBlockState().setValue(PulsationTubeBlock.AXIS,Direction.Axis.X));h.setBlock(target,CETransport.GAS_RECEIVER.get().defaultBlockState().setValue(PulsationTubeBlock.AXIS,Direction.Axis.X));
        for(int x:new int[]{2,3,7,8})h.setBlock(new BlockPos(x,2,5),AllBlocks.FLUID_PIPE.getDefaultState().setValue(FluidPipeBlock.EAST,true).setValue(FluidPipeBlock.WEST,true));
        for(int x:new int[]{2,7})h.setBlock(new BlockPos(x,2,5),AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(PumpBlock.FACING,Direction.EAST));
        var from=((FluidTankBlockEntity)h.getBlockEntity(source)).getTankInventory();var to=((FluidTankBlockEntity)h.getBlockEntity(target)).getTankInventory();from.fill(new FluidStack(CEFluids.NITROGEN_GAS.get(),8000),EXECUTE);
        h.onEachTick(()->{for(int x:new int[]{2,7})((KineticBlockEntity)h.getBlockEntity(new BlockPos(x,2,5))).setSpeed(128);});
        h.runAfterDelay(5,()->{for(int x:new int[]{2,7})((KineticBlockEntity)h.getBlockEntity(new BlockPos(x,2,5))).onSpeedChanged(0);});
        h.succeedWhen(()->h.assertTrue(to.getFluidAmount()==8000 && from.isEmpty() && ((PulsationTubeBlockEntity)h.getBlockEntity(tube)).getTankInventory().isEmpty(),"Native pumps transport exactly the finite gas through both ends"));
    }
}
