package net.createplant.gametest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlock;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.createplant.CreatePlant;
import net.createplant.block.ReactorCapBlock;
import net.createplant.block.ReactorControlConsoleBlock;
import net.createplant.block.entity.FuelChannelBlockEntity;
import net.createplant.block.entity.RodCapBlockEntity;
import net.createplant.block.entity.ControlRodLinkBlockEntity;
import net.createplant.block.entity.ControlRodControllerBlockEntity;
import net.createplant.block.entity.ReactorControlConsoleBlockEntity;
import net.createplant.compat.display.MachineReadings;
import net.createplant.nuclear.PlugPhysicsSimulator;
import net.createplant.nuclear.Radiation;
import net.createplant.nuclear.ReactorCore;
import net.createplant.nuclear.ReactorConsoleButton;
import net.createplant.nuclear.ReactorConsoleStructure;
import net.createplant.nuclear.ReactorCoreStructure;
import net.createplant.nuclear.ReactorPhysics;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CEDisplays;
import net.createplant.registry.CENuclear;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public class RbmkGameTests {
    private static void core(GameTestHelper h, int x0, int y0, int z0, int size, boolean circular) {
        for (int x = 0; x < size; x++) for (int z = 0; z < size; z++) {
            if (circular && !ReactorCoreStructure.inCircle(x, z, size)) continue;
            for (int y = 0; y < ReactorPhysics.CORE_HEIGHT; y++)
                h.setBlock(new BlockPos(x0 + x, y0 + y, z0 + z), CENuclear.FUEL_CHANNEL.get());
        }
    }

    private static ReactorCoreStructure.Result form(GameTestHelper h, BlockPos start) {
        var result = ReactorCoreStructure.scan(h.getLevel(), h.absolutePos(start));
        h.assertTrue(result.formed(), "Core forms: " + result.reason() + " at " + result.fault());
        ((FuelChannelBlockEntity) h.getLevel().getBlockEntity(result.controller())).adopt(result, null);
        return result;
    }

    private static void upperTubes(GameTestHelper h, BlockPos bottom) {
        for (int y = ReactorPhysics.CORE_HEIGHT; y < ReactorPhysics.FUEL_ASSEMBLY_LENGTH; y++)
            h.setBlock(bottom.above(y), CENuclear.FUEL_CHANNEL_TUBE.get());
    }

    /** A formed desk with its display board in the usual place behind it, linked. Returns the board master, relative. */
    public static BlockPos controlRoom(GameTestHelper h, BlockPos master, Direction facing) {
        BlockPos board = ReactorConsoleStructure.boardBehind(master, facing);
        ReactorConsoleStructure.DESK.place(h::setBlock, master, facing);
        ReactorConsoleStructure.DISPLAY.place(h::setBlock, board, facing);
        h.assertTrue(ReactorConsoleStructure.DESK.tryForm(h.getLevel(), h.absolutePos(master))
                && ReactorConsoleStructure.DISPLAY.tryForm(h.getLevel(), h.absolutePos(board)),
            "Identical desk units and display units form their stations");
        h.assertTrue(((ReactorControlConsoleBlockEntity) h.getBlockEntity(master)).linkDisplay(h.absolutePos(board)),
            "The desk links to its display board");
        return board;
    }

    private static DisplayLinkBlockEntity displayLink(GameTestHelper h, BlockPos pos, Direction facing, BlockPos target, String metric) {
        h.setBlock(pos, AllBlocks.DISPLAY_LINK.getDefaultState().setValue(DisplayLinkBlock.FACING, facing));
        var link = (DisplayLinkBlockEntity) h.getBlockEntity(pos);
        link.activeSource = CEDisplays.MACHINE.get();
        link.activeTarget = CEDisplays.REACTOR_CONSOLE.get();
        link.getSourceConfig().putInt("Metric", MachineReadings.KEYS.indexOf(metric));
        link.target(target);
        link.updateGatheredData();
        return link;
    }

    @GameTest(template = "test_rbmk", timeoutTicks = 20)
    public static void protectiveClothingDoesNotShieldGamma(GameTestHelper h) {
        var player = FakePlayerFactory.getMinecraft(h.getLevel());
        double bare = dose(player);
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(CENuclear.PROTECTIVE_COAT.get()));
        double coat = dose(player);
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(CENuclear.PROTECTIVE_CAP.get()));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(CENuclear.PROTECTIVE_TROUSERS.get()));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(CENuclear.PROTECTIVE_SHOE_COVERS.get()));
        double suit = dose(player);
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) player.setItemSlot(slot, ItemStack.EMPTY);
        player.setData(CENuclear.DOSE, 0d);
        h.assertTrue(Math.abs(bare - 1) < 1e-9, "Without clothing the whole dose is taken: " + bare);
        h.assertTrue(Math.abs(coat - 1) < 1e-9, "The coat does not shield gamma: " + coat);
        h.assertTrue(Math.abs(suit - 1) < 1e-9, "The whole set does not shield gamma: " + suit);
        h.succeed();
    }

    private static double dose(ServerPlayer player) {
        player.setData(CENuclear.DOSE, 0d);
        Radiation.expose(player, 1);
        return player.getData(CENuclear.DOSE);
    }

    @GameTest(template = "test_rbmk", timeoutTicks = 20)
    public static void controlRodLinkOnlySendsWithAnAdjacentController(GameTestHelper h) {
        BlockPos linkPos = new BlockPos(2, 2, 2), controllerPos = linkPos.east();
        h.setBlock(linkPos, CENuclear.ROD_LINK.get());
        var link = (ControlRodLinkBlockEntity) h.getBlockEntity(linkPos);
        link.lazyTick();
        h.assertTrue(!link.sendingControl(), "A free control-rod link must not transmit an implicit zero-depth command");

        h.setBlock(controllerPos, CENuclear.ROD_CONTROLLER.get());
        ((ControlRodControllerBlockEntity) h.getBlockEntity(controllerPos)).setSetting(37);
        link.lazyTick();
        h.assertTrue(link.sendingControl() && link.setting() == 37,
            "An adjacent 100-step controller activates the link with its selected depth");

        h.setBlock(controllerPos, Blocks.AIR);
        link.lazyTick();
        h.assertTrue(!link.sendingControl(), "Removing the controller stops the command instead of transmitting zero");
        h.succeed();
    }

    @GameTest(template = "test_rbmk")
    public static void fullSizeCoreHasExactRequestedChannelCounts(GameTestHelper h) {
        int size = ReactorCoreStructure.MAX_WIDTH;
        var columns = new java.util.ArrayList<BlockPos>();
        for (int x = 0; x < size; x++) for (int z = 0; z < size; z++)
            if (ReactorCoreStructure.inCircle(x, z, size)) columns.add(new BlockPos(2 + x, 2, 2 + z));
        var box = new net.minecraft.world.level.levelgen.structure.BoundingBox(2, 2, 2, 2 + size - 1,
            2 + ReactorPhysics.CORE_HEIGHT - 1, 2 + size - 1);
        var result = new ReactorCoreStructure.Result(box, true, java.util.List.copyOf(columns), java.util.List.of(),
            columns.getFirst(), columns.getFirst(), "ready");
        int active = 0, controls = 0;
        for (BlockPos bottom : result.fuel()) {
            int mask = ReactorCoreStructure.channelMask(result, bottom);
            active += Integer.bitCount(mask);
            controls += Integer.bitCount(mask & ReactorCoreStructure.defaultControlMask(result, bottom));
        }
        h.assertTrue(result.circular() && result.width() == 13 && result.height() == 7, "Active core is a 13 m by 7 m cylinder");
        h.assertTrue(result.fuel().size() == 137, "The fine channel grid is grouped into 137 physical bundle columns");
        h.assertTrue(ReactorCoreStructure.OUTER_DIAMETER == 17, "Two-block shield gives a 17 m outside diameter");
        h.assertTrue(active == 448 && controls == 51 && active - controls == 397,
            "Fine grid is exactly 397 fuel plus 51 control channels: " + active + "/" + controls);
        h.assertTrue(ReactorPhysics.UPPER_SHIELD_HEIGHT == 2 && ReactorPhysics.SUSPENSION_HEIGHT == 12
            && ReactorPhysics.LOWER_SHIELD_HEIGHT == 2 && ReactorPhysics.LOWER_SERVICE_HEIGHT == 5
            && ReactorPhysics.FUEL_ASSEMBLY_LENGTH == 21, "Vertical assembly is 12 + 2 + 7 + 2 + 5 metres");
        h.succeed();
    }

    @GameTest(template = "test_rbmk")
    public static void recommendedCoreDiametersFormWithStableChannelCounts(GameTestHelper h) {
        int[][] expected = {{5, 21, 52, 8}, {7, 37, 112, 13}, {9, 69, 208, 26}, {11, 97, 316, 37}, {13, 137, 448, 51}};
        for (int i = 0; i < expected.length; i++) {
            int size = expected[i][0], y = 2 + i * (ReactorPhysics.CORE_HEIGHT + 1);
            core(h, 2, y, 2, size, true);
            var result = form(h, new BlockPos(2 + size / 2, y, 2 + size / 2));
            ReactorCore reactor = ((FuelChannelBlockEntity) h.getLevel().getBlockEntity(result.controller())).core();
            h.assertTrue(result.circular() && result.width() == size, size + " x " + size + " circular core forms");
            h.assertTrue(result.fuel().size() == expected[i][1], size + " m core has " + expected[i][1] + " physical bundles");
            h.assertTrue(reactor.fuelChannels() + reactor.controlChannels() == expected[i][2]
                && reactor.controlChannels() == expected[i][3], size + " m channel layout is stable: "
                    + reactor.fuelChannels() + "/" + reactor.controlChannels());
        }
        h.succeed();
    }

    @GameTest(template = "test_rbmk")
    public static void minimumCircularCoreShowsEightMirrorSymmetricInteriorRodsOnTheBoard(GameTestHelper h) {
        BlockPos base = new BlockPos(2, 2, 2);
        core(h, base.getX(), base.getY(), base.getZ(), 5, true);
        var result = form(h, base.east(2).south(2));
        Set<Integer> active = new HashSet<>(), controls = new HashSet<>();
        for (BlockPos bottom : result.fuel()) {
            var channel = (FuelChannelBlockEntity) h.getLevel().getBlockEntity(bottom);
            for (int slot = 0; slot < FuelChannelBlockEntity.RODS; slot++) if (channel.active(slot)) {
                int index = fineIndex(result, bottom, slot);
                active.add(index);
                if (channel.control(slot)) {
                    controls.add(index);
                    h.assertTrue(channel.installControlRod(slot), "The physical rod can be loaded into its preset slot");
                } else h.assertTrue(channel.topItems().insertItem(slot, CENuclear.FUEL_ROD.toStack(), false).isEmpty(),
                    "Every other active channel accepts fuel");
            }
        }
        h.assertTrue(active.size() == 52 && controls.size() == 8, "The circular minimum has 44 fuel slots and 8 control slots");
        for (int index : controls) {
            int x = index % 10, z = index / 10;
            h.assertTrue(active.containsAll(List.of(index-1, index+1, index-10, index+10)), "Each rod lies inside the active perimeter: " + index);
            h.assertTrue(controls.contains(10*z + 9-x) && controls.contains(10*(9-z) + x),
                "Each position has both left-right and top-bottom mirror partners: " + index);
            for (int other : controls) if (other != index)
                h.assertTrue(Math.abs(x - other % 10) + Math.abs(z - other / 10) >= 2, "Control rods never share an edge");
        }
        ((FuelChannelBlockEntity) h.getLevel().getBlockEntity(result.controller())).adopt(result, null);
        BlockPos desk = new BlockPos(13, 2, 4);
        controlRoom(h, desk, Direction.NORTH);
        var console = (ReactorControlConsoleBlockEntity) h.getBlockEntity(desk);
        BlockPos source = base.offset(2, 0, 2);
        upperTubes(h, source);
        BlockPos cap = source.above(ReactorPhysics.FUEL_ASSEMBLY_LENGTH);
        h.setBlock(cap, CENuclear.TOP_CAP.get());
        var link = displayLink(h, cap.above(), Direction.UP, h.absolutePos(new BlockPos(11, 6, 5)), "reactor_coolant_temperature");
        h.assertTrue(link.getSourcePosition().equals(h.absolutePos(cap)) && MachineReadings.reactorController(h.getBlockEntity(cap)) != null,
            "The display link has the centre cap as its formed source: " + link.getSourcePosition() + " vs " + h.absolutePos(cap));
        console.stationChanged();
        Set<Integer> displayed = new HashSet<>();
        for (int point : console.rodMap()) displayed.add(((255 - (point >>> 8 & 255)) * 10 / 256) * 10 + (point & 255) * 10 / 256);
        h.assertTrue(console.linked() && displayed.equals(controls) && console.rodMap().length == 8 && console.fuelMap().length == 44,
            "The physical display samples the new layout: " + console.linkStatus() + ", " + displayed + " vs " + controls
                + ", fuel lamps=" + console.fuelMap().length);
        h.succeed();
    }

    @GameTest(template = "test_rbmk")
    public static void minimumCircularLegacyLayoutRefreshesOnlyWhenEmptyAndUnmodified(GameTestHelper h) {
        BlockPos base = new BlockPos(3, 3, 3);
        core(h, base.getX(), base.getY(), base.getZ(), 5, true);
        var result = form(h, base.east(2).south(2));
        var lead = (FuelChannelBlockEntity) h.getLevel().getBlockEntity(result.controller());
        var moved = (FuelChannelBlockEntity) h.getBlockEntity(base.offset(1, 0, 1));
        var original = Set.of(14, 41, 44, 45, 47, 54, 74, 77);
        var previous = Set.of(24, 26, 32, 47, 52, 67, 73, 75);
        for (var oldControls : List.of(original, previous)) {
            restoreLegacyMinimumLayout(h, result, oldControls);
            h.assertTrue(!moved.control(1), "The old empty layout uses this new preset position for fuel");
            lead.adopt(result, null);
            h.assertTrue(moved.control(1) && lead.core().controlChannels() == 8, "Both earlier empty presets update as a whole");
        }

        restoreLegacyMinimumLayout(h, result, previous);
        h.assertTrue(moved.topItems().insertItem(1, CENuclear.FUEL_ROD.toStack(), false).isEmpty(), "Prepare an occupied old-layout slot");
        lead.adopt(result, null);
        h.assertTrue(!moved.control(1) && moved.topItems().getStackInSlot(1).is(CENuclear.FUEL_ROD.get()) && lead.core().controlChannels() == 8,
            "An occupied old layout keeps every saved position and item");

        restoreLegacyMinimumLayout(h, result, original);
        h.assertTrue(moved.selectChannel(0, FuelChannelBlockEntity.CONTROL), "Prepare a player-edited but unoccupied layout");
        lead.adopt(result, null);
        h.assertTrue(moved.control(0) && !moved.control(1) && lead.core().controlChannels() == 9, "An empty custom layout retains the player's choice");
        h.succeed();
    }

    private static int fineIndex(ReactorCoreStructure.Result result, BlockPos bottom, int slot) {
        int x = 2*(bottom.getX() - result.box().minX()) + slot % 2;
        int z = 2*(bottom.getZ() - result.box().minZ()) + slot / 2;
        return z*10 + x;
    }

    private static void restoreLegacyMinimumLayout(GameTestHelper h, ReactorCoreStructure.Result result, Set<Integer> oldControls) {
        for (BlockPos bottom : result.fuel()) {
            var channel = (FuelChannelBlockEntity) h.getLevel().getBlockEntity(bottom);
            var saved = channel.saveWithoutMetadata(h.getLevel().registryAccess());
            byte[] types = new byte[FuelChannelBlockEntity.RODS];
            for (int slot = 0; slot < types.length; slot++)
                if (oldControls.contains(fineIndex(result, bottom, slot))) types[slot] = FuelChannelBlockEntity.CONTROL;
            saved.putByteArray("ChannelTypes", types);
            saved.putByteArray("Holes", new byte[types.length]);
            channel.loadWithComponents(saved, h.getLevel().registryAccess());
        }
    }

    @GameTest(template = "test_rbmk")
    public static void coresSmallerThanFiveByFiveAreRejected(GameTestHelper h) {
        core(h, 2, 2, 2, 3, false);
        var result = ReactorCoreStructure.scan(h.getLevel(), h.absolutePos(new BlockPos(3, 2, 3)));
        h.assertTrue(!result.formed() && result.reason().equals("size"), "A 3 x 3 core is below the five-block minimum");
        h.succeed();
    }

    @GameTest(template = "test_rbmk", timeoutTicks = 40)
    public static void directlyPipedFeedBundleFillsEveryChannelInOneTransfer(GameTestHelper h) {
        int size = ReactorCoreStructure.MAX_WIDTH;
        BlockPos bottom = new BlockPos(2, 9, 2);
        core(h, bottom.getX(), bottom.getY(), bottom.getZ(), size, false);
        for (int x = 0; x < size; x++) for (int z = 0; z < size; z++)
            for (int y = 1; y <= ReactorPhysics.LOWER_STRUCTURE_HEIGHT; y++)
                h.setBlock(bottom.offset(x, -y, z), CENuclear.FEED_BUNDLE.get());
        BlockPos feed = bottom.offset(size / 2, -ReactorPhysics.LOWER_STRUCTURE_HEIGHT, size / 2);

        var result = form(h, bottom);
        var lead = (FuelChannelBlockEntity) h.getLevel().getBlockEntity(result.controller());
        lead.core().tick();
        IFluidHandler water = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(feed), Direction.DOWN);
        int capacity = size * size * FuelChannelBlockEntity.COOLANT_PER_CELL * ReactorPhysics.CORE_HEIGHT;
        h.assertTrue(water != null && water.getTankCapacity(0) == capacity, "One directly piped bundle exposes all 13 x 13 channels through 1,183 bundle blocks");
        h.assertTrue(water.fill(new FluidStack(Fluids.WATER, capacity), IFluidHandler.FluidAction.SIMULATE) == capacity,
            "A simulated full-core fill accepts the exact capacity");
        h.assertTrue(water.fill(new FluidStack(Fluids.WATER, capacity), IFluidHandler.FluidAction.EXECUTE) == capacity,
            "One transfer reaches every connected fuel column");
        int stored = 0;
        for (BlockPos pos : result.fuel()) stored += ((FuelChannelBlockEntity) h.getLevel().getBlockEntity(pos)).coolantAmount();
        h.assertTrue(stored == capacity, "The bottom feed distributes all coolant without skipping columns");
        h.assertTrue(com.simibubi.create.content.fluids.FluidPropagator.hasFluidCapability(h.getLevel(), h.absolutePos(feed), Direction.DOWN),
            "Create pipes only connect to blocks with a block entity, so the feed bundle must have one");
        h.succeed();
    }

    @GameTest(template = "test_rbmk", timeoutTicks = 40)
    public static void reactorOutletKeepsWaterAndSteamInOneWetStream(GameTestHelper h) {
        int size=ReactorCoreStructure.MIN_WIDTH;BlockPos bottom=new BlockPos(2,9,2);core(h,2,9,2,size,false);
        var result=form(h,bottom);var lead=(FuelChannelBlockEntity)h.getLevel().getBlockEntity(result.controller());lead.core().tick();
        var channel=lead;var lookup=h.getLevel().registryAccess();var tag=channel.saveWithoutMetadata(lookup);
        tag.putInt("Coolant",1000);tag.putDouble("Specific",net.createplant.nuclear.ReactorCoolant.CP*ReactorPhysics.SATURATION_KELVIN+net.createplant.nuclear.ReactorCoolant.LATENT/2);channel.loadWithComponents(tag,lookup);
        var outlet=lead.core().outlet();
        var steam=new FluidStack(net.createplant.registry.CEProcessFluids.HIGH_PRESSURE_STEAM.get(),1000);
        h.assertTrue(outlet.drain(steam,IFluidHandler.FluidAction.SIMULATE).getAmount()==500 && channel.coolantAmount()==1000,"Preview exposes only the steam fraction without mutation");
        int gas=outlet.drain(steam,IFluidHandler.FluidAction.EXECUTE).getAmount();
        int liquid=outlet.drain(new FluidStack(net.createplant.registry.CEProcessFluids.PRESSURISED_WATER.get(),1000),IFluidHandler.FluidAction.EXECUTE).getAmount();
        h.assertTrue(gas==500 && liquid==500 && channel.coolantAmount()==0,"Graded ports export both finite phases exactly once");h.succeed();
    }

    @GameTest(template = "test_rbmk", timeoutTicks = 20)
    public static void boilingTakesLatentHeatFromTheFuel(GameTestHelper h) {
        BlockPos pos = new BlockPos(4, 2, 4);
        h.setBlock(pos, CENuclear.FUEL_CHANNEL.get());
        var channel = (FuelChannelBlockEntity) h.getBlockEntity(pos);
        var initial = channel.saveWithoutMetadata(h.getLevel().registryAccess());
        double saturated = net.createplant.nuclear.ReactorCoolant.CP * ReactorPhysics.SATURATION_KELVIN;
        initial.putInt("Coolant", 1000);
        initial.putDouble("Specific", saturated);
        initial.putDouble("FuelKelvin", ReactorPhysics.NOMINAL_FUEL_KELVIN);
        initial.putInt("CoreHeight", ReactorPhysics.CORE_HEIGHT);
        channel.loadWithComponents(initial, h.getLevel().registryAccess());

        double fuelBefore = channel.fuelKelvin();
        channel.heat(0, ReactorPhysics.CORE_HEIGHT);
        var after = channel.saveWithoutMetadata(h.getLevel().registryAccess());
        double specificAfter = after.getDouble("Specific");
        double coolantGain = specificAfter - saturated;
        double fuelCapacity = ReactorPhysics.CELL_HEAT_CAPACITY * ReactorPhysics.CORE_HEIGHT;
        double fuelLoss = (fuelBefore - channel.fuelKelvin()) * fuelCapacity;

        h.assertTrue(coolantGain > 0 && coolantGain < net.createplant.nuclear.ReactorCoolant.LATENT,
            "Transferred heat enters the latent-heat interval: " + coolantGain);
        h.assertTrue(Math.abs(channel.coolantKelvin() - ReactorPhysics.SATURATION_KELVIN) < 1e-9,
            "Partly boiled channel water remains at 284 C instead of gaining sensible temperature");
        h.assertTrue(Math.abs(coolantGain - fuelLoss) < 1e-5,
            "Every joule used for evaporation is removed from the fuel: " + coolantGain + "/" + fuelLoss);
        h.succeed();
    }

    @GameTest(template = "test_rbmk", timeoutTicks = 80)
    public static void operatingCapAddressesAllFourSubdivisions(GameTestHelper h) {
        BlockPos bottom = new BlockPos(4, 2, 4);
        core(h, 2, 2, 2, ReactorCoreStructure.MIN_WIDTH, false);
        form(h, bottom);
        upperTubes(h, bottom);
        BlockPos capPos = bottom.above(ReactorPhysics.FUEL_ASSEMBLY_LENGTH);
        h.setBlock(capPos, CENuclear.TOP_CAP.get());
        var cap = (RodCapBlockEntity) h.getBlockEntity(capPos);
        var column = (FuelChannelBlockEntity) h.getBlockEntity(bottom);

        boolean[] addressed = new boolean[FuelChannelBlockEntity.RODS];
        for (int z = 0; z < ReactorPhysics.CHANNEL_GRID; z++) for (int x = 0; x < ReactorPhysics.CHANNEL_GRID; x++) {
            Vec3 point = new Vec3(capPos.getX() + (x + .5) / ReactorPhysics.CHANNEL_GRID, capPos.getY() + 1,
                capPos.getZ() + (z + .5) / ReactorPhysics.CHANNEL_GRID);
            int slot = ReactorCapBlock.slot(capPos, new BlockHitResult(point, Direction.UP, capPos, false));
            addressed[slot] = true;
        }
        for (boolean seen : addressed) h.assertTrue(seen, "Every 2 x 2 cap subdivision maps to a distinct channel");

        int fuelSlot = 1, controlSlot = 0;
        h.assertTrue(cap.installFuel(fuelSlot) && column.loaded(fuelSlot), "Fuel item loads the selected fine channel");
        h.assertTrue(cap.installControl(controlSlot) && column.controlInstalled(controlSlot), "Control item loads the selected fine channel");
        h.assertTrue(!cap.toggle(fuelSlot) && !cap.toggle(controlSlot), "Occupied positions cannot change service type");
        ItemStack removed = cap.removeControl(controlSlot);
        h.assertTrue(removed.is(CENuclear.CONTROL_ROD.get()) && cap.toggle(controlSlot) && !column.control(controlSlot),
            "A withdrawn control rod can be removed and its empty position changed to fuel");
        h.succeed();
    }

    @GameTest(template = "test_rbmk", timeoutTicks = 40)
    public static void capReliefBindsTemperatureFlowAndRodDepthPerSubdivision(GameTestHelper h) {
        BlockPos bottom = new BlockPos(4, 2, 4);
        for (int y = 0; y < ReactorPhysics.CORE_HEIGHT; y++) h.setBlock(bottom.above(y), CENuclear.FUEL_CHANNEL.get());
        upperTubes(h, bottom);
        BlockPos capPos = bottom.above(ReactorPhysics.FUEL_ASSEMBLY_LENGTH);
        h.setBlock(capPos, CENuclear.TOP_CAP.get());
        var cap = (RodCapBlockEntity) h.getBlockEntity(capPos);
        var column = (FuelChannelBlockEntity) h.getBlockEntity(bottom);
        h.assertTrue(cap.installControl(0), "One of the four cap positions accepts its own control rod");
        column.controlDepth(0, ReactorPhysics.ROD_TRAVEL);
        var hot = column.saveWithoutMetadata(h.getLevel().registryAccess());
        hot.putDouble("FuelKelvin", ReactorPhysics.CHANNEL_RUPTURE_KELVIN);
        column.loadWithComponents(hot, h.getLevel().registryAccess());
        cap.lazyTick();
        cap.tick();
        h.assertTrue(cap.plugColumn() == column, "The cap resolves its pressure data through the complete upper tube stack");
        h.assertTrue(cap.plugPressure(0) < 1 && cap.plugPressure(1) > 1,
            "Full local insertion seats one plug while its withdrawn hot neighbour crosses the lift threshold: "
                + cap.plugPressure(0) + "/" + cap.plugPressure(1));

        var boiling = column.saveWithoutMetadata(h.getLevel().registryAccess());
        boiling.putInt("Coolant", 3_000);
        boiling.putDouble("Specific", net.createplant.nuclear.ReactorCoolant.CP * ReactorPhysics.SATURATION_KELVIN
            + .2 * net.createplant.nuclear.ReactorCoolant.LATENT);
        boiling.putDouble("FuelKelvin", ReactorPhysics.NOMINAL_FUEL_KELVIN);
        column.loadWithComponents(boiling, h.getLevel().registryAccess());
        h.assertTrue(column.coolantKelvin() == ReactorPhysics.SATURATION_KELVIN && column.flowRate() == 0,
            "The fixture holds boiling water and steam with no measured coolant flow");
        cap.tick();
        for (int i = 0; i < ReactorPhysics.RODS_PER_BLOCK; i++)
            h.assertTrue(cap.plugPressure(i) == 0 && cap.plugOffset(i, 0) == 0 && cap.plugOffset(i, .5f) == 0,
                "Ordinary boiling keeps plug " + i + " exactly seated at server and render sample times");

        var simulator = new PlugPhysicsSimulator();
        double[] temperatures = {1800, 0, 0, 0}, flow = new double[4], insertion = new double[4];
        for (int tick = 0; tick < 200 && simulator.relief(0) == 0; tick++)
            simulator.tick(7, -3, tick, 1, temperatures, flow, insertion);
        double savedRelief = simulator.relief(0);
        var saved = new net.minecraft.nbt.CompoundTag();
        simulator.write(saved, false);
        var restored = new PlugPhysicsSimulator();
        restored.read(saved, false);
        h.assertTrue(savedRelief > 0 && Math.abs(restored.relief(0) - savedRelief) < 1e-12,
            "Temporary pressure relief survives a server save");
        var packet = new net.minecraft.nbt.CompoundTag();
        simulator.write(packet, true);
        var client = new PlugPhysicsSimulator();
        client.read(packet, true);
        h.assertTrue(Math.abs(client.pressure(0) - simulator.pressure(0)) < 1e-6,
            "The renderer receives the authoritative effective local pressure");
        h.succeed();
    }

    @GameTest(template = "test_rbmk", timeoutTicks = 20)
    public static void formerNinePositionChannelSaveReturnsRemovedRods(GameTestHelper h) {
        BlockPos pos = new BlockPos(4, 2, 4);
        h.setBlock(pos, CENuclear.FUEL_CHANNEL.get());
        var channel = (FuelChannelBlockEntity) h.getBlockEntity(pos);
        var saved = channel.saveWithoutMetadata(h.getLevel().registryAccess());
        saved.putByteArray("Holes", new byte[] {1, 1, 2, 2, 0, 1, 1, 2, 0});
        saved.putByteArray("ChannelTypes", new byte[] {0, 0, 0, 0, 1, 0, 0, 0, 1});
        saved.putByteArray("ControlRods", new byte[] {0, 0, 0, 0, 1, 0, 0, 0, 1});
        saved.putFloat("ControlWear4", .5f);
        saved.putInt("ActiveMask", 0x1ff);
        channel.loadWithComponents(saved, h.getLevel().registryAccess());

        h.assertTrue(channel.activeChannels() == 4 && channel.loaded(0) && channel.loaded(1) && channel.loaded(2)
            && channel.controlInstalled(3) && channel.fresh() == 2 && channel.spent() == 1,
            "The four old corner positions become the complete 2 x 2 channel grid");
        var migrated = channel.saveWithoutMetadata(h.getLevel().registryAccess());
        h.assertTrue(migrated.getByteArray("Holes").length == 4 && migrated.getInt("ReducedGridFresh") == 2
            && migrated.getInt("ReducedGridSpent") == 2 && migrated.getInt("ReducedGridSpentControl") == 1,
            "The five retired positions remain queued for lossless return until the channel ticks");

        h.runAfterDelay(1, () -> {
            var drops = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(h.absolutePos(pos)).inflate(1));
            int fresh = drops.stream().mapToInt(e -> e.getItem().is(CENuclear.FUEL_ROD.get()) ? e.getItem().getCount() : 0).sum();
            int spent = drops.stream().mapToInt(e -> e.getItem().is(CENuclear.SPENT_FUEL_ROD.get()) ? e.getItem().getCount() : 0).sum();
            int controls = drops.stream().mapToInt(e -> e.getItem().is(CENuclear.SPENT_CONTROL_ROD.get()) ? e.getItem().getCount() : 0).sum();
            h.assertTrue(fresh == 2 && spent == 2 && controls == 1,
                "Contents of all five retired positions are returned exactly once: " + fresh + "/" + spent + "/" + controls);
            h.succeed();
        });
    }

    @GameTest(template = "test_rbmk", timeoutTicks = 300)
    public static void integratedCapDriveMovesRodWithoutWinch(GameTestHelper h) {
        BlockPos bottom = new BlockPos(4, 2, 4);
        core(h, 2, 2, 2, ReactorCoreStructure.MIN_WIDTH, false);
        var result = form(h, bottom);
        upperTubes(h, bottom);
        BlockPos capPos = bottom.above(ReactorPhysics.FUEL_ASSEMBLY_LENGTH);
        h.setBlock(capPos, CENuclear.TOP_CAP.get());
        var cap = (RodCapBlockEntity) h.getBlockEntity(capPos);
        int slot = 0;
        h.assertTrue(cap.installControl(slot), "Control rod installs through its operating-floor cap");

        BlockPos drive = new BlockPos(1, 4, 4), motor = drive.west();
        h.setBlock(drive, CENuclear.SHIELD_DRIVE.get().defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
        ((FuelChannelBlockEntity) h.getLevel().getBlockEntity(result.controller())).adopt(
            ReactorCoreStructure.scan(h.getLevel(), h.absolutePos(bottom)), null);
        h.setBlock(capPos.above(), Blocks.REDSTONE_BLOCK);
        cap.neighbourChanged();

        h.startSequence()
            .thenExecute(() -> ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(128))
            .thenWaitUntil(() -> {
                ReactorCore core = ((FuelChannelBlockEntity) h.getLevel().getBlockEntity(result.controller())).core();
                h.assertTrue(core != null && core.installedControlRods() == 1, "The cap-integrated rod belongs to the formed core");
                h.assertTrue(core.controlDepth(h.absolutePos(bottom), slot) > ReactorPhysics.ROD_TRAVEL - 1d / 512,
                    "Analogue power drives the integrated rod through its full seven-metre travel");
            })
            .thenExecute(() -> {
                h.assertTrue(!h.getLevel().getBlockState(h.absolutePos(capPos.below())).is(CENuclear.TOP_CAP.get()),
                    "No separate winch layer exists below the cap");
            })
            .thenSucceed();
    }

    @GameTest(template = "test_rbmk", timeoutTicks = 40)
    public static void displayLinksFromTwoReactorsRefuseToControlOneRoom(GameTestHelper h) {
        BlockPos first = new BlockPos(2, 2, 2), second = new BlockPos(10, 2, 2);
        core(h, first.getX(), first.getY(), first.getZ(), ReactorCoreStructure.MIN_WIDTH, false);
        core(h, second.getX(), second.getY(), second.getZ(), ReactorCoreStructure.MIN_WIDTH, false);
        form(h, first);form(h, second);
        BlockPos consolePos = new BlockPos(11, 2, 14);
        controlRoom(h, consolePos, Direction.NORTH);
        var console = (ReactorControlConsoleBlockEntity) h.getBlockEntity(consolePos);
        displayLink(h, first.below(), Direction.DOWN, console.getBlockPos(), "reactor_power");
        displayLink(h, second.below(), Direction.DOWN, console.getBlockPos(), "reactor_power");
        console.stationChanged();
        h.assertTrue(!console.linked() && console.linkStatus().equals("ambiguous"),
            "Standard Display Links from two formed reactors are rejected instead of selecting one silently");
        h.succeed();
    }

    @GameTest(template = "test_rbmk", timeoutTicks = 40)
    public static void malformedCoreIsRejected(GameTestHelper h) {
        core(h, 2, 2, 2, ReactorCoreStructure.MIN_WIDTH, false);
        h.setBlock(new BlockPos(4, 4, 4), Blocks.AIR);
        var broken = ReactorCoreStructure.scan(h.getLevel(), h.absolutePos(new BlockPos(2, 2, 2)));
        h.assertTrue(!broken.formed() && broken.reason().equals("column"), "A gap inside a seven-metre column is rejected");
        h.succeed();
    }

    @GameTest(template = "test_rbmk", timeoutTicks = 80)
    public static void physicalDeskControlsOneRodAndAz5OverridesEveryRod(GameTestHelper h) {
        BlockPos bottom = new BlockPos(4, 2, 4);
        core(h, 2, 2, 2, ReactorCoreStructure.MIN_WIDTH, false);
        var result = form(h, bottom);
        upperTubes(h, bottom);
        BlockPos capPos = bottom.above(ReactorPhysics.FUEL_ASSEMBLY_LENGTH);
        h.setBlock(capPos, CENuclear.TOP_CAP.get());
        var cap = (RodCapBlockEntity) h.getBlockEntity(capPos);
        h.assertTrue(cap.installControl(0) && cap.installControl(1) && cap.installFuel(2),
            "Two independently addressable rods and one fuel position install in one 2 x 2 bundle");
        var lead = (FuelChannelBlockEntity) h.getLevel().getBlockEntity(result.controller());
        lead.core().tick();
        ReactorCore reactor = lead.core();
        h.assertTrue(reactor.controlRodCount() == 2, "The formed core indexes both physical control rods separately");
        var tooltip = new java.util.ArrayList<net.minecraft.network.chat.Component>();
        lead.addToGoggleTooltip(tooltip, false);
        var temperature = (net.minecraft.network.chat.contents.TranslatableContents) tooltip.getLast().getContents();
        h.assertTrue(tooltip.size() == 4 && temperature.getKey().equals("reactor_core.create_refinery_n_reactors.fuel_temperature")
                && temperature.getArgs().length == 3,
            "The four-row reactor tooltip exposes fuel centre, requested rod-surface temperature and xenon");
        tooltip.clear();
        lead.addToGoggleTooltip(tooltip, true);
        h.assertTrue(tooltip.size() == 4
                && ((net.minecraft.network.chat.contents.TranslatableContents) tooltip.getLast().getContents()).getKey()
                    .equals("reactor_core.create_refinery_n_reactors.fuel"),
            "Shift replaces the temperature row with inventory without exceeding four rows");

        BlockPos consolePos = new BlockPos(13, 2, 4);
        var north = Direction.NORTH;
        controlRoom(h, consolePos, north);
        var console = (ReactorControlConsoleBlockEntity) h.getBlockEntity(consolePos);
        console.stationChanged();
        h.assertTrue(console.stationComplete() && !console.linked() && console.linkStatus().equals("missing"),
            "A complete room does not control a nearby reactor without an explicit standard Display Link");
        BlockPos mapTopRight = new BlockPos(11, 6, 5);
        var displayLink = displayLink(h, capPos.above(), Direction.UP, h.absolutePos(mapTopRight), "reactor_coolant_temperature");
        console.stationChanged();
        h.assertTrue(console.linked(), "A standard Display Link on the reactor can target any control-room panel");
        h.assertTrue(console.fuelMap().length == 1 && console.bundles() == 1,
            "The map carries the loaded fine fuel position and the station counts its coolant bundle");
        h.assertTrue(MachineReadings.owner(cap) == lead && MachineReadings.read(cap, "reactor_coolant_temperature") != null,
            "The top cap is a direct reactor information source for Create's Display Link");
        var displayText = displayLink.activeSource.provideText(new DisplayLinkContext(h.getLevel(), displayLink), new DisplayTargetStats(1, 20, null));
        h.assertTrue(displayText.size() == 1 && !displayText.getFirst().getString().equals("--"),
            "Create's standard Display Link exports a selected live reactor measurement directly from the reactor");
        for (String metric :
            List.of("reactor_power", "reactor_reactivity", "reactor_void", "reactor_fuel_temperature", "reactor_coolant_temperature",
                "reactor_xenon", "reactor_rods", "reactor_average_fuel_temperature", "reactor_instability", "reactor_hazard",
                "reactor_radiation_rate", "reactor_fuel_channels", "reactor_control_rods", "reactor_scram", "reactor_graphite_temperature",
                "reactor_channel_flow", "reactor_channel_outlet_temperature", "reactor_surface_temperature"))
            h.assertTrue(MachineReadings.read(cap, metric) != null, "The reactor source exposes " + metric);
        h.assertTrue(h.getBlockState(mapTopRight).getValue(net.createplant.block.ReactorConsoleDisplayBlock.SECTION) == 3 * 7 + 3,
            "The formed 4 x 4 map receives continuous tile coordinates");
        for (int row = 0; row < ReactorConsoleStructure.DISPLAY.height; row++)
            for (int column = 0; column < ReactorConsoleStructure.DISPLAY.width; column++) {
                BlockPos panelPos = new BlockPos(14 - column, 3 + row, 5);
                h.assertTrue(h.absolutePos(ReactorConsoleStructure.boardBehind(consolePos, north))
                        .equals(net.createplant.block.entity.ReactorConsoleDisplayBlockEntity.masterAt(h.getLevel(), h.absolutePos(panelPos))),
                    "Every display unit routes physical interaction to the board master");
            }
        var board = (net.createplant.block.entity.ReactorConsoleDisplayBlockEntity) h.getBlockEntity(ReactorConsoleStructure.boardBehind(consolePos, north));
        h.assertTrue(board.linkedConsole() == console && console.walled(), "The board shows the desk it is linked to");

        var player = FakePlayerFactory.getMinecraft(h.getLevel());
        h.assertTrue(console.chartCount() > 0, "The unified station records both temperature traces");
        console.press(player, ReactorConsoleButton.MODE, true);
        console.press(player, ReactorConsoleButton.MODE, true);
        h.assertTrue(console.mode() == 2 && reactor.automaticTarget() >= 0, "The mode switch reaches AUTO and renews the same reactor control lease");
        BlockPos absoluteBottom = h.absolutePos(bottom);
        float[] hole = FuelChannelBlockEntity.HOLES[0];
        var box = reactor.box();
        double u = (absoluteBottom.getX() + .5 + hole[0] - box.minX()) / box.getXSpan();
        double v = 1 - (absoluteBottom.getZ() + .5 + hole[1] - box.minZ()) / box.getZSpan();
        console.selectRod(player, u, v);
        for (int i = 0; i < 21; i++) console.press(player, ReactorConsoleButton.ROD_DIAL, true);
        h.assertTrue(reactor.individualTarget(absoluteBottom, 0) == 100, "The dial stops at full insertion");
        for (int i = 0; i < 7; i++) console.press(player, ReactorConsoleButton.ROD_DIAL, false);
        player.setShiftKeyDown(true);
        console.press(player, ReactorConsoleButton.ROD_DIAL, false);
        console.press(player, ReactorConsoleButton.ROD_DIAL, false);
        player.setShiftKeyDown(false);
        console.lazyTick();
        h.assertTrue(reactor.individualTarget(absoluteBottom, 0) == 63 && reactor.individualTarget(absoluteBottom, 1) < 0,
            "The dial's 5% and sneaking 1% steps set only the selected rod; its neighbour has no individual override");

        console.press(player, ReactorConsoleButton.AZ5, true);
        h.assertTrue(!console.guardOpen() && !reactor.consoleScram(), "The safety cover interlocks the switch");
        ReactorConsoleGameTests.clickGuard(h, consolePos, north);
        h.assertTrue(console.guardOpen() && !reactor.consoleScram(), "Opening the cover alone leaves the reactor running");
        console.press(player, ReactorConsoleButton.AZ5, true);
        for (int i = 0; i < 105; i++) reactor.tick();
        h.assertTrue(reactor.scram() && reactor.consoleScram()
            && reactor.controlDepth(absoluteBottom, 0) > ReactorPhysics.ROD_TRAVEL - 1d / 512
            && reactor.controlDepth(absoluteBottom, 1) > ReactorPhysics.ROD_TRAVEL - 1d / 512,
            "Latched AZ-5 overrides both individual and cap targets and fully inserts every rod in five seconds; scram="
                + reactor.scram() + ", console=" + reactor.consoleScram() + ", depths="
                + reactor.controlDepth(absoluteBottom, 0) + "/" + reactor.controlDepth(absoluteBottom, 1));
        console.press(player, ReactorConsoleButton.AZ5_RESET, true);
        h.assertTrue(!reactor.scram() && !reactor.consoleScram() && console.guardOpen(),
            "RESET releases the desk latch while the cover stays open");
        ReactorConsoleGameTests.clickGuard(h, consolePos, north);
        h.assertTrue(!console.guardOpen() && !reactor.consoleScram(), "Closing the cover leaves the reactor running");
        ReactorConsoleGameTests.click(h, consolePos, north, ReactorConsoleButton.MODE);
        ReactorConsoleGameTests.click(h, consolePos, north, ReactorConsoleButton.MODE);
        ReactorConsoleGameTests.click(h, consolePos, north, ReactorConsoleButton.BANK_DIAL, false);
        h.assertTrue(console.mode() == 1 && console.bankTarget() == 95 && reactor.automaticTarget() == 95,
            "The physical mode switch and the left half of the bank dial command only the fallback target");
        h.assertTrue(reactor.individualTarget(absoluteBottom, 0) == 63, "A bank target does not erase individual rod settings");
        ReactorConsoleGameTests.click(h, consolePos, north, ReactorConsoleButton.RELEASE_ROD);
        h.assertTrue(reactor.individualTarget(absoluteBottom, 0) < 0 && console.selectedCommand() == 95
                && MachineReadings.read(console, "rod_target").value() == 95,
            "The physical individual LOCAL button releases the selected override and displays the active bank target");
        ReactorConsoleGameTests.click(h, consolePos, north, ReactorConsoleButton.MODE);
        ReactorConsoleGameTests.click(h, consolePos, north, ReactorConsoleButton.MODE);
        h.assertTrue(console.mode() == 0 && reactor.automaticTarget() < 0, "LOCAL releases the bank lease for cap and rod-link commands");
        ReactorConsoleGameTests.click(h, consolePos, north, ReactorConsoleButton.AZ5);
        h.assertTrue(console.guardOpen() && !reactor.consoleScram(), "A closed guard intercepts the first red-button click");
        ReactorConsoleGameTests.click(h, consolePos, north, ReactorConsoleButton.AZ5);
        h.assertTrue(reactor.consoleScram(), "A deliberate second red-button click latches scram");
        ReactorConsoleGameTests.clickGuard(h, consolePos, north);
        h.assertTrue(!console.guardOpen() && reactor.consoleScram(), "Closing the cover leaves AZ-5 latched");
        console.lazyTick();
        ReactorConsoleGameTests.click(h, consolePos, north, ReactorConsoleButton.ACKNOWLEDGE);
        h.assertTrue(console.acknowledged() && (console.alarms() & 1) != 0 && reactor.consoleScram(),
            "Acknowledging a scram alarm does not clear the reactor fault");
        ReactorConsoleGameTests.click(h, consolePos, north, ReactorConsoleButton.MODE);
        h.setBlock(consolePos.offset(ReactorConsoleStructure.DESK.offset(2, north)), Blocks.AIR);
        h.assertTrue(!console.assembled() && reactor.automaticTarget() < 0 && reactor.consoleScram(),
            "Disassembly immediately releases the bank lease and preserves the latched emergency shutdown");
        h.succeed();
    }
}
