package net.createplant.gametest;

import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import static net.createplant.gametest.SurvivalWorkshop.*;

final class SurvivalAlloyWorkshop {
    private static final int PAIRS = 7;
    private static final int PLATES = 122;
    private final ServerLevel level;
    private final SurvivalMetalsWorkshop works;
    private final BlockPos origin, roast, nickel, ferrochrome, stainless, nickelMill, chromeMill;
    private final BlockPos roastExit, nickelExit, chromeExit, stainlessExit, iron, depot, chest, ingots;
    private final SurvivalMetalsWorkshop.OutputGate gate;
    private final List<BlockPos> burners, deployers;
    private final Map<String, Object> measurements = new LinkedHashMap<>();
    private final long start;
    private int fired, pair = 1;
    private boolean cakeInFlight, collectingIngots;

    SurvivalAlloyWorkshop(ServerLevel level, BlockPos origin, SurvivalMetalsWorkshop works) {
        this.level = level;
        this.origin = origin;
        this.works = works;
        roast = at(14, 19, 42);
        nickel = at(24, 14, 42);
        ferrochrome = at(16, 16, 62);
        stainless = at(42, 11, 48);
        for (var basin : List.of(roast, nickel, ferrochrome, stainless)) {
            works.mixer(basin);
            burner(level, basin.below());
        }
        burners = List.of(nickel.below(), ferrochrome.below(), stainless.below());
        deployers = List.of(works.installDeployer(nickel.below()), works.installDeployer(ferrochrome.below()),
            works.installDeployer(stainless.below(), Direction.EAST));
        roastExit = works.basinOut(roast);
        nickelExit = works.basinOut(nickel);
        chromeExit = works.basinOut(ferrochrome);
        stainlessExit = works.basinOut(stainless);

        works.hopper(roast.north(), Direction.SOUTH);
        convey(roastExit.below(), nickel.north(), Direction.SOUTH);
        nickelMill = works.mill(at(17,20,51));
        convey(nickelMill.east().below(), nickel.north().west(), Direction.EAST);
        chromeMill = works.mill(at(9,21,63));
        convey(chromeMill.east().below(), ferrochrome.south(), Direction.NORTH);
        works.hopper(ferrochrome.north(), Direction.SOUTH);
        convey(nickelExit.below(), stainless.north(), Direction.SOUTH);
        convey(chromeExit.below(), stainless.south(), Direction.NORTH);

        iron = at(46,16,56);
        works.furnace(iron);
        var ironFeed = stainless.south().east();
        convey(iron.below(), ironFeed, Direction.WEST);
        depot = at(50,8,48);
        works.press(depot);
        works.hopper(stainlessExit.below(), Direction.SOUTH);
        convey(stainlessExit.below().south(), depot.south(), Direction.NORTH);
        chest = depot.below(2);
        ingots = depot.south().below();
        place(level, ingots, Blocks.CHEST.defaultBlockState());
        gate = works.pressExit(depot, depot.south());

        start = level.getGameTime();
        works.routeFuel(deployers.getFirst());
        works.chargeCake();
        cakeInFlight = true;
        fuel(level, roast.below(), Items.COAL, BlazeBurnerBlock.HeatLevel.KINDLED);
        chargePair();
    }

    BlockPos plateChest() { return chest; }
    BlockPos ingotChest() { return ingots; }

    Map<String, Object> measurements() { return Map.copyOf(measurements); }

    boolean tick() {
        long time = level.getGameTime() - start;
        if (time == 130) {
            for (var at : List.of(roast, nickel, ferrochrome, stainless))
                check(rpm(level, at.above(2)) == 32, "Alloy mixer shaft powered at " + at);
            check(rpm(level, depot.above(2)) == 32, "Alloy press turns at 32 RPM");
            measurements.put("alloy_mixer_rpm", 32);
            measurements.put("alloy_press_rpm", 32);
        }
        heatBurners(time);

        observe(roastExit, "nickel_oxide", time);
        observe(nickelExit, "nickel_ingot", time);
        observe(chromeExit, "ferrochrome", time);
        observe(stainlessExit, "stainless_steel_ingot", time);
        if (!collectingIngots) works.release(gate, item("stainless_steel_sheet"));
        int plates = total(items(level, chest, Direction.UP), item("stainless_steel_sheet"));
        if (plates >= 16) measurements.putIfAbsent("sixteen_plates_at", time);
        if (pair < PAIRS && plates >= pair * 20 - 4 && fired == burners.size()) {
            pair++;
            fired = 0;
        }
        if (!collectingIngots && plates >= PLATES) {
            collectingIngots = true;
            works.hopper(gate.depot().south(), Direction.DOWN);
            works.setClosed(gate.input(), false);
        }
        int cast = total(items(level, ingots, Direction.UP), item("stainless_steel_ingot"));
        if (plates + cast == PAIRS * 20 && fired == burners.size()) {
            measurements.put("stainless_plates", plates);
            measurements.put("stainless_ingots_reserved", cast);
            measurements.put("alloy_batches", PAIRS * 2);
            measurements.put("alloy_elapsed_ticks", time);
            return true;
        }
        if (time >= 46000)
            check(false, "Alloy workshop stalled: " + measurements + ", sheets=" + plates + ", hot burners=" + fired
                + ", roast=" + works.inventories(roast) + "/" + works.inventories(roast.north()) + "/" + works.inventories(roastExit)
                + ", nickel=" + works.inventories(nickel) + ", chrome=" + works.inventories(ferrochrome)
                + ", alloy=" + works.inventories(stainless) + ", deployer=" + works.inventories(deployers.getFirst()) + ", " + works.cakeStatus());
        return false;
    }

    private void observe(BlockPos at, String material, long time) {
        if (!measurements.containsKey(material + "_first_output_at")
            && (total(items(level, at, Direction.UP), item(material)) > 0 || total(items(level, at.below(), Direction.UP), item(material)) > 0))
            measurements.putIfAbsent(material + "_first_output_at", time);
    }

    private void heatBurners(long time) {
        if (fired == burners.size()) return;
        boolean hot = level.getBlockState(burners.get(fired)).getValue(BlazeBurnerBlock.HEAT_LEVEL) == BlazeBurnerBlock.HeatLevel.SEETHING;
        if (!cakeInFlight && !hot) {
            works.routeFuel(deployers.get(fired));
            works.chargeCake();
            cakeInFlight = true;
        } else if (cakeInFlight && hot) {
            cakeInFlight = false;
            fired++;
            measurements.put("alloy_pair_" + pair + "_burner_" + fired + "_at", time);
            if (pair > 1 && fired == 1) {
                if (level.getBlockState(roast.below()).getValue(BlazeBurnerBlock.HEAT_LEVEL) != BlazeBurnerBlock.HeatLevel.KINDLED)
                    fuel(level, roast.below(), Items.COAL, BlazeBurnerBlock.HeatLevel.KINDLED);
                chargePair();
            }
        }
    }

    private void chargePair() {
        works.raw(roast.north(), item("pentlandite"), 4);
        works.raw(nickelMill.above(), Items.COAL, 2);
        works.raw(ferrochrome.north(), item("chromite"), 8);
        works.raw(chromeMill.above(), Items.COAL, 4);
        works.raw(iron.above(), Items.RAW_IRON, 12);
        works.raw(iron.east(), Items.COAL, 2);
    }

    private void convey(BlockPos from, BlockPos to, Direction face) {
        works.hopperPath(from, to.above());
        works.hopper(to, face);
    }

    private BlockPos at(int x, int y, int z) { return origin.offset(x, y, z); }
}
