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

/** The welded iron beam sample: the bare arm sets two iron blocks, then the torch welds their seam. The jig and arm play the same data the server runs. */
public final class ArmPonderScenes {
    private static final String ID = "industrial_arm";
    private static final String BEAM = "{\"size\":[2,1,1],\"stages\":[[{\"pos\":[0,0,0],\"action\":\"place\",\"tool\":\"hand\",\"block\":\"minecraft:iron_block\"},"
        + "{\"pos\":[1,0,0],\"action\":\"place\",\"tool\":\"hand\",\"block\":\"minecraft:iron_block\"}],"
        + "[{\"pos\":[0,0,0],\"action\":\"process\",\"tool\":\"welding_torch\",\"pattern\":\"seam_weld\",\"time\":60}]],"
        + "\"result\":{\"id\":\"create_refinery_n_reactors:welded_iron_beam\"}}";
    /** The magnetic core memory as the data pack has it, on the jig's own cell so this arm reaches every step. */
    private static final String MEMORY = "{\"size\":[1,1,1],\"stages\":["
        + "[{\"pos\":[0,0,0],\"action\":\"lay\",\"tool\":\"hand\",\"item\":\"create:iron_sheet\"}],"
        + "[{\"pos\":[0,0,0],\"action\":\"process\",\"tool\":\"hand\",\"pattern\":\"core_set\",\"consume\":{\"id\":\"create_refinery_n_reactors:ferrite_core\",\"count\":144},\"time\":60}],"
        + "[{\"pos\":[0,0,0],\"action\":\"process\",\"tool\":\"hand\",\"pattern\":\"wire_thread\",\"helpers\":1,\"consume\":{\"id\":\"create_refinery_n_reactors:enamelled_copper_wire\",\"count\":25},\"time\":120}],"
        + "[{\"pos\":[0,0,0],\"action\":\"process\",\"tool\":\"welding_torch\",\"pattern\":\"seam_weld\",\"time\":50}]],"
        + "\"result\":{\"id\":\"create_refinery_n_reactors:magnetic_core_memory\"}}";
    private static final BlockPos JIG = new BlockPos(2, 2, 4), ARM = new BlockPos(5, 3, 4), CHEST = new BlockPos(5, 3, 6), DEPOT = new BlockPos(5, 1, 2);
    /** The second, bare arm of the memory scene, which holds the far end of each wire while the first one threads it. */
    private static final BlockPos HELPER = new BlockPos(0, 1, 4);
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

        scene.world().showSection(util.select().fromTo(5, 1, 4, 5, 3, 4), Direction.DOWN);
        scene.world().setKineticSpeed(util.select().fromTo(5, 1, 4, 5, 3, 4), 64);
        text(scene, util, ARM, "arm");
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
        scene.overlay().showControls(util.vector().topOf(ARM), Pointing.DOWN, 50).rightClick().withItem(torch());
        scene.world().modifyBlockEntity(ARM, IndustrialArmBlockEntity.class, be -> be.toolSlot.setStackInSlot(0, torch()));
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
        // Points are chosen as with Create's arm: right-click with the arm in hand, once to take from, twice to put to.
        scene.overlay().showControls(util.vector().topOf(DEPOT), Pointing.DOWN, 40).rightClick().withItem(new ItemStack(CEArm.ARM_ITEM.get()));
        scene.overlay().chaseBoundingBoxOutline(net.createmod.ponder.api.PonderPalette.INPUT, DEPOT, new net.minecraft.world.phys.AABB(DEPOT), 200);
        scene.idle(20);
        scene.overlay().showControls(util.vector().topOf(CHEST), Pointing.DOWN, 40).rightClick().withItem(new ItemStack(CEArm.ARM_ITEM.get()));
        scene.overlay().chaseBoundingBoxOutline(net.createmod.ponder.api.PonderPalette.OUTPUT, CHEST, new net.minecraft.world.phys.AABB(CHEST), 200);
        scene.idle(10);
        text(scene, util, DEPOT, "points");
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
        // the finished anvil goes to the chosen output
        scene.world().removeItemsFromBelt(DEPOT);
        Vec3 chestTop = Vec3.atBottomCenterOf(CHEST).add(0, 1, 0);
        scene.world().modifyBlockEntity(ARM, IndustrialArmBlockEntity.class, be -> {
            be.held = new ItemStack(Blocks.CHIPPED_ANVIL);
            be.aim(IndustrialArmBlockEntity.CARRY, chestTop);
        });
        scene.idle(25);
        scene.world().modifyBlockEntity(ARM, IndustrialArmBlockEntity.class, be -> {
            be.held = ItemStack.EMPTY;
            be.aim(IndustrialArmBlockEntity.IDLE, null);
        });
        scene.idle(30);

        scene.markAsFinished();
    }

    /** One step played from start to end: the jig's progress and the arm's tip follow the same trajectory the server uses. */
    private static void work(CreateSceneBuilder scene, ArmRecipe recipe, int step) {
        work(scene, recipe, step, FRAMES, 2);
    }

    /** The magnetic core memory, start to end: parts, the iron sheet, 144 cores, 25 enamelled wires, soldering, the product. */
    public static void memory(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);
        String id = "magnetic_core_memory";
        var recipe = ArmRecipe.fromJson(CreatePlant.location(id), JsonParser.parseString(MEMORY).getAsJsonObject());
        scene.title(id, PonderText.title("ponder.create_refinery_n_reactors." + id + ".title", id));
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(.75f);
        scene.showBasePlate();
        scene.world().showSection(util.select().fromTo(2, 1, 4, 2, 2, 4), Direction.DOWN);
        scene.world().showSection(util.select().fromTo(5, 1, 4, 5, 3, 4), Direction.DOWN);
        scene.world().setKineticSpeed(util.select().fromTo(5, 1, 4, 5, 3, 4), 64);
        scene.world().showSection(util.select().position(CHEST), Direction.DOWN);
        scene.world().modifyBlockEntity(JIG, AssemblyJigBlockEntity.class, be -> be.setRecipe(recipe));
        scene.idle(10);

        scene.overlay().showControls(util.vector().topOf(CHEST), Pointing.DOWN, 60).withItem(new ItemStack(CEArm.FERRITE_CORE.get()));
        text(scene, util, CHEST, id, "cores");
        scene.overlay().showControls(util.vector().topOf(CHEST), Pointing.DOWN, 60).withItem(new ItemStack(CEArm.ENAMELLED_COPPER_WIRE.get()));
        text(scene, util, CHEST, id, "wire");

        text(scene, util, JIG, id, "sheet");
        work(scene, recipe, 0);
        finish(scene, 0);

        text(scene, util, JIG.above(), id, "set");
        work(scene, recipe, 1, 72, 1);
        finish(scene, 1);
        scene.world().setBlock(HELPER, CEArm.ARM.get().defaultBlockState(), false);
        scene.world().showSection(util.select().position(HELPER), Direction.DOWN);
        scene.world().setKineticSpeed(util.select().position(HELPER), 64);
        scene.idle(10);
        text(scene, util, HELPER, id, "thread");
        work(scene, recipe, 2, 200, 1);
        finish(scene, 2);
        scene.world().modifyBlockEntity(HELPER, IndustrialArmBlockEntity.class, be -> be.aim(IndustrialArmBlockEntity.IDLE, null));

        scene.overlay().showControls(util.vector().topOf(ARM), Pointing.DOWN, 40).rightClick().withItem(torch());
        scene.world().modifyBlockEntity(ARM, IndustrialArmBlockEntity.class, be -> be.toolSlot.setStackInSlot(0, torch()));
        scene.idle(10);
        text(scene, util, JIG.above(), id, "solder");
        work(scene, recipe, 3);
        finish(scene, 3);
        scene.world().modifyBlockEntity(ARM, IndustrialArmBlockEntity.class, be -> be.aim(IndustrialArmBlockEntity.IDLE, null));
        scene.idle(30);
        scene.world().modifyBlockEntity(JIG, AssemblyJigBlockEntity.class, be -> be.setRecipe(recipe));
        Vec3 top = util.vector().topOf(JIG);
        scene.world().createItemEntity(top.add(0, .2, 0), new Vec3(0, .1, 0), new ItemStack(CEArm.MAGNETIC_CORE_MEMORY.get()));
        scene.effects().emitParticles(top.add(0, .3, 0), scene.effects().simpleParticleEmitter(ParticleTypes.CLOUD, new Vec3(0, .05, 0)), 6, 20);
        text(scene, util, JIG, id, "done");
        scene.markAsFinished();
    }

    private static void work(CreateSceneBuilder scene, ArmRecipe recipe, int step, int frames, int ticksPerFrame) {
        var s = recipe.steps().get(step);
        Trajectory path = Trajectory.of(s.pattern(), s.seamDirection());
        Vec3 origin = Vec3.atLowerCornerOf(JIG.above().offset(s.rel())).add(0, recipe.base(s), 0);
        for (int frame = 1; frame <= frames; frame++) {
            float progress = frame / (float) frames;
            Vec3 tip = origin.add(path.tip(progress)).add(0, s.place() ? 0 : IndustrialArmBlockEntity.HOVER, 0);
            scene.world().modifyBlockEntity(JIG, AssemblyJigBlockEntity.class, be -> {
                be.status[step] = AssemblyJigBlockEntity.RESERVED;
                be.progress[step] = progress;
            });
            scene.world().modifyBlockEntity(ARM, IndustrialArmBlockEntity.class, be -> {
                // the bare arm holds what it works with: the block, the sheet, the cores, the wire
                be.held = s.tool() == ArmTool.HAND && s.material() != null ? new ItemStack(s.material()) : ItemStack.EMPTY;
                be.aim(IndustrialArmBlockEntity.WORK, tip);
            });
            if (s.helpers() > 0) {
                Vec3 end = origin.add(path.runEnd(progress)).add(0, IndustrialArmBlockEntity.HOVER + IndustrialArmBlockEntity.HELPER_LIFT, 0);
                scene.world().modifyBlockEntity(HELPER, IndustrialArmBlockEntity.class, be -> be.aim(IndustrialArmBlockEntity.ASSIST, end));
            }
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

    private static ItemStack torch() {
        return new ItemStack(CEArm.WELDING_TORCH.get());
    }

    private static void text(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos at, String key) {
        text(scene, util, at, ID, key);
    }

    private static void text(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos at, String id, String key) {
        PonderText.caption(scene, util.vector().centerOf(at), "ponder.create_refinery_n_reactors." + id + "." + key, 120);
    }
}
