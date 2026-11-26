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
    /** One cell of the magnetic core memory's wiring stage, to show the threading up close. */
    private static final String CORE = "{\"size\":[1,1,1],\"stages\":[[{\"pos\":[0,0,0],\"action\":\"place\",\"tool\":\"gripper\",\"block\":\"minecraft:iron_block\"}],"
        + "[{\"pos\":[0,0,0],\"action\":\"process\",\"tool\":\"wiring_head\",\"pattern\":\"grid_wire\",\"time\":120}]],"
        + "\"result\":{\"id\":\"create_refinery_n_reactors:magnetic_core_memory\"}}";
    private static final BlockPos JIG = new BlockPos(2, 2, 4), ARM = new BlockPos(5, 3, 4), CHEST = new BlockPos(5, 3, 6), DEPOT = new BlockPos(5, 1, 2);
    private static final int FRAMES = 24, WIRE_FRAMES = 240;
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

        scene.world().showSection(util.select().fromTo(5, 1, 4, 5, 3, 4), Direction.DOWN);
        scene.world().setKineticSpeed(util.select().fromTo(5, 1, 4, 5, 3, 4), 64);
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
            scene.world().setBlock(cell, Blocks.IRON_BLOCK.defaultBlockState(), false);
            scene.world().showSection(util.select().position(cell), Direction.DOWN);
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

        // Belt work: the same arm welds a damaged anvil on a depot back to a chipped one, holding it still like Create's arm does.
        scene.world().setBlock(DEPOT, com.simibubi.create.AllBlocks.DEPOT.getDefaultState(), false);
        scene.world().showSection(util.select().position(DEPOT), Direction.DOWN);
        scene.idle(10);
        scene.world().createItemOnBeltLike(DEPOT, Direction.UP, new ItemStack(Blocks.DAMAGED_ANVIL));
        text(scene, util, DEPOT, "belt");
        Trajectory path = Trajectory.of(net.createplant.arm.ArmPattern.defaultFor(ArmTool.WELDING_TORCH), null);
        Vec3 item = Vec3.atLowerCornerOf(DEPOT).add(.5, 13 / 16f + IndustrialArmBlockEntity.HOVER, .5);
        for (int frame = 1; frame <= FRAMES; frame++) {
            Vec3 p = path.tip(frame / (float) FRAMES);
            Vec3 tip = item.add((p.x - .5) * .45, 0, (p.z - .5) * .45);
            scene.world().modifyBlockEntity(ARM, IndustrialArmBlockEntity.class, be -> be.aim(IndustrialArmBlockEntity.WORK, tip));
            scene.idle(frame == 1 ? 12 : 3);
        }
        scene.world().removeItemsFromBelt(DEPOT);
        scene.world().createItemOnBeltLike(DEPOT, Direction.UP, new ItemStack(Blocks.CHIPPED_ANVIL));
        scene.world().modifyBlockEntity(ARM, IndustrialArmBlockEntity.class, be -> be.aim(IndustrialArmBlockEntity.IDLE, null));
        scene.idle(30);

        // Core memory wiring: cores set row by row, then the X, Y and sense wires threaded through each of them.
        var core = ArmRecipe.fromJson(CreatePlant.location("magnetic_core_memory"), JsonParser.parseString(CORE).getAsJsonObject());
        scene.world().removeItemsFromBelt(DEPOT);
        scene.world().modifyBlockEntity(JIG, AssemblyJigBlockEntity.class, be -> be.setRecipe(core));
        scene.world().setBlock(JIG.above(), Blocks.IRON_BLOCK.defaultBlockState(), false);
        scene.world().showSection(util.select().position(JIG.above()), Direction.DOWN);
        finish(scene, 0);
        scene.overlay().showControls(util.vector().topOf(ARM), Pointing.DOWN, 50).rightClick().withItem(tool(ArmTool.WIRING_HEAD));
        scene.world().modifyBlockEntity(ARM, IndustrialArmBlockEntity.class, be -> be.toolSlot.setStackInSlot(0, tool(ArmTool.WIRING_HEAD)));
        scene.idle(10);
        text(scene, util, JIG.above(), "wire");
        work(scene, core, 1, WIRE_FRAMES, 1);
        finish(scene, 1);
        scene.world().modifyBlockEntity(ARM, IndustrialArmBlockEntity.class, be -> be.aim(IndustrialArmBlockEntity.IDLE, null));
        scene.idle(40);
        scene.markAsFinished();
    }

    /** One step played from start to end: the jig's progress and the arm's tip follow the same trajectory the server uses. */
    private static void work(CreateSceneBuilder scene, ArmRecipe recipe, int step) {
        work(scene, recipe, step, FRAMES, 2);
    }

    private static void work(CreateSceneBuilder scene, ArmRecipe recipe, int step, int frames, int ticksPerFrame) {
        var s = recipe.steps().get(step);
        Trajectory path = Trajectory.of(s.pattern(), s.seamDirection());
        Vec3 origin = Vec3.atLowerCornerOf(JIG.above().offset(s.rel()));
        for (int frame = 1; frame <= frames; frame++) {
            float progress = frame / (float) frames;
            Vec3 tip = origin.add(path.tip(progress)).add(0, s.place() ? 0 : IndustrialArmBlockEntity.HOVER, 0);
            scene.world().modifyBlockEntity(JIG, AssemblyJigBlockEntity.class, be -> {
                be.status[step] = AssemblyJigBlockEntity.RESERVED;
                be.progress[step] = progress;
            });
            scene.world().modifyBlockEntity(ARM, IndustrialArmBlockEntity.class, be -> {
                be.held = s.place() ? new ItemStack(s.block()) : ItemStack.EMPTY;
                be.aim(IndustrialArmBlockEntity.WORK, tip);
            });
            scene.idle(frame == 1 ? 12 : ticksPerFrame);
        }
    }

    private static void finish(CreateSceneBuilder scene, int step) {
        scene.world().modifyBlockEntity(ARM, IndustrialArmBlockEntity.class, be -> be.held = ItemStack.EMPTY);
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
