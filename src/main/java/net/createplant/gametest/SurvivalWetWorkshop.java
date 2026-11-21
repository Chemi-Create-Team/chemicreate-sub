package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.createplant.CreatePlant;
import net.createplant.block.entity.LiddedVesselBlockEntity;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CEItems;
import net.createplant.registry.CEMetals;
import net.createplant.registry.CETransport;
import net.createplant.registry.CEUranium;
import net.createplant.registry.CEWetProcessing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import static net.createplant.gametest.SurvivalWorkshop.*;

final class SurvivalWetWorkshop {
    private final ServerLevel level;
    private final SurvivalMetalsWorkshop works;
    private final SurvivalCrafting crafting;
    private final SurvivalWaterElectrolysis electrolysis;
    private final BlockPos sulfur, acid, leach, plates, ingots;
    private final BlockPos clayChest, firedChest, waterSource, acidIn, oreIn;
    private final Map<String, Object> measurements = new LinkedHashMap<>();
    private long started, acidReady, leachCharge;
    private int waterCharges = 1, leachBatches;
    private boolean acidTransfer, leachHeated;
    private BlockPos oreChest;

    SurvivalWetWorkshop(ServerLevel level, BlockPos origin, SurvivalMetalsWorkshop works, SurvivalCrafting crafting,
                        BlockPos sulfur, BlockPos plates, BlockPos ingots) {
        this.level = level;
        this.works = works;
        this.crafting = crafting;
        this.sulfur = sulfur;
        this.plates = plates;
        this.ingots = ingots;
        acid = origin.offset(68, 15, 37);
        leach = origin.offset(68, 15, 45);
        waterSource = acid.east().north(6);
        acidIn = acid.west();
        oreIn = leach.west();

        clayChest = origin.offset(78, 8, 66);
        place(level, clayChest, Blocks.CHEST.defaultBlockState());
        works.hopper(clayChest.above(), Direction.DOWN);
        works.raw(clayChest.above(), Items.CLAY_BALL, 9);
        var furnace = origin.offset(78, 12, 69);
        works.furnace(furnace);
        works.raw(furnace.east(), Items.COAL, 1);
        firedChest = furnace.below(2);
        works.hopper(furnace.below(), Direction.DOWN);
        place(level, firedChest, Blocks.CHEST.defaultBlockState());

        electrolysis = new SurvivalWaterElectrolysis(level, origin, works, crafting, plates, leach);
    }

    BlockPos acid() { return acid; }
    BlockPos leach() { return leach; }
    Map<String, Object> measurements() {
        var results = new LinkedHashMap<>(measurements);
        results.putAll(electrolysis.measurements());
        return results;
    }

    void pressTick() { electrolysis.pressTick(); }

    boolean ready() { return electrolysis.ready(); }

    void prepare() { electrolysis.build(this); }

    void tickElectrolysis() { electrolysis.tick(); }

    void begin(long now, BlockPos recoveryChest) {
        oreChest = recoveryChest;
        works.crushIron();
        var sheet = CEItems.STAINLESS_STEEL_SHEET.get();
        var ingot = CEItems.STAINLESS_STEEL_INGOT.get();
        var stainless = CETransport.STAINLESS_PIPE.asItem();
        crafting.collect(plates, sheet, 4);
        crafting.collect(ingots, ingot, 2);
        crafting.craft(stainless, 4, 2, sheet, ingot, sheet);
        var gasPump = pump("stainless_steel_pump");
        // The material pump is crafted with a standard pump built from early-Create parts.
        crafting.craft(gasPump.asItem(), 1, 1, stainless, electrolysis.heldMechanicalPump());
        var transferAt = sulfur.above().east(2);
        pipeLine(CETransport.STAINLESS_PIPE.get(), stainless, gasPump, transferAt, Direction.EAST,
            sulfur, sulfur.above(), acid.above().north(3), acid.above(), acid);
        drive(transferAt, Direction.WEST, Direction.NORTH);

        var intake = acid.east(2);
        openAir(intake, Direction.WEST, Direction.SOUTH);
        var waterPump = acid.east().north(3);
        pipeLine(AllBlocks.FLUID_PIPE.get(), null, AllBlocks.MECHANICAL_PUMP.get(), waterPump, Direction.SOUTH, waterSource, acid.east());
        drive(waterPump, Direction.NORTH, Direction.EAST);
        waterSource(waterSource, Direction.SOUTH);
        works.hopper(acidIn, Direction.EAST);
        works.hopper(oreIn, Direction.EAST);
        for (int i = 0; i < 3; i++) crafting.dropOne(Items.IRON_NUGGET, acidIn);
        crafting.collect(clayChest, Items.CLAY_BALL, 9);
        var green = item("unfired_ceramic_pipe");
        crafting.craft(green, 2, 3, Items.CLAY_BALL, Items.CLAY_BALL, Items.CLAY_BALL);
        crafting.dropStack(green, firedChest.above(3));
        started = now;
    }

    boolean tick() {
        var reactor = entity(level, acid, LiddedVesselBlockEntity.class);
        var mixer = entity(level, leach, LiddedVesselBlockEntity.class);
        var burner = entity(level, sulfur, LiddedVesselBlockEntity.class);
        long now = level.getGameTime();
        var acidFluid = CEMetals.DILUTE_SULFURIC_ACID.get();
        int acidStock = liquid(reactor, acidFluid);
        int receivedSulfur = SurvivalSulfurWorkshop.gas(reactor, CEUranium.SULFUR_DIOXIDE.get());
        if (receivedSulfur > 0) measurements.putIfAbsent("first_so2_pipe_delivery_ticks", now - started);
        if (!acidTransfer) {
            for (int batch = 1; batch <= 3; batch++) if (acidStock >= batch * 1000)
                measurements.putIfAbsent("dilute_acid_" + batch + "_ticks", now - started);
            if (acidStock >= waterCharges * 1000 && waterCharges < 3) {
                waterSource(waterSource, Direction.SOUTH);
                waterCharges++;
            }
            if (acidStock >= 3000 && count(firedChest, CETransport.CERAMIC_PIPE.asItem()) >= 6) {
                crafting.collect(firedChest, CETransport.CERAMIC_PIPE.asItem(), 6);
                place(level, waterSource, Blocks.STONE_BRICKS.defaultBlockState());
                var acidPump = acid.east().south(5).above();
                var out = acid.east().south(2);
                var in = leach.east();
                pipeLine(CETransport.CERAMIC_PIPE.get(), CETransport.CERAMIC_PIPE.asItem(), AllBlocks.MECHANICAL_PUMP.get(),
                    acidPump, Direction.SOUTH, out, out.south(), out.south().above(), in.north().above(), in.north(), in);
                drive(acidPump, Direction.NORTH, Direction.WEST);
                measurements.put("dilute_acid_produced_mb", acidStock);
                measurements.put("fired_ceramic_pipes", 6);
                acidReady = now;
                acidTransfer = true;
            }
        }
        int acidAtLeach = liquid(mixer, acidFluid);
        if (acidAtLeach > 0) measurements.putIfAbsent("first_ceramic_pipe_delivery_ticks", now - acidReady);
        var oxidant = SurvivalSulfurWorkshop.gas(mixer, CEFluids.OXYGEN_GAS.get());
        if (oxidant > 0) measurements.putIfAbsent("first_oxygen_at_leach_ticks", now - started);
        var slurry = CEWetProcessing.SLURRIES.get("iron_leach_slurry").get();
        int slurryStock = liquid(mixer, slurry);
        if (leachCharge > 0 && slurryStock >= (leachBatches + 1) * 4000) {
            leachBatches++;
            measurements.put("iron_leach_" + leachBatches + "_ticks", now - leachCharge);
            measurements.put("iron_slurry_mb", slurryStock);
            measurements.put("iron_leach_batches", leachBatches);
            leachCharge = 0;
            if (leachBatches == 3) {
                measurements.put("remaining_dilute_acid_mb", acidAtLeach);
                return true;
            }
        }
        var crushed = BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create", "crushed_raw_iron"));
        if (acidTransfer && leachCharge == 0 && acidAtLeach >= 750 && oxidant >= 250 && count(oreChest, crushed) >= 3
            && (leachBatches == 0 || oldestFreshSlurry(mixer, slurry) == 0)) {
            if (!leachHeated) {
                crafting.useHeld(Items.COAL_BLOCK, leach.east(2).below(), Direction.UP);
                leachHeated = true;
            }
            crafting.collect(oreChest, crushed, 3);
            crafting.dropStack(crushed, oreIn);
            leachCharge = now;
        }
        if (now - started >= 16000) check(false, "Wet workshop stalled: SO2 burner/acid="
            + SurvivalSulfurWorkshop.gas(burner, CEUranium.SULFUR_DIOXIDE.get()) + "/" + receivedSulfur
            + ", acid=" + reactor.status() + "/" + acidStock + ", water=" + liquid(reactor, net.minecraft.world.level.material.Fluids.WATER)
            + ", air=" + SurvivalSulfurWorkshop.gas(reactor, CEFluids.AIR_GAS.get()) + ", Fe=" + reactor.displayedItem(0)
            + ", leach=" + mixer.status() + "/" + acidAtLeach + ", O2=" + oxidant + ", slurry=" + slurryStock
            + ", ore=" + count(oreChest, crushed) + ", ceramics=" + count(firedChest, CETransport.CERAMIC_PIPE.asItem())
            + ", electrolysis=" + electrolysis.measurements());
        return false;
    }

    private int oldestFreshSlurry(LiddedVesselBlockEntity vessel, Fluid slurry) {
        int fresh = 0;
        for (int i = 0; i < vessel.displayedFluidSlots(); i++) {
            var stack = vessel.displayedLiquid(i);
            if (stack.is(slurry) && net.createplant.transport.FluidHazards.of(stack).heat() == net.createplant.thermal.Grade.Heat.AMBIENT)
                fresh += stack.getAmount();
        }
        return fresh;
    }

    private int count(BlockPos chest, Item item) { return total(items(level, chest, Direction.UP), item); }

    static int liquid(LiddedVesselBlockEntity vessel, Fluid fluid) {
        int total = 0;
        for (int i = 0; i < vessel.displayedFluidSlots(); i++) {
            var stack = vessel.displayedLiquid(i);
            if (stack.is(fluid)) total += stack.getAmount();
        }
        return total;
    }

    void openAir(BlockPos target, Direction flow, Direction waterSide) {
        var open = target.relative(flow.getOpposite(), 6);
        var middle = target.relative(flow.getOpposite(), 3);
        pipeLine(CETransport.REINFORCED_PIPE.get(), null, pump("gas_pump"), middle, flow, open, target);
        drive(middle, flow.getOpposite(), waterSide);
    }

    void waterSource(BlockPos source, Direction stream) {
        place(level, source.below(), Blocks.STONE_BRICKS.defaultBlockState());
        for (var face : List.of(stream.getOpposite(), stream.getClockWise(), stream.getCounterClockWise()))
            place(level, source.relative(face), Blocks.STONE_BRICKS.defaultBlockState());
        place(level, source, Blocks.WATER.defaultBlockState());
    }

    void drive(BlockPos pump, Direction along, Direction waterSide) {
        var shaft = pump.above();
        place(level, shaft, axis(AllBlocks.COGWHEEL.getDefaultState(), along.getAxis()));
        place(level, shaft.relative(along), axis(AllBlocks.SHAFT.getDefaultState(), along.getAxis()));
        wheel(level, shaft.relative(along, 2), waterSide);
    }

    void pipeLine(Block pipe, Item earned, Block pump, BlockPos drive, Direction flow, BlockPos... turns) {
        var path = new ArrayList<BlockPos>();
        path.add(turns[0]);
        for (int i = 1; i < turns.length; i++) {
            var from = path.getLast();
            var to = turns[i];
            var diff = to.subtract(from);
            var face = Direction.fromDelta(Integer.signum(diff.getX()), Integer.signum(diff.getY()), Integer.signum(diff.getZ()));
            check(face != null, "Pipe waypoints must be separate and axis-aligned");
            while (!from.equals(to)) {
                from = from.relative(face);
                path.add(from);
            }
        }
        for (int i = 1; i < path.size() - 1; i++) {
            var at = path.get(i);
            if (at.equals(drive)) {
                if (earned != null && pump != AllBlocks.MECHANICAL_PUMP.get()) placeCrafted(pump.asItem(), at, path);
                place(level, at, pump.defaultBlockState().setValue(BlockStateProperties.FACING, flow));
            } else {
                if (earned != null) placeCrafted(earned, at, path);
                var state = pipe.defaultBlockState();
                var before = path.get(i - 1).subtract(at);
                var after = path.get(i + 1).subtract(at);
                for (var direction : Direction.values()) state = state.setValue(FluidPipeBlock.PROPERTY_BY_DIRECTION.get(direction),
                    direction.getNormal().equals(before) || direction.getNormal().equals(after));
                level.setBlock(at, state, Block.UPDATE_CLIENTS);
            }
        }
        for (int i = 1; i < path.size() - 1; i++) {
            var at = path.get(i);
            level.updateNeighborsAt(at, level.getBlockState(at).getBlock());
        }
    }

    private void placeCrafted(Item item, BlockPos at, List<BlockPos> path) {
        boolean leavesRoomForPipe = path.contains(at.below()) && level.getBlockState(at.below()).canBeReplaced();
        var face = leavesRoomForPipe ? Direction.WEST : Direction.UP;
        var support = at.relative(face.getOpposite());
        if (level.getBlockState(support).canBeReplaced()) place(level, support, Blocks.STONE_BRICKS.defaultBlockState());
        crafting.placeHeld(item, at, face);
    }

    static Block pump(String id) { return BuiltInRegistries.BLOCK.get(CreatePlant.location(id)); }
}
