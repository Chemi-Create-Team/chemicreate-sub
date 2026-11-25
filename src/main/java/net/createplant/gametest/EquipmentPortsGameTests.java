package net.createplant.gametest;

import net.createplant.thermal.FluidForm;
import net.createplant.CreatePlant;
import net.createplant.block.CoolerRadiatorBlock;
import net.createplant.block.entity.*;
import net.createplant.registry.*;
import net.createplant.thermal.FluidThermal;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class EquipmentPortsGameTests {
    private static RadiatorBlockEntity cell(ServerLevel level,BlockPos fan,Direction facing) {
        for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) for(int y=0;y<4;y++) {
            var block=y<3 ? CEProcessBlocks.COOLER_RADIATOR.get() : x==0 && z==0 ? CEProcessBlocks.COOLING_FAN.get() : CEProcessBlocks.COOLING_FAN_SHROUD.get();
            var state=block.defaultBlockState();if(y<3) state=state.setValue(CoolerRadiatorBlock.FACING,facing);
            level.setBlock(fan.offset(x,y-3,z),state,3);
        }
        var master=(RadiatorBlockEntity)level.getBlockEntity(fan.below(3));master.lazyTick();return master.owner();
    }
    @GameTest(template="test_fractionation")
    public static void exchangerHasNoShaftOrStressAndUsesAnUnpoweredSupport(GameTestHelper h) {
        var be=ThermalControlGameTests.build(h.getLevel(),h.absolutePos(new BlockPos(6,2,6)),Direction.NORTH,2);
        var block=(net.createplant.block.ThermalMachineBlock)be.getBlockState().getBlock();
        for(Direction side:Direction.values()) h.assertTrue(!block.hasShaftTowards(h.getLevel(),be.getBlockPos(),be.getBlockState(),side),"The exchanger cannot connect a shaft");
        h.assertTrue(be.calculateStressApplied()==0,"Passive heat exchange applies no stress");
        be.setSpeed(0);int flow=be.thermal().flowPerStep();be.setSpeed(256);
        h.assertTrue(be.thermal().flowPerStep()==flow,"Obsolete saved rotation cannot boost passive throughput");h.succeed();
    }
    @GameTest(template="test_fractionation")
    public static void radiatorHasTwoCentralBidirectionalPortsForEveryFacing(GameTestHelper h) {
        int index=0;
        for(Direction facing:Direction.Plane.HORIZONTAL) {
            var fan=h.absolutePos(new BlockPos(5+index++*7,5,5));var master=cell(h.getLevel(),fan,facing);
            var a=(RadiatorBlockEntity)h.getLevel().getBlockEntity(fan.below(2).relative(facing));
            var b=(RadiatorBlockEntity)h.getLevel().getBlockEntity(fan.below(2).relative(facing.getOpposite()));
            var inlet=a.port(facing);var outlet=b.port(facing.getOpposite());
            h.assertTrue(inlet!=null && outlet!=null && master.port(Direction.UP)==null,"Only central horizontal endpoints accept pipes");
            var fluid=FluidThermal.at(FluidForm.WARM_WATER.stack(100),400);
            h.assertTrue(inlet.fill(fluid,SIMULATE)==100 && outlet.fill(fluid,SIMULATE)==100,"Simulation cannot select a permanent direction");
            inlet.fill(fluid,EXECUTE);h.assertTrue(outlet.fill(fluid,EXECUTE)==0,"Opposing pumps cannot mix raw feed into the output");
            ((CoolingFanBlockEntity)h.getLevel().getBlockEntity(fan)).setSpeed(128);
            for(int tick=0;tick<5;tick++)master.tick();
            h.assertTrue(outlet.drain(100,EXECUTE).getAmount()==100,"Accepted fluid exits the opposite central port");
            h.assertTrue(outlet.fill(fluid,EXECUTE)==100,"An empty bank can reverse direction");
            for(int tick=0;tick<5;tick++)master.tick();
            h.assertTrue(inlet.drain(100,EXECUTE).getAmount()==100,"Reverse flow is also cooled and discharged");
        }
        h.succeed();
    }
    @GameTest(template="test_fractionation")
    public static void radiatorExtensionRotatesTheCentralLineWithoutLosingHeldFluid(GameTestHelper h) {
        var fan=h.absolutePos(new BlockPos(8,5,8));var master=cell(h.getLevel(),fan,Direction.NORTH);
        var old=((RadiatorBlockEntity)h.getLevel().getBlockEntity(fan.below(2).north())).port(Direction.NORTH);
        old.fill(FluidThermal.at(FluidForm.WARM_WATER.stack(50),400),EXECUTE);
        cell(h.getLevel(),fan.east(3),Direction.NORTH);master.lazyTick();master=master.owner();
        h.assertTrue(master.fanCount()==2 && master.port(null).getFluidInTank(0).getAmount()==50,"Lateral extension retains the occupied controller and buffer");
        h.assertTrue(old.fill(new FluidStack(Fluids.WATER,1),EXECUTE)==0,"Cached old-axis ports retire after expansion");
        h.assertTrue(((RadiatorBlockEntity)h.getLevel().getBlockEntity(fan.below(2).west())).port(Direction.WEST)!=null,"New west end is exposed");
        h.assertTrue(((RadiatorBlockEntity)h.getLevel().getBlockEntity(fan.east(3).below(2).east())).port(Direction.EAST)!=null,"New east end is exposed");
        cell(h.getLevel(),fan.east(6),Direction.NORTH);master.lazyTick();master=master.owner();
        h.assertTrue(master.fanCount()==3 && master.port(null).getFluidInTank(0).getAmount()==50,"Extending along the new axis adds another cell and retains the held fluid");
        var saved=master.saveWithoutMetadata(h.getLevel().registryAccess());master.loadWithComponents(saved,h.getLevel().registryAccess());master.lazyTick();
        h.assertTrue(master.port(null).getFluidInTank(0).getAmount()==50 && ((RadiatorBlockEntity)h.getLevel().getBlockEntity(fan.east(6).below(2).east())).port(Direction.EAST)!=null,"Reload preserves buffers and rebuilds end ports");h.succeed();
    }
}
