package net.createplant.atomics.gametest;

import net.createplant.CreatePlant;
import net.createplant.atomics.block.entity.FuelChannelBlockEntity;
import net.createplant.atomics.block.entity.FuelPoolBlockEntity;
import net.createplant.atomics.block.entity.RodCapBlockEntity;
import net.createplant.atomics.nuclear.ReactorCoreStructure;
import net.createplant.atomics.nuclear.ReactorFuel;
import net.createplant.atomics.nuclear.ReactorPhysics;
import net.createplant.atomics.registry.CEFuelCycle;
import net.createplant.atomics.registry.CENuclear;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public class CompactFuelGameTests {
    private static FuelChannelBlockEntity fourFuel(GameTestHelper h, ReactorCoreStructure.Result structure) {
        return structure.fuel().stream().map(pos -> (FuelChannelBlockEntity) h.getLevel().getBlockEntity(pos))
            .filter(column -> column.fuelChannels() == 4).findFirst().orElseThrow();
    }

    @GameTest(template = "test_rbmk")
    public static void mixedFuelWorthBurnupAndReloadPreserveFiniteEnergy(GameTestHelper h) {
        var fixture = CompactFuelLongGameTests.fixture(h, 5, 3, false, null);
        var column = fourFuel(h, fixture.structure());
        double bonus = ReactorFuel.COMPACT_ENRICHED.worth() - ReactorPhysics.FUEL_WORTH;
        for (int slot = 0; slot < 4; slot++) {
            var rod = new ItemStack((slot < 2 ? ReactorFuel.COMPACT_ENRICHED : ReactorFuel.URANIUM).item());
            h.assertTrue(column.topItems().insertItem(slot, rod, true).isEmpty() && column.fresh() == slot,
                "Simulating each mixed rod leaves both identity and count unchanged");
            h.assertTrue(column.topItems().insertItem(slot, rod, false).isEmpty(), "Both grades load in the compact assembly");
        }
        h.assertTrue(column.cellLoad() == 4 && Math.abs(column.cellReactivityBonus() - 2 * bonus) < 1e-12,
            "Two enriched and two ordinary rods add precisely two rod contributions without changing the power load");
        column.burn(12e9);
        h.assertTrue(column.rodJoules() == 12e9 && column.cellLoad() == 3 && Math.abs(column.cellReactivityBonus() - 1.5 * bonus) < 1e-12,
            "Each of the four rods spends 3 GJ and retains 75% of its own reactivity contribution");
        var saved = column.saveWithoutMetadata(h.getLevel().registryAccess());
        for (int reload = 0; reload < 3; reload++) column.loadWithComponents(saved.copy(), h.getLevel().registryAccess());
        h.assertTrue(column.cellLoad() == 3 && column.rodJoules() == 12e9 && Math.abs(column.cellReactivityBonus() - 1.5 * bonus) < 1e-12
            && column.topItems().getStackInSlot(0).is(CENuclear.COMPACT_ENRICHED_FUEL_ROD.get())
            && column.topItems().getStackInSlot(3).is(CENuclear.FUEL_ROD.get()) && !column.acceptsAssemblyHeight(7),
            "Repeated real save/load retains the two fuel identities, remaining 36 GJ and the locked assembly height");
        column.burn(36e9 - 4);
        h.assertTrue(column.spent() == 0 && column.cellLoad() > 0, "A rod is not spent before its exact energy budget is used");
        column.burn(4);
        h.assertTrue(column.spent() == 4 && column.cellReactivityBonus() == 0, "All four rods become spent after exactly 48 GJ");
        for (int slot = 0; slot < 4; slot++) h.assertTrue(column.topItems().extractItem(slot, 1, false).is(CENuclear.SPENT_FUEL_ROD.get()),
            "Every grade uses the existing spent fuel and reprocessing path");
        h.assertTrue(column.acceptsAssemblyHeight(7), "Only removing every assembly item releases the height lock");
        h.succeed();
    }

    @GameTest(template = "test_rbmk")
    public static void sevenMetreCoreRejectsEveryEnrichedInsertionPath(GameTestHelper h) {
        var fixture = CompactFuelLongGameTests.fixture(h, 5, 7, false, null);
        var column = fourFuel(h, fixture.structure());
        var capPos = column.getBlockPos().above(column.layout().fuelAssemblyLength());
        var cap = (RodCapBlockEntity) h.getLevel().getBlockEntity(capPos);
        var port = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, capPos, Direction.UP);
        var rod = CENuclear.COMPACT_ENRICHED_FUEL_ROD.toStack(2);
        h.assertTrue(port != null && !port.isItemValid(0, rod) && port.insertItem(0, rod, true).getCount() == 2
            && port.insertItem(0, rod, false).getCount() == 2 && column.topItems().insertItem(1, rod, false).getCount() == 2
            && !cap.installFuel(0, rod) && column.fresh() == 0 && rod.getCount() == 2,
            "Cap interaction and simulated/executed direct handlers all reject compact fuel without consuming it");
        for (var pos : fixture.structure().fuel()) {
            var candidate = (FuelChannelBlockEntity) h.getLevel().getBlockEntity(pos);
            for (int slot = 0; slot < 4; slot++) if (candidate.control(slot)) {
                var candidateCap = (RodCapBlockEntity) h.getLevel().getBlockEntity(pos.above(candidate.layout().fuelAssemblyLength()));
                h.assertTrue(!candidateCap.installFuel(slot, rod) && candidate.control(slot), "Rejected compact fuel cannot change a control channel into fuel");
            }
        }
        h.assertTrue(port.insertItem(0, CENuclear.FUEL_ROD.toStack(), false).isEmpty() && column.rodJoules() == 28e9
            && column.cellReactivityBonus() == 0, "Ordinary seven-metre fuel keeps its existing 28 GJ and reactivity");
        h.succeed();
    }

    @GameTest(template = "test_rbmk")
    public static void enrichedFuelCannotBeStretchedByRebuildingTheCore(GameTestHelper h) {
        var fixture = CompactFuelLongGameTests.fixture(h, 5, 3, false, null);
        var column = fourFuel(h, fixture.structure());
        column.topItems().insertItem(0, CENuclear.COMPACT_ENRICHED_FUEL_ROD.toStack(), false);
        column.burn(3e9);
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) for (int y = 3; y < 7; y++)
            h.setBlock(CompactFuelLongGameTests.BASE.offset(x, y, z), CENuclear.FUEL_CHANNEL.get());
        var saved = column.saveWithoutMetadata(h.getLevel().registryAccess());
        column.loadWithComponents(saved.copy(), h.getLevel().registryAccess());
        var refused = ReactorCoreStructure.scan(h.getLevel(), column.getBlockPos());
        h.assertTrue(!refused.formed() && refused.reason().equals("fuel_height") && column.rodJoules() == 12e9 && column.cellLoad() == .75,
            "A rebuilt seven-layer core cannot stretch its installed compact fuel or reset its remaining 9 GJ");
        h.succeed();
    }

    @GameTest(template = "test_rbmk")
    public static void poolSaveAndExistingMoxDataRetainFuelIdentities(GameTestHelper h) {
        var base = new BlockPos(5, 4, 5);
        FuelPoolGameTests.build(h, base, 2, 2);
        var water = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(base), Direction.DOWN);
        water.fill(new FluidStack(Fluids.WATER, 4 * FuelPoolBlockEntity.WATER_PER_CELL), EXECUTE);
        var port = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(base), Direction.UP);
        h.assertTrue(port.insertItem(0, CENuclear.COMPACT_ENRICHED_FUEL_ROD.toStack(), false).isEmpty()
            && port.insertItem(1, CEFuelCycle.MOX_FUEL_ROD.toStack(), false).isEmpty(), "The water-covered pool accepts both fuel identities");
        var pool = (FuelPoolBlockEntity) h.getBlockEntity(base);
        var saved = pool.saveWithoutMetadata(h.getLevel().registryAccess());pool.loadWithComponents(saved.copy(), h.getLevel().registryAccess());
        h.assertTrue(pool.fuel(0) == ReactorFuel.COMPACT_ENRICHED && pool.fuel(1) == ReactorFuel.MOX
            && port.getStackInSlot(0).is(CENuclear.COMPACT_ENRICHED_FUEL_ROD.get()), "Pool reload keeps enriched fuel distinguishable for the crane");
        saved.remove("FuelKinds");saved.putByteArray("Mox", new byte[]{0,1,0,0});pool.loadWithComponents(saved, h.getLevel().registryAccess());
        h.assertTrue(pool.fuel(0) == ReactorFuel.URANIUM && pool.fuel(1) == ReactorFuel.MOX, "Existing ordinary/MOX pools load with their original identities");
        h.succeed();
    }
}
