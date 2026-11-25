package net.createplant.ponder;

import com.google.gson.JsonParser;
import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createplant.CreatePlant;
import net.createplant.arm.ArmRecipe;
import net.createplant.arm.ArmTool;
import net.createplant.arm.Trajectory;
import net.createplant.block.entity.AssemblyJigBlockEntity;
import net.createplant.block.entity.IndustrialArmBlockEntity;
import net.createplant.registry.CEArm;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** The welded iron beam sample: the gripper sets two iron blocks, then the torch welds their seam. The jig and arm play the same data the server runs. */
public final class ArmPonderScenes {
    private static final String ID = "industrial_arm";
    private static final String BEAM = "{\"size\":[2,1,1],\"stages\":[[{\"pos\":[0,0,0],\"action\":\"place\",\"tool\":\"gripper\",\"block\":\"minecraft:iron_block\"},"
        + "{\"pos\":[1,0,0],\"action\":\"place\",\"tool\":\"gripper\",\"block\":\"minecraft:iron_block\"}],"
        + "[{\"pos\":[0,0,0],\"action\":\"process\",\"tool\":\"welding_torch\",\"pattern\":\"seam_weld\",\"time\":60}]],"
        + "\"result\":{\"id\":\"create_refinery_n_reactors:welded_iron_beam\"}}";
    private static final BlockPos JIG = new BlockPos(2, 2, 4), ARM = new BlockPos(6, 3, 4), CHEST = new BlockPos(6, 3, 6);
    private static final int FRAMES = 24;
    private ArmPonderScenes() { }

    public static void arm(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);
        var recipe = ArmRecipe.fromJson(CreatePlant.location("welded_iron_beam"), JsonParser.parseString(BEAM).getAsJsonObject());
        scene.title(ID, PonderText.title("ponder.create_refinery_n_reactors." + ID + ".title", ID));
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(.75f);
        scene.showBasePlate();
        scene.world().showSection(util.select().fromTo(2, 1, 4, 2, 2, 4), Direction.DOWN);
        scene.overlay().showControls(util.vector().topOf(JIG), Pointing.DOWN, 50).rightClick().withItem(AllItems.WRENCH.asStack());
        scene.world().modifyBlockEntity(JIG, AssemblyJigBlockEntity.class, be -> be.setRecipe(recipe));
        text(scene, util, JIG, "jig");

        scene.world().showSection(util.select().fromTo(6, 1, 4, 6, 3, 4), Direction.DOWN);
        scene.world().setKineticSpeed(util.select().fromTo(6, 1, 4, 6, 3, 4), 64);
        text(scene, util, ARM, "arm");
        scene.overlay().showControls(util.vector().topOf(ARM), Pointing.DOWN, 50).rightClick().withItem(tool(ArmTool.GRIPPER));
        scene.world().modifyBlockEntity(ARM, IndustrialArmBlockEntity.class, be -> be.toolSlot.setStackInSlot(0, tool(ArmTool.GRIPPER)));
        text(scene, util, ARM, "tool");
        scene.world().showSection(util.select().position(CHEST), Direction.DOWN);
        text(scene, util, CHEST, "supply");

        text(scene, util, JIG.above(), "grip");
        for (int step = 0; step < 2; step++) {
            work(scene, recipe, step);
            BlockPos cell = JIG.above().offset(recipe.steps().get(step).rel());
            scene.world().setBlock(cell, Blocks.IRON_BLOCK.defaultBlockState(), true);
            finish(scene, step);
        }
        scene.overlay().showControls(util.vector().topOf(ARM), Pointing.DOWN, 50).rightClick().withItem(tool(ArmTool.WELDING_TORCH));
        scene.world().modifyBlockEntity(ARM, IndustrialArmBlockEntity.class, be -> be.toolSlot.setStackInSlot(0, tool(ArmTool.WELDING_TORCH)));
        scene.idle(10);
        text(scene, util, JIG.above(), "weld");
        work(scene, recipe, 2);
        finish(scene, 2);
        scene.world().modifyBlockEntity(ARM, IndustrialArmBlockEntity.class, be -> be.aim(IndustrialArmBlockEntity.IDLE, null));
        scene.idle(20);
        for (int step = 0; step < 2; step++) scene.world().setBlock(JIG.above().offset(recipe.steps().get(step).rel()), Blocks.AIR.defaultBlockState(), false);
        Vec3 top = util.vector().topOf(JIG);
        scene.world().createItemEntity(top.add(.5, .2, 0), new Vec3(0, .1, 0), new ItemStack(CEArm.WELDED_IRON_BEAM.get()));
        scene.effects().emitParticles(top.add(.5, .3, 0), scene.effects().simpleParticleEmitter(ParticleTypes.CLOUD, new Vec3(0, .05, 0)), 6, 20);
        text(scene, util, JIG, "done");
        scene.markAsFinished();
    }

    /** One step played from start to end: the jig's progress and the arm's tip follow the same trajectory the server uses. */
    private static void work(CreateSceneBuilder scene, ArmRecipe recipe, int step) {
        var s = recipe.steps().get(step);
        Trajectory path = Trajectory.of(s.pattern(), s.seamDirection());
        Vec3 origin = Vec3.atLowerCornerOf(JIG.above().offset(s.rel()));
        for (int frame = 1; frame <= FRAMES; frame++) {
            float progress = frame / (float) FRAMES;
            Vec3 tip = origin.add(path.tip(progress)).add(0, s.place() ? 0 : IndustrialArmBlockEntity.HOVER, 0);
            scene.world().modifyBlockEntity(JIG, AssemblyJigBlockEntity.class, be -> {
                be.status[step] = AssemblyJigBlockEntity.RESERVED;
                be.progress[step] = progress;
            });
            scene.world().modifyBlockEntity(ARM, IndustrialArmBlockEntity.class, be -> be.aim(IndustrialArmBlockEntity.WORK, tip));
            scene.idle(frame == 1 ? 12 : 2);
        }
    }

    private static void finish(CreateSceneBuilder scene, int step) {
        scene.world().modifyBlockEntity(JIG, AssemblyJigBlockEntity.class, be -> {
            be.status[step] = AssemblyJigBlockEntity.DONE;
            be.progress[step] = 1;
            be.doneAt[step] = be.getLevel().getGameTime();
        });
    }

    private static ItemStack tool(ArmTool tool) {
        return new ItemStack(CEArm.TOOLS.get(tool).get());
    }

    private static void text(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos at, String key) {
        PonderText.caption(scene, util.vector().centerOf(at), "ponder.create_refinery_n_reactors." + ID + "." + key, 120);
    }
}
