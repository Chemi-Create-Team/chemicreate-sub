package net.createplant.gametest;

import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

import com.google.gson.GsonBuilder;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;

import net.createplant.CreatePlant;
import net.createplant.block.entity.*;
import net.createplant.chemistry.ReactorModule;
import net.createplant.registry.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.material.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.*;

import java.nio.file.*;
import java.util.*;

/** Four colocated production lines advanced by the actual server world ticks. */
@ExplicitGameTest
@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public class ConcurrentChemistryGameTests {
    private record Link(
            java.util.function.Supplier<IFluidHandler> from,
            java.util.function.Supplier<IFluidHandler> to,
            Fluid fluid) {}

    @GameTest(template = "test_fusion", timeoutTicks = 6200)
    public static void fourProductionLinesRunTogether(GameTestHelper h) {
        var level = h.getLevel();
        level.setDayTime(6000);
        level.setWeatherParameters(20000, 0, false, false);
        var machines = new ArrayList<ReactorMachineBlockEntity>();
        var links = new ArrayList<Link>();
        var products = new LinkedHashMap<String, Long>();
        java.util.function.Function<String, Fluid> f =
                id -> BuiltInRegistries.FLUID.get(CreatePlant.location(id));
        class Plant {
            ReactorMachineBlockEntity machine(int x, int z, ReactorModule module, int height) {
                var be =
                                ReactorGameTests.assemble(
                                        level,
                                        h.absolutePos(new BlockPos(x, 4, z)),
                                        module,
                                        height);
                h.assertTrue(be.formed(), "Structure forms " + module + " " + be.status());
                machines.add(be);
                return be;
            }

            ReactionTowerBlockEntity tower(int x, int z, ItemStack catalyst) {
                return ReactionTowerGameTests.tower(
                        level, h.absolutePos(new BlockPos(x, 4, z)), 3, catalyst);
            }

            void fill(ReactionTowerBlockEntity be, Fluid fluid, int amount) {
                h.assertTrue(
                        ReactionTowerGameTests.port(be).fill(new FluidStack(fluid, amount), EXECUTE)
                                == amount,
                        "Complete finite feed enters " + fluid);
            }

            void fill(ReactorMachineBlockEntity be, Fluid fluid, int amount) {
                h.assertTrue(
                        be.port(Direction.NORTH).fill(new FluidStack(fluid, amount), EXECUTE)
                                == amount,
                        "Complete finite feed enters " + fluid);
            }

            /** The solid feed and products at the west side of a tower's bottom layer. */
            net.neoforged.neoforge.items.IItemHandler solids(ReactionTowerBlockEntity be) {
                return level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, be.getBlockPos().west(), Direction.WEST);
            }

            void item(ReactorMachineBlockEntity be, ItemStack item) {
                h.assertTrue(
                        be.itemPort().insertItem(0, item, false).isEmpty(),
                        "Complete solid feed enters");
            }

            void link(ReactorMachineBlockEntity a, ReactorMachineBlockEntity b, Fluid fluid) {
                links.add(
                        new Link(
                                () -> a.port(Direction.NORTH),
                                () -> b.port(Direction.NORTH),
                                fluid));
            }

            void collect(ReactorMachineBlockEntity be, Fluid fluid) {
                var out =
                        be.port(Direction.NORTH)
                                .drain(new FluidStack(fluid, Integer.MAX_VALUE), EXECUTE);
                products.merge(
                        BuiltInRegistries.FLUID.getKey(fluid).getPath(),
                        (long) out.getAmount(),
                        Long::sum);
            }
        }
        var p = new Plant();
        var n0 = p.tower(2, 2, CEItems.OXIDATION_CATALYST.toStack());
        var n1 = p.tower(18, 2, ItemStack.EMPTY);
        var n2 = p.machine(26, 2, ReactorModule.TOWER, 4);
        var column =
                DistillationGameTests.build(level, h.absolutePos(new BlockPos(12, 4, 2)), 2, 14);
        var fan = h.absolutePos(new BlockPos(7, 7, 2));
        for (int x = -1; x <= 1; x++)
            for (int z = -1; z <= 1; z++)
                for (int y = 0; y < 4; y++)
                    level.setBlock(
                            fan.offset(x, y - 3, z),
                            y < 3
                                    ? CEProcessBlocks.COOLER_RADIATOR.get().defaultBlockState()
                                    : x == 0 && z == 0
                                            ? CEProcessBlocks.COOLING_FAN.get().defaultBlockState()
                                            : CEProcessBlocks.COOLING_FAN_SHROUD
                                                    .get()
                                                    .defaultBlockState(),
                            3);
        var radiator = (RadiatorBlockEntity) level.getBlockEntity(fan.below(3));
        radiator.lazyTick();

        links.add(
                new Link(
                        () -> ReactionTowerGameTests.port(n0),
                        () ->
                                ((RadiatorBlockEntity) level.getBlockEntity(fan.below(2).north()))
                                        .port(Direction.NORTH),
                        f.apply("cold_nitric_oxide_mixture")));
        links.add(
                new Link(
                        () ->
                                ((RadiatorBlockEntity) level.getBlockEntity(fan.below(2).south()))
                                        .port(Direction.SOUTH),
                        () -> DistillationGameTests.inlet(column, 2),
                        f.apply("cold_nitric_oxide_mixture")));
        links.add(
                new Link(
                        () -> DistillationGameTests.outlet(column, 13),
                        () -> ReactionTowerGameTests.port(n1),
                        f.apply("nitric_oxide")));
        links.add(new Link(() -> ReactionTowerGameTests.port(n1), () -> n2.port(Direction.NORTH), f.apply("nitrogen_dioxide")));
        var s0 = p.machine(2, 12, ReactorModule.STIRRED, 1);
        var s1 = p.tower(10, 12, CEItems.OXIDATION_CATALYST.toStack());
        var s2 = p.machine(18, 12, ReactorModule.TOWER, 4);
        links.add(new Link(() -> s0.port(Direction.NORTH), () -> ReactionTowerGameTests.port(s1), f.apply("sulfur_dioxide")));
        links.add(new Link(() -> ReactionTowerGameTests.port(s1), () -> s2.port(Direction.NORTH), f.apply("sulfur_trioxide")));
        var c0 = p.machine(2, 22, ReactorModule.STIRRED, 1);
        var c1 = p.machine(10, 22, ReactorModule.TOWER, 4);
        p.link(c0, c1, f.apply("hydrogen_chloride"));
        var u0 = p.tower(2, 32, ItemStack.EMPTY);
        var u1 = p.machine(10, 32, ReactorModule.STIRRED, 1);
        var u2 = p.tower(18, 32, ItemStack.EMPTY);
        var u3 = GasCentrifugeGameTests.build(h, new BlockPos(26, 4, 32), 4);
        var u4 = p.machine(34, 32, ReactorModule.STIRRED, 1);
        links.add(new Link(() -> ReactionTowerGameTests.port(u2), () -> u3.port(0), f.apply("uranium_hexafluoride")));
        // The towers' trays reduce and gasify over kindled and blue-flame burners.
        for (var tower : List.of(u0, u2)) {
            level.setBlock(tower.getBlockPos().below(), AllBlocks.BLAZE_BURNER.getDefaultState().setValue(BlazeBurnerBlock.HEAT_LEVEL,
                tower == u0 ? BlazeBurnerBlock.HeatLevel.KINDLED : BlazeBurnerBlock.HeatLevel.SEETHING), 3);
            if (level.getBlockEntity(tower.getBlockPos().below()) instanceof com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity burner) burner.isCreative = true;
        }
        links.add(
                new Link(
                        () -> u3.port(1),
                        () -> u4.port(Direction.NORTH),
                        f.apply("enriched_uranium_hexafluoride")));
        var heated =
                Map.of(
                        s0,
                        BlazeBurnerBlock.HeatLevel.KINDLED,
                        u1,
                        BlazeBurnerBlock.HeatLevel.KINDLED);
        long[] overlap = {0}, powder = {0};
        double[] maxPressure = {0}, maxTemperature = {0};
        int[] campaigns = {0};
        h.onEachTick(
                () -> {
                    long tick = h.getTick();
                    if (tick < 1 || tick > 6000) return;
                    for (var be : machines) {
                        h.assertTrue(
                                !be.isRemoved() && be.formed(),
                                "Structure survives at "
                                        + tick
                                        + ": "
                                        + be.getBlockPos()
                                        + " "
                                        + be.status());
                        if (be.module().stress > 0) ReactorGameTests.speed(be, 32);
                        maxPressure[0] = Math.max(maxPressure[0], (be.displayedFluid(0).isEmpty() ? 0 : net.createplant.transport.FluidHazards.of(be.displayedFluid(0)).pressure().ordinal()));
                        maxTemperature[0] =
                                Math.max(maxTemperature[0], be.currentBurnerHeat().ordinal());
                    }
                    GasCentrifugeGameTests.drive(u3, 32);
                    ((CoolingFanBlockEntity) level.getBlockEntity(fan)).setSpeed(128);
                    if (tick == 20 || tick == 1520 || tick == 3020 || tick == 4520) {
                        campaigns[0]++;
                        p.fill(n0, f.apply("ammonia_gas"), 300);
                        p.fill(n0, f.apply("oxygen_gas"), 375);
                        p.fill(n1, f.apply("oxygen_gas"), 150);
                        p.fill(n2, net.minecraft.world.level.material.Fluids.WATER, 100);
                        p.item(
                                s0,
                                BuiltInRegistries.ITEM
                                        .get(CreatePlant.location("sulfur_dust"))
                                        .getDefaultInstance());
                        p.fill(s0, f.apply("oxygen_gas"), 500);
                        p.fill(s1, f.apply("oxygen_gas"), 250);
                        p.fill(s2, net.minecraft.world.level.material.Fluids.WATER, 500);
                        p.fill(c0, f.apply("hydrogen_gas"), 250);
                        p.fill(c0, f.apply("chlorine"), 250);
                        p.fill(c1, net.minecraft.world.level.material.Fluids.WATER, 500);
                        var roast =
                                AllFanProcessingTypes.BLASTING.process(
                                        CEUranium.URANINITE.toStack(8), level);
                        h.assertTrue(p.solids(u0).insertItem(0, roast.getFirst(), false).isEmpty(), "Complete solid feed lies on the trays");
                        p.fill(u0, f.apply("hydrogen_gas"), 2000);
                        p.fill(u1, f.apply("hydrogen_fluoride"), 4000);
                        p.fill(u2, f.apply("fluorine"), 2000);
                        p.fill(u4, Fluids.WATER, 250);
                        p.fill(u4, f.apply("hydrogen_gas"), 250);
                    }
                    for (var entry : heated.entrySet()) {
                        var be = entry.getKey();
                        boolean feed = false;
                        for (int i = 0; i < 9; i++)
                            if (!be.itemPort().getStackInSlot(i).isEmpty()) feed = true;
                        level.setBlock(
                                be.getBlockPos().below(),
                                AllBlocks.BLAZE_BURNER
                                        .getDefaultState()
                                        .setValue(
                                                BlazeBurnerBlock.HEAT_LEVEL,
                                                feed
                                                        ? entry.getValue()
                                                        : BlazeBurnerBlock.HeatLevel.NONE),
                                3);
                        if (level.getBlockEntity(be.getBlockPos().below())
                                instanceof
                                com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity
                                                burner) burner.isCreative = true;
                    }
                    for (var link : links) {
                        if (link.from.get() == null || link.to.get() == null) continue;
                        var offered =
                                link.from.get().drain(new FluidStack(link.fluid, 1000), SIMULATE);
                        int accepted = link.to.get().fill(offered, SIMULATE);
                        if (accepted > 0) {
                            var drained =
                                    link.from
                                            .get()
                                            .drain(offered.copyWithAmount(accepted), EXECUTE);
                            h.assertTrue(
                                    link.to.get().fill(drained, EXECUTE) == accepted,
                                    "Transfer preserves all material");
                        }
                    }
                    var reduced = p.solids(u0);
                    for (int slot = u0.layers(); slot < reduced.getSlots(); slot++) {
                        var stack = reduced.extractItem(slot, 64, true);
                        if (!stack.isEmpty() && u1.itemPort().insertItem(0, stack, true).isEmpty())
                            u1.itemPort().insertItem(0, reduced.extractItem(slot, stack.getCount(), false), false);
                    }
                    var salt = u1.itemPort().extractItem(9, 64, true);
                    if (!salt.isEmpty() && net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(p.solids(u2), salt, true).isEmpty())
                        net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(p.solids(u2), u1.itemPort().extractItem(9, salt.getCount(), false), false);
                    p.collect(n2, CEFluids.NITRIC_ACID.get());
                    p.collect(n2, f.apply("nitric_oxide"));
                    p.collect(s2, f.apply("sulfuric_acid"));
                    p.collect(c1, f.apply("hydrochloric_acid"));
                    products.merge("depleted_uranium_hexafluoride", (long) u3.port(2).drain(Integer.MAX_VALUE, EXECUTE).getAmount(), Long::sum);
                    p.collect(u4, f.apply("hydrogen_fluoride"));
                    powder[0] += u4.itemPort().extractItem(9, 64, false).getCount();
                    if (n0.status().equals("running")
                            && s0.status().equals("running")
                            && c0.status().equals("running")
                            && u0.status().equals("running")) overlap[0]++;
                    if (tick % 500 == 0)
                        org.slf4j.LoggerFactory.getLogger(ConcurrentChemistryGameTests.class)
                                .info(
                                        "CONCURRENT CHEMISTRY tick={} products={} powder={}"
                                                + " states={}",
                                        tick,
                                        products,
                                        powder[0],
                                        machines.stream()
                                                .map(b -> b.module() + ":" + b.status())
                                                .toList());
                });
        h.runAfterDelay(
                6001,
                () -> {
                    h.assertTrue(
                            campaigns[0] == 4 && overlap[0] > 0,
                            "All four lines actually overlap in the same world ticks");
                    h.assertTrue(
                            products.getOrDefault("nitric_acid", 0L) == 800
                                    && products.getOrDefault("sulfuric_acid", 0L) == 2000
                                    && products.getOrDefault("hydrochloric_acid", 0L) == 4000
                                    && powder[0] == 4
                                    && products.getOrDefault("nitric_oxide", 0L) == 400
                                    && products.getOrDefault("depleted_uranium_hexafluoride", 0L)
                                            == 7000
                                    && products.getOrDefault("hydrogen_fluoride", 0L) == 3000,
                            "Complete four-campaign outputs: " + products + " powder=" + powder[0]);
                    var result =
                            Map.of(
                                    "worldTicks",
                                    6000,
                                    "campaigns",
                                    4,
                                    "overlappingRunningTicks",
                                    overlap[0],
                                    "products_mB",
                                    products,
                                    "enrichedDioxideItems",
                                    powder[0],
                                    "peakPressure_bar",
                                    maxPressure[0],
                                    "peakTemperature_C",
                                    maxTemperature[0]);
                    try {
                        Files.writeString(
                                Path.of(
                                        System.getProperty(
                                                "createplant.concurrentChemistryReport",
                                                "concurrent-chemistry.json")),
                                new GsonBuilder().setPrettyPrinting().create().toJson(result));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    h.succeed();
                });
    }
}
