package net.createplant.gametest;

import com.jesz.createdieselgenerators.CDGBlocks;
import com.jesz.createdieselgenerators.content.tools.FueledToolItem;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import net.createplant.CreatePlant;
import net.createplant.chemistry.DistillationProfile;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CEProcessFluids;
import net.createplant.registry.CETransport;
import net.createplant.thermal.FluidMaterials;
import net.createplant.thermal.FluidThermal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

/** Liquid and gas nitrogen are two selectable views of the column's one finite nitrogen stock. */
@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class VesselNitrogenGameTests {
    @GameTest(template = "test_fractionation", timeoutTicks = 260)
    public static void finiteColumnStockFeedsLiquidNitrogenThroughANativeSmartPipe(GameTestHelper h) {
        BlockPos base = new BlockPos(8, 2, 8);
        var owner = DistillationGameTests.build(h.getLevel(), h.absolutePos(base), 2, 14);
        try {
            h.assertTrue(DistillationGameTests.inlet(owner, 2).fill(FluidThermal.at(new FluidStack(net.createplant.registry.CEProcessFluids.COMPRESSED_AIR.get(), 10000), 300), EXECUTE) == 10000,
                "Ten real compressed-air batches enter the fourteen-layer air separator");
        } finally { }
        DistillationGameTests.run(owner, 10 * DistillationProfile.CYCLE_TICKS + 5);
        int initial = owner.stock(DistillationProfile.NITROGEN);
        var outlet = DistillationGameTests.outlet(owner, 13);
        h.assertTrue(initial == 7800 && owner.stock(DistillationProfile.AIR) == 0 && outlet.getTanks() == 2
            && outlet.getFluidInTank(0).is(CEFluids.NITROGEN_GAS.get()) && outlet.getFluidInTank(0).getAmount() == initial
            && outlet.getFluidInTank(1).is(CEProcessFluids.LIQUID_NITROGEN.get()) && outlet.getFluidInTank(1).getAmount() == 12,
            "The nitrogen outlet advertises gas and floor(stock / 646) liquid without duplicating its stored material");
        var liquidRequest = new FluidStack(CEProcessFluids.LIQUID_NITROGEN.get(), 3);
        var preview = outlet.drain(liquidRequest, SIMULATE);
        h.assertTrue(preview.getAmount() == 3 && preview.is(CEProcessFluids.LIQUID_NITROGEN.get())
            && owner.stock(DistillationProfile.NITROGEN) == initial && outlet.drain(100, SIMULATE).getAmount() == 100,
            "Simulating either phase leaves their shared finite gas-equivalent stock untouched");
        var liquid = outlet.drain(liquidRequest, EXECUTE);
        var gas = outlet.drain(100, EXECUTE);
        h.assertTrue(liquid.getAmount() == 3 && gas.is(CEFluids.NITROGEN_GAS.get()) && gas.getAmount() == 100
            && owner.stock(DistillationProfile.NITROGEN) == 5762 && outlet.getFluidInTank(1).getAmount() == 8,
            "Taking three liquid mB debits exactly 1938 gas mB; taking gas then debits the same stock once");

        BlockPos smart = base.above(13).north(), pump = smart.north(), receiver = pump.north(), cog = pump.east(), motor = cog.south();
        h.setBlock(smart, AllBlocks.SMART_FLUID_PIPE.getDefaultState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        h.setBlock(pump, AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        h.setBlock(receiver, CETransport.CRYOGENIC_TANK.get());
        h.setBlock(cog, AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        var selection = BlockEntityBehaviour.get(h.getBlockEntity(smart), FilteringBehaviour.TYPE);
        selection.setFilter(new ItemStack(Items.WATER_BUCKET));
        var stored = ((FluidTankBlockEntity) h.getBlockEntity(receiver)).getTankInventory();
        // A native canister supplies an ordinary fluid filter sample; the sample is independent of the plant inventory.
        var sample = new ItemStack(CDGBlocks.CANISTER.asItem());
        var sampleContainer = ((FueledToolItem) sample.getItem()).getFluidHandler(sample);
        h.assertTrue(sampleContainer.fill(FluidThermal.at(new FluidStack(CEProcessFluids.LIQUID_NITROGEN.get(), 1000), 77.36), EXECUTE) == 1000,
            "A real fluid container can hold the liquid-nitrogen filter sample");
        h.runAfterDelay(5, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(128));
        h.runAfterDelay(40, () -> {
            h.assertTrue(stored.getFluid().isEmpty() && owner.stock(DistillationProfile.NITROGEN) == 5762,
                "A mismatched native smart-pipe filter cannot drain either phase from the column");
            boolean accepted = selection.setFilter(sample);
            var current = DistillationGameTests.outlet(owner, 13);
            var actualLiquid = current.getFluidInTank(1);var actualGas = current.getFluidInTank(0);
            boolean untrackedMatch = selection.test(new FluidStack(CEProcessFluids.LIQUID_NITROGEN.get(), 1));
            boolean actualMatch = selection.test(actualLiquid), gasMatch = selection.test(actualGas);
            var resolved = FilterItemStack.of(sample).fluid(h.getLevel());
            h.assertTrue(accepted && actualMatch && !gasMatch,
                "The native fluid filter resolves a filled canister and selects the column's real 77.36 K liquid rather than gas: accepted=" + accepted
                    + ", untrackedMatch=" + untrackedMatch + ", actualMatch=" + actualMatch + ", gasMatch=" + gasMatch
                    + ", liquid=" + actualLiquid + actualLiquid.getComponentsPatch() + ", resolvedSample=" + resolved + resolved.getComponentsPatch()
                    + ", itemCapability=" + sample.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.ITEM)
                    + ", currentBlock=" + h.getBlockState(smart));
        });
        h.runAfterDelay(180, () -> {
            var received = stored.getFluid();
            var live = DistillationGameTests.outlet(owner, 13);
            int remaining = owner.stock(DistillationProfile.NITROGEN);
            h.assertTrue(received.is(CEProcessFluids.LIQUID_NITROGEN.get()) && received.getAmount() == 8 && remaining == 594
                && live.getFluidInTank(1).isEmpty() && live.getFluidInTank(0).getAmount() == 594,
                "An outlet-mounted smart pipe and real Create pump extract only whole liquid mB and retain the gas remainder: stock=" + remaining
                    + ", destination=" + received + ", filter=" + selection.getFilter());
            double initialMass = FluidMaterials.mass(new FluidStack(CEFluids.NITROGEN_GAS.get(), initial));
            double finalMass = FluidMaterials.mass(gas) + FluidMaterials.mass(liquid) + FluidMaterials.mass(received)
                + FluidMaterials.mass(new FluidStack(CEFluids.NITROGEN_GAS.get(), remaining));
            h.assertTrue(remaining + gas.getAmount() + (liquid.getAmount() + received.getAmount()) * 646 == initial
                && Math.abs(finalMass - initialMass) < initialMass * 1e-9 && sampleContainer.getFluidInTank(0).getAmount() == 1000,
                "Mixed gas/liquid recovery preserves exact nitrogen mass, integer stock and the unchanged filter sample");
            h.succeed();
        });
    }
}
