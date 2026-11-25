package net.createplant.gametest;

import net.createplant.thermal.FluidForm;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import net.createplant.CreatePlant;
import net.createplant.chemistry.ElectrolysisProcess;
import net.createplant.block.*;
import net.createplant.block.entity.*;
import net.createplant.registry.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.*;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ModularPortsGameTests {
    @GameTest(template="test_petrochemical")
    public static void electrolysisPortsExposeSeparateFiniteGasBuffers(GameTestHelper h) {
        ElectrolysisCellGameTests.waterUsesFinitePowerAndSeparatesBothGasPorts(h);
    }
    @GameTest(template="test_petrochemical")
    public static void theLongSidesExportCathodeAndAnodeGases(GameTestHelper h) {
        var owner = ElectrolysisCellGameTests.one(h);
        ElectrolysisCellGameTests.feed(owner, new FluidStack(Fluids.WATER, 300));ElectrolysisCellGameTests.run(owner, 10, 400);
        var low = (ElectrolysisCellBlockEntity)h.getLevel().getBlockEntity(owner.structure().minimum());var high = low;
        var a = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, low.getBlockPos(), Direction.WEST);
        var b = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, high.getBlockPos(), Direction.EAST);
        h.assertTrue(h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, low.getBlockPos(), Direction.UP) == null, "The electrode head closes the top of the cell");
        h.assertTrue(a != null && b != null && a.drain(1000, EXECUTE).is(CEFluids.HYDROGEN_GAS.get()) && b.drain(1000, EXECUTE).is(CEFluids.OXYGEN_GAS.get()), "The two long sides expose both gases separately to standard pipes");h.succeed();
    }
    @GameTest(template="test_fractionation")
    public static void hydrogenFluorideGivesHydrogenAndFluorineWithoutWater(GameTestHelper h) {
        var base = new BlockPos(12, 3, 12);
        var vessel = (LiddedVesselBlockEntity) ReactorGameTests.assemble(h.getLevel(), h.absolutePos(base), net.createplant.chemistry.ReactorModule.STIRRED, 2);
        h.setBlock(base.below(), AllBlocks.BLAZE_BURNER.getDefaultState().setValue(
            com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HEAT_LEVEL,
            com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.KINDLED));
        var items = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, vessel.getBlockPos(), Direction.NORTH);
        var fluids = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, vessel.getBlockPos(), Direction.NORTH);
        h.assertTrue(vessel.formed() && items != null && fluids != null, "The impeller vessel exposes standard feed ports");
        h.assertTrue(items.insertItem(0, CEUranium.FLUORITE.toStack(), false).isEmpty()
            && fluids.fill(new FluidStack(CEUranium.SULFURIC_ACID.get(), 250), EXECUTE) == 250, "Load one fluorite and 250 mB sulfuric acid");
        ReactorGameTests.speed(vessel, 32);
        for (int tick = 0; tick < 300; tick++) vessel.tickProcess();
        var gas = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, vessel.getBlockPos().above(vessel.sections() - 1), Direction.UP);
        h.assertTrue(gas != null && !gas.drain(new FluidStack(CEUranium.HYDROGEN_FLUORIDE.get(), 1), SIMULATE).isEmpty(),
            "Hydrogen fluoride gas leaves through the top; with no liquid left, the sides may give it too");
        var feed = gas.drain(new FluidStack(CEUranium.HYDROGEN_FLUORIDE.get(), 1000), EXECUTE);
        h.assertTrue(feed.getAmount() == 1000 && items.getStackInSlot(0).isEmpty()
            && fluids.drain(new FluidStack(CEUranium.SULFURIC_ACID.get(), 1), SIMULATE).isEmpty(),
            "Fluorite and sulfuric acid give exactly 1000 mB hydrogen fluoride: " + vessel.status() + ", recovered=" + feed.getAmount());
        var owner = ElectrolysisCellGameTests.one(h);
        h.assertTrue(ElectrolysisCellGameTests.feed(owner, feed) == 1000, "Recovered hydrogen fluoride feeds the electrolysis cell");
        ElectrolysisCellGameTests.run(owner, 500, 400);
        h.assertTrue(owner.stored(0) == 0 && owner.stored(1) == 500 && owner.fluid(1).is(CEFluids.HYDROGEN_GAS.get())
            && owner.stored(2) == 500 && owner.fluid(2).is(CEUranium.FLUORINE.get()), "One fluorite yields 500 mB each of hydrogen and fluorine without water");
        var shown = net.createplant.ponder.ManufacturingRecipe.resolve(h.getLevel(), "operation:fluoride_electrolysis");
        h.assertTrue(shown.fluids().getFirst().is(CEUranium.HYDROGEN_FLUORIDE.get()) && shown.fluids().getFirst().getAmount() == 200
            && shown.fluidProducts().size() == 2 && shown.fluidProducts().get(1).is(CEUranium.FLUORINE.get())
            && shown.fluidProducts().get(1).getAmount() == 100, "Ponder shows the same fluoride-electrolysis batch");h.succeed();
    }
    @GameTest(template="test_petrochemical")
    public static void exteriorTurbineEndPanelExposesTheSameExhaustAndDisconnectsSafely(GameTestHelper h) {
        BlockPos turbinePos=new BlockPos(3,1,3),end=turbinePos.east(),panel=end.above(2);
        h.setBlock(turbinePos,CEBlocks.STEAM_TURBINE.get().defaultBlockState().setValue(SteamTurbineBlock.AXIS,Direction.Axis.X));
        var turbine=(SteamTurbineBlockEntity)h.getBlockEntity(turbinePos);
        var saved=turbine.saveWithoutMetadata(h.getLevel().registryAccess());saved.put("RotorExhaust",steamTank(5000,h));
        turbine.loadWithComponents(saved,h.getLevel().registryAccess());
        var state=CEBlocks.LOW_PRESSURE_TURBINE_END.get().defaultBlockState();h.setBlock(end,state);
        var panelState=state.setValue(HighPressureTurbineEndBlock.RING_Y,HighPressureTurbineEndBlock.ringStateValue(2));h.setBlock(panel,panelState);
        var port=h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,h.absolutePos(panel),Direction.EAST);
        h.assertTrue(port!=null && port.drain(5000,SIMULATE).getAmount()==5000 && turbine.getRecoverableExhaust()==5000,"The reachable outer end panel must expose the same finite internal exhaust, without consuming it in simulation");
        h.setBlock(end,Blocks.AIR);
        h.assertTrue(port.drain(5000,EXECUTE).isEmpty() && turbine.getRecoverableExhaust()==5000,"Removing the end connection must disable even a cached panel port");
        h.setBlock(end,state);h.setBlock(panel,panelState);
        var restored=h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,h.absolutePos(panel),Direction.EAST);
        h.assertTrue(restored!=null && restored.drain(5000,EXECUTE).getAmount()==5000 && turbine.getSteamCapability().drain(5000,EXECUTE).isEmpty(),"Reconnecting a panel must recover the shared exhaust once, without a second copy at the center");h.succeed();
    }
    @GameTest(template="test_petrochemical")
    public static void turbineRecoveryPreservesSavedExhaustAndCannotDrainUnspentSteam(GameTestHelper h) {
        BlockPos at=new BlockPos(3,1,3);h.setBlock(at,CEBlocks.STEAM_TURBINE.get());
        var be=(SteamTurbineBlockEntity)h.getBlockEntity(at);
        var tag=be.saveWithoutMetadata(h.getLevel().registryAccess());tag.put("RotorExhaust",steamTank(5000,h));tag.put("RotorInput",steamTank(1234,h));
        be.loadWithComponents(tag,h.getLevel().registryAccess());
        var handler=h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,h.absolutePos(at),Direction.UP);
        h.assertTrue(handler!=null && handler.getTanks()==2 && handler.getFluidInTank(1).getAmount()==5000,"The saved low-pressure exhaust must have a physical condenser port");
        h.assertTrue(handler.drain(5000,SIMULATE).getAmount()==5000 && be.getRecoverableExhaust()==5000,"Recovery simulation cannot spend saved exhaust");
        h.assertTrue(handler.drain(5000,EXECUTE).getAmount()==5000 && handler.drain(5000,EXECUTE).isEmpty() && be.thermodynamics().input().getFluidAmount()==1234,"Only spent exhaust may be extracted, exactly once; unspent turbine feed remains protected");
        h.setBlock(at,Blocks.AIR);h.assertTrue(handler.drain(5000,EXECUTE).isEmpty(),"Removed turbine ports must be inert");
        h.setBlock(at,CEBlocks.HIGH_PRESSURE_STEAM_TURBINE.get());var high=(SteamTurbineBlockEntity)h.getBlockEntity(at);
        high.loadWithComponents(tag,h.getLevel().registryAccess());h.assertTrue(high.getSteamCapability().drain(5000,EXECUTE).getAmount()==5000,"A high-pressure stage exposes its finite exhaust for standard inter-stage pipes");h.succeed();
    }
    private static net.minecraft.nbt.CompoundTag steamTank(int amount,GameTestHelper h){var tank=new net.createplant.thermal.ThermalFluidTank(64000,f->{});tank.fill(net.createplant.thermal.FluidThermal.at(FluidForm.STEAM.stack(amount),450),EXECUTE);return tank.writeToNBT(h.getLevel().registryAccess(),new net.minecraft.nbt.CompoundTag());}

}
