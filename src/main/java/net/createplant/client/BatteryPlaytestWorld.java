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
import net.createplant.block.PulsationTubeBlock;
import net.createplant.gametest.AbsorptionTowerGameTests;
import net.createplant.registry.CETransport;
import net.minecraft.network.chat.Component;
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
 * Opt-in creative flat world: one continuous plant. Lead is roasted and reduced; the sulfur dioxide goes through an absorption tower and an
 * electrolysis cell (the sulfur cycle of {@link SulfurCyclePlaytestWorld}) to sulfuric acid, which is piped on to the aluminium sulfate leach,
 * the lithium carbonate vessel and an open vessel for the burn test. Sulfate solution goes on to the calcining vessel, its alumina by hopper into
 * the molten-salt furnace. A second line leaches lead in hydrochloric acid, clarifies it and electrowins it. Only the raw materials come from
 * creative tanks (oxygen, water, carbon monoxide, hydrochloric acid, electrolytes) and chests (ores, coal, soda ash).
 * The fertilizer plant of {@link FertilizerPlaytestWorld} (wood to NPK fertilizer, 70 blocks east) is built in the same world.
 * {@code -Dcreateplant.batteryPlaytest} builds it, saves it and stays open for play; add {@code -Dcreateplant.batteryPlaytestClose} to quit after saving.
 * A part that cannot be built is recorded and skipped, so one wrong API leaves the rest standing; the failures are told in chat and the result file.
 * It has not been run by its author: the first run is the verification.
 */
public final class BatteryPlaytestWorld {
    private static final Logger LOG = LoggerFactory.getLogger(BatteryPlaytestWorld.class);
    private static final String WORLD = "createplant-battery-" + System.currentTimeMillis();
    private static final int G = -60, RUN_TICKS = 400;
    private static final BlockPos T = new BlockPos(0, -58, 0), CELL = T.south(8), ACID = CELL.south(5);
    private static final Map<String, Object> REPORT = new LinkedHashMap<>();
    private static final List<String> FAILED = new ArrayList<>();
    private static final List<Object[]> SUPPLIES = new ArrayList<>(), WIRES = new ArrayList<>(), LATER = new ArrayList<>();
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
                    new LevelSettings("Create-Plant battery, lead and aluminium plant", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
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
                        LOG.error("BATTERY PLAYTEST build failed", error);
                        failure = error.toString();
                    }
                });
            } else if (phase == 2) {
                if (failure != null) throw new IllegalStateException(failure);
                if (!done) return;
                phase = 3;
                ticks = 0;
                var server = mc.getSingleplayerServer();
                server.execute(() -> {
                    server.saveEverything(false, true, true);
                    var player = server.getPlayerList().getPlayers().getFirst();
                    player.sendSystemMessage(Component.literal(FAILED.isEmpty() ? "試験プラントを作りました。看板で各設備を確認できます。"
                        : "作れなかった設備が" + FAILED.size() + "件あります：" + String.join(" / ", FAILED)));
                });
            } else if (phase == 3 && ++ticks > 40) {
                phase = 4;
                REPORT.put("failedStations", FAILED);
                Files.writeString(mc.gameDirectory.toPath().resolve("battery-playtest-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(REPORT));
                if (FAILED.isEmpty()) LOG.info("BATTERY PLAYTEST BUILT: {}", REPORT);
                else LOG.error("BATTERY PLAYTEST BUILT WITH {} FAILED PARTS: {}", FAILED.size(), REPORT);
                if (Boolean.getBoolean("createplant.batteryPlaytestClose")) mc.stop();
            }
        } catch (Exception error) {
            phase = -1;
            LOG.error("BATTERY PLAYTEST FAILED: {}", REPORT, error);
            if (Boolean.getBoolean("createplant.batteryPlaytestClose")) mc.stop();
        }
    }

    private interface Part { void build(ServerLevel level); }

    /** Builds one part; a failure is recorded and the next part is still built. */
    private static void part(ServerLevel level, String name, Part part) {
        try {
            part.build(level);
        } catch (RuntimeException | LinkageError error) {
            FAILED.add(name + ": " + error);
            LOG.error("BATTERY PLAYTEST part {} failed", name, error);
        }
    }

    private static void build(ServerLevel level, ServerPlayer player) {
        var server = level.getServer();
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.setDayTime(6000);
        plant = new PlaytestPlant(level, G);

        part(level, "硫黄サイクル", BatteryPlaytestWorld::sulfur);
        part(level, "鉛 焙焼・還元", BatteryPlaytestWorld::leadDry);
        part(level, "アルミニウム", BatteryPlaytestWorld::aluminium);
        part(level, "炭酸リチウム", BatteryPlaytestWorld::lithium);
        part(level, "酸の槽", BatteryPlaytestWorld::burn);
        part(level, "鉛 湿式", BatteryPlaytestWorld::leadWet);
        part(level, "方解石", l -> calcite(l, -40, -20));
        part(level, "鉛蓄電池", l -> bank(l, new BlockPos(-70, G, -10), true, 230));
        part(level, "リチウムイオン電池", l -> bank(l, new BlockPos(-70, G, -30), false, 330));
        part(level, "ポンプの駆動", l -> plant.driveAll());
        // The fertilizer plant of FertilizerPlaytestWorld stands east of this one, with its own pumps and cells and its own run to NPK fertilizer.
        part(level, "肥料プラント", FertilizerPlaytestWorld::addTo);

        player.setItemSlot(EquipmentSlot.HEAD, AllItems.GOGGLES.asStack());
        player.getInventory().setItem(1, AllItems.WRENCH.asStack());
        BlockPos spawn = new BlockPos(60, G, -12); // between the two plants, so both stay within the simulation distance
        player.teleportTo(level, spawn.getX() + .5, spawn.getY(), spawn.getZ() + .5, 150, 15);
        player.setRespawnPosition(Level.OVERWORLD, spawn, 150, true, false);
        level.setDefaultSpawnPos(spawn, 150);
        REPORT.put("world", WORLD);
    }

    // ---- helpers -------------------------------------------------------------------------------------------------------------------

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

    private static void hopper(BlockPos pos, Direction facing) {
        plant.put(pos, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, facing));
    }

    /** A chest on a hopper that pushes into the block it faces: how a solid raw material enters a vessel or the furnace. */
    private static void intake(ServerLevel level, BlockPos hopper, Direction into, List<ItemStack> items) {
        hopper(hopper, into);
        chest(level, hopper.above(), items);
    }

    /** A hopper under a vessel or furnace cell that pulls the product and pushes it toward {@code toward}, into a chest there when {@code store} is set. */
    private static void outlet(ServerLevel level, BlockPos hopper, Direction toward, boolean store) {
        hopper(hopper, toward);
        if (store) plant.put(hopper.relative(toward), Blocks.CHEST.defaultBlockState());
    }

    private static BlockPos tank(BlockPos pos, String fluid) {
        plant.put(pos, AllBlocks.CREATIVE_FLUID_TANK.getDefaultState());
        SUPPLIES.add(new Object[] {pos, fluid});
        return pos;
    }

    /** A straight stainless line from beside {@code source} to {@code last}; the source is a port cell or a tank, and the target stands beyond {@code last}. */
    private static List<BlockPos> straight(BlockPos source, Direction direction, BlockPos last) {
        return plant.route(direction, source.relative(direction), List.of(last), direction);
    }

    private static void wire(BlockPos battery, BlockPos device, double volts) {
        plant.put(battery, CEEBlocks.CREATIVE_BATTERY.getDefaultState());
        WIRES.add(new Object[] {battery, device, volts});
    }

    /** The middle cell of a vessel's side on its bottom layer; every cell of a formed vessel is a port. */
    private static BlockPos mid(BlockPos base, Direction side) {
        return base.offset(1 + side.getStepX(), 0, 1 + side.getStepZ());
    }

    /** A 3x3 stirred vessel with a creative motor turning its stirrer through a cog and a creative blaze burner under a corner. */
    private static void vessel(ServerLevel level, BlockPos base, BlazeBurnerBlock.HeatLevel heat) {
        ReactorGameTests.assemble(level, base, ReactorModule.STIRRED, 1);
        var stirrer = base.offset(1, -1, 1);
        plant.put(stirrer.north(), AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y));
        plant.motor(stirrer.north().below(), Direction.UP, 128);
        plant.burner(base.below(), heat);
    }

    /** An electrolysis cell with its head, controller and panel; the nickel plate and START are pressed later, as a player would. */
    private static void cell(ServerLevel level, BlockPos cell, double volts, BlockPos battery) {
        plant.put(cell, CEElectrolysis.CELL.get().defaultBlockState().setValue(ElectrolysisCellBlock.AXIS, Direction.Axis.Z));
        plant.put(cell.above(), CEElectrolysis.HEAD.get().defaultBlockState().setValue(ElectrodeHeadBlock.AXIS, Direction.Axis.Z));
        for (int y = 0; y < 2; y++)
            plant.put(cell.north().above(y), CEElectrolysis.CONTROLLER.get().defaultBlockState().setValue(ElectrolysisControllerBlock.FACING, Direction.NORTH));
        var panel = cell.north().above();
        wire(battery, panel, volts);
        LATER.add(new Object[] {"電解セルの始動 " + cell.toShortString(), (Part) l -> {
            var rack = l.getCapability(Capabilities.ItemHandler.BLOCK, cell.above(), Direction.UP);
            if (rack == null || !rack.insertItem(1, CEElectrolysis.NICKEL_ELECTRODE_PLATE.toStack(), false).isEmpty()) throw new IllegalStateException("The nickel plate does not fit the head");
            var owner = (ElectrolysisControllerBlockEntity) l.getBlockEntity(cell.north());
            if (!owner.validate()) throw new IllegalStateException("The electrolysis cell does not form: " + owner.status());
            var player = FakePlayerFactory.getMinecraft(l);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            l.getBlockState(panel).useWithoutItem(l, player, new BlockHitResult(Vec3.atLowerCornerOf(panel).add(.75, .21, 0), Direction.NORTH, panel, false));
        }});
    }

    // ---- the plant -----------------------------------------------------------------------------------------------------------------

    /** The sulfur cycle: sulfur dioxide into the tower, water on top, sulfurous acid under it to the cell, hydrogen to a receiver, sulfuric acid to a tank. */
    private static void sulfur(ServerLevel level) {
        AbsorptionTowerGameTests.tower(level, T, 4, true);
        var top = T.above(3);
        // Water from a tank above runs down onto the top storey's centre.
        plant.route(Direction.DOWN, top.above(3), List.of(top.above()), Direction.DOWN);
        tank(top.above(4), "water");
        // Unabsorbed gas leaves a straight side of the top storey for a store.
        var off = straight(top.north(), Direction.NORTH, top.north(5));
        plant.store(off.getLast(), Direction.NORTH, "未吸収ガス", PlaytestPlant.Kind.ANY);
        // Sulfurous acid from the underside to the cell's input below it.
        plant.route(Direction.DOWN, T.below(), List.of(T.below().south(8)), Direction.UP);
        cell(level, CELL, 400, CELL.offset(4, 0, -1));
        plant.put(CELL.west(5), CETransport.GAS_RECEIVER.get().defaultBlockState().setValue(PulsationTubeBlock.AXIS, Direction.Axis.X));
        straight(CELL, Direction.WEST, CELL.west(4));
        straight(CELL, Direction.SOUTH, CELL.south(4));
        plant.put(ACID, CETransport.STAINLESS_STEEL_TANK.get().defaultBlockState());
        plant.sign(T.west(3).north(3), "吸収塔（亜硫酸）");
        plant.sign(CELL.east(3), "電解セル→硫酸");
        plant.sign(CELL.west(5).south(2), "水素");
        plant.sign(ACID.south(2), "硫酸の母管");
        plant.sign(T.east(3).north(3), "水は塔の頂上へ");
    }

    /** Galena is roasted (oxygen in; sulfur dioxide to the tower; sinter by hopper) and the sinter reduced with carbon monoxide to lead ingots. */
    private static void leadDry(ServerLevel level) {
        var r1 = new BlockPos(-14, -58, -1);
        vessel(level, r1, BlazeBurnerBlock.HeatLevel.KINDLED);
        intake(level, r1.west(), Direction.EAST, List.of(stack("galena", 64)));
        straight(tank(mid(r1, Direction.WEST).west(6), "oxygen_gas"), Direction.EAST, mid(r1, Direction.WEST).west());
        // The sulfur dioxide to the absorption tower's west side.
        straight(mid(r1, Direction.EAST), Direction.EAST, T.west(2));
        // The sinter drops by hopper into the reduction vessel, one layer lower.
        var r2 = new BlockPos(-14, -59, 2);
        vessel(level, r2, BlazeBurnerBlock.HeatLevel.SEETHING);
        hopper(r1.offset(2, -1, 2), Direction.SOUTH);
        straight(tank(mid(r2, Direction.WEST).west(6), "carbon_monoxide"), Direction.EAST, mid(r2, Direction.WEST).west());
        var co2 = straight(mid(r2, Direction.EAST), Direction.EAST, mid(r2, Direction.EAST).east(5));
        plant.store(co2.getLast(), Direction.EAST, "二酸化炭素", true);
        outlet(level, r2.offset(2, -1, 2), Direction.SOUTH, true);
        plant.sign(r1.north(2), "鉛① 焙焼（方鉛鉱→焼結鉱＋SO2）");
        plant.sign(r2.south(5).west(), "鉛② 還元溶錬（焼結鉱→鉛・左のチェスト）");
    }

    /** Sulfuric acid and bauxite make sulfate solution; it is calcined to alumina, which goes by hopper into the furnace with coal; the metal drops into a chest. */
    private static void aluminium(ServerLevel level) {
        var a1 = new BlockPos(14, -58, 12);
        vessel(level, a1, BlazeBurnerBlock.HeatLevel.KINDLED);
        intake(level, a1.west(), Direction.EAST, List.of(stack("bauxite", 64)));
        straight(ACID, Direction.EAST, mid(a1, Direction.WEST).west());
        var a2 = new BlockPos(30, -58, 12);
        vessel(level, a2, BlazeBurnerBlock.HeatLevel.SEETHING);
        straight(mid(a1, Direction.EAST), Direction.EAST, mid(a2, Direction.WEST).west());
        // The sulfur trioxide to a store: not returned to the tower, which would mix it with the sulfur dioxide.
        var so3 = straight(mid(a2, Direction.NORTH), Direction.NORTH, mid(a2, Direction.NORTH).north(5));
        plant.store(so3.getLast(), Direction.NORTH, "三酸化硫黄", true);
        // The molten-salt furnace: its shell takes alumina from the hopper under the calcining vessel and coal from a chest; its busbar is wired at 200 V.
        var anode = new BlockPos(35, -57, 13);
        for (int y = -2; y <= 0; y++) for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++)
            plant.put(anode.offset(x, y, z), (y == 0 && x == 0 && z == 0 ? CEFurnace.ANODE : CEFurnace.SHELL).get().defaultBlockState());
        var busbar = anode.south(3);
        plant.put(busbar, CEFurnace.BUSBAR.get().defaultBlockState().setValue(FurnaceBusbarBlock.FACING, Direction.NORTH));
        wire(busbar.south(2), busbar, 200);
        hopper(a2.offset(2, -1, 1), Direction.EAST);
        intake(level, anode.offset(3, -1, 0), Direction.WEST, List.of(new ItemStack(Items.COAL, 64)));
        outlet(level, anode.below(3), Direction.SOUTH, true);
        plant.sign(a1.north(2), "Al① ボーキサイト＋硫酸→硫酸アルミニウム");
        plant.sign(a2.north(2).west(2), "Al② 焼成（→アルミナ）");
        plant.sign(anode.atY(G).south(5), "溶融塩電解炉（200V）：アルミニウムは左のチェスト");
    }

    /** Spodumene and soda ash with sulfuric acid make lithium carbonate, which a hopper collects. */
    private static void lithium(ServerLevel level) {
        var l1 = new BlockPos(-1, -58, 20);
        vessel(level, l1, BlazeBurnerBlock.HeatLevel.SEETHING);
        intake(level, l1.west(), Direction.EAST, List.of(stack("spodumene", 64)));
        intake(level, l1.west().south(), Direction.EAST, List.of(stack("sodium_carbonate", 64)));
        straight(ACID, Direction.SOUTH, mid(l1, Direction.NORTH).north());
        outlet(level, l1.offset(2, -1, 2), Direction.SOUTH, true);
        plant.sign(l1.north(2).east(2), "炭酸リチウム（左下のチェスト）");
    }

    /** An open vessel on the acid line, an alkali vessel and a water vessel: stepping into the first two burns until death, the third does not. */
    private static void burn(ServerLevel level) {
        var b1 = new BlockPos(-12, -58, 12);
        for (int layer = 1; layer <= 2; layer++) ReactorGameTests.layer(level, b1, layer);
        straight(ACID, Direction.WEST, mid(b1, Direction.EAST).east());
        ((LiddedVesselBlockEntity) level.getBlockEntity(b1)).toggleLid();
        plant.sign(b1.north(2), "硫酸の槽：ハッチを開けてある");
        var b2 = new BlockPos(-26, -58, 12);
        var b3 = new BlockPos(-38, -58, 12);
        for (int layer = 1; layer <= 2; layer++) { ReactorGameTests.layer(level, b2, layer);ReactorGameTests.layer(level, b3, layer); }
        LATER.add(new Object[] {"苛性ソーダ・水の槽", (Part) l -> {
            var alkali = (LiddedVesselBlockEntity) ((LiddedVesselBlockEntity) l.getBlockEntity(b2)).getControllerBE();
            alkali.getTankInventory().fill(new FluidStack(fluid("caustic_soda_solution"), 8000), IFluidHandler.FluidAction.EXECUTE);
            alkali.toggleLid();
            var water = (LiddedVesselBlockEntity) ((LiddedVesselBlockEntity) l.getBlockEntity(b3)).getControllerBE();
            water.getTankInventory().fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 8000), IFluidHandler.FluidAction.EXECUTE);
            water.toggleLid();
        }});
        plant.sign(b2.north(2), "苛性ソーダの槽（燃える）");
        plant.sign(b3.north(2), "水の槽（燃えない・対照）");
    }

    /** Galena leached in hydrochloric acid, the slurry clarified, the solution electrowon to lead ingots. */
    private static void leadWet(ServerLevel level) {
        var w1 = new BlockPos(0, -58, 40);
        vessel(level, w1, BlazeBurnerBlock.HeatLevel.KINDLED);
        intake(level, w1.west().south(2), Direction.EAST, List.of(stack("galena", 64)));
        straight(tank(mid(w1, Direction.WEST).west(6), "hydrochloric_acid"), Direction.EAST, mid(w1, Direction.WEST).west());
        straight(tank(mid(w1, Direction.NORTH).north(6), "oxygen_gas"), Direction.SOUTH, mid(w1, Direction.NORTH).north());
        // The clarifier stands east of it, fed from below at its centre.
        var drive = new BlockPos(12, -57, 41);
        for (var cell : WetProcessStructure.cells(drive, Direction.SOUTH, 7, 3)) level.setBlock(cell.pos(), cell.state(), 3);
        ((WetProcessBlockEntity) level.getBlockEntity(drive)).rebuild();
        plant.motor(drive.above(), Direction.DOWN, 4);
        var feed = mid(w1, Direction.EAST).east();
        plant.route(Direction.EAST, feed, List.of(feed.below(2), new BlockPos(drive.getX(), feed.getY() - 2, feed.getZ())), Direction.UP);
        // The clear solution over the weir on the clarifier's front to the cell, from below.
        var weir = drive.south(4);
        var cellAt = new BlockPos(drive.getX(), -58, 52);
        plant.route(Direction.SOUTH, weir, List.of(weir.below(2), new BlockPos(weir.getX(), weir.getY() - 2, cellAt.getZ())), Direction.UP);
        cell(level, cellAt, 400, cellAt.offset(4, 0, -1));
        plant.sign(w1.north(2).west(2), "鉛 湿式① 浸出（方鉛鉱＋塩酸）");
        plant.sign(drive.atY(G).south(6), "沈降槽（4 RPM）");
        plant.sign(cellAt.atY(G).south(2), "電解採取セル：鉛インゴットはヘッドから取り出す");
    }

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
    }

    /** A solid box of casings with its terminal on top, a creative tank of its electrolyte piped to the terminal, and a creative battery wired to it. */
    private static void bank(ServerLevel level, BlockPos corner, boolean lead, double volts) {
        var casing = (lead ? CEBattery.LEAD_ACID_CASING : CEBattery.LITHIUM_ION_CASING).get().defaultBlockState();
        for (int dx = 0; dx < 4; dx++) for (int dy = 0; dy < 2; dy++) for (int dz = 0; dz < 3; dz++) plant.put(corner.offset(dx, dy, dz), casing);
        var terminal = corner.offset(1, 2, 1);
        plant.put(terminal, (lead ? CEBattery.LEAD_ACID_TERMINAL : CEBattery.LITHIUM_ION_TERMINAL).get().defaultBlockState());
        BANKS.add(terminal);
        wire(corner.offset(6, 0, 1), terminal, volts);
        straight(tank(terminal.west(8), lead ? "dilute_sulfuric_acid" : "acetone"), Direction.EAST, terminal.west());
        plant.sign(corner.north(), (lead ? "鉛蓄電池 200V" : "リチウムイオン電池 300V") + "（" + (int) volts + "Vで充電）");
    }

    // ---- ticks ---------------------------------------------------------------------------------------------------------------------

    private static void serverTick(ServerTickEvent.Post event) {
        if (done || failure != null) return;
        ServerLevel level = event.getServer().overworld();
        try {
            serverTicks++;
            // A cog train sets each pump's turning direction; a positive speed moves fluid toward its front.
            if (serverTicks == 5 || serverTicks == 40) plant.correctDrives((pos, speed) -> speed < 0);
            if (serverTicks == 20) for (var supply : SUPPLIES) part(level, "供給タンク " + supply[1], l -> {
                var tank = ((com.simibubi.create.content.fluids.tank.FluidTankBlockEntity) l.getBlockEntity((BlockPos) supply[0])).getControllerBE();
                ((com.simibubi.create.content.fluids.tank.CreativeFluidTankBlockEntity.CreativeSmartFluidTank) tank.getTankInventory())
                    .setContainedFluid(new FluidStack(fluid((String) supply[1]), 1_000));
            });
            if (serverTicks == 25) for (var wire : WIRES) part(level, "配線 " + wire[1], l -> {
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
            if (serverTicks == 90) for (var later : LATER) part(level, (String) later[0], (Part) later[1]);
            if (serverTicks == RUN_TICKS) {
                for (var terminal : BANKS) part(level, "電池の確認", l -> {
                    var bank = (BatteryTerminalBlockEntity) l.getBlockEntity(terminal);
                    REPORT.put("bank " + terminal.toShortString(), bank.status() + ", casings " + bank.casings() + ", charge " + Math.round(bank.stateOfCharge() * 100) + "%");
                });
                REPORT.put("ticks", serverTicks);
                REPORT.put("pipeBlocks", plant.LINES.size());
                REPORT.put("pumps", plant.PUMPS.size());
                done = true;
            }
        } catch (RuntimeException error) {
            failure = error.toString();
        }
    }
}
