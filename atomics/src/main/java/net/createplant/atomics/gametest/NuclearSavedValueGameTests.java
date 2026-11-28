package net.createplant.atomics.gametest;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.createplant.CreatePlant;
import net.createplant.atomics.block.entity.CraneDriveBlockEntity;
import net.createplant.atomics.block.entity.FuelChannelBlockEntity;
import net.createplant.atomics.block.entity.FuelPoolBlockEntity;
import net.createplant.atomics.block.entity.FusionControlConsoleBlockEntity;
import net.createplant.atomics.block.entity.ReactorControlConsoleBlockEntity;
import net.createplant.atomics.nuclear.ReactorCore;
import net.createplant.atomics.nuclear.ReactorPhysics;
import net.createplant.atomics.registry.CECrane;
import net.createplant.atomics.registry.CEFusion;
import net.createplant.atomics.registry.CENuclear;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Broken saves and packets (NaN or infinity) of the reactor plant, the fusion console and the crane come back as finite values. */
@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NuclearSavedValueGameTests {
    private static CompoundTag saved(GameTestHelper h, SmartBlockEntity entity, String... keys) {
        CompoundTag tag = entity.saveWithoutMetadata(h.getLevel().registryAccess());
        for (String key : keys) tag.putDouble(key, Double.NaN);
        return tag;
    }

    @GameTest(template = "test_empty")
    public static void brokenCoreSaveRestartsFromRestAndScrams(GameTestHelper h) {
        BlockPos pos = new BlockPos(2, 2, 2);
        h.setBlock(pos, CENuclear.FUEL_CHANNEL.get());
        var channel = (FuelChannelBlockEntity) h.getBlockEntity(pos);
        CompoundTag tag = saved(h, channel, "Burnt0", "Specific", "FuelKelvin");
        tag.putFloat("ControlDepth0", Float.NaN);
        channel.loadWithComponents(tag, h.getLevel().registryAccess());
        CompoundTag repaired = channel.saveWithoutMetadata(h.getLevel().registryAccess());
        h.assertTrue(Double.isFinite(channel.fuelKelvin()) && Double.isFinite(repaired.getDouble("Specific"))
                && repaired.getDouble("Burnt0") == FuelChannelBlockEntity.ROD_JOULES && Float.isFinite(repaired.getFloat("ControlDepth0")),
            "A channel with a broken save keeps finite water and fuel, and a rod of unknown burn-up counts as spent rather than fresh");

        var core = new ReactorCore(channel);
        CompoundTag server = new CompoundTag();
        for (String key : new String[] {"Hazard", "ScramPower", "Level", "Precursors", "Iodine", "Xenon"}) server.putDouble(key, Double.NaN);
        core.read(server, false);
        h.assertTrue(core.scram() && core.hazard() == 0 && core.xenon() == 0, "A core with a broken save restarts from rest and is scrammed");
        var healthy = new ReactorCore(channel);
        CompoundTag fine = new CompoundTag();
        fine.putDouble("Level", ReactorPhysics.SOURCE_LEVEL);
        healthy.read(fine, false);
        h.assertTrue(!healthy.scram(), "A sound save does not scram the core");

        var shown = new ReactorCore(channel);
        CompoundTag packet = new CompoundTag();
        for (String key : new String[] {"Power", "Reactivity", "Void", "FuelKelvin", "Hottest", "Rods", "Leak"}) packet.putDouble(key, Double.POSITIVE_INFINITY);
        shown.read(packet, true);
        h.assertTrue(Double.isFinite(shown.power()) && Double.isFinite(shown.reactivity()) && Double.isFinite(shown.averageVoid())
                && Double.isFinite(shown.hottestKelvin()) && Double.isFinite(shown.rodInsertion()),
            "The core's display values from a bad packet are finite");
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void displaysOfThePlantRejectNonFiniteValues(GameTestHelper h) {
        var registries = h.getLevel().registryAccess();
        BlockPos consolePos = new BlockPos(1, 2, 1), poolPos = new BlockPos(5, 2, 1), fusionPos = new BlockPos(7, 2, 1);
        h.setBlock(consolePos, CENuclear.REACTOR_CONTROL_CONSOLE.get());
        h.setBlock(poolPos, CENuclear.FUEL_POOL.get());
        h.setBlock(fusionPos, CEFusion.CONSOLE.get());

        var console = (ReactorControlConsoleBlockEntity) h.getBlockEntity(consolePos);
        CompoundTag consoleTag = saved(h, console, "Power", "Reactivity", "Hottest", "Coolant", "Graphite", "Outlet", "Void", "Rods");
        consoleTag.putInt("SelectedIndex", 7);
        console.handleUpdateTag(consoleTag, registries);
        h.assertTrue(Double.isFinite(console.power()) && Double.isFinite(console.reactivity()) && Double.isFinite(console.hottestKelvin())
                && Double.isFinite(console.coolantKelvin()) && Double.isFinite(console.graphiteKelvin()) && Double.isFinite(console.representativeOutletKelvin())
                && Double.isFinite(console.voidFraction()) && Double.isFinite(console.rodInsertion()) && console.selectedDepth() == -1,
            "The reactor console shows finite values and no rod beyond its map");

        var pool = (FuelPoolBlockEntity) h.getBlockEntity(poolPos);
        CompoundTag poolTag = saved(h, pool, "PoolKelvin");
        poolTag.putInt("PoolCells", 1);
        pool.handleUpdateTag(poolTag, registries);
        h.assertTrue(!pool.saveWithFullMetadata(registries).contains("PoolKelvin") && pool.waterAmount() == 0, "The fuel pool does not retain an obsolete temperature field");

        var fusion = (FusionControlConsoleBlockEntity) h.getBlockEntity(fusionPos);
        fusion.handleUpdateTag(saved(h, fusion, "Power", "Vacuum", "Coil", "Charge", "Plasma"), registries);
        h.assertTrue(Double.isFinite(fusion.power()) && Double.isFinite(fusion.vacuumPa()) && Double.isFinite(fusion.coilK())
                && Double.isFinite(fusion.charge()) && Double.isFinite(fusion.plasmaK()), "The fusion console shows finite values");
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void brokenCraneSaveReturnsToASafePosition(GameTestHelper h) {
        BlockPos pos = new BlockPos(2, 2, 2);
        h.setBlock(pos, CECrane.DRIVE.get());
        var drive = (CraneDriveBlockEntity) h.getBlockEntity(pos);
        CompoundTag tag = saved(h, drive, "U", "V", "Depth", "Reach");
        drive.loadWithComponents(tag, h.getLevel().registryAccess());
        CompoundTag repaired = drive.saveWithoutMetadata(h.getLevel().registryAccess());
        h.assertTrue(repaired.getDouble("U") == 0 && repaired.getDouble("V") == 1 && drive.depth() == 0
                && repaired.getDouble("Reach") == CraneDriveBlockEntity.ROD_LENGTH, "A crane with a broken save goes back to the start of its frame");
        h.succeed();
    }
}
