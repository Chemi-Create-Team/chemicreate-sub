package net.createplant.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.rotor.AlternatorBrushesBlock;
import com.george_vi.electroenergetics.content.rotor.StatorBlock;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.steamEngine.SteamEngineBlock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.createplant.block.ElectrodeHeadBlock;
import net.createplant.block.ElectrolysisCellBlock;
import net.createplant.block.ElectrolysisControllerBlock;
import net.createplant.block.entity.ElectrolysisControllerBlockEntity;
import net.createplant.registry.CEElectrolysis;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CETransport;
import net.createplant.registry.CEItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import static net.createplant.gametest.SurvivalWorkshop.*;

final class SurvivalWaterElectrolysis {
    private final ServerLevel level;
    private final SurvivalMetalsWorkshop works;
    private final SurvivalCrafting crafting;
    private final BlockPos plates, leach, cell, brush, panel, rotor, oxygenPump, boiler, boilerPump;
    private final BlockPos copperSheets, copperIngots, glass, materials;
    private final SurvivalMetalsWorkshop.OutputGate pressGate;
    private final Map<String, Object> measurements = new LinkedHashMap<>();
    private long built;
    private boolean started;
    private double maxVolts;

    SurvivalWaterElectrolysis(ServerLevel level, BlockPos origin, SurvivalMetalsWorkshop works,
                              SurvivalCrafting crafting, BlockPos plates, BlockPos leach) {
        this.level = level;
        this.works = works;
        this.crafting = crafting;
        this.plates = plates;
        this.leach = leach;
        cell = leach.east(10).south(5);
        oxygenPump = cell.west(3).south(2);
        panel = cell.north().above();
        rotor = cell.offset(13, 4, -3);
        brush = rotor.east();
        boiler = rotor.west(2).below(4);
        boilerPump = boiler.west(4);
        var depot = origin.offset(88, 9, 60);
        works.press(depot);
        pressGate = works.pressExit(depot, depot.south());
        copperSheets = depot.below(2);
        var furnace = depot.south().above(6);
        works.furnace(furnace);
        works.hopperPath(furnace.below(), depot.south().above());
        works.hopper(depot.south(), Direction.NORTH);
        works.raw(furnace.above(), Items.RAW_COPPER, 10);
        works.raw(furnace.east(), Items.COAL, 2);
        copperIngots = smelt(origin.offset(83, 12, 76), Items.RAW_COPPER, 4);
        glass = smelt(origin.offset(88, 12, 76), Items.SAND, 2);
        materials = origin.offset(90, 8, 68);
        place(level, materials, Blocks.CHEST.defaultBlockState());
        works.hopper(materials.above(), Direction.DOWN);
        works.raw(materials.above(), Items.GRAY_WOOL, 6);
        works.raw(materials.above(), Items.OAK_LOG, 2);
    }

    private BlockPos smelt(BlockPos furnace, Item feed, int count) {
        works.furnace(furnace);
        works.hopper(furnace.below(), Direction.DOWN);
        var chest = furnace.below(2);
        place(level, chest, Blocks.CHEST.defaultBlockState());
        works.raw(furnace.above(), feed, count);
        works.raw(furnace.east(), Items.COAL, 1);
        return chest;
    }

    void pressTick() { works.release(pressGate, AllItems.COPPER_SHEET.get()); }

    boolean ready() {
        return count(copperSheets, AllItems.COPPER_SHEET.get()) >= 8 && count(copperIngots, Items.COPPER_INGOT) >= 4
            && count(glass, Items.GLASS) >= 2 && count(materials, Items.GRAY_WOOL) >= 6 && count(materials, Items.OAK_LOG) >= 2;
    }

    private int count(BlockPos chest, Item item) { return total(items(level, chest, Direction.UP), item); }

    Map<String, Object> measurements() {
        if (built == 0) return Map.of();
        return Map.copyOf(measurements);
    }

    Item heldMechanicalPump() { return AllBlocks.MECHANICAL_PUMP.asItem(); }

    void build(SurvivalWetWorkshop wet) {
        var copper = AllItems.COPPER_SHEET.get();
        var steel = CEItems.STAINLESS_STEEL_SHEET.get();
        var iron = AllItems.IRON_SHEET.get();
        var shaft = AllBlocks.SHAFT.asItem();
        var alloy = AllItems.ANDESITE_ALLOY.get();
        var basin = AllBlocks.BASIN.asItem();
        crafting.collect(plates, steel, 4);
        crafting.collect(copperSheets, copper, 8);
        crafting.collect(copperIngots, Items.COPPER_INGOT, 4);
        crafting.collect(glass, Items.GLASS, 2);
        crafting.collect(materials, Items.GRAY_WOOL, 6);
        crafting.collect(materials, Items.OAK_LOG, 2);
        crafting.craft(basin, 1, 1, alloy, null, alloy, alloy, alloy, alloy);
        crafting.craft(CEElectrolysis.CELL.asItem(), 4, 1, steel, Items.GLASS, steel, copper, basin, copper, steel, Items.GLASS, steel);
        // The casing and the two control units are ordinary early-Create parts; their placed recipes are listed in the playtest report.
        putCrafted(CEElectrolysis.CELL.asItem(), cell);
        place(level, cell, level.getBlockState(cell).setValue(ElectrolysisCellBlock.AXIS, Direction.Axis.Z));
        for (int i = 0; i < 2; i++) place(level, panel.below(i), CEElectrolysis.CONTROLLER.get().defaultBlockState()
            .setValue(ElectrolysisControllerBlock.FACING, Direction.NORTH));
        place(level, cell.above(), CEElectrolysis.HEAD.get().defaultBlockState().setValue(ElectrodeHeadBlock.AXIS, Direction.Axis.Z));
        var wool = Items.GRAY_WOOL;
        crafting.craft(CEElectrolysis.ELECTRODE_PLATE.get(), 4, 1, wool, copper, wool, wool, iron, wool, wool, copper, wool);
        for (int i = 0; i < 2; i++) crafting.useHeld(CEElectrolysis.ELECTRODE_PLATE.get(), cell.above(), Direction.UP);
        buildSpoolsAndPump(copper, shaft);
        buildAlternator(wet);
        var suction = cell.south(6).below(2);
        var waterPump = cell.south(3).below(2);
        wet.pipeLine(AllBlocks.FLUID_PIPE.get(), null, AllBlocks.MECHANICAL_PUMP.get(), waterPump, Direction.NORTH, suction, cell.below(2), cell);
        wet.drive(waterPump, Direction.SOUTH, Direction.EAST);
        wet.waterSource(suction, Direction.NORTH);
        for (var at : new BlockPos[]{suction.east(), suction.south(), suction.east().south()}) {
            place(level, at.below(), Blocks.STONE_BRICKS.defaultBlockState());
            place(level, at.south(), Blocks.STONE_BRICKS.defaultBlockState());
            place(level, at, Blocks.WATER.defaultBlockState());
        }
        var discharge = cell.east(3);
        wet.pipeLine(CETransport.STAINLESS_PIPE.get(), null, SurvivalWetWorkshop.pump("stainless_steel_pump"), oxygenPump, Direction.WEST,
            cell, discharge, discharge.south(2), leach.east().south(7), leach.east().south(2));
        wet.drive(oxygenPump, Direction.WEST, Direction.SOUTH);
        built = level.getGameTime();
    }

    private void putCrafted(Item item, BlockPos at) {
        if (level.isEmptyBlock(at.below())) place(level, at.below(), Blocks.STONE_BRICKS.defaultBlockState());
        crafting.placeHeld(item, at);
    }

    private void buildSpoolsAndPump(Item copper, Item shaft) {
        var plank = Items.OAK_PLANKS;
        var slab = Items.OAK_SLAB;
        var wire = cee("copper_wire");
        var spool = cee("empty_spool");
        var nugget = AllItems.COPPER_NUGGET.get();
        crafting.craft(plank, 4, 2, Items.OAK_LOG);
        crafting.craft(slab, 6, 1, plank, plank, plank);
        crafting.craft(Items.STICK, 4, 1, plank, null, null, plank);
        crafting.craft(spool, 8, 1, slab, null, null, Items.STICK, null, null, slab);
        crafting.craft(nugget, 9, 3, Items.COPPER_INGOT);
        crafting.craft(wire, 1, 8, nugget, null, null, nugget, null, null, nugget);
        crafting.craft(cee("copper_wire_spool"), 1, 2, null, wire, null, wire, spool, wire, null, wire);
        var pipe = AllBlocks.FLUID_PIPE.asItem();
        var cog = AllBlocks.COGWHEEL.asItem();
        crafting.craft(pipe, 4, 1, copper, Items.COPPER_INGOT, copper);
        crafting.craft(cog, 1, 1, shaft, plank);
        crafting.craft(heldMechanicalPump(), 1, 1, pipe, cog);
    }

    private void buildAlternator(SurvivalWetWorkshop wet) {
        var westBrush = rotor.west();
        var shaft = westBrush.west();
        for (int dx = 0; dx < 2; dx++) for (int dy = 0; dy < 2; dy++) for (int dz = 0; dz < 2; dz++)
            place(level, boiler.offset(dx, dy, dz), AllBlocks.FLUID_TANK.getDefaultState());
        burner(level, boiler.below());
        crafting.useHeld(Items.COAL_BLOCK, boiler.below(), Direction.UP);
        place(level, shaft, axis(AllBlocks.SHAFT.getDefaultState(), Direction.Axis.X));
        place(level, boiler.above(2), AllBlocks.STEAM_ENGINE.getDefaultState().setValue(SteamEngineBlock.FACE, AttachFace.FLOOR)
            .setValue(SteamEngineBlock.FACING, Direction.NORTH));
        var source = boilerPump.west(4);
        wet.pipeLine(AllBlocks.FLUID_PIPE.get(), null, AllBlocks.MECHANICAL_PUMP.get(), boilerPump, Direction.EAST, source, boiler);
        var wheel = boilerPump.above().offset(-2, -2, -2);
        check(horizontalDrive(level, wheel, Direction.NORTH).equals(boilerPump.above()),
            "Boiler pump cogwheel must mesh at 32 RPM");
        place(level, wheel.above(), Blocks.STONE_BRICKS.defaultBlockState());
        var pool = List.of(source, source.west(), source.south(), source.west().south());
        for (var at : pool) {
            place(level, at.below(), Blocks.STONE_BRICKS.defaultBlockState());
            for (var edge : Direction.Plane.HORIZONTAL) {
                var wall = at.relative(edge);
                if (!pool.contains(wall) && !wall.equals(source.east())) place(level, wall, Blocks.STONE_BRICKS.defaultBlockState());
            }
            place(level, at, Blocks.WATER.defaultBlockState());
        }
        place(level, westBrush, CEEBlocks.ALTERNATOR_BRUSHES.getDefaultState().setValue(BlockStateProperties.FACING, Direction.WEST)
            .setValue(AlternatorBrushesBlock.ROLL, true));
        place(level, rotor, CEEBlocks.ALTERNATOR_ROTOR.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        place(level, brush, CEEBlocks.ALTERNATOR_BRUSHES.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST)
            .setValue(AlternatorBrushesBlock.ROLL, true));
        place(level, rotor.below(2), Blocks.STONE_BRICKS.defaultBlockState());
        place(level, rotor.below(), CEEBlocks.STATOR.getDefaultState().setValue(StatorBlock.FACING, Direction.UP).setValue(StatorBlock.ROLL, true));
    }

    void tick() {
        if (built == 0) return;
        var control = entity(level, panel.below(), ElectrolysisControllerBlockEntity.class);
        if (!started && level.getGameTime() - built >= 30) {
            var spool = cee("copper_wire_spool");
            for (int terminal = 0; terminal < 2; terminal++) {
                crafting.clickWithItem(spool, brush, terminalPoint(brush, terminal));
                crafting.clickWithItem(spool, panel, terminalPoint(panel, terminal));
                check(InfrastructureSavedData.load(level).isConnected(new InWorldNode(terminal, brush), new InWorldNode(terminal, panel)),
                    "Copper spool did not connect terminal " + terminal);
            }
            var player = FakePlayerFactory.getMinecraft(level);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            level.getBlockState(panel).useWithoutItem(level, player, hit(panel, Direction.NORTH, Vec3.atLowerCornerOf(panel).add(.75, .21, 0)));
            check(control.enabled(), "Water electrolysis START did not engage");
            started = true;
        }
        double volts = control.volts();
        if (volts > maxVolts) {
            maxVolts = volts;
            measurements.put("electrolysis_peak_volts", maxVolts);
        }
        if (control.anodeRate() > 0) measurements.putIfAbsent("electrolysis_first_oxygen_ticks", level.getGameTime() - built);
        measurements.put("alternator_rpm", rpm(level, rotor));
        measurements.put("boiler_feed_pump_rpm", rpm(level, boilerPump));
        measurements.put("boiler_water_supply_mb_per_tick", entity(level, boiler, FluidTankBlockEntity.class).boiler.waterSupply);
        measurements.put("oxygen_pump_rpm", rpm(level, oxygenPump));
        measurements.put("electrolysis_status", control.status());
        measurements.put("electrolysis_oxygen_output_mb", fluidAt(cell, Direction.EAST, CEFluids.OXYGEN_GAS.get()));
    }

    private int fluidAt(BlockPos pos, Direction face, net.minecraft.world.level.material.Fluid fluid) {
        var storage = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, pos, face);
        if (storage == null) return 0;
        int total = 0;
        for (int slot = 0; slot < storage.getTanks(); slot++) if (storage.getFluidInTank(slot).is(fluid)) total += storage.getFluidInTank(slot).getAmount();
        return total;
    }

    private Vec3 terminalPoint(BlockPos at, int terminal) {
        var state = level.getBlockState(at);
        return Vec3.atLowerCornerOf(at).add(((ElectricalDeviceBlock<?>) state.getBlock()).getNodePosition(level, at, state, terminal));
    }

    private static Item cee(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("electroenergetics", id));
    }
}
