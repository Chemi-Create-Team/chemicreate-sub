package net.createplant.ponder;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createplant.CreatePlant;
import net.createplant.arm.ArmRecipe;
import net.createplant.arm.Trajectory;
import net.createplant.block.entity.IndustrialArmBlockEntity;
import net.createplant.registry.CEArm;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The arms at work on a depot: the welded iron beam (a bare arm sets a second iron block on the first, the torch welds them) and the
 * magnetic core memory (cores, two bare arms threading the wire, soldering). The arms play the same paths the server runs.
 */
public final class ArmPonderScenes {
    private static final String ID = "industrial_arm";
    /** The sample recipes as the data pack has them. */
    private static final String BEAM = "{\"input\":\"minecraft:iron_block\",\"steps\":[{\"pattern\":\"place\",\"consume\":{\"id\":\"minecraft:iron_block\"},\"time\":40},"
        + "{\"tool\":\"welding_torch\",\"time\":60}],\"result\":{\"id\":\"create_refinery_n_reactors:welded_iron_beam\"}}";
    private static final String MEMORY = "{\"input\":\"create:iron_sheet\",\"transitional\":\"create_refinery_n_reactors:incomplete_magnetic_core_memory\",\"steps\":["
        + "{\"pattern\":\"core_set\",\"consume\":{\"id\":\"create_refinery_n_reactors:ferrite_core\",\"count\":144},\"time\":60},"
        + "{\"pattern\":\"wire_thread\",\"helpers\":1,\"consume\":{\"id\":\"create_refinery_n_reactors:enamelled_copper_wire\",\"count\":25},\"time\":120},"
        + "{\"tool\":\"welding_torch\",\"time\":50}],\"result\":{\"id\":\"create_refinery_n_reactors:magnetic_core_memory\"}}";
    /** The schematic's work stand gets a depot on top; the arm, its chest and, for threading, a second arm stand around it. */
    private static final BlockPos DEPOT = new BlockPos(2, 2, 4), ARM = new BlockPos(5, 3, 4), CHEST = new BlockPos(5, 3, 6), OUT = new BlockPos(5, 1, 2),
        HELPER = new BlockPos(0, 1, 4), WIRE_SUPPLY = new BlockPos(6, 3, 6);
    private static final int FRAMES = 24;
    private ArmPonderScenes() { }

    private static ArmRecipe recipe(String json) {
        return ArmRecipe.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    private static void stage(CreateSceneBuilder scene, SceneBuildingUtil util, String id) {
        scene.title(id, PonderText.title("ponder.create_refinery_n_reactors." + id + ".title", id));
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(.75f);
        scene.showBasePlate();
        scene.world().setBlock(DEPOT, AllBlocks.DEPOT.getDefaultState(), false);
        scene.world().showSection(util.select().fromTo(2, 1, 4, 2, 2, 4), Direction.DOWN);
        scene.world().showSection(util.select().fromTo(5, 1, 4, 5, 3, 4), Direction.DOWN);
        scene.world().setKineticSpeed(util.select().fromTo(5, 1, 4, 5, 3, 4), 64);
        scene.world().showSection(util.select().position(CHEST), Direction.DOWN);
    }

    public static void arm(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);
        ArmRecipe beam = recipe(BEAM);
        stage(scene, util, ID);
        text(scene, util, ARM, "arm");
        text(scene, util, ARM, "tool");
        // Points are chosen as with Create's arm: right-click with the arm in hand, once to take from, twice to put to.
        scene.overlay().showControls(util.vector().topOf(DEPOT), Pointing.DOWN, 40).rightClick().withItem(new ItemStack(CEArm.ARM_ITEM.get()));
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.INPUT, DEPOT, new AABB(DEPOT), 200);
        scene.idle(15);
        scene.overlay().showControls(util.vector().topOf(CHEST), Pointing.DOWN, 40).rightClick().withItem(new ItemStack(CEArm.ARM_ITEM.get()));
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.INPUT, CHEST, new AABB(CHEST), 200);
        text(scene, util, DEPOT, "points");
        text(scene, util, CHEST, "supply");

        scene.world().createItemOnBeltLike(DEPOT, Direction.UP, new ItemStack(Blocks.IRON_BLOCK));
        text(scene, util, DEPOT, "grip");
        work(scene, beam, 0, FRAMES, 2);
        scene.overlay().showControls(util.vector().topOf(ARM), Pointing.DOWN, 50).rightClick().withItem(torch());
        scene.world().modifyBlockEntity(ARM, IndustrialArmBlockEntity.class, be -> be.toolSlot.setStackInSlot(0, torch()));
        scene.idle(10);
        text(scene, util, DEPOT, "weld");
        work(scene, beam, 1, FRAMES, 2);
        rest(scene, ARM);
        swap(scene, new ItemStack(CEArm.WELDED_IRON_BEAM.get()));
        text(scene, util, DEPOT, "done");

        // the product goes to a place chosen to put to
        scene.world().setBlock(OUT, Blocks.CHEST.defaultBlockState(), false);
        scene.world().showSection(util.select().position(OUT), Direction.DOWN);
        scene.overlay().showControls(util.vector().topOf(OUT), Pointing.DOWN, 40).rightClick().withItem(new ItemStack(CEArm.ARM_ITEM.get()));
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.OUTPUT, OUT, new AABB(OUT), 120);
        scene.world().removeItemsFromBelt(DEPOT);
        Vec3 outTop = Vec3.atBottomCenterOf(OUT).add(0, 1, 0);
        scene.world().modifyBlockEntity(ARM, IndustrialArmBlockEntity.class, be -> {
            be.held = new ItemStack(CEArm.WELDED_IRON_BEAM.get());
            be.aim(IndustrialArmBlockEntity.CARRY, outTop);
        });
        text(scene, util, OUT, "belt");
        scene.world().modifyBlockEntity(ARM, IndustrialArmBlockEntity.class, be -> {
            be.held = ItemStack.EMPTY;
            be.aim(IndustrialArmBlockEntity.IDLE, null);
        });
        scene.idle(20);
        scene.markAsFinished();
    }

    /** The magnetic core memory, start to end: parts, the iron sheet, 144 cores, 25 wires threaded by two arms, soldering. */
    public static void memory(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);
        String id = "magnetic_core_memory";
        ArmRecipe memory = recipe(MEMORY);
        stage(scene, util, id);
        scene.idle(10);
        scene.overlay().showControls(util.vector().topOf(CHEST), Pointing.DOWN, 60).withItem(new ItemStack(CEArm.FERRITE_CORE.get()));
        text(scene, util, CHEST, id, "cores");
        scene.world().setBlock(WIRE_SUPPLY.below(), AllBlocks.ANDESITE_CASING.getDefaultState(), false);
        scene.world().setBlock(WIRE_SUPPLY, AllBlocks.DEPOT.getDefaultState(), false);
        scene.world().showSection(util.select().fromTo(WIRE_SUPPLY.below(), WIRE_SUPPLY), Direction.DOWN);
        scene.overlay().showControls(util.vector().topOf(WIRE_SUPPLY), Pointing.DOWN, 40).rightClick().withItem(new ItemStack(CEArm.ARM_ITEM.get()));
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.INPUT, WIRE_SUPPLY, new AABB(WIRE_SUPPLY), 120);
        scene.world().createItemOnBeltLike(WIRE_SUPPLY, Direction.UP, new ItemStack(CEArm.ENAMELLED_COPPER_WIRE.get(), 25));
        text(scene, util, WIRE_SUPPLY, id, "wire");

        scene.overlay().showControls(util.vector().topOf(DEPOT), Pointing.DOWN, 40).withItem(new ItemStack(AllItems.IRON_SHEET.get()));
        scene.world().createItemOnBeltLike(DEPOT, Direction.UP, new ItemStack(AllItems.IRON_SHEET.get()));
        text(scene, util, DEPOT, id, "sheet");
        text(scene, util, DEPOT, id, "set");
        work(scene, memory, 0, 72, 1);
        rest(scene, ARM);
        swap(scene, new ItemStack(CEArm.INCOMPLETE_MAGNETIC_CORE_MEMORY.get()));

        scene.world().setBlock(HELPER, CEArm.ARM.get().defaultBlockState(), false);
        scene.world().showSection(util.select().position(HELPER), Direction.DOWN);
        scene.world().setKineticSpeed(util.select().position(HELPER), 64);
        scene.idle(10);
        text(scene, util, HELPER, id, "thread");
        work(scene, memory, 1, 200, 1);
        scene.world().removeItemsFromBelt(WIRE_SUPPLY);
        rest(scene, ARM);
        rest(scene, HELPER);

        scene.overlay().showControls(util.vector().topOf(ARM), Pointing.DOWN, 40).rightClick().withItem(torch());
        scene.world().modifyBlockEntity(ARM, IndustrialArmBlockEntity.class, be -> be.toolSlot.setStackInSlot(0, torch()));
        scene.idle(10);
        text(scene, util, DEPOT, id, "solder");
        work(scene, memory, 2, FRAMES, 2);
        rest(scene, ARM);
        swap(scene, new ItemStack(CEArm.MAGNETIC_CORE_MEMORY.get()));
        scene.effects().emitParticles(util.vector().topOf(DEPOT).add(0, .2, 0), scene.effects().simpleParticleEmitter(ParticleTypes.CLOUD, new Vec3(0, .05, 0)), 6, 20);
        text(scene, util, DEPOT, id, "done");
        scene.markAsFinished();
    }

    /** One step played from start to end along the same path, drawn over the depot item as the server's arm draws it. */
    private static void work(CreateSceneBuilder scene, ArmRecipe recipe, int step, int frames, int ticksPerFrame) {
        var s = recipe.steps().get(step);
        Trajectory path = Trajectory.of(s.pattern());
        Vec3 item = Vec3.atLowerCornerOf(DEPOT).add(.5, 13 / 16f, .5);
        for (int frame = 1; frame <= frames; frame++) {
            float progress = frame / (float) frames;
            Vec3 tip = IndustrialArmBlockEntity.on(item, path.tip(progress), s.pattern());
            scene.world().modifyBlockEntity(ARM, IndustrialArmBlockEntity.class, be -> {
                // the bare arm holds the parts it works with: the block, the cores, the wire
                be.held = s.consume().map(p -> new ItemStack(p.item(), p.count())).orElse(ItemStack.EMPTY);
                be.workRecipe = recipe;
                be.workStep = step;
                be.workAt = item;
                be.progress = progress;
                be.aim(IndustrialArmBlockEntity.WORK, tip);
            });
            if (s.helpers() > 0) {
                Vec3 end = IndustrialArmBlockEntity.on(item, path.runEnd(progress), s.pattern()).add(0, IndustrialArmBlockEntity.HELPER_LIFT, 0);
                scene.world().modifyBlockEntity(HELPER, IndustrialArmBlockEntity.class, be -> be.aim(IndustrialArmBlockEntity.ASSIST, end));
            }
            scene.idle(frame == 1 ? 12 : ticksPerFrame);
        }
    }

    private static void rest(CreateSceneBuilder scene, BlockPos arm) {
        scene.world().modifyBlockEntity(arm, IndustrialArmBlockEntity.class, be -> {
            be.held = ItemStack.EMPTY;
            be.workRecipe = null;
            be.aim(IndustrialArmBlockEntity.IDLE, null);
        });
    }

    /** The depot item becomes what the step made of it. */
    private static void swap(CreateSceneBuilder scene, ItemStack now) {
        scene.world().removeItemsFromBelt(DEPOT);
        scene.world().createItemOnBeltLike(DEPOT, Direction.UP, now);
        scene.idle(15);
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
