package net.createplant.atomics.gametest;

import net.createplant.gametest.ExplicitGameTest;
import com.google.gson.GsonBuilder;

import net.createplant.CreatePlant;
import net.createplant.atomics.block.entity.FuelPoolBlockEntity;
import net.createplant.atomics.registry.CENuclear;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
@ExplicitGameTest
public class RadiationBenchmarkGameTests {
    private static ServerLevel measuring;
    private static long start;
    private static final ArrayList<Double> samples = new ArrayList<>();
    private static boolean installed;

    private static void pre(LevelTickEvent.Pre event) {
        if (event.getLevel() == measuring) start = System.nanoTime();
    }

    private static void post(LevelTickEvent.Post event) {
        if (event.getLevel() == measuring) samples.add((System.nanoTime() - start) / 1e6);
    }

    @GameTest(template = "test_rbmk", timeoutTicks = 240)
    public static void loadedPoolAndEightPlayers(GameTestHelper h) {
        if (!installed) {
            NeoForge.EVENT_BUS.addListener(RadiationBenchmarkGameTests::pre);
            NeoForge.EVENT_BUS.addListener(RadiationBenchmarkGameTests::post);
            installed = true;
        }
        var base = new BlockPos(5, 1, 5);
        FuelPoolGameTests.build(h, base, 7, 7);
        var water =
                h.getLevel()
                        .getCapability(
                                Capabilities.FluidHandler.BLOCK,
                                h.absolutePos(base),
                                Direction.DOWN);
        water.fill(
                new FluidStack(Fluids.WATER, 49 * FuelPoolBlockEntity.WATER_PER_CELL),
                FluidAction.EXECUTE);
        for (int x = 0; x < 7; x++)
            for (int z = 0; z < 7; z++) {
                var port =
                        h.getLevel()
                                .getCapability(
                                        Capabilities.ItemHandler.BLOCK,
                                        h.absolutePos(base.offset(x, 0, z)),
                                        Direction.UP);
                for (int i = 0; i < port.getSlots(); i++)
                    h.assertTrue(
                            port.insertItem(i, CENuclear.SPENT_FUEL_ROD.toStack(), false).isEmpty(),
                            "All 196 assemblies must be physically stored before timing");
            }
        var players = new ArrayList<ServerPlayer>();
        for (int i = 0; i < 8; i++) {
            var player =
                    new net.neoforged.neoforge.common.util.FakePlayer(
                            h.getLevel(),
                            new com.mojang.authlib.GameProfile(
                                    java.util.UUID.randomUUID(), "radiation-bench-" + i));
            player.getAbilities().invulnerable = true;
            h.getLevel().addNewPlayer(player);
            var at = h.absolutePos(base.offset(i - 1, 7, -3));
            player.setPos(at.getX() + .5, at.getY(), at.getZ() + .5);
            players.add(player);
        }
        h.runAfterDelay(
                40,
                () -> {
                    samples.clear();
                    measuring = h.getLevel();
                });
        h.runAfterDelay(
                160,
                () -> {
                    measuring = null;
                    var sorted = samples.stream().sorted().toList();
                    double mean =
                            samples.stream()
                                    .mapToDouble(Double::doubleValue)
                                    .average()
                                    .orElseThrow();
                    var result =
                            Map.of(
                                    "samples",
                                    samples.size(),
                                    "meanLevelTickMs",
                                    mean,
                                    "p95LevelTickMs",
                                    sorted.get((int) (sorted.size() * .95)),
                                    "players",
                                    8,
                                    "racks",
                                    49,
                                    "spentRods",
                                    196,
                                    "waterMb",
                                    water.getFluidInTank(0).getAmount(),
                                    "mode",
                                    "survival fake players, immune to injury");
                    try {
                        var path =
                                Path.of(
                                        System.getProperty(
                                                "createplant.radiationBenchmarkOutput",
                                                "radiation-benchmark.json"));
                        Files.createDirectories(path.toAbsolutePath().getParent());
                        Files.writeString(
                                path,
                                new GsonBuilder().setPrettyPrinting().create().toJson(result));
                    } catch (java.io.IOException error) {
                        throw new RuntimeException(error);
                    }
                    for (var player : players)
                        h.getLevel()
                                .removePlayerImmediately(
                                        player,
                                        net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                    h.succeed();
                });
    }
}
