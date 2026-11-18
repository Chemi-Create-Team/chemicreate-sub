package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlock;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import java.util.LinkedHashMap;
import java.util.Map;
import net.createplant.registry.CEItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import static net.createplant.gametest.SurvivalWorkshop.*;

public final class SurvivalMetalsWorkshop {
    private final ServerLevel level;
    private final BlockPos origin, steelMixer, steelBurner, steelMill, steelDepot, steelChest;
    private final OutputGate steelGate, cakeGate;
    private final BlockPos cakeCrusher, cakeCrushOutput, cakePress, cakeSpout, cakePump, cakeDepot, cakeInput, cakeLava;
    private final BlockPos deployer, ironOre;
    private final Map<String, Object> measurements = new LinkedHashMap<>();
    private final long start;
    private long heatedAt = -1;
    private int steel, pressPass;
    private boolean charged;
    private SurvivalAlloyWorkshop alloy;
    private SulfurCrushingProbe sulfur;
    private SurvivalSulfurWorkshop chemistry;
    private final java.util.List<BlockPos> fuelRoute = new java.util.ArrayList<>();

    public SurvivalMetalsWorkshop(ServerLevel level, BlockPos origin) {
        this.level = level;
        this.origin = origin;
        var steelBasin = at(20, 12, 18);
        steelMixer = mixer(steelBasin);
        steelBurner = steelBasin.below();
        burner(level, steelBurner);
        steelMill = mill(at(12, 16, 18));
        hopperPath(steelMill.east().below(), steelBasin.south().above());
        hopper(steelBasin.south(), Direction.NORTH);
        var ironFurnace = at(20, 16, 13);
        furnace(ironFurnace);
        ironOre = ironFurnace.above();
        hopperPath(ironFurnace.below(), steelBasin.north().above());
        hopper(steelBasin.north(), Direction.SOUTH);
        var steelMixExit = basinOut(steelBasin);
        steelDepot = at(28, 8, 18);
        press(steelDepot);
        hopper(steelMixExit.below(), Direction.NORTH);
        hopperPath(steelMixExit.below().north(), steelDepot.north().above());
        hopper(steelDepot.north(), Direction.SOUTH);
        steelChest = steelDepot.below(2);
        steelGate = pressExit(steelDepot, steelDepot.north());

        cakeCrusher = at(5, 29, 6);
        cakeCrushOutput = crusher(cakeCrusher);
        var cakeBasin = at(14, 25, 6);
        place(level, cakeBasin, AllBlocks.BASIN.getDefaultState());
        cakePress = press(cakeBasin);
        hopper(cakeCrushOutput.below(), Direction.NORTH);
        hopperPath(cakeCrushOutput.below().north(), cakeBasin.north().above());
        hopper(cakeBasin.north(), Direction.SOUTH);
        cakeInput = cakeBasin.south();
        hopper(cakeInput, Direction.NORTH);
        var cakeMixExit = basinOut(cakeBasin);
        cakeDepot = at(24, 21, 6);
        cakeSpout = cakeDepot.above(2);
        place(level, cakeDepot, AllBlocks.DEPOT.getDefaultState());
        place(level, cakeSpout, AllBlocks.SPOUT.getDefaultState());
        hopperPath(cakeMixExit.below(), cakeDepot.west().above());
        hopper(cakeDepot.west(), Direction.EAST);
        var lava = cakeSpout.north(5);
        cakeLava = lava;
        place(level, lava.below(), Blocks.STONE_BRICKS.defaultBlockState());
        for (Direction face : new Direction[]{Direction.NORTH, Direction.WEST, Direction.EAST})
            place(level, lava.relative(face), Blocks.STONE_BRICKS.defaultBlockState());
        place(level, lava, Blocks.LAVA.defaultBlockState());
        cakePump = lava.south(2);
        liquidLine(lava, cakeSpout, cakePump);

        deployer = installDeployer(steelBurner);
        cakeGate = pressExit(cakeDepot, cakeDepot.west());
        routeFuel(deployer);
        start = level.getGameTime();
    }

    public boolean measuringSulfurCombustion() { return chemistry != null && chemistry.measuringAir(); }
    public Map<String, Object> measurements() {
        var result = new LinkedHashMap<>(measurements);
        if (alloy != null) result.putAll(alloy.measurements());
        if (sulfur != null) result.putAll(sulfur.measurements());
        if (chemistry != null) result.putAll(chemistry.measurements());
        return result;
    }

    public boolean tick() {
        long time = level.getGameTime() - start;
        if (sulfur != null) {
            if (!sulfur.complete && sulfur.tick(time)) {
                check(sulfur.previousSulfur >= 3, "Collect at least three sulfur dust for the furnace test");
                chemistry.sulfurChest(sulfur.chest);
            }
            return chemistry.tick();
        }
        if (alloy != null) {
            release(cakeGate, AllItems.BLAZE_CAKE.get());
            if (alloy.tick()) {
                sulfur = new SulfurCrushingProbe(time);
                chemistry = new SurvivalSulfurWorkshop(level, origin, this, alloy.plateChest(), alloy.ingotChest());
            }
            return false;
        }
        if (!charged && time >= 150) {
            measurements.put("mill_rpm", rpm(level, steelMill));
            measurements.put("mixer_rpm", rpm(level, steelMixer));
            measurements.put("press_rpm", rpm(level, steelDepot.above(2)));
            measurements.put("cake_press_rpm", rpm(level, cakePress));
            measurements.put("spout_pump_rpm", rpm(level, cakePump));
            measurements.put("deployer_rpm", rpm(level, deployer));
            check(rpm(level, steelMill) == 8 && rpm(level, steelMixer) == 32
                && rpm(level, steelDepot.above(2)) == 32 && rpm(level, cakePress) == 32
                && rpm(level, cakePump) == 8 && rpm(level, deployer) == 8, "Water-wheel speeds: " + measurements + ", wheels="
                    + wheelState(steelMill.below().west(), Direction.NORTH) + ", " + wheelState(steelMixer.offset(-4,-2,-2), Direction.NORTH)
                    + ", " + wheelState(steelDepot.offset(-3,0,-2), Direction.NORTH) + ", " + wheelState(cakePress.offset(-3,-2,-2), Direction.NORTH)
                    + ", " + wheelState(deployer.east(2), Direction.SOUTH));
            var crusherState = level.getBlockState(cakeCrusher);
            check(crusherState.is(AllBlocks.CRUSHING_WHEEL_CONTROLLER.get())
                && crusherState.getValue(CrushingWheelControllerBlock.VALID)
                && crusherState.getValue(BlockStateProperties.FACING) == Direction.DOWN,
                "The crushing wheels must turn into their chute: " + crusherState);
            measurements.put("crushing_wheel_rpm", rpm(level, cakeCrusher.north()));
            chargeCake();
            raw(steelMill.above(), Items.COAL, 3);
            raw(ironOre, Items.RAW_IRON, 12);
            raw(ironOre.below().east(), Items.COAL, 2);
            fuel(level, steelBurner, Items.COAL, BlazeBurnerBlock.HeatLevel.KINDLED);
            charged = true;
            measurements.put("raw_feed_at", time);
        }
        if (!charged) return false;
        observe(cakeCrushOutput, AllItems.CINDER_FLOUR.get(), "cinder_flour_at", time);
        observe(cakeCrushOutput, CEItems.SULFUR_DUST.get(), "sulfur_dust_at", time);
        observe(cakePress.below(2).east().below(), AllItems.BLAZE_CAKE_BASE.get(), "cake_base_at", time);
        observe(cakeDepot, AllItems.BLAZE_CAKE.get(), "cake_finished_at", time);
        observe(cakeDepot.below(2), AllItems.BLAZE_CAKE.get(), "cake_chest_at", time);
        observe(deployer, AllItems.BLAZE_CAKE.get(), "cake_deployer_at", time);
        release(cakeGate, AllItems.BLAZE_CAKE.get());
        release(steelGate, CEItems.STEEL_INGOT.get());
        var heat = level.getBlockState(steelBurner).getValue(BlazeBurnerBlock.HEAT_LEVEL);
        if (heatedAt < 0 && heat == BlazeBurnerBlock.HeatLevel.SEETHING) {
            heatedAt = time;
            measurements.put("deployer_fired_cake_at", time);
        }
        var pressInput = items(level, steelDepot, Direction.UP).getStackInSlot(0);
        var sequence = pressInput.get(com.simibubi.create.AllDataComponents.SEQUENCED_ASSEMBLY);
        if (steel == 0 && sequence != null && sequence.step() > pressPass) {
            pressPass = sequence.step();
            measurements.put("steel_press_" + pressPass + "_at", time);
        }
        int count = total(items(level, steelChest, Direction.UP), CEItems.STEEL_INGOT.get());
        if (count > steel) {
            steel = count;
            measurements.put("steel_" + count + "_at", time);
        }
        if (steel == 12) {
            check(heatedAt >= 0 && pressPass == 4, "The first workpiece must go through all five presses");
            measurements.put("steel_mixer_batches", 3);
            measurements.put("steel_qualified", true);
            alloy = new SurvivalAlloyWorkshop(level, origin, this);
            return false;
        }
        if (time >= 15000) {
            var pressMaterials = items(level, steelDepot, Direction.UP);
            check(false, "Workshop stalled: " + measurements + ", steel tray=" + pressMaterials.getStackInSlot(0)
                + ", cake tray=" + items(level, cakeDepot, Direction.UP).getStackInSlot(0)
                + ", spout=" + fluids(level, cakeSpout, Direction.NORTH).getFluidInTank(0)
                + ", burner=" + heat + ", steel basin=" + inventories(steelMixer.below(2))
                + ", cake basin=" + inventories(cakePress.below(2)) + ", cake chest=" + inventories(cakeDepot.below(2))
                + ", deployer=" + inventories(deployer) + ", fuel blockers=" + fuelContents());
        }
        return false;
    }

    String inventories(BlockPos pos) {
        var handler = items(level, pos, Direction.UP);
        var found = new java.util.ArrayList<String>();
        for (int i = 0; i < handler.getSlots(); i++)
            if (!handler.getStackInSlot(i).isEmpty()) found.add(handler.getStackInSlot(i).toString());
        return found.toString();
    }

    private void observe(BlockPos at, Item item, String key, long tick) {
        if (!measurements.containsKey(key) && total(items(level, at, Direction.UP), item) > 0) measurements.put(key, tick);
    }

    private String fuelContents() {
        return fuelRoute.stream().map(pos -> pos + ":" + inventories(pos)).filter(message -> !message.endsWith("[]")).toList().toString();
    }

    String cakeStatus() {
        return "cake basin=" + inventories(cakePress.below(2)) + ", depot=" + inventories(cakeDepot)
            + ", fuel storage=" + inventories(cakeDepot.below(2)) + ", fuel line=" + fuelContents();
    }

    private final class SulfurCrushingProbe {
        private final BlockPos chest;
        private final Map<String, Object> observed = new LinkedHashMap<>();
        private long inputAt;
        private int batch = 1;
        private int previousFlour, previousSulfur;
        private boolean occupied, complete;

        SulfurCrushingProbe(long time) {
            var outputHopper = cakeCrushOutput.below();
            check(inventories(outputHopper).equals("[]"), "Crusher export must be empty before connecting the sampling chest");
            chest = outputHopper.below();
            hopper(outputHopper, Direction.DOWN);
            place(level, chest, Blocks.CHEST.defaultBlockState());
            var recipes = level.getRecipeManager();
            observed.put("create_netherrack_recipe_loaded", recipes.byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create", "crushing/netherrack")).isPresent());
            observed.put("separate_sulfur_recipe_loaded", recipes.byKey(net.createplant.CreatePlant.location("crushing/netherrack_to_sulfur_dust")).isPresent());
            observed.put("sulfur_probe_started_at", time);
            begin(time);
        }

        private void begin(long time) {
            raw(cakeCrusher, Items.NETHERRACK, 8);
            inputAt = time;
            occupied = false;
        }

        Map<String, Object> measurements() { return Map.copyOf(observed); }

        boolean tick(long time) {
            var machine = entity(level, cakeCrusher, com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlockEntity.class);
            var working = machine.isOccupied();
            occupied |= working;
            check(time - inputAt < 3200, "Netherrack batch has not discharged from the physical crusher: " + batch);
            if (!occupied || working || !inventories(cakeCrushOutput).equals("[]") || !inventories(cakeCrushOutput.below()).equals("[]")) return false;
            var inv = items(level, chest, Direction.UP);
            int flour = total(inv, AllItems.CINDER_FLOUR.get());
            int powder = total(inv, CEItems.SULFUR_DUST.get());
            observed.put("netherrack_batch_" + batch, Map.of("input", 8, "ticks", time - inputAt,
                "cinder_flour", flour - previousFlour, "sulfur_dust", powder - previousSulfur));
            previousFlour = flour;
            previousSulfur = powder;
            if (batch >= 3 && (powder >= 3 || batch >= 6)) {
                observed.put("sulfur_netherrack_progressed", powder > 0);
                observed.put("sulfur_netherrack_total", powder);
                complete = true;
                return true;
            }
            batch++;
            begin(time);
            return false;
        }
    }

    private BlockPos at(int x, int y, int z) { return origin.offset(x, y, z); }

    private String wheelState(BlockPos pos, Direction waterSide) {
        return level.getBlockState(pos) + "/" + rpm(level,pos) + "/" + level.getBlockState(pos.relative(waterSide));
    }

    BlockPos mixer(BlockPos basin) {
        place(level, basin, AllBlocks.BASIN.getDefaultState());
        var mix = basin.above(2);
        verticalDrive(level, mix.offset(-4, -2, -2), Direction.NORTH);
        place(level, mix, AllBlocks.MECHANICAL_MIXER.getDefaultState());
        return mix;
    }

    BlockPos press(BlockPos surface) {
        var press = surface.above(2);
        horizontalDrive(level, press.offset(-3, -2, -2), Direction.NORTH);
        place(level, press, AllBlocks.MECHANICAL_PRESS.getDefaultState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
        if (!level.getBlockState(surface).is(AllBlocks.BASIN.get()))
            place(level, surface, AllBlocks.DEPOT.getDefaultState());
        return press;
    }

    BlockPos mill(BlockPos mill) {
        wheel(level, mill.west().below(), Direction.NORTH);
        place(level, mill.below(), axis(AllBlocks.GEARBOX.getDefaultState(), Direction.Axis.Z));
        place(level, mill, AllBlocks.MILLSTONE.getDefaultState());
        hopper(mill.above(), Direction.DOWN);
        var funnel = AllBlocks.ANDESITE_FUNNEL.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST)
            .setValue(FunnelBlock.EXTRACTING, true);
        place(level, mill.east(), funnel);
        return mill;
    }

    private BlockPos crusher(BlockPos throat) {
        var north = throat.north();
        var south = throat.south();
        place(level, north, axis(AllBlocks.CRUSHING_WHEEL.getDefaultState(), Direction.Axis.X));
        place(level, south, axis(AllBlocks.CRUSHING_WHEEL.getDefaultState(), Direction.Axis.X));
        place(level, north.west(), axis(AllBlocks.SHAFT.getDefaultState(), Direction.Axis.X));
        place(level, south.east(), axis(AllBlocks.SHAFT.getDefaultState(), Direction.Axis.X));
        wheel(level, north.west(2), Direction.NORTH);
        wheel(level, south.east(2), Direction.SOUTH);
        var collector = throat.below();
        place(level, collector, AllBlocks.DEPOT.getDefaultState());
        return collector;
    }

    BlockPos installDeployer(BlockPos burner) {
        return installDeployer(burner, Direction.SOUTH);
    }

    BlockPos installDeployer(BlockPos burner, Direction side) {
        var pos = burner.relative(side, 2);
        boolean driveX = side.getAxis() == Direction.Axis.Z;
        var shaft = driveX ? Direction.EAST : Direction.SOUTH;
        place(level, pos, AllBlocks.DEPLOYER.getDefaultState().setValue(BlockStateProperties.FACING, side.getOpposite())
            .setValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, driveX));
        place(level, pos.relative(shaft), axis(AllBlocks.SHAFT.getDefaultState(), shaft.getAxis()));
        wheel(level, pos.relative(shaft, 2), driveX ? Direction.SOUTH : Direction.EAST);
        return pos;
    }

    void routeFuel(BlockPos destination) {
        for (var pos : fuelRoute) {
            var inv = items(level, pos, Direction.UP);
            for (int i = 0; i < inv.getSlots(); i++)
                check(inv.getStackInSlot(i).isEmpty(), "Cake route still has a load at " + pos);
            level.removeBlock(pos, false);
        }
        fuelRoute.clear();
        var pos = cakeDepot.below(3);
        fuelHopper(pos, Direction.SOUTH);
        pos = pos.south();
        while (pos.getX() < origin.getX() + 26) {
            fuelHopper(pos, Direction.EAST);
            pos = pos.east();
        }
        var end = destination.above();
        while (!pos.equals(end)) {
            Direction face = pos.getZ() != end.getZ() ? (pos.getZ() < end.getZ() ? Direction.SOUTH : Direction.NORTH)
                : pos.getX() != end.getX() ? (pos.getX() < end.getX() ? Direction.EAST : Direction.WEST) : Direction.DOWN;
            fuelHopper(pos, face);
            pos = pos.relative(face);
        }
        fuelHopper(end, Direction.DOWN);
    }

    private void fuelHopper(BlockPos pos, Direction face) {
        check(level.getBlockState(pos).isAir(), "The cake route is obstructed at " + pos + " (" + level.getBlockState(pos) + ")");
        hopper(pos, face);
        fuelRoute.add(pos);
    }

    void chargeCake() {
        if (level.getFluidState(cakeLava).isEmpty()
            && fluids(level, cakeSpout, Direction.NORTH).getFluidInTank(0).getAmount() < 250)
            place(level, cakeLava, Blocks.LAVA.defaultBlockState());
        if (total(items(level, cakePress.below(2), Direction.UP), AllItems.CINDER_FLOUR.get()) == 0) {
            raw(cakeCrusher, Items.NETHERRACK, 1);
            measurements.merge("cake_netherrack", 1, (a,b) -> (int) a + (int) b);
        }
        raw(cakeInput, Items.EGG, 1);
        raw(cakeInput, Items.SUGAR, 1);
        measurements.merge("cake_charges", 1, (a,b) -> (int) a + (int) b);
    }

    void furnace(BlockPos pos) {
        place(level, pos, Blocks.FURNACE.defaultBlockState());
        hopper(pos.above(), Direction.DOWN);
        hopper(pos.east(), Direction.WEST);
    }

    BlockPos basinOut(BlockPos basin) {
        var depot = basin.east().below();
        place(level, depot, AllBlocks.DEPOT.getDefaultState());
        return depot;
    }

    void hopper(BlockPos at, Direction face) {
        place(level, at, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, face));
    }

    /** A physical gravity conveyor ending in the last down-facing hopper. */
    void hopperPath(BlockPos start, BlockPos end) {
        var at = start;
        check(start.getY() >= end.getY(), "Hopper cannot climb");
        while (at.getX() != end.getX()) {
            Direction face = at.getX() < end.getX() ? Direction.EAST : Direction.WEST;
            hopper(at, face);
            at = at.relative(face);
        }
        while (at.getZ() != end.getZ()) {
            Direction face = at.getZ() < end.getZ() ? Direction.SOUTH : Direction.NORTH;
            hopper(at, face);
            at = at.relative(face);
        }
        while (at.getY() > end.getY()) {
            hopper(at, Direction.DOWN);
            at = at.below();
        }
        hopper(at, Direction.DOWN);
    }

    record OutputGate(BlockPos depot, BlockPos input, BlockPos output) { }

    OutputGate pressExit(BlockPos depot, BlockPos inputHopper) {
        place(level, depot.below(2), Blocks.CHEST.defaultBlockState());
        var exit = depot.below();
        hopper(exit, Direction.DOWN);
        var output = exit.east();
        var input = inputHopper.relative(inputHopper.getX() == depot.getX() ? Direction.EAST : Direction.WEST);
        lever(output);
        lever(input);
        setClosed(output, true);
        return new OutputGate(depot, input, output);
    }

    private void lever(BlockPos lever) {
        place(level, lever.below(), Blocks.STONE_BRICKS.defaultBlockState());
        place(level, lever, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR));
    }

    void release(OutputGate gate, Item product) {
        boolean ready = total(items(level, gate.depot(), Direction.UP), product) > 0
            || total(items(level, gate.depot().below(), Direction.UP), product) > 0;
        setClosed(gate.input(), ready);
        setClosed(gate.output(), !ready);
    }

    void setClosed(BlockPos lever, boolean closed) {
        var state = level.getBlockState(lever);
        if (state.getValue(LeverBlock.POWERED) == closed) return;
        var player = FakePlayerFactory.getMinecraft(level);
        state.useWithoutItem(level, player, hit(lever, Direction.UP, Vec3.atCenterOf(lever)));
    }

    void raw(BlockPos hopper, Item raw, int count) {
        drop(level, hopper.above(), new ItemStack(raw, count));
    }

    void crushIron() { raw(cakeCrusher, Items.RAW_IRON, 9); }

    private void liquidLine(BlockPos source, BlockPos target, BlockPos pump) {
        var straight = AllBlocks.FLUID_PIPE.getDefaultState().setValue(FluidPipeBlock.NORTH, true).setValue(FluidPipeBlock.SOUTH, true);
        for (BlockPos pos = source.south(); !pos.equals(target); pos = pos.south()) {
            if (pos.equals(pump)) place(level, pos, AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(BlockStateProperties.FACING, Direction.SOUTH));
            else level.setBlock(pos, straight, 2);
        }
        var drive = pump.above();
        place(level, drive, axis(AllBlocks.COGWHEEL.getDefaultState(), Direction.Axis.Z));
        place(level, drive.north(), axis(AllBlocks.SHAFT.getDefaultState(), Direction.Axis.Z));
        wheel(level, drive.north(2), Direction.EAST);
    }
}
