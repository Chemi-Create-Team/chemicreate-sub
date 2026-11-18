package net.createplant.client;

import com.google.gson.GsonBuilder;

import net.createplant.CreatePlant;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Opt-in native gallery for the supplied ore overlays and mineral item sprites. */
final class MineralClientCheck {
    private static final List<String> ORES =
            List.of(
                    "bauxite",
                    "pentlandite",
                    "ilmenite",
                    "zircon",
                    "chromite",
                    "molybdenite",
                    "uranium",
                    "halite");
    private static final List<String> ITEMS =
            List.of(
                    "uraninite",
                    "bauxite",
                    "pentlandite",
                    "ilmenite",
                    "zircon",
                    "chromite",
                    "molybdenite",
                    "fluorite");
    private static int ticks;
    private static volatile Throwable failure;

    private MineralClientCheck() {}

    static void start(Minecraft mc) {
        ticks = 0;
        failure = null;
        mc.setScreen(null);
        mc.options.hideGui = true;
        mc.options.pauseOnLostFocus = false;
        mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        mc.getSingleplayerServer()
                .execute(
                        () -> {
                            try {
                                var level = mc.getSingleplayerServer().overworld();
                                level.setDayTime(6000);
                                for (int x = -3; x <= 17; x++)
                                    for (int z = -10; z <= 9; z++)
                                        level.setBlockAndUpdate(
                                                new BlockPos(x, 80, z),
                                                Blocks.SMOOTH_STONE.defaultBlockState());
                                for (int i = 0; i < ORES.size(); i++) {
                                    for (int row = 0; row < 2; row++) {
                                        var id =
                                                CreatePlant.location(
                                                        (row == 0 ? "" : "deepslate_")
                                                                + ORES.get(i)
                                                                + "_ore");
                                        level.setBlockAndUpdate(
                                                new BlockPos(i * 2, 82 + row * 2, 6),
                                                BuiltInRegistries.BLOCK
                                                        .get(id)
                                                        .defaultBlockState());
                                    }
                                    BlockPos pos = new BlockPos(i * 2, 86, 6);
                                    level.setBlockAndUpdate(
                                            pos.south(), Blocks.SMOOTH_STONE.defaultBlockState());
                                    var frame = new ItemFrame(level, pos, Direction.NORTH);
                                    frame.setItem(
                                            BuiltInRegistries.ITEM
                                                    .get(CreatePlant.location(ITEMS.get(i)))
                                                    .getDefaultInstance());
                                    level.addFreshEntity(frame);
                                }
                                var player =
                                        mc.getSingleplayerServer()
                                                .getPlayerList()
                                                .getPlayer(mc.player.getUUID());
                                player.setGameMode(GameType.CREATIVE);
                                player.getAbilities().flying = true;
                                player.onUpdateAbilities();
                                player.connection.teleport(7.5, 83, -7, 0, 0);
                            } catch (Throwable error) {
                                failure = error;
                            }
                        });
    }

    static boolean tick(Minecraft mc) throws Exception {
        if (failure != null) throw new IllegalStateException("Mineral gallery fixture", failure);
        if (++ticks == 100) {
            var ids = new ArrayList<>(ITEMS);
            for (String ore : ORES) {
                ids.add(ore + "_ore");
                ids.add("deepslate_" + ore + "_ore");
            }
            for (String id : ids) {
                var item = BuiltInRegistries.ITEM.get(CreatePlant.location(id));
                if (item == net.minecraft.world.item.Items.AIR
                        || mc.getItemRenderer()
                                        .getModel(item.getDefaultInstance(), mc.level, mc.player, 0)
                                == mc.getModelManager().getMissingModel())
                    throw new IllegalStateException("Missing mineral model: " + id);
            }
            Files.writeString(
                    mc.gameDirectory.toPath().resolve("mineral-client-result.json"),
                    new GsonBuilder()
                            .setPrettyPrinting()
                            .create()
                            .toJson(
                                    Map.of(
                                            "itemModelsLoaded",
                                            ids.size(),
                                            "ores",
                                            ORES,
                                            "minerals",
                                            ITEMS,
                                            "purpose",
                                            "Native visual gallery; ore and deepslate rows plus"
                                                + " framed mineral sprites")));
            LoggerFactory.getLogger(MineralClientCheck.class)
                    .info(
                            "MINERAL CLIENT PASSED: 24 models; 16 ore blocks and 8 framed mineral"
                                + " items");
        }
        return ticks >= Integer.getInteger("createplant.mineralClientHold", 800);
    }
}
