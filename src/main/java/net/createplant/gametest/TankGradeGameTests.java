package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.content.fluids.tank.CreativeFluidTankBlockEntity;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import java.util.List;
import net.createplant.CreatePlant;
import net.createplant.registry.*;
import net.createplant.thermal.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public final class TankGradeGameTests {
    private static final BlockPos AT=new BlockPos(3,2,3);
    private static FluidTankBlockEntity tank(GameTestHelper h,Block block) {
        h.setBlock(AT,Blocks.AIR);h.setBlock(AT,block);return (FluidTankBlockEntity)h.getBlockEntity(AT);
    }

    @GameTest(template="test_fractionation")
    public static void everyStorageTankRejectsCorrosionAndOutOfRangeGrades(GameTestHelper h) {
        for(var block:List.of(AllBlocks.FLUID_TANK.get(),CETransport.HORIZONTAL_FLUID_TANK.get(),CETransport.GAS_RECEIVER.get(),CETransport.GAS_TANK_CONTAINER.get(),CETransport.PRESSURE_TANK.get(),CETransport.CRYOGENIC_TANK.get())) {
            var be=tank(h,block);var inventory=be.getTankInventory();
            h.assertTrue(inventory.fill(new FluidStack(CEFluids.NITRIC_ACID.get(),1000),EXECUTE)==0,"Non-resistant tank refuses acid: "+block);
            h.assertTrue(inventory.fill(new FluidStack(CEProcessFluids.HOT_NITRIC_OXIDE_MIXTURE.get(),1000),EXECUTE)==0,"Tank refuses very hot gas: "+block);
            h.assertTrue(inventory.isEmpty(),"Refusal keeps inventory empty without a failure behaviour: "+block);
        }
        var stainless=tank(h,CETransport.STAINLESS_STEEL_TANK.get());
        h.assertTrue(stainless.getTankInventory().fill(new FluidStack(CEFluids.NITRIC_ACID.get(),1000),EXECUTE)==1000,"Stainless accepts acid");
        stainless.getTankInventory().setFluid(FluidStack.EMPTY);
        h.assertTrue(stainless.getTankInventory().fill(new FluidStack(CEUranium.URANIUM_HEXAFLUORIDE.get(),1000),EXECUTE)==0,"Stainless is not fluoride resistant");
        h.succeed();
    }

    @GameTest(template="test_fractionation")
    public static void waterAndSteamNeverBypassTheMaterialOrFluidFilter(GameTestHelper h) {
        var liquid=tank(h,AllBlocks.FLUID_TANK.get());var port=h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,h.absolutePos(AT),Direction.UP);
        var steam=new FluidStack(CEFluids.STEAM.get(),1000);
        h.assertTrue(!port.isFluidValid(0,steam) && port.fill(steam,SIMULATE)==0 && port.fill(steam,EXECUTE)==0,"Liquid-only tank refuses steam at its real port");
        var gas=tank(h,CETransport.GAS_RECEIVER.get());
        h.assertTrue(gas.getTankInventory().fill(new FluidStack(Fluids.WATER,1000),EXECUTE)==0,"Gas receiver refuses water");
        h.assertTrue(gas.getTankInventory().fill(steam,EXECUTE)==1000 && gas.getTankInventory().getFluid().is(CEFluids.STEAM.get()),"Steam stays steam rather than becoming water");
        h.succeed();
    }

    @GameTest(template="test_fractionation")
    public static void allLiquidStorageRejectsEveryKnownGasThroughRealHandlers(GameTestHelper h) {
        for (var block : List.of(AllBlocks.FLUID_TANK.get(), CETransport.HORIZONTAL_FLUID_TANK.get(), CETransport.PRESSURE_TANK.get(), CETransport.STAINLESS_STEEL_TANK.get(), CETransport.CRYOGENIC_TANK.get())) {
            var be = tank(h, block);
            var port = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(AT), Direction.UP);
            for (var fluid : net.minecraft.core.registries.BuiltInRegistries.FLUID) {
                var gas = new FluidStack(fluid, 1000);
                if (gas.isEmpty() || net.createplant.mixture.MixtureHelper.isMixture(gas)) continue;
                if (!net.createplant.transport.FluidHazards.of(gas).gas() && !fluid.getFluidType().isLighterThanAir()) continue;
                h.assertTrue(!port.isFluidValid(0, gas) && port.fill(gas, SIMULATE) == 0 && port.fill(gas, EXECUTE) == 0 && be.getTankInventory().isEmpty(), "Liquid storage rejects gas through validation, simulation and execution: " + block + "/" + fluid);
            }
        }
        h.succeed();
    }

    @GameTest(template="test_fractionation")
    public static void creativeTankSelectsGasFromABucketAndSuppliesItAfterReload(GameTestHelper h) {
        var be = (CreativeFluidTankBlockEntity) tank(h, AllBlocks.CREATIVE_FLUID_TANK.get());
        var level = h.getLevel();
        var at = h.absolutePos(AT);
        var gas = new FluidStack(CEProcessFluids.COMPRESSED_HYDROGEN.get(), 1000);
        var port = level.getCapability(Capabilities.FluidHandler.BLOCK, at, Direction.UP);
        h.assertTrue(port != null && port.isFluidValid(0, gas), "The creative tank's actual fluid port validates gas");
        h.assertTrue(port.fill(gas, SIMULATE) == 1000 && port.fill(gas, EXECUTE) == 1000 && port.getFluidInTank(0).isEmpty(),
            "Pumped gas is accepted with Create's standard creative sink semantics");
        var player = h.makeMockPlayer(GameType.CREATIVE);
        var bucket = gas.getFluidType().getBucket(gas);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        var hit = new BlockHitResult(Vec3.atCenterOf(at).add(0, .5, 0), Direction.UP, at, false);
        level.getBlockState(at).useItemOn(bucket, level, player, InteractionHand.MAIN_HAND, hit);
        h.assertTrue(FluidStack.isSameFluidSameComponents(be.getTankInventory().getFluid(), gas), "A real gas bucket sets the creative tank to its high-pressure gas");
        var saved = be.saveWithoutMetadata(level.registryAccess());
        be = (CreativeFluidTankBlockEntity) tank(h, AllBlocks.CREATIVE_FLUID_TANK.get());
        be.loadWithComponents(saved, level.registryAccess());
        port = level.getCapability(Capabilities.FluidHandler.BLOCK, at, Direction.UP);
        for (int i = 0; i < 10; i++) {
            var drained = port.drain(1000, EXECUTE);
            h.assertTrue(drained.getAmount() == 1000 && FluidStack.isSameFluidSameComponents(drained, gas), "Reload preserves an unlimited gas supply");
        }
        h.succeed();
    }

    @GameTest(template="test_fractionation")
    public static void jeiBareMixtureSamplesAreRefusedWithoutBreakingFluidRecipes(GameTestHelper h) {
        var sample = new FluidStack(CEMixtures.CHEMICAL_MIXTURE.get(), 1000);
        for (var block : List.of(AllBlocks.FLUID_TANK.get(), AllBlocks.CREATIVE_FLUID_TANK.get(), CETransport.STAINLESS_STEEL_TANK.get())) {
            var be = tank(h, block);var port = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(AT), Direction.UP);
            h.assertTrue(!port.isFluidValid(0, sample) && port.fill(sample, SIMULATE) == 0 && port.fill(sample, EXECUTE) == 0 && be.getTankInventory().isEmpty(), "Component-free JEI samples cannot mutate storage or throw");
        }
        for (var tool : List.of(CEPortableTools.CHEMICAL_SPRAYER.get(), CEPortableTools.FLAMETHROWER.get(), CEPortableTools.FIRE_EXTINGUISHER.get()))
            h.assertTrue(!tool.accepts(sample), "Spray filters reject invalid JEI samples");
        h.succeed();
    }

    @GameTest(template="test_fractionation")
    public static void cryogenicLiquidsRequireCryogenicStorage(GameTestHelper h) {
        var liquid=new FluidStack(CEFusion.LIQUID_HELIUM.get(),1000);
        h.assertTrue(tank(h,AllBlocks.FLUID_TANK.get()).getTankInventory().fill(liquid,EXECUTE)==0,"Ordinary tank refuses cryogenic helium");
        var cold=tank(h,CETransport.CRYOGENIC_TANK.get());
        h.assertTrue(cold.getTankInventory().fill(liquid,EXECUTE)==1000,"Cryogenic tank accepts helium");
        cold.getTankInventory().setFluid(FluidStack.EMPTY);
        h.assertTrue(cold.getTankInventory().fill(new FluidStack(CEFluids.STEAM.get(),1000),EXECUTE)==0,"Cryogenic tank refuses hot steam");h.succeed();
    }

    @GameTest(template="test_fractionation")
    public static void gasCapacityUsesTheTankGradeAndSimulationDoesNotChangeIt(GameTestHelper h) {
        var blocks=List.of(CEGasBell.TANK.get(),CETransport.GAS_RECEIVER.get());int[] ratios={1,64};
        for(int index=0;index<blocks.size();index++) {
            var be=tank(h,blocks.get(index));var inventory=be.getTankInventory();int volume=inventory.getCapacity();
            var gas=new FluidStack(CEFluids.NITROGEN_GAS.get(),volume*ratios[index]+1000);
            var saved=be.saveWithoutMetadata(h.getLevel().registryAccess());
            h.assertTrue(inventory.fill(gas,SIMULATE)==volume*ratios[index] && be.saveWithoutMetadata(h.getLevel().registryAccess()).equals(saved),"Simulation is read-only at grade "+index);
            h.assertTrue(inventory.fill(gas,EXECUTE)==volume*ratios[index] && inventory.getCapacity()==volume*ratios[index],"Gas quota follows tank grade "+index);
            saved=be.saveWithoutMetadata(h.getLevel().registryAccess());be.loadWithComponents(saved,h.getLevel().registryAccess());
            h.assertTrue(be.getTankInventory().getFluidAmount()==volume*ratios[index],"Reload does not multiply gas");
            h.assertTrue(be.getTankInventory().fill(gas,EXECUTE)==0,"Full storage rejects excess gas");
        }
        h.succeed();
    }

    @GameTest(template="test_fractionation")
    public static void compressedGasSurvivesNativeTankMergeAndSplit(GameTestHelper h) {
        var be=tank(h,CETransport.GAS_RECEIVER.get());int amount=be.getTankInventory().getCapacity()*64;
        be.getTankInventory().fill(new FluidStack(CEFluids.NITROGEN_GAS.get(),amount),EXECUTE);
        h.setBlock(AT.above(2),CETransport.GAS_RECEIVER.get());var upper=(FluidTankBlockEntity)h.getBlockEntity(AT.above(2));
        upper.getTankInventory().fill(new FluidStack(CEFluids.NITROGEN_GAS.get(),amount),EXECUTE);
        h.setBlock(AT.above(),CETransport.GAS_RECEIVER.get());ConnectivityHandler.formMulti(be);
        var owner=be.getControllerBE();h.assertTrue(owner.getTankInventory().getFluidAmount()==amount*2,"Merge keeps both full gas stocks");
        ConnectivityHandler.splitMulti(owner);
        int total=0;for(int y=0;y<3;y++)total+=((FluidTankBlockEntity)h.getBlockEntity(AT.above(y))).getTankInventory().getFluidAmount();
        h.assertTrue(total==amount*2,"Split conserves compressed gas");h.succeed();
    }

    @GameTest(template="test_fractionation",timeoutTicks=100)
    public static void rejectedFluidCannotDamageOrExplodeATank(GameTestHelper h) {
        var be=tank(h,CETransport.PRESSURE_TANK.get());be.getTankInventory().fill(new FluidStack(Fluids.WATER,1000),EXECUTE);
        for(int i=0;i<100;i++)be.getTankInventory().fill(new FluidStack(CEProcessFluids.HOT_NITRIC_OXIDE_MIXTURE.get(),1000),EXECUTE);
        h.runAfterDelay(60,()->{h.assertTrue(h.getBlockState(AT).is(CETransport.PRESSURE_TANK.get()) && be.getTankInventory().getFluidAmount()==1000,"Repeated incompatible inflow leaves the tank and water intact");h.succeed();});
    }
}
