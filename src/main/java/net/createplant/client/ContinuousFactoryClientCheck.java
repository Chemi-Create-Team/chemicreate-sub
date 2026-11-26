package net.createplant.client;

import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import java.util.UUID;
import net.createplant.CreatePlant;
import net.createplant.block.entity.ContinuousFactoryBlockEntity;
import net.createplant.contraptions.FactoryBodyEntity;
import net.createplant.factory.FactoryGeometry;
import net.createplant.factory.FactoryKind;
import net.createplant.registry.CEArm;
import net.createplant.registry.CEMetals;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import org.slf4j.LoggerFactory;

/** Opt-in client fixture. The equipment is assembled from the published Ponder palettes in a native server world. */
public final class ContinuousFactoryClientCheck {
    private static final FactoryKind[] KIND = FactoryKind.values();
    private static final int SHOTS = 6;
    private static int shot, warm;
    private static boolean announced;
    private static volatile boolean ready;

    private ContinuousFactoryClientCheck() { }

    private static BlockPos origin(int row) { return new BlockPos(-10 + 20 * row, -59, 1); }
    private static BlockPos controller(int row) { return origin(row).offset(4, 2, 2); }

    public static void start(Minecraft minecraft) {
        minecraft.setScreen(null);
        minecraft.options.hideGui = true;
        minecraft.options.pauseOnLostFocus = false;
        UUID player = minecraft.player.getUUID();
        var server = minecraft.getSingleplayerServer();
        server.execute(() -> {
            var level = server.overworld();
            level.setDayTime(6500);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            for (int row = 0; row < KIND.length; row++) {
                template(level, row);
                if (row == 0) { motor(level, controller(row).below(), 21); burner(level, controller(row).south(10).below()); }
                if (row == 1) {
                    burner(level, controller(row).below());
                    for (int i = 0; i < 3; i++) motor(level, controller(row).offset(3, 1, 2 + i * 3), new int[]{27, 36, 48}[i]);
                }
                if (row == 2) { motor(level, controller(row).below(), 32); motor(level, controller(row).offset(3, 1, 2), 16); }
                stock(level, row);
            }
            camera(level, player, 0);
            ready = true;
        });
    }

    public static boolean tick(Minecraft minecraft) {
        if (!ready) return false;
        if (!announced && ++warm >= 30) {
            int row = shot / 2;
            BlockPos root = controller(row);
            boolean missing = true;
            FactoryGeometry required = switch (KIND[row]) { case KILN -> FactoryGeometry.DRUM; case MILL -> FactoryGeometry.SLAB; case WINDER -> FactoryGeometry.WIRE; };
            for (var entity : minecraft.level.entitiesForRendering()) if (entity instanceof FactoryBodyEntity part && part.hasPose()
                && part.geometry() == required && part.pose(1).center().closerThan(root.getCenter(), 14)) { missing = false; break; }
            if (missing) {
                if (warm > 240) throw new IllegalStateException("No synchronized native " + required + " in the camera area");
                return false;
            }
            if (!VisualizationManager.supportsVisualization(minecraft.level)) throw new IllegalStateException("Flywheel is disabled in the continuous factory fixture");
            LoggerFactory.getLogger(ContinuousFactoryClientCheck.class).info("FACTORY_CLIENT_SHOT {} {}", KIND[row].id, shot % 2 == 0 ? "normal" : "hitboxes");
            announced = true;
            warm = 0;
        }
        if (!announced || ++warm < 80) return false;
        if (++shot == SHOTS) { minecraft.getEntityRenderDispatcher().setRenderHitBoxes(false); return true; }
        warm = 0;
        announced = false;
        minecraft.getEntityRenderDispatcher().setRenderHitBoxes(shot % 2 == 1);
        var server = minecraft.getSingleplayerServer();
        UUID player = minecraft.player.getUUID();
        if (shot % 2 == 0) server.execute(() -> { stock(server.overworld(), shot / 2); camera(server.overworld(), player, shot / 2); });
        return false;
    }

    private static void template(ServerLevel level, int row) {
        String path = "/assets/" + CreatePlant.MOD_ID + "/ponder/plant/" + KIND[row].id + ".nbt";
        try (var stream = ContinuousFactoryClientCheck.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing " + path);
            var template = new StructureTemplate();
            template.load(level.registryAccess().lookupOrThrow(Registries.BLOCK), NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap()));
            var at = origin(row);
            if (!template.placeInWorld(level, at, at, new StructurePlaceSettings().setIgnoreEntities(true), level.getRandom(), Block.UPDATE_ALL))
                throw new IllegalStateException("Failed to place " + path);
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
    }

    private static void motor(ServerLevel level, BlockPos at, int rpm) {
        ((CreativeMotorBlockEntity) level.getBlockEntity(at)).generatedSpeed.setValue(rpm);
    }
    private static void burner(ServerLevel level, BlockPos at) { ((BlazeBurnerBlockEntity) level.getBlockEntity(at)).isCreative = true; }

    private static void stock(ServerLevel level, int row) {
        var machine = (ContinuousFactoryBlockEntity) level.getBlockEntity(controller(row));
        switch (KIND[row]) {
            case KILN -> machine.inlet(Direction.UP).insertItem(0, new ItemStack(Items.CALCITE, 48), false);
            case MILL -> machine.inlet(Direction.UP).insertItem(0, CEMetals.STEEL_BLOCK.toStack(48), false);
            case WINDER -> {
                machine.inlet(Direction.UP).insertItem(0, CEArm.ENAMELLED_COPPER_WIRE.toStack(64), false);
                machine.inlet(Direction.WEST).insertItem(0, com.simibubi.create.AllBlocks.BRASS_CASING.asStack(32), false);
            }
        }
    }

    private static void camera(ServerLevel level, UUID id, int row) {
        var player = level.getServer().getPlayerList().getPlayer(id);
        var focus = controller(row).getCenter().add(0, row == 0 ? 2.2 : 1.4, row == 2 ? 2.9 : 5.6);
        var feet = focus.add(row == 0 ? 9 : row == 1 ? 6.5 : 7, row == 0 ? 2.8 : 1.7, row == 1 ? -9.4 : 5.7);
        var delta = focus.subtract(feet.add(0, 1.62, 0));
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.connection.teleport(feet.x, feet.y, feet.z, (float)Math.toDegrees(Math.atan2(-delta.x, delta.z)),
            (float)-Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance())));
    }
}
