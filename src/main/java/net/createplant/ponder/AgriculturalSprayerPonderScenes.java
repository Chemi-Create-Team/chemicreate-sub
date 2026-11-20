package net.createplant.ponder;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createplant.registry.CEProcessFluids;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;

public final class AgriculturalSprayerPonderScenes {
    private AgriculturalSprayerPonderScenes() { }

    public static void bearing(SceneBuilder builder, SceneBuildingUtil util) {
        String key = "agricultural_sprayer_bearing";
        var scene = start(builder, key, "Spraying with a rotating arm", 11);
        var pivot = new BlockPos(4, 3, 4);
        var tank = new BlockPos(5, 4, 4);
        var sprayer = new BlockPos(6, 3, 4);
        var arm = util.select().fromTo(4, 3, 4, 6, 4, 4);
        scene.world().showSection(util.select().fromTo(0, 1, 0, 10, 4, 10).substract(arm.copy()), Direction.DOWN);
        scene.idle(15);
        var moving = scene.world().showIndependentSection(arm, Direction.DOWN);
        scene.world().configureCenterOfRotation(moving, Vec3.atCenterOf(pivot));
        glue(scene, sprayer);
        caption(scene, key, "glue", sprayer);
        fill(scene, tank);
        caption(scene, key, "tank", tank);
        scene.world().setKineticSpeed(util.select().fromTo(pivot.below(2), pivot.below()), 16);
        scene.world().rotateBearing(pivot.below(), 90, 60);
        scene.world().rotateSection(moving, 0, 90, 0, 60);
        mist(scene, Vec3.atCenterOf(sprayer).add(.5, 0, 0), new Vec3(.14, .02, 0));
        grow(scene, util.select().fromTo(7, 3, 4, 9, 3, 4));
        caption(scene, key, "move", pivot.below());
        caption(scene, key, "recipes", tank);
        scene.markAsFinished();
    }

    public static void gantry(SceneBuilder builder, SceneBuildingUtil util) {
        String key = "agricultural_sprayer_gantry";
        var scene = start(builder, key, "Spraying a field with a gantry", 11);
        var sprayer = new BlockPos(2, 3, 5);
        var tank = new BlockPos(2, 4, 6);
        var assembly = util.select().fromTo(2, 3, 5, 2, 4, 7);
        scene.world().showSection(util.select().fromTo(0, 1, 0, 10, 4, 10).substract(assembly.copy()), Direction.DOWN);
        scene.idle(15);
        var moving = scene.world().showIndependentSection(assembly, Direction.DOWN);
        glue(scene, sprayer);
        caption(scene, key, "glue", sprayer);
        fill(scene, tank);
        caption(scene, key, "tank", tank);
        scene.world().setKineticSpeed(util.select().fromTo(1, 2, 7, 8, 2, 7), 32);
        scene.world().moveSection(moving, new Vec3(5, 0, 0), 75);
        for (int x = 2; x <= 7; x++) {
            mist(scene, new Vec3(x + .5, 3.5, 5), new Vec3(0, .02, -.14));
            grow(scene, util.select().fromTo(x, 3, 2, x, 3, 4));
            scene.idle(11);
        }
        caption(scene, key, "move", new BlockPos(7, 3, 5));
        caption(scene, key, "stop", new BlockPos(7, 4, 6));
        scene.markAsFinished();
    }

    public static void stationary(SceneBuilder builder, SceneBuildingUtil util) {
        String key = "agricultural_sprayer_stationary";
        var scene = start(builder, key, "Feeding a stationary sprayer", 9);
        var sprayer = new BlockPos(4, 2, 4);
        var lever = sprayer.east();
        var tank = sprayer.south(2);
        scene.world().showSection(util.select().fromTo(2, 1, 1, 6, 2, 7), Direction.DOWN);
        scene.idle(18);
        fill(scene, tank);
        scene.world().setKineticSpeed(util.select().fromTo(3, 2, 5, 4, 2, 6), 32);
        caption(scene, key, "pipe", sprayer.south());
        scene.overlay().showControls(Vec3.atCenterOf(lever), Pointing.RIGHT, 40).rightClick();
        scene.world().modifyBlock(lever, state -> state.setValue(LeverBlock.POWERED, true), false);
        scene.effects().indicateRedstone(lever);
        mist(scene, Vec3.atCenterOf(sprayer).add(0, 0, -.5), new Vec3(0, .02, -.14));
        grow(scene, util.select().fromTo(4, 2, 1, 4, 2, 3));
        caption(scene, key, "signal", lever);
        caption(scene, key, "goggles", sprayer);
        scene.markAsFinished();
    }

    private static CreateSceneBuilder start(SceneBuilder builder, String key, String title, int size) {
        var scene = new CreateSceneBuilder(builder);
        scene.title(key, PonderText.title(lang(key, "title"), title));
        scene.configureBasePlate(0, 0, size);
        scene.scaleSceneView(.8f);
        scene.showBasePlate();
        return scene;
    }

    private static void caption(CreateSceneBuilder scene, String key, String text, BlockPos at) {
        PonderText.caption(scene, Vec3.atCenterOf(at), lang(key, text), 110);
    }

    private static String lang(String key, String text) {
        return "ponder.create_refinery_n_reactors." + key + "." + text;
    }

    private static void glue(CreateSceneBuilder scene, BlockPos sprayer) {
        scene.overlay().showControls(Vec3.atCenterOf(sprayer), Pointing.DOWN, 90).withItem(AllItems.SUPER_GLUE.asStack());
    }

    private static void fill(CreateSceneBuilder scene, BlockPos tank) {
        var fertilizer = new FluidStack(CEProcessFluids.LIQUID_FERTILIZER.get(), 2000);
        scene.world().modifyBlockEntity(tank, FluidTankBlockEntity.class, be -> be.getTankInventory().setFluid(fertilizer));
        scene.overlay().showControls(Vec3.atCenterOf(tank), Pointing.DOWN, 90).withItem(fertilizer.getFluidType().getBucket(fertilizer));
    }

    private static void mist(CreateSceneBuilder scene, Vec3 nozzle, Vec3 speed) {
        scene.effects().emitParticles(nozzle, scene.effects().simpleParticleEmitter(ParticleTypes.CLOUD, speed), 4, 12);
    }

    private static void grow(CreateSceneBuilder scene, Selection crops) {
        scene.world().setBlocks(crops, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 1), false);
    }
}
