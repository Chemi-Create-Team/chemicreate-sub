package net.createplant.ponder;

import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import java.util.UUID;
import net.createplant.block.entity.HighPressurePumpBlockEntity;
import net.createplant.block.entity.HoseConnectorBlockEntity;
import net.createplant.registry.CEPressureInfrastructure;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class PressureInfrastructurePonderScenes {
    private static final String KEY = "ponder.create_refinery_n_reactors.pressure_infrastructure.";
    private PressureInfrastructurePonderScenes() { }

    public static void pump(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);
        scene.title("pressure_infrastructure", PonderText.title(KEY + "title", "Driving a pressure pump and linking a hose"));
        scene.configureBasePlate(0, 0, 11);
        scene.scaleSceneView(.8f);
        scene.showBasePlate();
        var drive = new BlockPos(3, 2, 5);
        var intake = new BlockPos(3, 1, 6);
        var socket = new BlockPos(8, 2, 3);
        scene.world().showSection(util.select().fromTo(3, 1, 5, 4, 2, 6), Direction.DOWN);
        scene.idle(20);
        cue(scene, drive.below(), CEPressureInfrastructure.PUMP_ITEM.toStack());
        text(scene, drive.below(), "place");
        scene.world().showSection(util.select().fromTo(3, 1, 7, 3, 1, 8), Direction.SOUTH);
        text(scene, intake, "intake");
        scene.world().showSection(util.select().fromTo(drive.above(), drive.above(2)), Direction.DOWN);
        scene.world().setKineticSpeed(util.select().fromTo(drive, drive.above(2)), 48);
        text(scene, drive.above(), "drive");
        scene.world().showSection(util.select().fromTo(8, 1, 3, 8, 2, 4), Direction.DOWN);
        cue(scene, drive, CEPressureInfrastructure.HOSE.toStack());
        text(scene, drive, "first");
        cue(scene, socket, CEPressureInfrastructure.HOSE.toStack());
        var id = new UUID(0, 45);
        scene.world().modifyBlockEntity(drive, HighPressurePumpBlockEntity.class, be ->
            be.readClient(link(be.writeClient(new CompoundTag(), be.getLevel().registryAccess()), socket, Direction.NORTH, id),
                be.getLevel().registryAccess()));
        scene.world().modifyBlockEntity(socket, HoseConnectorBlockEntity.class, be ->
            be.readClient(link(be.writeClient(new CompoundTag(), be.getLevel().registryAccess()), drive, Direction.NORTH, id),
                be.getLevel().registryAccess()));
        text(scene, socket, "second");
        text(scene, drive, "goggles");
        cue(scene, socket, AllItems.WRENCH.asStack());
        text(scene, socket, "remove");
        scene.world().modifyBlockEntity(drive, HighPressurePumpBlockEntity.class, be ->
            be.readClient(unlink(be.writeClient(new CompoundTag(), be.getLevel().registryAccess())),
                be.getLevel().registryAccess()));
        scene.world().modifyBlockEntity(socket, HoseConnectorBlockEntity.class, be ->
            be.readClient(unlink(be.writeClient(new CompoundTag(), be.getLevel().registryAccess())),
                be.getLevel().registryAccess()));
        scene.idle(20);
        cue(scene, new BlockPos(6, 1, 6), CEPressureInfrastructure.HELMET.toStack());
        text(scene, new BlockPos(6, 1, 6), "hazard");
        scene.markAsFinished();
    }

    private static CompoundTag link(CompoundTag tag, BlockPos peer, Direction face, UUID id) {
        tag.putUUID("HoseId", id);
        tag.putLong("HoseEnd", peer.asLong());
        tag.putString("HoseFace", face.getName());
        return tag;
    }

    private static CompoundTag unlink(CompoundTag tag) {
        tag.remove("HoseId");
        tag.remove("HoseEnd");
        tag.remove("HoseFace");
        return tag;
    }

    private static void cue(CreateSceneBuilder scene, BlockPos pos, ItemStack stack) {
        scene.overlay().showControls(Vec3.atCenterOf(pos), Pointing.DOWN, 90).rightClick().withItem(stack);
    }

    private static void text(CreateSceneBuilder scene, BlockPos pos, String key) {
        PonderText.caption(scene, Vec3.atCenterOf(pos), KEY + key, 110);
    }
}
