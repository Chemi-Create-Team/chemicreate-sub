package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.OpenEndedPipe;
import com.simibubi.create.content.fluids.hosePulley.HosePulleyFluidHandler;
import com.simibubi.create.content.fluids.transfer.FluidDrainingBehaviour;
import com.simibubi.create.content.fluids.transfer.FluidFillingBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import net.createplant.CreatePlant;
import net.createplant.registry.CEUranium;
import net.createplant.thermal.FluidThermal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.createmod.catnip.math.BlockFace;

import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorldLiquidTransferGameTests {
    private static FluidStack acid(int amount) { return new FluidStack(CEUranium.SULFURIC_ACID.get(), amount); }

    private static SmartBlockEntity pulley(GameTestHelper h, BlockPos pos) {
        h.getLevel().setBlock(pos, AllBlocks.HOSE_PULLEY.getDefaultState(), 3);
        return (SmartBlockEntity) h.getLevel().getBlockEntity(pos);
    }

    private static final class Drainer extends FluidDrainingBehaviour {
        Drainer(SmartBlockEntity be) { super(be); }
        boolean infiniteFor(Fluid fluid) { return canDrainInfinitely(fluid); }
    }

    private static final class CountingFiller extends FluidFillingBehaviour {
        private int sources;
        CountingFiller(SmartBlockEntity be) { super(be); }
        @Override public boolean tryDeposit(Fluid fluid, BlockPos pos, boolean simulate) {
            if (!simulate) sources++;
            return true;
        }
    }

    private static Drainer searchedDrainer(SmartBlockEntity be, BlockPos root) {
        var drainer = new Drainer(be);
        drainer.rebuildContext(root);
        drainer.tick();
        drainer.rebuildContext(root);
        drainer.tick();
        return drainer;
    }

    @GameTest(template = "test_reactor")
    public static void openPipeRejectsWrongFiltersAndPreservesEveryPartialBucket(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(4, 3, 4));
        level.setBlock(pos, CEUranium.SULFURIC_ACID.get().defaultFluidState().createLegacyBlock(), 3);
        var pipe = new OpenEndedPipe(new BlockFace(pos.west(), Direction.EAST));
        pipe.manageSource(level, null);
        var handler = pipe.provideHandler().getCapability();
        var wrong = new FluidStack(net.createplant.registry.CEFluids.NITRIC_ACID.get(), 1000);
        h.assertTrue(handler.drain(wrong, EXECUTE).isEmpty() && level.getFluidState(pos).isSource(),
            "A wrong filter is rejected before the world source is removed");
        var before = pipe.serializeNBT(level.registryAccess());
        FluidStack preview = handler.drain(100, SIMULATE);
        h.assertTrue(preview.getAmount() == 100 && level.getFluidState(pos).isSource()
                && before.equals(pipe.serializeNBT(level.registryAccess())), "Open-pipe simulation changes neither source nor buffer state");
        h.assertTrue(Math.abs(FluidThermal.kelvin(preview) - 293) < 1e-8,
            "An untracked process-liquid source is sampled at the nominal fluid temperature");
        FluidStack first = handler.drain(100, EXECUTE);
        h.assertTrue(handler.drain(wrong, EXECUTE).isEmpty(), "The measured remainder also rejects a wrong filter");
        FluidStack rest = handler.drain(900, EXECUTE);
        h.assertTrue(first.getAmount() + rest.getAmount() == 1000 && handler.getFluidInTank(0).isEmpty()
                && handler.drain(1, EXECUTE).isEmpty(), "A full bucket leaves no duplicable internal remainder");
        h.succeed();
    }

    @GameTest(template = "test_reactor")
    public static void openPipeFillDoesNotDiscardADifferentBufferedLiquid(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(12, 3, 4));
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        var pipe = new OpenEndedPipe(new BlockFace(pos.west(), Direction.EAST));
        pipe.manageSource(level, null);
        var handler = pipe.provideHandler().getCapability();
        h.assertTrue(handler.fill(FluidThermal.at(acid(500), 400), EXECUTE) == 500, "Half a bucket is buffered");
        var before = pipe.serializeNBT(level.registryAccess());
        var wrong = new FluidStack(net.createplant.registry.CEFluids.NITRIC_ACID.get(), 500);
        h.assertTrue(handler.fill(wrong, SIMULATE) == 0 && before.equals(pipe.serializeNBT(level.registryAccess()))
                && handler.fill(wrong, EXECUTE) == 0 && handler.getFluidInTank(0).getAmount() == 500,
            "Neither simulated nor actual fill can clear a different buffered process liquid");
        h.assertTrue(handler.fill(FluidThermal.at(acid(500), 300), SIMULATE) == 500 && before.equals(pipe.serializeNBT(level.registryAccess())),
            "Compatible preview also leaves all state unchanged");
        h.assertTrue(handler.fill(FluidThermal.at(acid(500), 300), EXECUTE) == 500 && handler.getFluidInTank(0).isEmpty()
                && level.getFluidState(pos).isSource(), "A complete mixed bucket becomes exactly one world source");
        var recovered = handler.drain(1000, EXECUTE);
        h.assertTrue(recovered.getAmount() == 1000 && Math.abs(FluidThermal.kelvin(recovered) - 293) < 1e-8,
            "Placing intentionally discards measured heat and later sampling uses local ambient");
        h.succeed();
    }

    @GameTest(template = "test_reactor")
    public static void hoseRejectsFiltersBeforeDrainingAndClearsTheLastRemainder(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos root = h.absolutePos(new BlockPos(20, 3, 4));
        for (Direction direction : Direction.values()) level.setBlock(root.relative(direction), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(root, CEUranium.SULFURIC_ACID.get().defaultFluidState().createLegacyBlock(), 3);
        var be = pulley(h, root.above(2));
        var drainer = searchedDrainer(be, root);
        var tank = new SmartFluidTank(1000, stack -> { });
        var hose = new HosePulleyFluidHandler(tank, new FluidFillingBehaviour(be), drainer, () -> root, () -> true);
        h.assertTrue(!drainer.infiniteFor(CEUranium.SULFURIC_ACID.get()), "Process liquid can never become a bottomless hose supply");
        var wrong = new FluidStack(net.createplant.registry.CEFluids.NITRIC_ACID.get(), 1000);
        h.assertTrue(hose.drain(wrong, EXECUTE).isEmpty() && level.getFluidState(root).isSource(),
            "A hose filter mismatch does not consume the selected source");
        FluidStack preview = hose.drain(100, SIMULATE);
        h.assertTrue(preview.getAmount() == 100 && tank.isEmpty() && level.getFluidState(root).isSource(),
            "The hose can preview a partial bucket without world or buffer changes");
        FluidStack first = hose.drain(100, EXECUTE);
        h.assertTrue(first.getAmount() == 100 && tank.getFluidAmount() == 900 && !level.getFluidState(root).isSource(),
            "A partial hose draw consumes one source and stores only its remaining volume");
        h.assertTrue(hose.drain(wrong, EXECUTE).isEmpty() && tank.getFluidAmount() == 900, "Wrong requests leave the remainder intact");
        FluidStack rest = hose.drain(900, EXECUTE);
        h.assertTrue(first.getAmount() + rest.getAmount() == 1000 && tank.isEmpty(), "The final partial draw clears the internal tank");
        h.succeed();
    }

    @GameTest(template = "test_reactor")
    public static void hoseFillSimulationMatchesActualAcceptedVolume(GameTestHelper h) {
        BlockPos pos = h.absolutePos(new BlockPos(4, 4, 12));
        var be = pulley(h, pos);
        var filler = new CountingFiller(be);
        var tank = new SmartFluidTank(1000, stack -> { });
        tank.setFluid(FluidThermal.at(acid(500), 400));
        var hose = new HosePulleyFluidHandler(tank, filler, new Drainer(be), () -> pos.below(), () -> true);
        FluidStack incoming = FluidThermal.at(acid(1500), 300);
        int preview = hose.fill(incoming, SIMULATE);
        h.assertTrue(preview == 1500 && tank.getFluidAmount() == 500 && filler.sources == 0,
            "Preview includes the source and retained buffer capacity without consuming either");
        int actual = hose.fill(incoming, EXECUTE);
        h.assertTrue(actual == preview && filler.sources == 1 && tank.getFluidAmount() == 1000
                && Math.abs(FluidThermal.kelvin(tank.getFluid()) - 293) < 1e-8,
            "One bucket is deposited and the remaining incoming bucket is buffered exactly once");
        h.succeed();
    }

    @GameTest(template = "test_reactor")
    public static void hoseReturnsOldWaterBeforeTouchingANewAcidSupply(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos root = h.absolutePos(new BlockPos(20, 3, 12));
        for (Direction direction : Direction.values()) level.setBlock(root.relative(direction), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(root, CEUranium.SULFURIC_ACID.get().defaultFluidState().createLegacyBlock(), 3);
        var be = pulley(h, root.above(2));
        var tank = new SmartFluidTank(1000, stack -> { });
        tank.setFluid(FluidThermal.at(new FluidStack(Fluids.WATER, 500), 350));
        var hose = new HosePulleyFluidHandler(tank, new FluidFillingBehaviour(be), searchedDrainer(be, root), () -> root, () -> true);
        h.assertTrue(hose.drain(acid(1000), EXECUTE).isEmpty() && tank.getFluidAmount() == 500 && level.getFluidState(root).isSource(),
            "An acid request cannot flush old water or consume the new acid source");
        FluidStack preview = hose.drain(1000, SIMULATE);
        h.assertTrue(preview.is(Fluids.WATER) && preview.getAmount() == 500 && tank.getFluidAmount() == 500
                && level.getFluidState(root).isSource(), "A mixed-boundary preview only offers the old buffered water");
        FluidStack oldWater = hose.drain(1000, EXECUTE);
        h.assertTrue(oldWater.getAmount() == 500 && Math.abs(FluidThermal.kelvin(oldWater) - 293) < 1e-8 && tank.isEmpty()
                && level.getFluidState(root).isSource(), "The measured water is returned first without touching the acid source");
        h.assertTrue(hose.drain(new FluidStack(Fluids.WATER, 1000), EXECUTE).isEmpty() && level.getFluidState(root).isSource(),
            "A subsequent water filter still cannot consume the acid source");
        FluidStack acid = hose.drain(1000, EXECUTE);
        h.assertTrue(acid.getAmount() == 1000 && acid.is(CEUranium.SULFURIC_ACID.get()) && tank.isEmpty()
                && !level.getFluidState(root).isSource(), "The complete acid bucket remains available after the old water was removed");
        h.succeed();
    }

    @GameTest(template = "test_reactor")
    public static void openPipeReturnsOldWaterBeforeTouchingANewAcidSupply(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(12, 3, 12));
        level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
        var pipe = new OpenEndedPipe(new BlockFace(pos.west(), Direction.EAST));
        pipe.manageSource(level, null);
        var handler = pipe.provideHandler().getCapability();
        FluidStack firstWater = handler.drain(500, EXECUTE);
        double measuredWaterKelvin = FluidThermal.kelvin(firstWater);
        h.assertTrue(firstWater.is(Fluids.WATER) && firstWater.getAmount() == 500 && handler.getFluidInTank(0).getAmount() == 500,
            "Drawing half a water source leaves the other half measured in the pipe");
        level.setBlock(pos, CEUranium.SULFURIC_ACID.get().defaultFluidState().createLegacyBlock(), 3);
        h.assertTrue(handler.drain(acid(1000), EXECUTE).isEmpty() && handler.getFluidInTank(0).getAmount() == 500
                && level.getFluidState(pos).isSource(), "An acid filter keeps both the foreign buffer and the new world source intact");
        var before = pipe.serializeNBT(level.registryAccess());
        h.assertTrue(handler.drain(1000, SIMULATE).getAmount() == 500 && before.equals(pipe.serializeNBT(level.registryAccess()))
                && level.getFluidState(pos).isSource(), "A mixed-boundary pipe preview makes no changes");
        FluidStack oldWater = handler.drain(1000, EXECUTE);
        h.assertTrue(oldWater.is(Fluids.WATER) && oldWater.getAmount() == 500 && Math.abs(FluidThermal.kelvin(oldWater) - measuredWaterKelvin) < 1e-8
                && handler.getFluidInTank(0).isEmpty() && level.getFluidState(pos).isSource(), "Old measured water is returned before world acid");
        FluidStack acid = handler.drain(1000, EXECUTE);
        h.assertTrue(acid.is(CEUranium.SULFURIC_ACID.get()) && acid.getAmount() == 1000 && handler.getFluidInTank(0).isEmpty(),
            "Changing the exposed liquid did not discard or duplicate either bucket fraction");
        h.succeed();
    }
}
