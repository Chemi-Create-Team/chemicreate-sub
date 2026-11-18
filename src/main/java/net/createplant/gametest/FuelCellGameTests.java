package net.createplant.gametest;

import net.createplant.CreatePlant;
import net.createplant.block.entity.FuelCellBlockEntity;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CEFuelCell;
import net.createplant.thermal.FluidThermal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

/** The fuel cell: the hydrogen, oxygen and water that go with the current it gives, and a backfed stack. The real circuit is in CircuitGameTests. */
@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class FuelCellGameTests {
    @GameTest(template = "test_reactor", timeoutTicks = 100)
    public static void fuelCellBurnsHydrogenAndOxygenIntoWaterWithTheCurrent(GameTestHelper h) {
        BlockPos pos = new BlockPos(2, 2, 2);
        h.setBlock(pos, CEFuelCell.FUEL_CELL.get());
        var ports = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(pos), Direction.UP);
        h.assertTrue(ports.fill(new FluidStack(CEFluids.HYDROGEN_GAS.get(), 1000), EXECUTE) == 1000 && ports.fill(new FluidStack(CEFluids.OXYGEN_GAS.get(), 500), EXECUTE) == 500
            && ports.fill(new FluidStack(Fluids.WATER, 100), EXECUTE) == 0, "Hydrogen and oxygen go in, water does not");
        h.runAfterDelay(5, () -> {
            var cell = (FuelCellBlockEntity) h.getBlockEntity(pos);
            h.assertTrue(cell.ready(h.getLevel().getGameTime()) && (cell.status().equals("unwired") || cell.status().equals("idle")) && cell.hydrogen().getAmount() == 1000,
                "With fuel and no wires the cell is ready, gives no current and burns nothing: " + cell.status() + ", " + cell.volts() + " V");
            long time = h.getLevel().getGameTime();
            // A matched 20 ohm load: 200 V at the terminals, 10 A, 2 kW; 400 V x 10 A over a tick is 200 J, 13.3 mB of hydrogen at 15 J each.
            for (int i = 0; i < 3; i++) cell.supplied(200, true, time);
            h.assertTrue(Math.abs(cell.watts() - 2000) < 1e-6, "A matched load takes 2 kW: " + cell.watts());
            h.assertTrue(cell.hydrogen().getAmount() == 960 && cell.oxygen().getAmount() == 480 && cell.water().getAmount() == 60,
                "Three ticks burn 40 mB of hydrogen with 20 mB of oxygen into 60 mB of water: " + cell.hydrogen().getAmount() + " " + cell.oxygen().getAmount() + " " + cell.water().getAmount());
            h.assertTrue(cell.water().is(Fluids.WATER), "The output is ordinary water: " + FluidThermal.kelvin(cell.water()));
            var out = ports.drain(100, EXECUTE);
            h.assertTrue(out.is(Fluids.WATER) && out.getAmount() == 60 && ports.drain(new FluidStack(CEFluids.HYDROGEN_GAS.get(), 10), EXECUTE).isEmpty(), "Only the water comes out");
            cell.supplied(450, true, time);
            h.assertTrue(cell.status().equals("backfed") && !cell.ready(time + 1) && cell.ready(time + 20), "A stack driven above 400 V lets go and looks again a second later");
            h.succeed();
        });
    }

    @GameTest(template = "test_reactor")
    public static void fuelCellWaitsForACompleteAtomicReaction(GameTestHelper h) {
        BlockPos oxygenStarved = new BlockPos(2, 2, 2);
        h.setBlock(oxygenStarved, CEFuelCell.FUEL_CELL.get());
        var starvedPort = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(oxygenStarved), Direction.UP);
        starvedPort.fill(new FluidStack(CEFluids.HYDROGEN_GAS.get(), 1000), EXECUTE);
        starvedPort.fill(new FluidStack(CEFluids.OXYGEN_GAS.get(), 1), EXECUTE);
        var starved = (FuelCellBlockEntity) h.getBlockEntity(oxygenStarved);
        starved.supplied(200, true, h.getLevel().getGameTime());
        h.assertTrue(starved.hydrogen().getAmount() == 1000 && starved.oxygen().getAmount() == 1 && starved.water().isEmpty(),
            "A source tick does not consume hydrogen or make water without enough oxygen");
        h.assertTrue(starvedPort.getFluidInTank(-1).isEmpty() && starvedPort.getFluidInTank(3).isEmpty()
            && starvedPort.getTankCapacity(-1) == 0 && starvedPort.getTankCapacity(3) == 0,
            "Out-of-range tank indices expose neither contents nor capacity");

        BlockPos waterFull = oxygenStarved.east(2);
        h.setBlock(waterFull, CEFuelCell.FUEL_CELL.get());
        var full = (FuelCellBlockEntity) h.getBlockEntity(waterFull);
        CompoundTag saved = full.saveWithoutMetadata(h.getLevel().registryAccess());
        saved.put("Hydrogen", new FluidStack(CEFluids.HYDROGEN_GAS.get(), 1000).save(h.getLevel().registryAccess()));
        saved.put("Oxygen", new FluidStack(CEFluids.OXYGEN_GAS.get(), 500).save(h.getLevel().registryAccess()));
        saved.put("Water", FluidThermal.at(new FluidStack(Fluids.WATER, FuelCellBlockEntity.CAPACITY - 2), FuelCellBlockEntity.WATER_KELVIN)
            .save(h.getLevel().registryAccess()));
        full.loadWithComponents(saved, h.getLevel().registryAccess());
        full.supplied(200, true, h.getLevel().getGameTime());
        h.assertTrue(full.hydrogen().getAmount() == 1000 && full.oxygen().getAmount() == 500
            && full.water().getAmount() == FuelCellBlockEntity.CAPACITY - 2,
            "A source tick consumes nothing when there is no room for all reaction water");
        h.succeed();
    }

    @GameTest(template = "test_reactor")
    public static void brokenFuelCellKeepsItsThreeTanks(GameTestHelper h) {
        BlockPos pos = new BlockPos(2, 2, 2);
        h.setBlock(pos, CEFuelCell.FUEL_CELL.get());
        var port = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(pos), Direction.UP);
        port.fill(new FluidStack(CEFluids.HYDROGEN_GAS.get(), 700), EXECUTE);
        port.fill(new FluidStack(CEFluids.OXYGEN_GAS.get(), 350), EXECUTE);
        var cell = (FuelCellBlockEntity) h.getBlockEntity(pos);
        cell.supplied(200, true, h.getLevel().getGameTime());
        var drops = Block.getDrops(h.getBlockState(pos), h.getLevel(), h.absolutePos(pos), cell);
        var data = drops.size() == 1 ? drops.getFirst().get(DataComponents.BLOCK_ENTITY_DATA) : null;
        h.assertTrue(data != null && !data.isEmpty(), "The dropped fuel cell keeps its block entity data");
        BlockPos restored = pos.east(2);
        h.setBlock(restored, CEFuelCell.FUEL_CELL.get());
        var next = (FuelCellBlockEntity) h.getBlockEntity(restored);
        h.assertTrue(data.loadInto(next, h.getLevel().registryAccess())
            && next.hydrogen().getAmount() == cell.hydrogen().getAmount()
            && next.oxygen().getAmount() == cell.oxygen().getAmount()
            && next.water().getAmount() == cell.water().getAmount(),
            "Placing the dropped fuel cell restores every tank");
        h.succeed();
    }
}
