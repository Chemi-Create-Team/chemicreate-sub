package net.createplant.ponder;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.ponder.api.scene.*;
import net.minecraft.core.Direction;

public final class SeasonalThermalPonderScenes {
    private SeasonalThermalPonderScenes() { }
    public static void seasons(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);String lang = "ponder.create_refinery_n_reactors.seasonal_thermal.";
        scene.title("seasonal_thermal", PonderText.title(lang + "title", "Seasonal heating and cooling"));
        scene.configureBasePlate(0, 0, 27);scene.scaleSceneView(.095f);scene.setSceneOffsetY(-14);scene.showBasePlate();
        scene.world().showSection(util.select().everywhere().substract(util.select().layer(0)), Direction.DOWN);
        PonderText.caption(scene, null, lang + "rates", 170);
        PonderText.caption(scene, null, lang + "heating", 170);
        var controller = new net.minecraft.core.BlockPos(13, 2, 1);
        scene.world().modifyBlockEntity(controller, net.createplant.block.entity.CoolingTowerBlockEntity.class, be -> {
            var lookup = be.getLevel().registryAccess();var tag = be.writeClient(new net.minecraft.nbt.CompoundTag(), lookup);
            tag.putBoolean("Formed", true);tag.putDouble("Watts", 338_000_000);tag.putInt("Flow", 845000);tag.putString("Status", "cooling");be.readClient(tag, lookup);
        });
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) scene.effects().emitParticles(new net.minecraft.world.phys.Vec3(13 + x * 4, 37, 13 + z * 4),
            scene.effects().simpleParticleEmitter(net.createplant.registry.CEParticles.TOWER_STEAM.get(), new net.minecraft.world.phys.Vec3(.025, .22, .01)), .2f, 220);
        PonderText.caption(scene, null, lang + "steam", 170);
        scene.world().modifyBlockEntity(controller, net.createplant.block.entity.CoolingTowerBlockEntity.class, be -> {
            var lookup = be.getLevel().registryAccess();var tag = be.writeClient(new net.minecraft.nbt.CompoundTag(), lookup);
            tag.putDouble("Watts", 0);tag.putInt("Flow", 0);tag.putString("Status", "feed");be.readClient(tag, lookup);
        });
        scene.idle(160);scene.markAsFinished();
    }
}
