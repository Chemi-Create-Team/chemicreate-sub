package net.createplant.client;

import com.google.gson.GsonBuilder;
import com.simibubi.create.AllBlocks;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.createplant.CreatePlant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in native model gallery. Individual default-state samples do not demonstrate an operating plant. */
public final class AllBlocksClientCheck {
    private static final int COLUMNS = 6, ROWS = 4, PAGE_SIZE = COLUMNS * ROWS, SPACING = 6, PAGE_SPACING = 48;
    private static final Path PAGE_FILE = Path.of("/workspace/outputs/all-blocks-view.txt");
    private static final Path RESULT_FILE = Path.of("/workspace/outputs/all-blocks-client-result.json");
    private record Sample(int row, int column, String id, String state, List<Integer> position, boolean containedFluid) { }
    private record Reference(String id, List<Integer> position) { }
    private record Page(int index, List<Sample> samples, List<Reference> references) { }
    private record JoinedPanel(int column, String id, boolean reference, String shape, List<List<Integer>> positions) { }
    private record Result(String modId, int blockCount, int pageCount, int columns, int rows, int spacing,
                          int pageSpacing, String purpose, List<Page> pages, int registryPageCount,
                          int joinedPanelPage, List<JoinedPanel> joinedPanels) { }

    private static int phase, ticks, page = -1, stableTicks, pageCount, joinedPanelPage;
    private static BlockPos origin;
    private static boolean[] captured;
    private static volatile boolean prepared;
    private static volatile String failure;

    private AllBlocksClientCheck() { }

    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (failure != null) throw new IllegalStateException(failure);
        if (phase == 0 && mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen && mc.getOverlay() == null && ++ticks > 80) {
            phase = 1;
            ticks = 0;
            mc.createWorldOpenFlows().createFreshLevel("all-blocks-gallery-" + System.currentTimeMillis(),
                new net.minecraft.world.level.LevelSettings("All block models", net.minecraft.world.level.GameType.CREATIVE, false,
                    net.minecraft.world.Difficulty.PEACEFUL, true, new net.minecraft.world.level.GameRules(), net.minecraft.world.level.WorldDataConfiguration.DEFAULT),
                new net.minecraft.world.level.levelgen.WorldOptions(42L, false, false),
                access -> access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                    .getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
        }
        if (phase == 1 && mc.level != null && mc.player != null && mc.getOverlay() == null && ++ticks > 100) {
            phase = 2;
            ticks = 0;
            mc.options.pauseOnLostFocus = false;
            mc.options.hideGui = true;
            mc.options.renderDistance().set(16);
            origin = new BlockPos(0, mc.player.blockPosition().getY() + 1, 0);
            mc.getSingleplayerServer().execute(() -> prepare(mc));
        }
        if (phase != 2 || !prepared || mc.level == null || mc.player == null) return;
        if (mc.screen != null) mc.setScreen(null);
        if (++ticks % 20 == 0) {
            try {
                String value = Files.exists(PAGE_FILE) ? Files.readString(PAGE_FILE).trim() : "0";
                int requested = value.isEmpty() ? Math.max(0, page) : Integer.parseInt(value);
                requested = Math.clamp(requested, 0, pageCount - 1);
                if (requested != page) {
                    page = requested;
                    stableTicks = 0;
                    move(mc);
                }
            } catch (NumberFormatException ignored) {
                // A page file may be between truncation and its new integer during a capture session.
            } catch (Exception exception) {
                throw new IllegalStateException("All-block gallery page control", exception);
            }
        }
        if (page < 0) return;
        float[] angles = angles();
        mc.player.setYRot(angles[0]);
        mc.player.setXRot(angles[1]);
        mc.player.yRotO = angles[0];
        mc.player.xRotO = angles[1];
        if (++stableTicks == 200 && Boolean.getBoolean("createplant.allBlocksAutoScreenshot") && !captured[page]) {
            Screenshot.grab(mc.gameDirectory, String.format(java.util.Locale.ROOT, "all-blocks-page-%02d.png", page + 1),
                mc.getMainRenderTarget(), message -> { });
            captured[page] = true;
        }
    }

    private static void prepare(Minecraft mc) {
        try {
            var server = mc.getSingleplayerServer();
            var level = server.overworld();
            level.setDayTime(6000);
            level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, server);
            List<Block> blocks = BuiltInRegistries.BLOCK.stream()
                .filter(block -> BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals(CreatePlant.MOD_ID))
                .sorted(Comparator.comparing(block -> BuiltInRegistries.BLOCK.getKey(block).toString())).toList();
            if (blocks.isEmpty()) throw new IllegalStateException("No registered mod blocks found");
            joinedPanelPage = (blocks.size() + PAGE_SIZE - 1) / PAGE_SIZE;
            pageCount = joinedPanelPage + 1;
            captured = new boolean[pageCount];
            List<Page> pages = new ArrayList<>();
            Block[] references = {AllBlocks.INDUSTRIAL_IRON_BLOCK.get(), AllBlocks.ANDESITE_ALLOY_BLOCK.get(), AllBlocks.ANDESITE_CASING.get(),
                AllBlocks.BRASS_CASING.get(), AllBlocks.COPPER_CASING.get()};
            for (int index = 0; index < joinedPanelPage; index++) {
                int zBase = index * PAGE_SPACING;
                for (int x = -5; x <= 35; x++) for (int z = -12; z <= 24; z++)
                    level.setBlock(origin.offset(x, -1, zBase + z), Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                List<Sample> samples = new ArrayList<>();
                for (int cell = 0; cell < PAGE_SIZE && index * PAGE_SIZE + cell < blocks.size(); cell++) {
                    Block block = blocks.get(index * PAGE_SIZE + cell);
                    int row = cell / COLUMNS, column = cell % COLUMNS;
                    BlockPos pos = origin.offset(column * SPACING, 0, zBase + row * SPACING);
                    BlockState state = representative(block);
                    boolean fluid = block instanceof LiquidBlock;
                    if (fluid) containFluid(level, pos);
                    level.setBlock(pos, state, Block.UPDATE_CLIENTS);
                    samples.add(new Sample(row, column, BuiltInRegistries.BLOCK.getKey(block).toString(),
                        state.toString(), position(pos), fluid));
                }
                List<Reference> referencePositions = new ArrayList<>();
                for (int ref = 0; ref < references.length; ref++) {
                    BlockPos pos = origin.offset(6 + ref * 4, 0, zBase - 8);
                    level.setBlock(pos, references[ref].defaultBlockState(), Block.UPDATE_CLIENTS);
                    referencePositions.add(new Reference(BuiltInRegistries.BLOCK.getKey(references[ref]).toString(), position(pos)));
                }
                pages.add(new Page(index, samples, referencePositions));
            }
            List<JoinedPanel> joinedPanels = prepareJoinedPanels(level);
            Files.createDirectories(RESULT_FILE.getParent());
            Files.writeString(RESULT_FILE, new GsonBuilder().setPrettyPrinting().create().toJson(new Result(
                CreatePlant.MOD_ID, blocks.size(), pageCount, COLUMNS, ROWS, SPACING, PAGE_SPACING,
                "Native default-state block models; separate joined-material panels with the original Create steel reference; unformed samples are not an operating test",
                pages, joinedPanelPage, joinedPanelPage, joinedPanels)));
            prepared = true;
        } catch (Exception exception) {
            failure = "Could not prepare all-block gallery: " + exception;
        }
    }

    private static List<JoinedPanel> prepareJoinedPanels(ServerLevel level) {
        int zBase = joinedPanelPage * PAGE_SPACING;
        for (int x = -3; x <= 17; x++) for (int z = -4; z <= 4; z++)
            level.setBlock(origin.offset(x, -1, zBase + z), Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        String[] ids = {"stainless_steel_block", "titanium_block", "industrial_iron_block", "condenser_shell"};
        List<JoinedPanel> panels = new ArrayList<>();
        for (int column = 0; column < ids.length; column++) {
            boolean reference = column == 2;
            Block block = reference ? AllBlocks.INDUSTRIAL_IRON_BLOCK.get() : BuiltInRegistries.BLOCK.get(CreatePlant.location(ids[column]));
            if (block == Blocks.AIR) throw new IllegalStateException("Missing joined-material sample: " + ids[column]);
            BlockState state = representative(block);
            boolean missingCorner = column == 0;
            List<List<Integer>> positions = new ArrayList<>();
            for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) {
                if (missingCorner && x == 1 && y == 1) continue;
                BlockPos pos = origin.offset(column * 4 + x, y, zBase);
                level.setBlock(pos, state, Block.UPDATE_CLIENTS);
                positions.add(position(pos));
            }
            if (!missingCorner && !reference) {
                BlockPos pos = origin.offset(column * 4, 0, zBase - 1);
                level.setBlock(pos, state, Block.UPDATE_CLIENTS);
                positions.add(position(pos));
            }
            String shape = reference ? "2x2 original Create steel wall"
                : missingCorner ? "2x2 wall with upper-right corner missing" : "2x2 wall with lower-left block protruding north";
            panels.add(new JoinedPanel(column, BuiltInRegistries.BLOCK.getKey(block).toString(), reference, shape, positions));
        }
        return panels;
    }

    private static BlockState representative(Block block) {
        BlockState state = block.defaultBlockState();
        if (state.hasProperty(BlockStateProperties.FACING)) state = state.setValue(BlockStateProperties.FACING, Direction.NORTH);
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        return state;
    }

    private static void containFluid(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.Plane.HORIZONTAL)
            level.setBlock(pos.relative(direction), Blocks.GLASS.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    private static List<Integer> position(BlockPos pos) { return List.of(pos.getX(), pos.getY(), pos.getZ()); }

    private static void move(Minecraft mc) {
        mc.options.fov().set(page == joinedPanelPage ? 42 : 50);
        int selected = page;
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (player == null) return;
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            float[] angles = angles();
            boolean joined = selected == joinedPanelPage;
            player.connection.teleport(origin.getX() + (joined ? 7 : -6), origin.getY() + (joined ? 4.5 : 23),
                origin.getZ() + selected * PAGE_SPACING - (joined ? 12 : 17), angles[0], angles[1]);
        });
    }

    private static float[] angles() {
        boolean joined = page == joinedPanelPage;
        double dx = joined ? 0 : 21, dy = joined ? 1 - 4.5 - 1.62 : 1.5 - 23 - 1.62, dz = joined ? 12.5 : 25;
        return new float[] {(float) Math.toDegrees(-Math.atan2(dx, dz)),
            (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)))};
    }
}
