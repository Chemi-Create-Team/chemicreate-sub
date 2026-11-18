package net.createplant.ponder;

import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.*;
import net.createplant.block.entity.GasifierBlockEntity;
import net.createplant.registry.CEItems;
import net.createplant.registry.CEProcessFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

public final class GasifierPonderScenes {
    private static final String LANG = "ponder.create_refinery_n_reactors.entrained_gasifier.";
    private GasifierPonderScenes() { }

    public static void processing(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);
        scene.title("entrained_gasifier", PonderText.title(LANG + "title", "Steam gasifier"));
        scene.configureBasePlate(3, 3, 7);
        scene.scaleSceneView(.58f);
        scene.setSceneOffsetY(-1);
        scene.showBasePlate();
        var heater = new BlockPos(6, 1, 6);
        var controller = heater.above();
        scene.world().showSection(util.select().position(heater), Direction.DOWN);
        caption(scene, util, heater, "heater");
        scene.overlay().showControls(util.vector().topOf(heater), Pointing.DOWN, 55)
            .rightClick().withItem(CEProcessFluids.WATER_GAS.get().getBucket().getDefaultInstance());
        scene.world().modifyBlock(heater, state -> state.setValue(BlazeBurnerBlock.HEAT_LEVEL,
            BlazeBurnerBlock.HeatLevel.SEETHING), false);
        caption(scene, util, heater, "fuel_bucket");
        var fuelSupply = util.select().fromTo(6, 1, 3, 7, 1, 5);
        scene.world().showSection(fuelSupply, Direction.NORTH);
        scene.world().setKineticSpeed(fuelSupply, 32);
        scene.world().setKineticSpeed(util.select().position(6, 1, 4), -32);
        caption(scene, util, heater.north(), "fuel_pipe");
        PonderText.build(scene, util, 5, 2, 5, 7, 4, 7, Direction.DOWN);
        caption(scene, util, new BlockPos(6, 4, 6), "base");
        PonderText.build(scene, util, 5, 5, 5, 7, 7, 7, Direction.DOWN);
        caption(scene, util, new BlockPos(6, 5, 7), "nozzles");
        scene.world().showSection(util.select().position(6, 5, 4).add(util.select().position(6, 5, 8))
            .add(util.select().fromTo(6, 8, 6, 7, 9, 6)), Direction.DOWN);
        scene.overlay().showControls(util.vector().centerOf(6, 5, 8), Pointing.DOWN, 50)
            .withItem(CEItems.CARBON_POWDER.toStack());
        caption(scene, util, new BlockPos(6, 5, 5), "feed");
        scene.world().modifyBlockEntity(controller, GasifierBlockEntity.class, be -> be.preview("preheat", 900));
        caption(scene, util, controller, "preheat");
        scene.world().modifyBlockEntity(controller, GasifierBlockEntity.class, be -> be.preview("running", 1450));
        var pumpDrive = util.select().fromTo(7, 8, 6, 7, 9, 6);
        scene.world().setKineticSpeed(pumpDrive, 32);
        scene.world().setKineticSpeed(util.select().position(6, 9, 6), -32);
        scene.effects().emitParticles(new Vec3(6.5, 6.25, 6.5),
            scene.effects().simpleParticleEmitter(ParticleTypes.CLOUD, new Vec3(0, .025, 0)), 1, 70);
        caption(scene, util, new BlockPos(6, 8, 6), "output");
        scene.world().setKineticSpeed(fuelSupply, 0);
        scene.world().modifyBlock(heater, state -> state.setValue(BlazeBurnerBlock.HEAT_LEVEL,
            BlazeBurnerBlock.HeatLevel.SMOULDERING), false);
        scene.world().modifyBlockEntity(controller, GasifierBlockEntity.class, be -> be.preview("heat_required", 1100));
        caption(scene, util, heater, "stop");
        scene.markAsFinished();
    }

    private static void caption(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos pos, String key) {
        PonderText.caption(scene, util.vector().centerOf(pos), LANG + key, 130);
    }
}
