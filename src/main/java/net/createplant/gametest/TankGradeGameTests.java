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
import net.minecraft.world.item.ItemStack;
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
    public static void everyStorageTankTakesAnUnratedFluidAndNamesTheHazard(GameTestHelper h) {
        for(var block:List.of(AllBlocks.FLUID_TANK.get(),CETransport.HORIZONTAL_FLUID_TANK.get(),CETransport.GAS_RECEIVER.get(),CETransport.GAS_TANK_CONTAINER.get(),CETransport.PRESSURE_TANK.get(),CETransport.CRYOGENIC_TANK.get())) {
            var be=tank(h,block);var inventory=be.getTankInventory();
            // A gas-only tank holds no liquid at all, so it is worn by a corrosive gas instead.
            var acid=TankGrade.of(be)==TankGrade.GAS ? new FluidStack(CEChlorAlkali.CHLORINE.get(),1000) : new FluidStack(CEFluids.NITRIC_ACID.get(),1000);var hot=FluidState.with(FluidForm.HOT_NITRIC_OXIDE_MIXTURE.stack(1000),16,4);
            h.assertTrue(!TankGrade.of(be).accepts(acid) && inventory.fill(acid,EXECUTE)==1000,"A non-resistant tank still takes a corrosive fluid, which wears it: "+block);
            inventory.setFluid(FluidStack.EMPTY);
            h.assertTrue(!TankGrade.of(be).accepts(hot) && inventory.fill(hot,EXECUTE)==1000,"A tank still takes a very hot gas, which wears it: "+block);
            inventory.setFluid(FluidStack.EMPTY);
        }
        var stainless=tank(h,CETransport.STAINLESS_STEEL_TANK.get());
        h.assertTrue(stainless.getTankInventory().fill(new FluidStack(CEFluids.NITRIC_ACID.get(),1000),EXECUTE)==1000,"Stainless accepts acid");
        stainless.getTankInventory().setFluid(FluidStack.EMPTY);
        h.assertTrue(!TankGrade.STAINLESS.accepts(new FluidStack(CESharedChemicals.HYDROGEN_FLUORIDE.get(),1000)),"Stainless is not rated for hydrogen fluoride");
        h.succeed();
    }

    @GameTest(template="test_fractionation")
    public static void stainlessTankTakesRatedSolutionByPipeAndSurvivalBucket(GameTestHelper h) {
        var tank=tank(h,CETransport.STAINLESS_STEEL_TANK.get());
        var at=h.absolutePos(AT);
        var pipe=h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,at,Direction.WEST);
        var brine=new FluidStack(CEChlorAlkali.BRINE.get(),1000);
        var alkali=new FluidStack(CEChlorAlkali.CAUSTIC_SODA.get(),1000);
        h.assertTrue(TankGrade.STAINLESS.accepts(brine) && TankGrade.STAINLESS.accepts(alkali) && pipe.fill(brine,EXECUTE)==1000,
            "Brine is accepted through the normal pipe connection");
        var player=h.makeMockPlayer(GameType.SURVIVAL);
        var bucket=net.createplant.item.StainlessBuckets.filled(alkali);
        player.setItemInHand(InteractionHand.MAIN_HAND,bucket);
        h.useBlock(AT,player);
        h.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).is(CEWorldFluids.STAINLESS_BUCKET.get()),"A survival bucket can be emptied into the stainless tank");
        var contents=net.createplant.mixture.MixtureHelper.components(tank.getTankInventory().getFluid());
        h.assertTrue(contents.size()==2 && contents.stream().anyMatch(stack->stack.is(brine.getFluid()) && stack.getAmount()==1000)
            && contents.stream().anyMatch(stack->stack.is(alkali.getFluid()) && stack.getAmount()==1000),"The tank retains both full charges");
        int tested=0;
        for (var fluid:net.minecraft.core.registries.BuiltInRegistries.FLUID) {
            if (fluid instanceof net.minecraft.world.level.material.FlowingFluid flowing && flowing.getSource()!=fluid) continue;
            var info=net.createplant.chemistry.FluidChemicalRegistry.base(fluid);
            var solution=new FluidStack(fluid,1000);
            if (info==null || !info.aqueous() || !TankGrade.STAINLESS.accepts(solution)) continue;
            tank.getTankInventory().setFluid(new FluidStack(Fluids.WATER,1000));
            h.assertTrue(pipe.fill(solution,EXECUTE)==1000 && tank.getTankInventory().getFluidAmount()==2000,
                "A rated aqueous solution fits alongside water: "+solution);
            tested++;
        }
        h.assertTrue(tested>0,"The registered catalogue includes tested aqueous solutions");
        h.succeed();
    }

    @GameTest(template="test_fractionation")
    public static void waterAndSteamNeverBypassTheMaterialOrFluidFilter(GameTestHelper h) {
        var liquid=tank(h,AllBlocks.FLUID_TANK.get());var port=h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,h.absolutePos(AT),Direction.UP);
        var steam=FluidForm.STEAM.stack(1000);
        h.assertTrue(port.isFluidValid(0,steam) && port.fill(steam,EXECUTE)==1000 && TankGrade.LIQUID.incompatibility(steam).equals("gas"),"A liquid-only tank takes steam at its real port, and is worn by it");
        var gas=tank(h,CETransport.GAS_RECEIVER.get());
        h.assertTrue(TankGrade.GAS.incompatibility(new FluidStack(Fluids.WATER,1000)).equals("liquid"),"A gas receiver is not rated for water");
        h.assertTrue(gas.getTankInventory().fill(steam,EXECUTE)==1000 && FluidForm.STEAM.matches(gas.getTankInventory().getFluid()),"Steam stays steam rather than becoming water");
        h.succeed();
    }

    @GameTest(template="test_fractionation")
    public static void stainlessTankScoopsWithTheCorrectBucket(GameTestHelper h) {
        var tank = tank(h, CETransport.STAINLESS_STEEL_TANK.get());
        var sulfate = new FluidStack(CEMetals.ZIRCONIUM_SULFATE_SOLUTION.get(), 1500);
        var port = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(AT), Direction.WEST);
        h.assertTrue(TankGrade.STAINLESS.accepts(sulfate) && port.fill(sulfate, EXECUTE) == 1500,
            "The stainless tank is rated to store and pipe in zirconium sulfate solution");
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        var hand = InteractionHand.MAIN_HAND;
        player.setItemInHand(hand, CEWorldFluids.STAINLESS_BUCKET.toStack());
        h.useBlock(AT, player);
        var sampleBucket = player.getItemInHand(hand);
        var filled = net.neoforged.neoforge.fluids.FluidUtil.getFluidHandler(sampleBucket).orElseThrow();
        h.assertTrue(net.createplant.item.StainlessBuckets.marked(sampleBucket) && filled.drain(1000, EXECUTE).is(sulfate.getFluid())
            && filled.getContainer().is(CEWorldFluids.STAINLESS_BUCKET.get())
            && tank.getTankInventory().getFluidAmount() == 500, "A stainless bucket takes 1000 mB and keeps the stainless shell");
        player.setItemInHand(hand, CEWorldFluids.STAINLESS_BUCKET.toStack());
        h.useBlock(AT, player);
        h.assertTrue(player.getItemInHand(hand).is(CEWorldFluids.STAINLESS_BUCKET.get())
            && tank.getTankInventory().getFluidAmount() == 500, "A partial bucketful stays in the tank");

        var compatible = new FluidStack(CEChlorAlkali.CAUSTIC_SODA.get(), 1000);
        tank.getTankInventory().setFluid(compatible.copy());
        player.setItemInHand(hand, new ItemStack(CEWorldFluids.STAINLESS_BUCKET.get(), 2));
        h.useBlock(AT, player);
        var sample = player.getInventory().items.stream().filter(net.createplant.item.StainlessBuckets::marked).findFirst().orElse(ItemStack.EMPTY);
        var sampledFluid = net.neoforged.neoforge.fluids.FluidUtil.getFluidContained(sample).orElse(FluidStack.EMPTY);
        h.assertTrue(player.getItemInHand(hand).is(CEWorldFluids.STAINLESS_BUCKET.get()) && player.getItemInHand(hand).getCount() == 1
            && sampledFluid.getFluid().isSame(compatible.getFluid()) && sampledFluid.getAmount() == 1000 && tank.getTankInventory().isEmpty(),
            "A rated liquid fills one of the stacked stainless buckets: held " + player.getItemInHand(hand)
                + ", sample " + sampledFluid + ", tank " + tank.getTankInventory().getFluid());
        h.succeed();
    }

    @GameTest(template="test_fractionation")
    public static void allLiquidStorageTakesGasButIsRatedAgainstIt(GameTestHelper h) {
        var gas = new FluidStack(CEFluids.HYDROGEN_GAS.get(), 1000);
        for (var block : List.of(AllBlocks.FLUID_TANK.get(), CETransport.HORIZONTAL_FLUID_TANK.get(), CETransport.PRESSURE_TANK.get(), CETransport.STAINLESS_STEEL_TANK.get(), CETransport.CRYOGENIC_TANK.get())) {
            var be = tank(h, block);
            var port = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(AT), Direction.UP);
            h.assertTrue(port.fill(gas, SIMULATE) == 1000 && port.fill(gas, EXECUTE) == 1000 && TankGrade.of(be).incompatibility(gas).equals("gas"),
                "Liquid storage takes gas through its real handler and is worn by it: " + block);
        }
        h.succeed();
    }

    @GameTest(template="test_fractionation")
    public static void creativeTankSelectsGasFromABucketAndSuppliesItAfterReload(GameTestHelper h) {
        var be = (CreativeFluidTankBlockEntity) tank(h, AllBlocks.CREATIVE_FLUID_TANK.get());
        var level = h.getLevel();
        var at = h.absolutePos(AT);
        var gas = FluidForm.COMPRESSED_HYDROGEN.stack(1000);
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
        var liquid=FluidForm.LIQUID_HELIUM.stack(1000);
        h.assertTrue(TankGrade.LIQUID.incompatibility(liquid).equals("cold"),"An ordinary tank is not rated for cryogenic helium");
        var cold=tank(h,CETransport.CRYOGENIC_TANK.get());
        h.assertTrue(cold.getTankInventory().fill(liquid,EXECUTE)==1000,"Cryogenic tank accepts helium");
        cold.getTankInventory().setFluid(FluidStack.EMPTY);
        h.assertTrue(TankGrade.CRYOGENIC.incompatibility(FluidForm.STEAM.stack(1000)).equals("gas"),"A cryogenic tank is not rated for steam");h.succeed();
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

    @GameTest(template="test_fractionation")
    public static void anUnratedFluidCracksATankUntilItBurstsAndDrainingLetsItRecover(GameTestHelper h) {
        var be=tank(h,CETransport.PRESSURE_TANK.get());
        // Create attaches behaviours when the block entity first ticks.
        h.runAfterDelay(2,()->{
            var wear=com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour.get(be,net.createplant.transport.ContainmentWear.TYPE);
            var hot=FluidState.with(FluidForm.HOT_NITRIC_OXIDE_MIXTURE.stack(1000),16,4);
            h.assertTrue(be.getTankInventory().fill(hot,EXECUTE)==1000,"The pressure tank takes the very hot gas");
            for(int i=0;i<600;i++)wear.tick();
            h.assertTrue(wear.wear()==600 && wear.reason().equals("gas") && net.createplant.transport.ContainmentWear.warning(be)!=null && h.getBlockState(AT).is(CETransport.PRESSURE_TANK.get()),
                "Half a minute of wear leaves a cracked tank with a warning: "+wear.wear()+" "+wear.reason());
            be.getTankInventory().setFluid(FluidStack.EMPTY);
            for(int i=0;i<300;i++)wear.tick();
            h.assertTrue(wear.wear()==300 && wear.reason().isEmpty(),"Draining the gas lets the cracks close at the same pace");
            be.getTankInventory().fill(hot,EXECUTE);
            for(int i=0;i<900;i++)wear.tick();
            h.assertTrue(h.getBlockState(AT).isAir(),"A full minute of wear bursts the tank");
            h.succeed();
        });
    }

    @GameTest(template="test_fractionation")
    public static void aCorrosiveFluidTheTankIsNotRatedForNamesItsKindInTheWarning(GameTestHelper h) {
        var be=tank(h,com.simibubi.create.AllBlocks.FLUID_TANK.get());
        h.runAfterDelay(2,()->{
            var wear=com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour.get(be,net.createplant.transport.ContainmentWear.TYPE);
            h.assertTrue(be.getTankInventory().fill(new FluidStack(net.createplant.registry.CEChlorAlkali.HYDROCHLORIC_ACID.get(),1000),EXECUTE)==1000,"The plain tank takes the acid");
            for(int i=0;i<20;i++)wear.tick();
            var warning=net.createplant.transport.ContainmentWear.warning(be);
            var reason=warning==null ? null : ((net.minecraft.network.chat.contents.TranslatableContents)warning.getContents()).getArgs()[0];
            h.assertTrue(wear.reason().equals("halogen") && reason instanceof net.minecraft.network.chat.Component name
                && ((net.minecraft.network.chat.contents.TranslatableContents)name.getContents()).getKey().endsWith(".wear.halogen")
                && warning.getStyle().getColor()==net.minecraft.network.chat.TextColor.fromLegacyFormat(net.minecraft.ChatFormatting.RED),
                "Hydrochloric acid in a plain tank warns in red that it does not resist halogens: "+wear.reason());
            h.succeed();
        });
    }
}
