package net.createplant.gametest;

import java.util.*;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.createplant.CreatePlant;
import net.createplant.block.entity.LiddedVesselBlockEntity;
import net.createplant.mixture.*;
import net.createplant.registry.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.gametest.*;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class MixtureGameTests {
    private static final ResourceLocation WATER = ResourceLocation.withDefaultNamespace("water");
    private static final ResourceLocation ANTIFREEZE = CreatePlant.location("antifreeze");
    private static final ResourceLocation ETHANOL = ResourceLocation.fromNamespaceAndPath("createdieselgenerators", "ethanol");
    private static MixtureContents awkward() { return new MixtureContents(Map.of(WATER, 333, ANTIFREEZE, 333, ETHANOL, 334)); }
    private static void same(GameTestHelper h, MixtureContents expected, FluidStack stack) { h.assertTrue(expected.equals(MixtureHelper.extractContents(stack)), "Exact component ledger: " + expected + " / " + stack.getComponentsPatch()); }

    @GameTest(template = "test_empty")
    public static void largestRemaindersConserveEveryComponentAcrossRepeatedDrains(GameTestHelper h) {
        var normalized = new MixtureContents(Map.of(WATER, 10, ANTIFREEZE, 0, ETHANOL, -1));
        h.assertTrue(normalized.amounts().equals(Map.of(WATER, 10)), "Zero and negative entries normalize away");
        h.assertTrue(normalized.merge(normalized).totalAmount() == 20, "Merge adds absolute volumes");
        var original = awkward();
        for (int amount = 0; amount <= 1000; amount++) {
            var split = original.split(amount);
            h.assertTrue(split.extracted().totalAmount() == amount && split.remaining().totalAmount() == 1000 - amount && split.extracted().merge(split.remaining()).equals(original), "Every extraction conserves all IDs and mB: " + amount);
        }
        var expected = original.split(100).extracted();h.assertTrue(expected.amounts().get(ETHANOL) == 34 && expected.amounts().get(WATER) == 33 && expected.amounts().get(ANTIFREEZE) == 33, "The greatest remainder receives the extra millibucket");
        var remaining = original;var collected = MixtureContents.EMPTY;
        while (remaining.totalAmount() > 0) { var split = remaining.split(1);collected = collected.merge(split.extracted());remaining = split.remaining(); }
        h.assertTrue(remaining.amounts().isEmpty() && collected.equals(original), "One thousand one-mB drains conserve each constituent");
        boolean overflow = false;try { new MixtureContents(Map.of(WATER, Integer.MAX_VALUE, ETHANOL, 1)); } catch (ArithmeticException expectedError) { overflow = true; }
        h.assertTrue(overflow, "Integer overflow cannot create a smaller mixture");h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void wrappingCodecsForeignLiquidsAndTankSimulationRoundTrip(GameTestHelper h) {
        var liquid = new FluidStack(Fluids.WATER, 1000);var contents = MixtureHelper.extractContents(liquid);
        var wrapped = MixtureHelper.createStack(contents, false);h.assertTrue(MixtureHelper.isMixture(wrapped) && MixtureHelper.createStack(contents, true).is(Fluids.WATER), "Pure wrapping and opt-in unwrapping return the source fluid");
        var encoded = MixtureContents.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, awkward()).getOrThrow();
        h.assertTrue(MixtureContents.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, encoded).getOrThrow().equals(awkward()), "Persistent component codec round-trips");
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
        MixtureContents.STREAM_CODEC.encode(buffer, awkward());h.assertTrue(MixtureContents.STREAM_CODEC.decode(buffer).equals(awkward()), "Network codec round-trips integer volumes");buffer.release();
        int[] changes = {0};var tank = new MixtureFluidTank(1000, true, () -> changes[0]++);
        h.assertTrue(tank.fill(liquid, SIMULATE) == 1000 && tank.isEmpty() && changes[0] == 0, "Simulation does not change contents or notify owners");
        tank.fill(new FluidStack(Fluids.WATER, 333), EXECUTE);tank.fill(new FluidStack(BuiltInRegistries.FLUID.get(ANTIFREEZE), 333), EXECUTE);tank.fill(new FluidStack(BuiltInRegistries.FLUID.get(ETHANOL), 334), EXECUTE);same(h, awkward(), tank.getFluid());
        h.assertTrue(changes[0] == 3 && tank.fill(liquid, EXECUTE) == 0, "Foreign fluid, mod fluid and water mix to exact capacity");
        var saved = tank.writeToNBT(h.getLevel().registryAccess(), new net.minecraft.nbt.CompoundTag());var restored = new MixtureFluidTank(1000, true, () -> {});restored.readFromNBT(h.getLevel().registryAccess(), saved);same(h, awkward(), restored.getFluid());
        var preview = restored.drain(100, SIMULATE);same(h, awkward().split(100).extracted(), preview);same(h, awkward(), restored.getFluid());
        var total = MixtureContents.EMPTY;
        for (int i = 0; i < 1000; i++) total = total.merge(MixtureHelper.extractContents(restored.drain(1, EXECUTE)));
        h.assertTrue(total.equals(awkward()) && restored.isEmpty(), "Opt-in pure output still conserves the full ledger down to the last mB");h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void nativeStackCopiesShrinksAndStockTanksStayConsistent(GameTestHelper h) {
        var source = MixtureHelper.createStack(awkward(), false);var part = source.copyWithAmount(100);source.shrink(100);
        same(h, awkward(), MixtureHelper.merge(source, part, false));
        var tank = new FluidTank(1000);h.assertTrue(tank.fill(part, EXECUTE) == 100 && tank.fill(source, EXECUTE) == 900, "Native tank merges component-different batches");same(h, awkward(), tank.getFluid());
        var request = tank.getFluid().copyWithAmount(100);var drained = tank.drain(request, EXECUTE);same(h, awkward().split(100).extracted(), drained);
        h.assertTrue(MixtureHelper.extractContents(tank.getFluid()).merge(MixtureHelper.extractContents(drained)).equals(awkward()), "Native typed drain uses a complementary remainder");
        var partial = new MixtureFluidTank(117, false, () -> {});h.assertTrue(partial.fill(MixtureHelper.createStack(awkward(), false), EXECUTE) == 117, "Partial fill accepts exactly its space");same(h, awkward().split(117).extracted(), partial.getFluid());h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void gasesInvalidVolumesAndSelectivePureDrainAreRejected(GameTestHelper h) {
        var tank = new MixtureFluidTank(1000, false, () -> {});var gas = new FluidStack(CEFluids.HYDROGEN_GAS.get(), 1000);
        h.assertTrue(tank.fill(gas, EXECUTE) == 0 && tank.isEmpty(), "Gas classification is shared with the mod transport system");
        var invalid = new FluidStack(CEMixtures.CHEMICAL_MIXTURE.get(), 999);invalid.set(CEMixtures.MIXTURE_CONTENTS.get(), awkward());
        h.assertTrue(tank.fill(invalid, EXECUTE) == 0 && tank.isEmpty(), "Mismatched component totals cannot enter a tank");
        tank.fill(MixtureHelper.createStack(awkward(), false), EXECUTE);
        h.assertTrue(tank.drain(new FluidStack(Fluids.WATER, 100), EXECUTE).isEmpty() && tank.getFluidAmount() == 1000, "A water filter cannot selectively drain water from a mixture");h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void mixtureKeepsRadioactiveMaterialAndHeatDataDuringSplitAndReload(GameTestHelper h) {
        var radio = new FluidStack(BuiltInRegistries.FLUID.get(CreatePlant.location("high_level_raffinate")), 667);
        long now = h.getLevel().getGameTime();var isotope = new net.createplant.nuclear.Radionuclides(10, 20, 30, now);
        radio.set(CERadiation.MATERIAL.get(), isotope);radio.set(CEThermal.HEAT_GRADE.get(), 3);
        var tank = new MixtureFluidTank(1000, false, () -> {});tank.fill(new FluidStack(Fluids.WATER, 333), EXECUTE);tank.fill(radio, EXECUTE);
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
        FluidStack.STREAM_CODEC.encode(buffer, tank.getFluid());var synchronizedStack = FluidStack.STREAM_CODEC.decode(buffer);buffer.release();
        h.assertTrue(FluidStack.matches(tank.getFluid(), synchronizedStack), "Full network stack preserves both component amounts and isotope/heat patches");
        var before = net.createplant.nuclear.RadiationSources.fluid(tank.getFluid(), now);
        var saved = tank.writeToNBT(h.getLevel().registryAccess(), new net.minecraft.nbt.CompoundTag());var reloaded = new MixtureFluidTank(1000, false, () -> {});reloaded.readFromNBT(h.getLevel().registryAccess(), saved);
        var a = reloaded.drain(317, EXECUTE);var b = reloaded.getFluid().copy();
        var after = net.createplant.nuclear.RadiationSources.fluid(a, now).plus(net.createplant.nuclear.RadiationSources.fluid(b, now));
        h.assertTrue(Math.abs(before.activity() - after.activity()) < 1e-8, "Splitting and saving do not dilute away isotope inventory");
        for (var part : MixtureHelper.components(b)) if (part.is(radio.getFluid())) h.assertTrue(part.get(CERadiation.MATERIAL.get()).equals(isotope) && part.get(CEThermal.HEAT_GRADE.get()) == 3, "Constituent metadata survives carrier serialization");h.succeed();
    }

    @GameTest(template = "test_fractionation", timeoutTicks = 300)
    public static void realNativePumpAndBranchesConserveAwkwardComponentVolumes(GameTestHelper h) {
        var at = new BlockPos(1, 2, 3);h.setBlock(at, CETransport.LIDDED_VESSEL.get());
        var source = (LiddedVesselBlockEntity) h.getBlockEntity(at);source.getTankInventory().fill(new FluidStack(Fluids.WATER, 333), EXECUTE);
        source.getTankInventory().fill(new FluidStack(BuiltInRegistries.FLUID.get(ANTIFREEZE), 333), EXECUTE);source.getTankInventory().fill(new FluidStack(BuiltInRegistries.FLUID.get(ETHANOL), 334), EXECUTE);
        same(h, awkward(), source.getTankInventory().getFluid());
        for (int x = 2; x <= 6; x++) h.setBlock(new BlockPos(x, 2, 3), AllBlocks.FLUID_PIPE.getDefaultState().setValue(FluidPipeBlock.EAST, true).setValue(FluidPipeBlock.WEST, true));
        for (int z : new int[]{2, 4}) {
            h.setBlock(new BlockPos(6, 2, z), AllBlocks.FLUID_PIPE.getDefaultState().setValue(FluidPipeBlock.NORTH, true).setValue(FluidPipeBlock.SOUTH, true).setValue(FluidPipeBlock.EAST, true));
            h.setBlock(new BlockPos(7, 2, z), AllBlocks.FLUID_TANK.get());
        }
        h.setBlock(new BlockPos(6, 2, 3), AllBlocks.FLUID_PIPE.getDefaultState().setValue(FluidPipeBlock.WEST, true).setValue(FluidPipeBlock.NORTH, true).setValue(FluidPipeBlock.SOUTH, true));
        var pump = new BlockPos(3, 2, 3);h.setBlock(pump, AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(PumpBlock.FACING, Direction.EAST));
        h.setBlock(pump.south(), AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));h.setBlock(pump.south().west(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
        h.runAfterDelay(10, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(pump.south().west())).generatedSpeed.setValue(64));
        h.runAfterDelay(230, () -> {
            var total = MixtureHelper.extractContents(source.getTankInventory().getFluid());int moved = 0;
            for (int z : new int[]{2, 4}) {
                var tank = ((FluidTankBlockEntity) h.getBlockEntity(new BlockPos(7, 2, z))).getTankInventory();moved += tank.getFluidAmount();total = total.merge(MixtureHelper.extractContents(tank.getFluid()));
            }
            h.assertTrue(source.getTankInventory().isEmpty() && moved == 1000 && total.equals(awkward()), "Native branched transfer conserves each component: moved=" + moved + ", total=" + total + ", source=" + source.getTankInventory().getFluid());h.succeed();
        });
    }
    @GameTest(template = "test_reactor")
    public static void nativeAndLiddedMultiblocksSplitAndRejoinTheirExactComponentLedgers(GameTestHelper h) {
        for (boolean lidded : new boolean[]{false, true}) {
            var base = new BlockPos(lidded ? 9 : 3, 2, 3);
            h.setBlock(base, lidded ? CETransport.LIDDED_VESSEL.get() : AllBlocks.FLUID_TANK.get());
            h.setBlock(base.above(), lidded ? CETransport.LIDDED_VESSEL.get() : AllBlocks.FLUID_TANK.get());
            var block = (net.minecraft.world.level.block.entity.BlockEntity & com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer.Fluid) h.getBlockEntity(base);ConnectivityHandler.formMulti(block);
            var container = (com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer.Fluid) block;
            h.assertTrue(container.getHeight() == 2, "Two storeys form the actual tank");
            var original = new MixtureContents(Map.of(WATER, 3333, ANTIFREEZE, 3333, ETHANOL, 3334));
            container.getTank(0).fill(MixtureHelper.createStack(original, false), EXECUTE);
            var saved = block.saveWithoutMetadata(h.getLevel().registryAccess());block.loadWithComponents(saved, h.getLevel().registryAccess());
            ConnectivityHandler.splitMulti(block);var combined = MixtureContents.EMPTY;
            for (var at : List.of(base, base.above())) {
                var part = (com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer.Fluid) h.getBlockEntity(at);
                combined = combined.merge(MixtureHelper.extractContents(part.getTank(0).getFluid()));
            }
            h.assertTrue(combined.equals(original), "Splitting keeps complementary component shares: " + lidded + " / " + combined);
            ConnectivityHandler.formMulti(block);same(h, original, container.getTank(0).getFluid());
        }
        h.succeed();
    }

    @GameTest(template = "test_reactor")
    public static void gogglesDisplayLinksAndTransportRatingsReadAllConstituents(GameTestHelper h) {
        var at = new BlockPos(3, 3, 3);h.setBlock(at, CETransport.LIDDED_VESSEL.get());
        var vessel = (LiddedVesselBlockEntity) h.getBlockEntity(at);
        var mixture = new MixtureContents(Map.of(WATER, 400, ANTIFREEZE, 300, ETHANOL, 200, ResourceLocation.fromNamespaceAndPath("createdieselgenerators", "plant_oil"), 100));
        vessel.getTankInventory().fill(MixtureHelper.createStack(mixture, false), EXECUTE);
        var lines = new ArrayList<net.minecraft.network.chat.Component>();vessel.addToGoggleTooltip(lines, false);
        h.assertTrue(lines.size() == 4 && MixtureGoggleHelper.describe(vessel.getTankInventory().getFluid()).size() == 5, "Four goggles lines retain a full five-line composition for displays");
        h.setBlock(at.below(), AllBlocks.DISPLAY_LINK.getDefaultState().setValue(com.simibubi.create.content.redstone.displayLink.DisplayLinkBlock.FACING, Direction.DOWN));
        var link = (com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity) h.getBlockEntity(at.below());link.activeSource = CEDisplays.MIXTURE.get();
        var text = link.activeSource.provideText(new com.simibubi.create.content.redstone.displayLink.DisplayLinkContext(h.getLevel(), link), new com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats(8, 80, null));
        h.assertTrue(text.size() == 5, "A real Display Link context supplies every constituent");same(h, mixture, vessel.getTankInventory().getFluid());
        var acidic = MixtureHelper.merge(new FluidStack(Fluids.WATER, 999), new FluidStack(CEFluids.NITRIC_ACID.get(), 1), false);
        h.assertTrue(!net.createplant.thermal.TankGrade.STANDARD.accepts(acidic) && net.createplant.thermal.TankGrade.STAINLESS.accepts(acidic), "A one-mB acidic constituent is still subject to corrosion ratings");
        h.assertTrue(!net.createplant.transport.PipeMaterial.COPPER.carries(acidic), "Mixture carrier does not hide an incompatible constituent from ordinary pipes");
        h.succeed();
    }

    @GameTest(template = "test_reactor")
    public static void anEmptyTankAndDensityOutletsPreserveEveryConstituent(GameTestHelper h) {
        var at = new BlockPos(3, 3, 3);
        h.setBlock(at, CETransport.LIDDED_VESSEL.get());
        var vessel = (LiddedVesselBlockEntity) h.getBlockEntity(at);
        var level = h.getLevel();
        var pos = h.absolutePos(at);
        var top = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, Direction.UP);
        var bottom = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, Direction.DOWN);
        h.assertTrue(top.drain(Integer.MAX_VALUE, SIMULATE).isEmpty() && vessel.getTankInventory().isEmpty(), "A new tank holds nothing, not even air");
        var lookup = level.registryAccess();

        var gases = List.of(CEFluids.HYDROGEN_GAS.get(), CEFluids.NITROGEN_GAS.get(), CEFluids.OXYGEN_GAS.get(),
            CEFluids.ARGON_GAS.get(), CEProcessFluids.CARBON_DIOXIDE.get());
        for (var gas : gases) h.assertTrue(top.fill(new FluidStack(gas, 200), EXECUTE) == 200, "A gas enters the tank without displacing other stored gas");
        var saved = vessel.saveWithoutMetadata(lookup);
        vessel.loadWithComponents(saved, lookup);
        top = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, Direction.UP);
        bottom = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, Direction.DOWN);
        var lines = new ArrayList<net.minecraft.network.chat.Component>();
        vessel.addToGoggleTooltip(lines, false);
        h.assertTrue(lines.size() == 6, "An overlay displays all five gases");
        h.assertTrue(top.drain(1, SIMULATE).is(CEFluids.HYDROGEN_GAS.get()) && bottom.drain(1, SIMULATE).is(CEProcessFluids.CARBON_DIOXIDE.get()),
            "Upper and lower ports preview lightest and densest gas respectively");
        h.assertTrue(bottom.drain(new FluidStack(CEFluids.HYDROGEN_GAS.get(), 50), EXECUTE).isEmpty(), "A filter cannot draw the upper layer through the lower port");
        h.assertTrue(bottom.drain(50, EXECUTE).is(CEProcessFluids.CARBON_DIOXIDE.get())
            && top.drain(70, EXECUTE).is(CEFluids.HYDROGEN_GAS.get()) && vessel.getTankInventory().getFluidAmount() == 880,
            "Taking the extreme layers removes precisely the exported component volumes");
        h.succeed();
    }

    @GameTest(template = "test_reactor")
    public static void settledLiquidsShowAndDrainTheSameLayerOrder(GameTestHelper h) {
        var at = new BlockPos(3, 3, 3);
        h.setBlock(at, CETransport.LIDDED_VESSEL.get());
        var vessel = (LiddedVesselBlockEntity) h.getBlockEntity(at);
        var alcohol = BuiltInRegistries.FLUID.get(ETHANOL);
        vessel.getTankInventory().fill(new FluidStack(alcohol, 400), EXECUTE);
        vessel.getTankInventory().fill(new FluidStack(Fluids.WATER, 600), EXECUTE);
        var layers = vessel.liquidLayers();
        h.assertTrue(layers.size() == 2 && layers.getFirst().is(Fluids.WATER) && layers.getLast().is(alcohol),
            "The visible layers are ordered by liquid density");
        var top = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(at), Direction.UP);
        var bottom = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(at), Direction.DOWN);
        h.assertTrue(top.getFluidInTank(0).is(alcohol) && top.drain(100, EXECUTE).is(alcohol), "The top exposes the lighter liquid");
        h.assertTrue(bottom.drain(100, EXECUTE).is(Fluids.WATER), "The bottom exposes the denser liquid");
        same(h, new MixtureContents(Map.of(WATER, 500, ETHANOL, 300)), vessel.getTankInventory().getFluid());
        h.succeed();
    }
}
