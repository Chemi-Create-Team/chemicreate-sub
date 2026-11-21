package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.createplant.CreatePlant;
import net.createplant.block.entity.LiddedVesselBlockEntity;
import net.createplant.chemistry.ReactorModule;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CEItems;
import net.createplant.registry.CEReactor;
import net.createplant.registry.CETransport;
import net.createplant.registry.CEUranium;
import net.createplant.transport.FluidHazards;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import static net.createplant.gametest.SurvivalWorkshop.*;

final class SurvivalSulfurWorkshop {
    private final ServerLevel level;
    private final SurvivalMetalsWorkshop works;
    private final SurvivalCrafting crafting;
    private final BlockPos plateChest, ironSheets, stoneChest, sheetFurnace;
    private final List<BlockPos> ironChests = new ArrayList<>();
    private final SurvivalWetWorkshop wet;
    private final BlockPos vessel, vesselBurner, airPump, sulfurHopper;
    private final SurvivalMetalsWorkshop.OutputGate pressGate;
    private final Map<String, Object> measurements = new LinkedHashMap<>();
    private BlockPos sulfurChest;
    private long built, intake = -1, charged = -1;
    private int phase, runs;

    SurvivalSulfurWorkshop(ServerLevel level, BlockPos origin, SurvivalMetalsWorkshop works, BlockPos plateChest, BlockPos ingotChest) {
        this.level = level;
        this.works = works;
        this.plateChest = plateChest;
        vessel = origin.offset(64, 15, 34);
        vesselBurner = vessel.east(2).below();
        airPump = vessel.offset(1, 0, -3);
        sulfurHopper = vessel.west();
        var table = origin.offset(68, 8, 65);
        place(level, table, Blocks.CRAFTING_TABLE.defaultBlockState());
        crafting = new SurvivalCrafting(level, table);
        for (int batch = 0; batch < 3; batch++) {
            ironChests.add(smelter(origin.offset(58, 13, 67 + batch * 6), batch == 0 ? 26 : 24));
            ironChests.add(smelter(origin.offset(62, 13, 67 + batch * 6), 23));
        }
        stoneChest = origin.offset(66, 8, 69);
        place(level, stoneChest, Blocks.CHEST.defaultBlockState());
        works.hopper(stoneChest.above(), Direction.DOWN);
        for (int batch = 0; batch < 3; batch++) {
            works.raw(stoneChest.above(), Items.ANDESITE, 64);
            works.raw(stoneChest.above(), Items.ANDESITE, 34);
        }
        works.raw(stoneChest.above(), Items.ANDESITE, 10);
        works.raw(stoneChest.above(), Items.COAL, 27);

        var depot = origin.offset(70, 9, 58);
        works.press(depot);
        pressGate = works.pressExit(depot, depot.south());
        ironSheets = depot.below(2);
        sheetFurnace = depot.south().above(6);
        works.furnace(sheetFurnace);
        works.hopperPath(sheetFurnace.below(), depot.south().above());
        works.hopper(depot.south(), Direction.NORTH);
        works.raw(sheetFurnace.above(), Items.RAW_IRON, 22);
        works.raw(sheetFurnace.east(), Items.COAL, 3);
        wet = new SurvivalWetWorkshop(level, origin, works, crafting, vessel, plateChest, ingotChest);
        built = level.getGameTime();
    }

    void sulfurChest(BlockPos chest) {
        sulfurChest = chest;
    }

    boolean measuringAir() { return phase >= 2; }

    Map<String, Object> measurements() {
        var result = new LinkedHashMap<>(measurements);
        result.putAll(wet.measurements());
        return result;
    }

    boolean tick() {
        long now = level.getGameTime();
        if (phase == 0) {
            wet.pressTick();
            works.release(pressGate, AllItems.IRON_SHEET.get());
            int smelted = ironChests.stream().mapToInt(chest -> chestCount(chest, Items.IRON_INGOT)).sum();
            if (smelted < 143 || chestCount(ironSheets, AllItems.IRON_SHEET.get()) < 22 || chestCount(stoneChest, Items.ANDESITE) < 304 || !wet.ready()) {
                check(now - built < 6500, "Vessel components stalled: iron=" + smelted + ", sheets=" + chestCount(ironSheets, AllItems.IRON_SHEET.get())
                    + ", andesite=" + chestCount(stoneChest, Items.ANDESITE) + ", press=" + rpm(level, pressGate.depot().above(2))
                    + ", depot=" + works.inventories(pressGate.depot()) + ", input=" + works.inventories(pressGate.depot().south())
                    + ", furnace=" + works.inventories(sheetFurnace));
                return false;
            }
            for (int i = 0; i < 3; i++) {
                crafting.collect(ironChests.get(i * 2), Items.IRON_INGOT, i == 0 ? 26 : 24);
                crafting.collect(ironChests.get(i * 2 + 1), Items.IRON_INGOT, 23);
                makeVessel(List.of(vessel, wet.acid(), wet.leach()).get(i));
            }
            var coal = Items.COAL;
            var nugget = Items.IRON_NUGGET;
            crafting.collect(ironSheets, AllItems.IRON_SHEET.get(), 1);
            crafting.collect(stoneChest, Items.ANDESITE, 10);
            crafting.craft(nugget, 9, 2, Items.IRON_INGOT);
            crafting.craft(AllItems.ANDESITE_ALLOY.get(), 1, 5, nugget, Items.ANDESITE, null, Items.ANDESITE, nugget);
            crafting.collect(stoneChest, coal, 27);
            crafting.craft(Items.COAL_BLOCK, 1, 3, coal, coal, coal, coal, coal, coal, coal, coal, coal);
            burner(level, vesselBurner);
            burner(level, wet.leach().east(2).below());
            works.hopper(sulfurHopper, Direction.EAST);
            measurements.put("crafted_vessel_shell_blocks", 27);
            measurements.put("stainless_sheets_used_for_vessels", 108);
            measurements.put("starting_iron_ore_for_vessels_and_cell", 165);
            built = now;
            phase = 1;
            return false;
        }
        var controller = entity(level, vessel, LiddedVesselBlockEntity.class);
        if (phase == 1) {
            if (sulfurChest == null || now - built < 20) return false;
            for (var at : List.of(vessel, wet.acid(), wet.leach()))
                crafting.useHeld(CEReactor.INTERNALS.get(ReactorModule.STIRRED).get(), at.east().south(), Direction.UP);
            wet.prepare();
            airLine();
            crafting.useHeld(Items.COAL_BLOCK, vesselBurner, Direction.UP);
            crafting.collect(sulfurChest, CEItems.SULFUR_DUST.get(), 3);
            measurements.put("sulfur_fuel_coal_blocks", 1);
            built = now;
            phase = 2;
            return false;
        }
        if (intake < 0) {
            if (rpm(level, airPump) == 8 && controller.formed() && Math.abs(controller.rotationSpeed()) == 32) {
                intake = now;
                measurements.put("open_air_pump_rpm", 8);
                measurements.put("vessel_impeller_rpm", 32);
            } else check(now - built < 180, "Vessel startup: " + controller.status() + ", formed=" + controller.formed()
                + ", rpm=" + controller.rotationSpeed() + ", pump=" + rpm(level, airPump));
            return false;
        }
        wet.tickElectrolysis();
        if (phase == 4) return wet.tick();
        int air = gas(controller, CEFluids.AIR_GAS.get());
        int sulfurDioxide = gas(controller, CEUranium.SULFUR_DIOXIDE.get());
        if (phase == 2) {
            if (air < 2400) {
                check(now - intake < 1800, "Open-ended gas pipe did not deliver one air charge; tank=" + air + " mB, " + controller.status());
                return false;
            }
            measurements.put("air_2400_mb_ticks", now - intake);
            measurements.put("first_air_charge_mb", air);
            phase = 3;
        }
        if (charged < 0) {
            if (air < 7200) {
                check(now - intake < 3600, "Open-ended gas pipe did not deliver the three air charges: " + air);
                return false;
            }
            measurements.put("air_buffer_before_combustion_mb", air);
            crafting.dropStack(CEItems.SULFUR_DUST.get(), sulfurHopper);
            charged = now;
        }
        if (sulfurDioxide >= (runs + 1) * 500) {
            runs++;
            measurements.put("sulfur_burn_" + runs + "_ticks", now - charged);
            measurements.put("sulfur_burn_batches", runs);
            measurements.put("sulfur_dioxide_mb", sulfurDioxide);
            measurements.put("nitrogen_mb", gas(controller, CEFluids.NITROGEN_GAS.get()));
            measurements.put("gas_after_burn_" + runs, gasBuffers(controller));
            charged = now;
            if (runs == 3) {
                wet.begin(now, sulfurChest);
                phase = 4;
                return false;
            }
        }
        if (now - charged >= 1800) {
            measurements.put("sulfur_stall_ticks", now - charged);
            measurements.put("sulfur_stall_buffers", gasBuffers(controller));
            measurements.put("sulfur_stall_status", controller.status());
            measurements.put("sulfur_stall_burner", controller.currentBurnerHeat().name());
            measurements.put("sulfur_stall_rpm", Math.abs(controller.rotationSpeed()));
            check(false, "Sulfur combustion paused: " + controller.status() + ", air=" + air
                + ", SO2=" + sulfurDioxide + ", hopper=" + works.inventories(sulfurHopper) + ", vessel item=" + controller.displayedItem(0));
        }
        return false;
    }

    private BlockPos smelter(BlockPos furnace, int count) {
        works.furnace(furnace);
        works.hopper(furnace.below(), Direction.DOWN);
        place(level, furnace.below(2), Blocks.CHEST.defaultBlockState());
        works.raw(furnace.above(), Items.RAW_IRON, count);
        works.raw(furnace.east(), Items.COAL, (count + 7) / 8);
        return furnace.below(2);
    }

    private int chestCount(BlockPos chest, Item item) { return total(items(level, chest, Direction.UP), item); }

    private void makeVessel(BlockPos vessel) {
        var sheet = AllItems.IRON_SHEET.get();
        crafting.collect(plateChest, CEItems.STAINLESS_STEEL_SHEET.get(), 36);
        crafting.collect(ironSheets, sheet, 7);
        crafting.collect(stoneChest, Items.ANDESITE, 98);
        var ingot = Items.IRON_INGOT;
        var nugget = Items.IRON_NUGGET;
        var alloy = AllItems.ANDESITE_ALLOY.get();
        var basin = AllBlocks.BASIN.asItem();
        var trapdoor = Items.IRON_TRAPDOOR;
        var shaft = AllBlocks.SHAFT.asItem();
        var whisk = AllItems.WHISK.get();
        crafting.craft(trapdoor, 1, 9, ingot, ingot, null, ingot, ingot);
        crafting.craft(nugget, 9, 11, ingot);
        crafting.craft(alloy, 1, 49, nugget, Items.ANDESITE, null, Items.ANDESITE, nugget);
        crafting.craft(basin, 1, 9, alloy, null, alloy, alloy, alloy, alloy);
        crafting.craft(shaft, 8, 1, alloy, null, null, alloy);
        crafting.craft(whisk, 1, 1, null, alloy, null, sheet, alloy, sheet, sheet, sheet, sheet);
        crafting.craft(CEReactor.INTERNALS.get(ReactorModule.STIRRED).get(), 1, 1, null, shaft, null, sheet, whisk, sheet, null, shaft);

        var shell = CETransport.LIDDED_VESSEL.asItem();
        var stainless = CEItems.STAINLESS_STEEL_SHEET.get();
        crafting.craft(shell, 1, 9, stainless, trapdoor, stainless, stainless, basin, stainless);
        var center = vessel.east().south();
        var drive = center.offset(-3, -3, -2);
        check(verticalDrive(level, drive, Direction.NORTH).equals(center.below()), "Vessel shaft must enter from below");
        for (int i = 1; i <= 6; i++) {
            var at = drive.west(i);
            if (i % 3 == 0) wheel(level, at, Direction.NORTH);
            else place(level, at, axis(AllBlocks.SHAFT.getDefaultState(), Direction.Axis.X));
        }
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) {
            var pos = vessel.offset(x, 0, z);
            if (level.isEmptyBlock(pos.below())) place(level, pos.below(), Blocks.STONE_BRICKS.defaultBlockState());
            crafting.placeHeld(shell, pos);
        }

    }

    private void airLine() {
        var target = vessel.east();
        var pipe = CETransport.REINFORCED_PIPE.get().defaultBlockState().setValue(FluidPipeBlock.NORTH, true).setValue(FluidPipeBlock.SOUTH, true);
        for (var at = target.north(5); !at.equals(target); at = at.south()) {
            if (at.equals(airPump)) {
                var pump = BuiltInRegistries.BLOCK.get(CreatePlant.location("gas_pump"));
                place(level, at, pump.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.SOUTH));
            } else level.setBlock(at, pipe, 2);
        }
        place(level, target.north(6), AllBlocks.NOZZLE.getDefaultState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        var drive = airPump.above();
        place(level, drive, axis(AllBlocks.COGWHEEL.getDefaultState(), Direction.Axis.Z));
        place(level, drive.north(), axis(AllBlocks.SHAFT.getDefaultState(), Direction.Axis.Z));
        wheel(level, drive.north(2), Direction.EAST);
    }

    static int gas(LiddedVesselBlockEntity controller, Fluid fluid) {
        int amount = 0;
        for (int slot = 0; slot < controller.displayedFluidSlots(); slot++) {
            var stack = controller.displayedGas(slot);
            if (stack.is(fluid)) amount += stack.getAmount();
        }
        return amount;
    }

    private List<Map<String, Object>> gasBuffers(LiddedVesselBlockEntity controller) {
        var contents = new ArrayList<Map<String, Object>>();
        for (int slot = 0; slot < controller.displayedFluidSlots(); slot++) {
            FluidStack gas = controller.displayedGas(slot);
            if (!gas.isEmpty()) contents.add(Map.of("slot", slot, "id", BuiltInRegistries.FLUID.getKey(gas.getFluid()).getPath(),
                "mb", gas.getAmount(), "heat", FluidHazards.of(gas).heat().name()));
        }
        return contents;
    }
}
