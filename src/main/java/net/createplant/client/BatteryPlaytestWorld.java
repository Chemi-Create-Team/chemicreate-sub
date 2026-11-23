package net.createplant.client;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.google.gson.GsonBuilder;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import net.createplant.CreatePlant;
import net.createplant.block.ElectrodeHeadBlock;
import net.createplant.block.ElectrolysisCellBlock;
import net.createplant.block.ElectrolysisControllerBlock;
import net.createplant.block.WetProcessStructure;
import net.createplant.block.entity.BatteryTerminalBlockEntity;
import net.createplant.block.entity.ElectrolysisControllerBlockEntity;
import net.createplant.block.entity.ElectrolysisFurnaceBlockEntity;
import net.createplant.block.entity.LiddedVesselBlockEntity;
import net.createplant.block.entity.WetProcessBlockEntity;
import net.createplant.chemistry.ReactorModule;
import net.createplant.compat.electro.FurnaceBusbarBlock;
import net.createplant.gametest.ReactorGameTests;
import net.createplant.registry.CEBattery;
import net.createplant.registry.CEElectrolysis;
import net.createplant.registry.CEFurnace;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opt-in creative flat world for the lead-acid and lithium-ion banks, the acid-vessel burn, the calcite haunting and the lead, lithium and aluminium
 * lines. {@code -Dcreateplant.batteryPlaytest} builds it, wires the Electro Energetics circuits, fills the supplies, saves it and closes the game.
 * A station that cannot be built is recorded and skipped, so one wrong API leaves the rest of the world standing; the result file lists them.
 * The stations stand apart and each has its own creative fluid supplies and a chest of materials: the stages of a line are tried one by one, and
 * a player pipes one stage into the next. The product leaves a vessel through the ports the vessel exposes; the world does not pipe it away.
 * It has not been run by its author: the first run is the verification.
 */
public final class BatteryPlaytestWorld {
    private static final Logger LOG = LoggerFactory.getLogger(BatteryPlaytestWorld.class);
    private static final String WORLD = "createplant-battery-" + System.currentTimeMillis();
    private static final int G = -60, TIMEOUT = 600;
    private static final Map<String, Object> REPORT = new LinkedHashMap<>();
    private static final List<String> FAILED = new ArrayList<>();
    private static final List<Object[]> SUPPLIES = new ArrayList<>(), WIRES = new ArrayList<>();
    private static final List<Runnable> LATER = new ArrayList<>();
    private static final List<BlockPos> BANKS = new ArrayList<>();
    private static PlaytestPlant plant;
    private static volatile boolean done;
    private static volatile String failure;
    private static int phase, ticks;
    private static long serverTicks;

    private BatteryPlaytestWorld() { }

    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (phase == 0 && mc.screen != null && mc.getOverlay() == null && ++ticks > 80) {
                phase = 1;
                ticks = 0;
                mc.options.pauseOnLostFocus = false;
                mc.createWorldOpenFlows().createFreshLevel(WORLD,
                    new LevelSettings("Create-Plant battery, lead and aluminium test", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(42L, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
            } else if (phase == 1 && mc.level != null && mc.getOverlay() == null && ++ticks > 80) {
                phase = 2;
                var server = mc.getSingleplayerServer();
                server.execute(() -> {
                    try {
                        build(server.overworld(), server.getPlayerList().getPlayers().getFirst());
                        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, BatteryPlaytestWorld::serverTick);
                    } catch (RuntimeException error) {
                        failure = error.toString();
                    }
                });
            } else if (phase == 2) {
                if (failure != null) throw new IllegalStateException(failure);
                if (!done) return;
                phase = 3;
                ticks = 0;
                var server = mc.getSingleplayerServer();
                server.execute(() -> server.saveEverything(false, true, true));
            } else if (phase == 3 && ++ticks > 40) {
                phase = -2;
                REPORT.put("failedStations", FAILED);
                Files.writeString(mc.gameDirectory.toPath().resolve("battery-playtest-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(REPORT));
                if (FAILED.isEmpty()) LOG.info("BATTERY PLAYTEST BUILT: {}", REPORT);
                else LOG.error("BATTERY PLAYTEST BUILT WITH {} FAILED STATIONS: {}", FAILED.size(), REPORT);
                mc.stop();
            }
        } catch (Exception error) {
            phase = -1;
            LOG.error("BATTERY PLAYTEST FAILED: {}", REPORT, error);
            mc.stop();
        }
    }

    private interface Station { void build(ServerLevel level); }

    private static void station(ServerLevel level, String name, Station station) {
        try {
            station.build(level);
        } catch (RuntimeException | LinkageError error) {
            FAILED.add(name + ": " + error);
            LOG.error("BATTERY PLAYTEST station {} failed", name, error);
        }
    }

    private static void build(ServerLevel level, ServerPlayer player) {
        var server = level.getServer();
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.setDayTime(6000);
        plant = new PlaytestPlant(level, G);

        // Row 1 (z = 0): calcite, the three vessels for the burn, the two banks.
        station(level, "calcite", l -> calcite(l, 0, 0));
        station(level, "acid vessel", l -> burnVessel(l, new BlockPos(24, G, -1), "sulfuric_acid", "硫酸の槽：入ると死ぬまで燃える"));
        station(level, "alkali vessel", l -> burnVessel(l, new BlockPos(32, G, -1), "caustic_soda_solution", "苛性ソーダの槽：入ると死ぬまで燃える"));
        station(level, "water vessel", l -> burnVessel(l, new BlockPos(40, G, -1), "water", "水の槽：燃えない（対照）"));
        station(level, "lead-acid bank", l -> bank(l, new BlockPos(56, G, 0), true, 230));
        station(level, "lithium-ion bank", l -> bank(l, new BlockPos(76, G, 0), false, 330));

        // Row 2 (z = 40): lead, dry and wet.
        station(level, "lead roasting", l -> reactor(l, new BlockPos(0, G + 2, 40), "鉛 焙焼・焼結", true,
            List.of(feed(Direction.NORTH, "oxygen_gas")), List.of(stack("galena", 64))));
        station(level, "lead reduction", l -> reactor(l, new BlockPos(24, G + 2, 40), "鉛 還元溶錬", false,
            List.of(feed(Direction.NORTH, "carbon_monoxide")), List.of(stack("lead_sinter", 64))));
        station(level, "lead direct smelting", l -> reactor(l, new BlockPos(48, G + 2, 40), "鉛 直接製錬", true,
            List.of(feed(Direction.NORTH, "oxygen_gas")), List.of(stack("galena", 64))));
        station(level, "lead leaching", l -> reactor(l, new BlockPos(72, G + 2, 40), "鉛 浸出（湿式）", true,
            List.of(feed(Direction.NORTH, "hydrochloric_acid"), feed(Direction.EAST, "oxygen_gas")), List.of(stack("galena", 64))));

        // Row 3 (z = 80): clarifier, electrowinning cell, lithium.
        station(level, "lead clarifier", l -> clarifier(l, new BlockPos(3, G + 6, 83)));
        station(level, "lead electrowinning", l -> cell(l, new BlockPos(30, G + 4, 83), "lead_chloride_solution"));
        station(level, "lithium carbonate", l -> reactor(l, new BlockPos(56, G + 2, 80), "炭酸リチウム", false,
            List.of(feed(Direction.NORTH, "sulfuric_acid")), List.of(stack("spodumene", 64), stack("sodium_carbonate", 64))));

        // Row 4 (z = 120): aluminium, sulfate route, Bayer route and the molten-salt furnace.
        station(level, "aluminium sulfate", l -> reactor(l, new BlockPos(0, G + 2, 120), "Al ①硫酸塩 浸出", true,
            List.of(feed(Direction.NORTH, "sulfuric_acid")), List.of(stack("bauxite", 64))));
        station(level, "alumina calcining", l -> reactor(l, new BlockPos(24, G + 2, 120), "Al ②アルミナ焼成", false,
            List.of(feed(Direction.NORTH, "aluminium_sulfate_solution")), List.of()));
        station(level, "bayer leaching", l -> reactor(l, new BlockPos(48, G + 2, 120), "Al ①' バイヤー法 浸出", true,
            List.of(feed(Direction.NORTH, "caustic_soda_solution")), List.of(stack("bauxite", 64))));
        station(level, "bayer alumina", l -> reactor(l, new BlockPos(72, G + 2, 120), "Al ②' アルミナ析出", false,
            List.of(feed(Direction.NORTH, "sodium_aluminate_solution")), List.of()));
        station(level, "electrolysis furnace", l -> furnace(l, new BlockPos(104, G + 3, 124)));

        station(level, "pumps", l -> plant.driveAll());
        station(level, "index", l -> index(l));
        player.setItemSlot(EquipmentSlot.HEAD, AllItems.GOGGLES.asStack());
        player.getInventory().setItem(1, AllItems.WRENCH.asStack());
        BlockPos spawn = new BlockPos(4, G, 8);
        player.teleportTo(level, spawn.getX() + .5, spawn.getY(), spawn.getZ() + .5, 180, 10);
        player.setRespawnPosition(Level.OVERWORLD, spawn, 180, true, false);
        level.setDefaultSpawnPos(spawn, 180);
        REPORT.put("world", WORLD);
    }

    // ---- helpers -------------------------------------------------------------------------------------------------------------------

    private record Feed(Direction side, String fluid) { }

    private static Feed feed(Direction side, String fluid) { return new Feed(side, fluid); }

    private static ItemStack stack(String id, int count) {
        var item = BuiltInRegistries.ITEM.get(CreatePlant.location(id));
        if (item == Items.AIR) throw new IllegalStateException("No item " + id);
        return new ItemStack(item, count);
    }

    private static Fluid fluid(String id) {
        var key = id.contains(":") ? ResourceLocation.parse(id) : CreatePlant.location(id);
        var fluid = BuiltInRegistries.FLUID.get(key);
        if (fluid == net.minecraft.world.level.material.Fluids.EMPTY) throw new IllegalStateException("No fluid " + id);
        return fluid;
    }

    private static void chest(ServerLevel level, BlockPos pos, List<ItemStack> items) {
        plant.put(pos, Blocks.CHEST.defaultBlockState());
        var chest = (ChestBlockEntity) level.getBlockEntity(pos);
        for (int i = 0; i < items.size() && i < 27; i++) chest.setItem(i, items.get(i));
    }

    /** A creative tank of one fluid that fills at tick 20, when its block entity is ready. */
    private static BlockPos supply(ServerLevel level, BlockPos pos, String fluid) {
        plant.put(pos, AllBlocks.CREATIVE_FLUID_TANK.getDefaultState());
        SUPPLIES.add(new Object[] {pos, fluid});
        return pos;
    }

    private static void wire(ServerLevel level, BlockPos battery, BlockPos device, double volts) {
        plant.put(battery, CEEBlocks.CREATIVE_BATTERY.getDefaultState());
        WIRES.add(new Object[] {battery, device, volts});
    }

    // ---- stations ------------------------------------------------------------------------------------------------------------------

    /** Diorite on a depot under a fan blowing through a soul campfire, and a millstone to crush the calcite. */
    private static void calcite(ServerLevel level, int x, int z) {
        var ground = new BlockPos(x, G, z);
        plant.motor(ground.above(), Direction.EAST, 64);
        plant.put(ground.above().east(), AllBlocks.ENCASED_FAN.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
        plant.put(ground.east(2), Blocks.STONE.defaultBlockState());
        plant.put(ground.above().east(2), Blocks.SOUL_CAMPFIRE.defaultBlockState());
        plant.put(ground.east(4), AllBlocks.DEPOT.getDefaultState());
        plant.put(ground.east(8).above(), AllBlocks.MILLSTONE.getDefaultState());
        plant.motor(ground.east(8), Direction.UP, 32);
        chest(level, ground.east(6).south(2), List.of(new ItemStack(Items.DIORITE, 64), new ItemStack(Items.CALCITE, 64)));
        plant.sign(ground.east(4).north(), "閃緑岩を台に置く→方解石");
        plant.sign(ground.east(8).north(), "石臼：方解石→蛍石50%");
        plant.sign(ground.east(6).south(3), "閃緑岩・方解石");
    }

    /** A 3x3x2 stainless vessel with its hatch open and a liquid in it. */
    private static void burnVessel(ServerLevel level, BlockPos base, String fluid, String label) {
        ReactorGameTests.layer(level, base, 1);
        ReactorGameTests.layer(level, base, 2);
        var vessel = (LiddedVesselBlockEntity) ((LiddedVesselBlockEntity) level.getBlockEntity(base)).getControllerBE();
        vessel.getTankInventory().fill(new FluidStack(fluid(fluid), 8000), IFluidHandler.FluidAction.EXECUTE);
        vessel.toggleLid();
        plant.sign(base.offset(1, 0, -2), label);
    }

    /** A solid box of casings with its terminal on top, an electrolyte filled once the structure is found, and a creative battery wired to it. */
    private static void bank(ServerLevel level, BlockPos corner, boolean lead, double volts) {
        var casing = (lead ? CEBattery.LEAD_ACID_CASING : CEBattery.LITHIUM_ION_CASING).get().defaultBlockState();
        for (int dx = 0; dx < 4; dx++) for (int dy = 0; dy < 2; dy++) for (int dz = 0; dz < 3; dz++) plant.put(corner.offset(dx, dy, dz), casing);
        var terminal = corner.offset(1, 2, 1);
        plant.put(terminal, (lead ? CEBattery.LEAD_ACID_TERMINAL : CEBattery.LITHIUM_ION_TERMINAL).get().defaultBlockState());
        BANKS.add(terminal);
        wire(level, corner.offset(6, 0, 1), terminal, volts);
        var electrolyte = lead ? "dilute_sulfuric_acid" : "acetone";
        LATER.add(() -> ((BatteryTerminalBlockEntity) level.getBlockEntity(terminal)).electrolyte()
            .fill(new FluidStack(fluid(electrolyte), 24 * 250), IFluidHandler.FluidAction.EXECUTE));
        plant.sign(corner.offset(1, 0, -1), (lead ? "鉛蓄電池 200V" : "リチウムイオン電池 300V") + "（電池は " + (int) volts + "V で充電）");
    }

    /**
     * A 3x3 stirred vessel with a creative blaze burner under a corner, a creative motor turning the stirrer through a cog, a creative tank and a
     * stainless pump line on each feed side and a chest of materials. The feed cells are the flat sides' middles, as the vessel's ports are.
     */
    private static void reactor(ServerLevel level, BlockPos base, String label, boolean heated, List<Feed> feeds, List<ItemStack> items) {
        ReactorGameTests.assemble(level, base, ReactorModule.STIRRED, 1);
        var stirrer = base.offset(1, -1, 1);
        plant.put(stirrer.north(), AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y));
        plant.motor(stirrer.north().below(), Direction.UP, 128);
        plant.burner(base.below(), heated ? BlazeBurnerBlock.HeatLevel.KINDLED : BlazeBurnerBlock.HeatLevel.SEETHING);
        for (var feed : feeds) {
            var side = feed.side();
            // The middle cell of the side, and a line of five cells outward from it ending at a creative tank.
            var middle = base.offset(1 + (side.getStepX() == 0 ? 0 : side.getStepX() > 0 ? 1 : -1), 0, 1 + (side.getStepZ() == 0 ? 0 : side.getStepZ() > 0 ? 1 : -1));
            var last = middle.relative(side);
            var first = middle.relative(side, 5);
            var tank = middle.relative(side, 6);
            supply(level, tank, feed.fluid());
            plant.route(side.getOpposite(), first, List.of(last), side.getOpposite());
        }
        chest(level, base.offset(-2, 0 - 2, -2), items.isEmpty() ? List.of(new ItemStack(Items.PAPER)) : items);
        plant.sign(base.offset(-2, -2, -3), label);
    }

    /** A 7x7x3 clarifier fed with the lead slurry from below and turned slowly by a creative motor on its drive. */
    private static void clarifier(ServerLevel level, BlockPos drive) {
        for (var cell : WetProcessStructure.cells(drive, Direction.NORTH, 7, 3)) level.setBlock(cell.pos(), cell.state(), 3);
        ((WetProcessBlockEntity) level.getBlockEntity(drive)).rebuild();
        plant.motor(drive.above(), Direction.DOWN, 4);
        var port = drive.below(2);
        var tank = supply(level, port.below(4), "lead_leach_slurry");
        plant.route(Direction.UP, tank.above(), List.of(port.below()), Direction.UP);
        chest(level, drive.offset(-5, G - drive.getY(), 0), List.of(stack("flocculant", 16)));
        plant.sign(drive.offset(0, G - drive.getY(), 5), "沈降槽：鉛スラリー（4 RPM）");
    }

    /** An electrolysis cell fed from below, with a creative battery wired to its panel at 400 V. */
    private static void cell(ServerLevel level, BlockPos cell, String solution) {
        plant.put(cell, CEElectrolysis.CELL.get().defaultBlockState().setValue(ElectrolysisCellBlock.AXIS, Direction.Axis.Z));
        plant.put(cell.above(), CEElectrolysis.HEAD.get().defaultBlockState().setValue(ElectrodeHeadBlock.AXIS, Direction.Axis.Z));
        for (int y = 0; y < 2; y++)
            plant.put(cell.north().above(y), CEElectrolysis.CONTROLLER.get().defaultBlockState().setValue(ElectrolysisControllerBlock.FACING, Direction.NORTH));
        var panel = cell.north().above();
        var tank = supply(level, cell.below(4), solution);
        plant.route(Direction.UP, tank.above(), List.of(cell.below()), Direction.UP);
        wire(level, cell.offset(4, -3, -1), panel, 400);
        LATER.add(() -> {
            var rack = level.getCapability(Capabilities.ItemHandler.BLOCK, cell.above(), Direction.UP);
            if (rack == null || !rack.insertItem(1, CEElectrolysis.NICKEL_ELECTRODE_PLATE.toStack(), false).isEmpty()) throw new IllegalStateException("The nickel plate does not fit the head");
            var owner = (ElectrolysisControllerBlockEntity) level.getBlockEntity(cell.north());
            if (!owner.validate()) throw new IllegalStateException("The electrolysis cell does not form: " + owner.status());
            var player = FakePlayerFactory.getMinecraft(level);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            level.getBlockState(panel).useWithoutItem(level, player, new BlockHitResult(Vec3.atLowerCornerOf(panel).add(.75, .21, 0), Direction.NORTH, panel, false));
        });
        chest(level, cell.offset(-3, G - cell.getY(), 0), List.of(stack("nickel_electrode_plate", 16)));
        plant.sign(cell.offset(0, G - cell.getY(), -2), "電解採取セル（400V）：鉛溶液→鉛インゴット");
    }

    /** The 5x5x3 molten-salt furnace with a hopper of alumina and coal on its anode, a hopper under it into a chest, and a creative battery at 200 V. */
    private static void furnace(ServerLevel level, BlockPos anode) {
        for (int y = -2; y <= 0; y++) for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++)
            plant.put(anode.offset(x, y, z), (y == 0 && x == 0 && z == 0 ? CEFurnace.ANODE : CEFurnace.SHELL).get().defaultBlockState());
        var busbar = anode.south(3);
        plant.put(busbar, CEFurnace.BUSBAR.get().defaultBlockState().setValue(FurnaceBusbarBlock.FACING, Direction.NORTH));
        wire(level, busbar.south(2), busbar, 200);
        plant.put(anode.above(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        chest(level, anode.above(2), List.of(stack("alumina", 64), new ItemStack(Items.COAL, 64), stack("titanium_dioxide", 16), stack("zirconia", 16)));
        var under = anode.below(3);
        plant.put(under, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.SOUTH));
        plant.put(under.south(), Blocks.CHEST.defaultBlockState());
        plant.sign(anode.offset(0, G - anode.getY(), 4), "溶融塩電解炉（200V）：アルミナ+石炭→アルミニウム");
    }

    private static void index(ServerLevel level) {
        String[][] labels = {{"北：方解石・酸の槽・電池", "z=0"}, {"鉛 乾式・湿式", "z=40"}, {"沈降槽・電解セル・リチウム", "z=80"}, {"アルミニウム", "z=120"}};
        for (int i = 0; i < labels.length; i++) plant.sign(new BlockPos(2 + 2 * i, G, 10), labels[i][0] + " " + labels[i][1]);
    }

    // ---- ticks ---------------------------------------------------------------------------------------------------------------------

    private static void serverTick(ServerTickEvent.Post event) {
        if (done || failure != null) return;
        ServerLevel level = event.getServer().overworld();
        try {
            serverTicks++;
            // A cog train sets each pump's turning direction; a positive speed moves fluid toward its front.
            if (serverTicks == 5 || serverTicks == 40) plant.correctDrives((pos, speed) -> speed < 0);
            if (serverTicks == 20) for (var supply : SUPPLIES) station(level, "supply " + supply[1], l -> {
                var tank = ((com.simibubi.create.content.fluids.tank.FluidTankBlockEntity) l.getBlockEntity((BlockPos) supply[0])).getControllerBE();
                ((com.simibubi.create.content.fluids.tank.CreativeFluidTankBlockEntity.CreativeSmartFluidTank) tank.getTankInventory())
                    .setContainedFluid(new FluidStack(fluid((String) supply[1]), 1_000));
            });
            if (serverTicks == 25) for (var wire : WIRES) station(level, "wire " + wire[1], l -> {
                var battery = (BlockPos) wire[0];
                CEEBlocks.CREATIVE_BATTERY.get().ensureNodesExist(l, battery, l.getBlockState(battery));
                var device = DevicesSavedData.load(l).getDevice(battery, CreativeBatteryDevice.class);
                if (device == null) throw new IllegalStateException("The battery is not a circuit device at " + battery.toShortString());
                device.voltage = (Double) wire[2];
                device.acFrequency = 0;
                var copper = CEERegistries.WIRE_TYPE.get(ResourceLocation.fromNamespaceAndPath("electroenergetics", "copper"));
                var wiring = InfrastructureSavedData.load(l);
                for (int node = 0; node < 2; node++) wiring.connect(new InWorldNode(node, battery), new InWorldNode(node, (BlockPos) wire[1]), copper);
            });
            if (serverTicks == 90) for (var later : LATER) station(level, "late step", l -> later.run());
            if (serverTicks == TIMEOUT / 2) {
                for (var terminal : BANKS) station(level, "bank report", l -> {
                    var bank = (BatteryTerminalBlockEntity) l.getBlockEntity(terminal);
                    REPORT.put("bank " + terminal.toShortString(), bank.status() + ", casings " + bank.casings() + ", charge " + Math.round(bank.stateOfCharge() * 100) + "%");
                });
            }
            if (serverTicks >= TIMEOUT / 2 + 20) {
                REPORT.put("ticks", serverTicks);
                REPORT.put("lines", plant.LINES.size());
                done = true;
            }
        } catch (RuntimeException error) {
            failure = error.toString();
        }
    }
}
