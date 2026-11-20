package net.createplant.gametest;

import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe;

import net.createplant.CreatePlant;
import net.createplant.block.*;
import net.createplant.block.entity.SteamTurbineBlockEntity;
import net.createplant.registry.*;
import net.createplant.thermal.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;

import java.util.*;

@ExplicitGameTest
@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TurbineUnitGameTests {
    private static TurbineUnit.Layout section(
            GameTestHelper h, TurbineUnit.Stage stage, Direction.Axis axis, BlockPos relative) {
        var unit = new TurbineUnit.Layout(h.absolutePos(relative), axis, stage);
        for (var pos : unit.volume()) h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        h.assertTrue(
                TurbineUnit.place(h.getLevel(), unit, null, ItemStack.EMPTY),
                "A partial or complete section can be placed");
        h.assertTrue(TurbineUnit.intact(h.getLevel(), unit), "Section owns its full cross-section");
        return unit;
    }

    private static SteamTurbineBlockEntity rotor(GameTestHelper h, TurbineUnit.Layout unit) {
        return (SteamTurbineBlockEntity) h.getLevel().getBlockEntity(unit.origin());
    }

    private static List<TurbineUnit.Layout> stage(
            GameTestHelper h, TurbineUnit.Stage stage, Direction.Axis axis, BlockPos start) {
        var result = new ArrayList<TurbineUnit.Layout>();
        for (int i = 0; i < stage.sections(); i++)
            result.add(
                    section(
                            h,
                            stage,
                            axis,
                            start.relative(
                                    Direction.fromAxisAndDirection(
                                            axis, Direction.AxisDirection.POSITIVE),
                                    i)));
        return result;
    }

    @GameTest(template = "test_fractionation")
    public static void exactLengthsAndBothAxes(GameTestHelper h) {
        for (var kind : TurbineUnit.Stage.values())
            for (var axis : new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Z}) {
                var units = new ArrayList<TurbineUnit.Layout>();
                var start = new BlockPos(8, 6, 8);
                for (int i = 0; i < kind.sections(); i++) {
                    var current =
                            section(
                                    h,
                                    kind,
                                    axis,
                                    start.relative(
                                            Direction.fromAxisAndDirection(
                                                    axis, Direction.AxisDirection.POSITIVE),
                                            i));
                    units.add(current);
                    var first = rotor(h, units.getFirst());
                    h.assertTrue(
                            current.volume().stream()
                                    .allMatch(
                                            p ->
                                                    axis == Direction.Axis.X
                                                            ? p.getX() == current.origin().getX()
                                                            : p.getZ() == current.origin().getZ()),
                            "Each item places one axial voxel");
                    h.assertTrue(
                            first.unitAssembly().size()
                                    == (i + 1 == kind.sections() ? kind.sections() : 0),
                            "Only exact HP 5 and LP 10 are formed");
                    if (i + 1 < kind.sections())
                        h.assertTrue(
                                first.getGeneratedSpeed() == 0
                                        && first.getSteamCapability()
                                                        .fill(
                                                                FluidThermal.at(
                                                                        new FluidStack(
                                                                                CEFluids.STEAM
                                                                                        .get(),
                                                                                1),
                                                                        700),
                                                                SIMULATE)
                                                == 0,
                                "Incomplete stage accepts no steam and produces no power");
                }
                var extra =
                        new TurbineUnit.Layout(
                                units.getLast().origin().relative(units.getLast().along()),
                                axis,
                                kind);
                h.assertTrue(
                        !TurbineUnit.place(h.getLevel(), extra, null, ItemStack.EMPTY),
                        "An extra section is rejected without changing the completed stage");
                for (var unit : units) {
                    var be = rotor(h, unit);
                    var tag = be.saveWithoutMetadata(h.getLevel().registryAccess());
                    be.loadWithComponents(tag, h.getLevel().registryAccess());
                    h.assertTrue(
                            unit.equals(be.unitLayout()), "Stage and ownership survive save/load");
                }
                h.assertTrue(
                        rotor(h, units.getFirst()).unitAssembly().size() == kind.sections(),
                        "Rejected extension and reload preserve the stage");
                for (var unit : units) TurbineUnit.remove(h.getLevel(), unit.origin(), false);
            }
        for (var kind : TurbineUnit.Stage.values()) {
            var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(kind.item());
            var recipe = h.getLevel().getRecipeManager().byKey(id).orElseThrow().value();
            int craftingSize = kind == TurbineUnit.Stage.HIGH ? 5 : 7;
            h.assertTrue(
                    recipe instanceof MechanicalCraftingRecipe r
                            && r.getWidth() == craftingSize
                            && r.getHeight() == craftingSize,
                    "The turbine recipe uses the selected stage's mechanical crafting size");
        }
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void lowPressureSectionsExtendFromCasingFacesWithoutChangingAxis(GameTestHelper h) {
        for (var axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z})
            for (var sign : Direction.AxisDirection.values()) {
                var direction = Direction.fromAxisAndDirection(axis, sign);
                var side = axis == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
                var first = section(h, TurbineUnit.Stage.LOW, axis, new BlockPos(15, 9, 15));
                var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
                var stack = new ItemStack(CEItems.LOW_PRESSURE_TURBINE_UNIT.get(), 16);
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
                player.setYRot(axis == Direction.Axis.X ? 0 : 90);
                var current = first;
                var placed = new ArrayList<TurbineUnit.Layout>();placed.add(first);
                for (int i = 1; i < TurbineUnit.Stage.LOW.sections(); i++) {
                    var clicked = current.cell(0, 4, -2);
                    var eye = current.origin().relative(direction, 3).relative(side, 6).getCenter();
                    player.setPos(eye.x, eye.y - 2, eye.z);
                    var hit = new net.minecraft.world.phys.BlockHitResult(clicked.getCenter(), side, clicked, false);
                    var context = new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
                    var result = stack.getItem().useOn(context);
                    h.assertTrue(result.consumesAction(), "Clicking the LP casing extends the existing shaft rather than overlapping a new 9x9 section");
                    current = new TurbineUnit.Layout(current.origin().relative(direction), axis, TurbineUnit.Stage.LOW);
                    h.assertTrue(TurbineUnit.intact(h.getLevel(), current), "Extension preserves the center height and shaft axis");
                    placed.add(current);
                }
                h.assertTrue(rotor(h, first).unitAssembly().size() == 10 && stack.getCount() == 7, "Real item interactions form ten LP sections and consume exactly nine items");
                var clicked = current.cell(0, 4, -2);
                var hit = new net.minecraft.world.phys.BlockHitResult(clicked.getCenter(), side, clicked, false);
                var context = new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
                h.assertTrue(!stack.getItem().useOn(context).consumesAction() && stack.getCount() == 7, "Extending a complete LP stage is rejected without consuming an item");
                for (var unit : placed) TurbineUnit.remove(h.getLevel(), unit.origin(), false);
            }
        h.succeed();
    }

    @GameTest(template = "test_fractionation", timeoutTicks = 120)
    public static void completeStagesStayFormedWhileTheWorldRuns(GameTestHelper h) {
        var high = stage(h, TurbineUnit.Stage.HIGH, Direction.Axis.X, new BlockPos(2, 3, 4));
        var low = stage(h, TurbineUnit.Stage.LOW, Direction.Axis.X, new BlockPos(2, 9, 16));
        h.setBlock(low.get(3).cell(0, 0, -5), Blocks.STONE);
        h.runAfterDelay(80, () -> {
            for (var units : List.of(high, low)) {
                var broken = units.stream().filter(unit -> !TurbineUnit.intact(h.getLevel(), unit)).findFirst();
                h.assertTrue(broken.isEmpty(), "A placed section keeps its blocks: " + broken.map(unit -> TurbineUnit.states(unit).entrySet().stream()
                    .filter(entry -> !h.getLevel().getBlockState(entry.getKey()).equals(entry.getValue()))
                    .map(entry -> entry.getKey().subtract(unit.origin()) + " " + h.getLevel().getBlockState(entry.getKey()) + " != " + entry.getValue()).findFirst().orElse("layout")).orElse(""));
                h.assertTrue(rotor(h, units.getFirst()).unitAssembly().size() == units.getFirst().stage().sections(), "The whole stage stays formed: " + units.getFirst().stage());
                for (var unit : units) for (var pos : unit.volume()) {
                    if (!(h.getLevel().getBlockEntity(pos) instanceof net.createplant.block.entity.HighPressureTurbinePartBlockEntity part)) continue;
                    var lookup = h.getLevel().registryAccess();
                    var client = new net.createplant.block.entity.HighPressureTurbinePartBlockEntity(pos, part.getBlockState());
                    client.handleUpdateTag(part.getUpdateTag(lookup), lookup);
                    h.assertTrue(unit.equals(client.unitLayout()) && part.getUpdatePacket() != null,
                        "A client receives each casing's section, so it sees the stage formed: " + unit.stage());
                }
            }
            h.succeed();
        });
    }

    @GameTest(template = "test_fractionation")
    public static void sectionLoadingPreservesAndRepairsOwnedCasingOffsets(GameTestHelper h) {
        for (var kind : TurbineUnit.Stage.values())
            for (var axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}) {
                var unit = section(h, kind, axis, new BlockPos(15, 9, 15));
                var at = unit.cell(0, kind.radius(), 0);
                var part = (net.createplant.block.entity.HighPressureTurbinePartBlockEntity) h.getLevel().getBlockEntity(at);
                var expected = h.getLevel().getBlockState(at);
                part.setUnitLayout(null);part.onLoad();
                h.assertTrue(h.getLevel().getBlockState(at).equals(expected), "Loading before ownership assignment must not run the legacy casing attachment algorithm");
                part.setUnitLayout(unit);
                var saved = part.saveWithoutMetadata(h.getLevel().registryAccess());
                h.getLevel().setBlock(at, expected.setValue(HighPressureTurbinePartBlock.ATTACHED, false)
                        .setValue(HighPressureTurbinePartBlock.RING_X, HighPressureTurbineEndBlock.ringStateValue(0)), 2);
                part.loadWithComponents(saved, h.getLevel().registryAccess());part.onLoad();
                h.assertTrue(h.getLevel().getBlockState(at).equals(expected) && TurbineUnit.intact(h.getLevel(), unit),
                        "Reload repairs previously corrupted offsets from persisted section ownership");
                TurbineUnit.remove(h.getLevel(), unit.origin(), false);
            }
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void splitPreservesNeighborsAndStopsPower(GameTestHelper h) {
        var units = stage(h, TurbineUnit.Stage.HIGH, Direction.Axis.X, new BlockPos(5, 6, 10));
        var port = rotor(h, units.getFirst()).getSteamCapability();
        var feed = FluidThermal.at(new FluidStack(net.createplant.registry.CEProcessFluids.HIGH_PRESSURE_STEAM.get(), 80000), 700);
        h.assertTrue(
                port == rotor(h, units.getLast()).getSteamCapability()
                        && port.getTankCapacity(0) == 80000,
                "Five HP sections share a finite 80000 mB inlet");
        h.assertTrue(
                port.fill(feed, SIMULATE) == 80000
                        && rotor(h, units.getFirst()).thermodynamics().input().isEmpty(),
                "Simulation changes no mass");
        h.assertTrue(port.fill(feed, EXECUTE) == 80000, "All five sections fill");
        double before =
                units.stream()
                        .mapToDouble(
                                u ->
                                        FluidThermal.energy(
                                                rotor(h, u).thermodynamics().input().getFluid()))
                        .sum();
        for (var unit : units) {
            var be = rotor(h, unit);
            be.loadWithComponents(
                    be.saveWithoutMetadata(h.getLevel().registryAccess()),
                    h.getLevel().registryAccess());
        }
        h.assertTrue(
                Math.abs(
                                before
                                        - units.stream()
                                                .mapToDouble(
                                                        u ->
                                                                FluidThermal.energy(
                                                                        rotor(h, u)
                                                                                .thermodynamics()
                                                                                .input()
                                                                                .getFluid()))
                                                .sum())
                        < .01,
                "Saving creates no energy");
        var middle = units.get(2);
        h.assertTrue(
                TurbineUnit.remove(h.getLevel(), middle.origin(), true),
                "Individual recovery works");
        h.assertTrue(
                rotor(h, units.getFirst()).getGeneratedSpeed() == 0
                        && rotor(h, units.getLast()).getGeneratedSpeed() == 0,
                "Both shortened pieces stop");
        h.assertTrue(
                !TurbineUnit.connected(h.getLevel(), units.get(1), Direction.EAST)
                        && !TurbineUnit.connected(h.getLevel(), units.get(3), Direction.WEST),
                "Exposed ends close");
        int remaining =
                units.stream()
                        .filter(u -> !u.equals(middle))
                        .mapToInt(u -> rotor(h, u).thermodynamics().input().getFluidAmount())
                        .sum();
        h.assertTrue(remaining == 64000, "Only the removed section vents its 16000 mB");
        h.succeed();
    }

    @GameTest(template = "test_fractionation", timeoutTicks = 220)
    public static void fiveHpAndTenLpExpandSteamAndReturnWater(GameTestHelper h) {
        var high = stage(h, TurbineUnit.Stage.HIGH, Direction.Axis.X, new BlockPos(17, 6, 10));
        var low = stage(h, TurbineUnit.Stage.LOW, Direction.Axis.X, new BlockPos(3, 6, 10));
        for (int x = 13; x <= 16; x++)
            h.getLevel()
                    .setBlock(
                            h.absolutePos(new BlockPos(x, 6, 10)),
                            AllBlocks.SHAFT
                                    .getDefaultState()
                                    .setValue(BlockStateProperties.AXIS, Direction.Axis.X),
                            3);
        var pipe = CETransport.STAINLESS_PIPE.get().defaultBlockState();
        for (int z = 12; z <= 15; z++)
            h.getLevel().setBlock(h.absolutePos(new BlockPos(19, 6, z)), pipe, 3);
        for (int x = 8; x < 19; x++)
            h.getLevel().setBlock(h.absolutePos(new BlockPos(x, 6, 15)), pipe, 3);
        var pump=h.absolutePos(new BlockPos(14,6,15));
        h.getLevel().setBlock(pump,AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(BlockStateProperties.FACING,Direction.WEST),3);
        h.getLevel().setBlock(pump.above(),AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS,Direction.Axis.X),3);
        h.getLevel().setBlock(pump.above().east(),AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,Direction.WEST),3);
        ((com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity)h.getLevel().getBlockEntity(pump.above().east())).generatedSpeed.setValue(128);
        h.assertTrue(
                rotor(h, high.getFirst())
                                .getSteamCapability()
                                .fill(
                                        FluidThermal.at(
                                                new FluidStack(net.createplant.registry.CEProcessFluids.HIGH_PRESSURE_STEAM.get(), 40000), 700),
                                        EXECUTE)
                        == 40000,
                "The completed HP takes steam");
        h.runAfterDelay(
                140,
                () -> {
                    double
                            hp =
                                    high.stream()
                                            .mapToDouble(
                                                    u -> rotor(h, u).thermodynamics().totalWork())
                                            .sum(),
                            lp =
                                    low.stream()
                                            .mapToDouble(
                                                    u -> rotor(h, u).thermodynamics().totalWork())
                                            .sum();
                    var shaft =
                            (com.simibubi.create.content.kinetics.base.KineticBlockEntity)
                                    h.getLevel()
                                            .getBlockEntity(h.absolutePos(new BlockPos(15, 6, 10)));
                    h.assertTrue(
                            hp > 0 && lp > 0 && Math.abs(shaft.getSpeed()) == 256,
                            "Both completed stages perform work through real pipes and an ordinary"
                                + " shaft");
                    var port =
                            h.getLevel()
                                    .getCapability(
                                            net.neoforged.neoforge.capabilities.Capabilities
                                                    .FluidHandler.BLOCK,
                                            low.getFirst().cell(0, 0, -4),
                                            Direction.DOWN);
                    var water = port == null ? FluidStack.EMPTY : port.drain(40000, EXECUTE);
                    h.assertTrue(
                            !water.isEmpty()
                                    && water.getFluid()
                                            == net.minecraft.world.level.material.Fluids.WATER,
                            "LP yields ordinary water from its bottom port");
                    int remaining =
                            java.util.stream.Stream.concat(high.stream(), low.stream())
                                    .mapToInt(
                                            u ->
                                                    rotor(h, u)
                                                                    .thermodynamics()
                                                                    .input()
                                                                    .getFluidAmount()
                                                            + rotor(h, u)
                                                                    .thermodynamics()
                                                                    .exhaust()
                                                                    .getFluidAmount())
                                    .sum();
                    h.assertTrue(
                            remaining + water.getAmount() == 40000,
                            "Water-equivalent mass is conserved");
                    try {
                        java.nio.file.Files.writeString(
                                java.nio.file.Path.of("turbine-fixed-length-operation.json"),
                                new com.google.gson.GsonBuilder()
                                        .setPrettyPrinting()
                                        .create()
                                        .toJson(
                                                Map.of(
                                                        "hp_joules",
                                                        hp,
                                                        "lp_joules",
                                                        lp,
                                                        "shaft_rpm",
                                                        shaft.getSpeed(),
                                                        "condensate_mb",
                                                        water.getAmount(),
                                                        "remaining_mb",
                                                        remaining,
                                                        "lp_size",
                                                        "9x9x10",
                                                        "hp_size",
                                                        "3x3x5")));
                    } catch (java.io.IOException e) {
                        throw new RuntimeException(e);
                    }
                    h.succeed();
                });
    }
}
