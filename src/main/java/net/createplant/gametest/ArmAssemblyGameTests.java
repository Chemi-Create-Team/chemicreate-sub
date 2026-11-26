package net.createplant.gametest;

import net.createplant.CreatePlant;
import net.createplant.arm.ArmRecipes;
import net.createplant.arm.ArmTool;
import net.createplant.block.entity.AssemblyJigBlockEntity;
import net.createplant.registry.CEArm;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public class ArmAssemblyGameTests {
    private static final BlockPos JIG = new BlockPos(1, 2, 1);
    private static final String MEMORY = "magnetic_core_memory", BEAM = "welded_iron_beam";

    private static AssemblyJigBlockEntity jig(GameTestHelper h, String recipe) {
        h.setBlock(JIG, CEArm.JIG.get().defaultBlockState());
        var jig = (AssemblyJigBlockEntity) h.getBlockEntity(JIG);
        var r = ArmRecipes.get(CreatePlant.location(recipe));
        h.assertTrue(r != null, "the sample recipe " + recipe + " is loaded");
        jig.setRecipe(r);
        return jig;
    }

    @GameTest(template = "test_empty")
    public static void sampleRecipesLoad(GameTestHelper h) {
        var memory = ArmRecipes.get(CreatePlant.location(MEMORY));
        var beam = ArmRecipes.get(CreatePlant.location(BEAM));
        h.assertTrue(memory != null && beam != null, "both sample recipes load");
        h.assertTrue(memory.stages() == 3 && memory.steps().size() == 6, "the memory has three stages and six steps");
        h.assertTrue(beam.steps().get(2).seamDirection() != null, "the weld knows which side its neighbour is on");
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void laterStagesWaitAndSteps_goToOneArmNearestFirst(GameTestHelper h) {
        var jig = jig(h, MEMORY);
        BlockPos a = h.absolutePos(JIG.west(3)), b = h.absolutePos(JIG.east(3));
        h.assertTrue(jig.reserve(a, ArmTool.WIRING_HEAD, Vec3.atCenterOf(a)) == -1, "wiring waits for the frame");
        h.assertTrue(jig.reserve(a, ArmTool.WELDING_TORCH, Vec3.atCenterOf(a)) == -1, "welding waits for the wiring");
        int first = jig.reserve(a, ArmTool.GRIPPER, Vec3.atCenterOf(a));
        h.assertTrue(first >= 0 && jig.recipe.steps().get(first).rel().getX() == 0, "the west arm takes the west cell first");
        int second = jig.reserve(b, ArmTool.GRIPPER, Vec3.atCenterOf(b));
        h.assertTrue(second >= 0 && second != first && jig.recipe.steps().get(second).rel().getX() == 2, "the east arm takes another one, the east cell");
        int third = jig.reserve(a, ArmTool.GRIPPER, Vec3.atCenterOf(a));
        h.assertTrue(third >= 0 && third != first && third != second, "no step is given twice");
        h.assertTrue(jig.reserve(a, ArmTool.GRIPPER, Vec3.atCenterOf(a)) == -1, "nothing is left in the stage");
        h.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void aReservationWithoutArmReturns(GameTestHelper h) {
        var jig = jig(h, BEAM);
        BlockPos a = h.absolutePos(JIG.west(3));
        int s = jig.reserve(a, ArmTool.GRIPPER, Vec3.atCenterOf(a));
        h.assertTrue(s >= 0 && jig.status[s] == AssemblyJigBlockEntity.RESERVED, "reserved");
        h.runAfterDelay(45, () -> {
            h.assertTrue(jig.status[s] == AssemblyJigBlockEntity.PENDING, "the step is open again once no arm holds it");
            h.succeed();
        });
    }

    @GameTest(template = "test_empty", timeoutTicks = 200)
    public static void finishingRemovesTheBlocksAndDropsTheProduct(GameTestHelper h) {
        var jig = jig(h, BEAM);
        BlockPos a = h.absolutePos(JIG.west(3));
        for (int stage = 0; stage < 2; stage++) {
            ArmTool tool = stage == 0 ? ArmTool.GRIPPER : ArmTool.WELDING_TORCH;
            int s;
            while ((s = jig.reserve(a, tool, Vec3.atCenterOf(a))) >= 0) {
                if (jig.recipe.steps().get(s).place()) h.assertTrue(jig.place(s), "the block is placed into the free cell");
                else jig.complete(s);
            }
        }
        h.assertTrue(jig.allDone(), "all steps are done");
        h.assertTrue(h.getBlockState(JIG.above()).is(Blocks.IRON_BLOCK), "the frame stands");
        h.runAfterDelay(50, () -> {
            h.assertTrue(h.getBlockState(JIG.above()).isAir() && h.getBlockState(JIG.above().east()).isAir(), "the temporary blocks are gone");
            var items = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(JIG)).inflate(3));
            h.assertTrue(items.stream().anyMatch(i -> i.getItem().is(CEArm.WELDED_IRON_BEAM.get())), "the product appears");
            h.assertTrue(jig.status[0] == AssemblyJigBlockEntity.PENDING, "the jig is ready for the next one");
            h.succeed();
        });
    }

    @GameTest(template = "test_empty")
    public static void placingNeverOverwritesABlock(GameTestHelper h) {
        var jig = jig(h, BEAM);
        h.setBlock(JIG.above(), Blocks.DIRT.defaultBlockState());
        BlockPos a = h.absolutePos(JIG.west(3));
        int s = jig.reserve(a, ArmTool.GRIPPER, Vec3.atCenterOf(a));
        h.assertTrue(!jig.place(s) || jig.recipe.steps().get(s).rel().getX() != 0, "an occupied cell is not overwritten");
        h.assertTrue(h.getBlockState(JIG.above()).is(Blocks.DIRT), "the dirt is still there");
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void theArmAimsWithCreatesAnglesWithinItsReach(GameTestHelper h) {
        BlockPos arm = h.absolutePos(JIG);
        Vec3 near = Vec3.atCenterOf(arm).add(2, .5, 1), far = Vec3.atCenterOf(arm).add(5, .5, 0);
        float[] a = net.createplant.arm.ArmPose.toward(arm, near), b = net.createplant.arm.ArmPose.toward(arm, Vec3.atCenterOf(arm).add(-2, .5, 1));
        h.assertTrue(!java.util.Arrays.equals(a, net.createplant.arm.ArmPose.REST), "the arm leaves its rest pose");
        h.assertTrue(Math.abs(a[0] - b[0]) > 1, "a target on the other side turns the base");
        h.assertTrue(net.createplant.arm.ArmPose.reaches(arm, near) && !net.createplant.arm.ArmPose.reaches(arm, far), "steps two blocks away are in reach, five blocks away are not");
        // At Create's segment lengths the port gives Create's own angles.
        Vec3 shoulder = Vec3.atCenterOf(arm).add(0, net.createplant.arm.ArmPose.SHOULDER, 0);
        for (Vec3 t : new Vec3[] {Vec3.atCenterOf(arm).add(1, -.3, .5), Vec3.atCenterOf(arm).add(-.4, .8, -1.2)}) {
            float[] mine = net.createplant.arm.ArmPose.angles(shoulder, t, .875f, .9375f), create = createAngles(arm, t);
            for (int i = 0; i < 4; i++) h.assertTrue(Math.abs(mine[i] - create[i]) < .01, "joint " + i + " matches Create: " + mine[i] + " vs " + create[i]);
        }
        h.succeed();
    }

    private static float[] createAngles(BlockPos arm, Vec3 t) {
        try {
            Class<?> c = Class.forName("com.simibubi.create.content.kinetics.mechanicalArm.ArmAngleTarget");
            Object o = c.getConstructor(BlockPos.class, Vec3.class, net.minecraft.core.Direction.class, boolean.class).newInstance(arm, t, net.minecraft.core.Direction.DOWN, false);
            float[] r = new float[4];
            String[] names = {"baseAngle", "lowerArmAngle", "upperArmAngle", "headAngle"};
            for (int i = 0; i < 4; i++) {
                var f = c.getDeclaredField(names[i]);
                f.setAccessible(true);
                r[i] = f.getFloat(o);
            }
            return r;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @GameTest(template = "test_empty", timeoutTicks = 600)
    public static void aWeldingArmRepairsTheAnvilOnADepot(GameTestHelper h) {
        BlockPos arm = new BlockPos(1, 2, 1), depot = new BlockPos(3, 2, 1);
        h.setBlock(arm.below(), com.simibubi.create.AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.UP));
        h.setBlock(arm, CEArm.ARM.get().defaultBlockState());
        ((net.createplant.block.entity.IndustrialArmBlockEntity) h.getBlockEntity(arm)).toolSlot.setStackInSlot(0, new net.minecraft.world.item.ItemStack(CEArm.TOOLS.get(ArmTool.WELDING_TORCH).get()));
        h.setBlock(depot, com.simibubi.create.AllBlocks.DEPOT.getDefaultState());
        var items = h.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, h.absolutePos(depot), null);
        h.assertTrue(items != null && items.insertItem(0, new net.minecraft.world.item.ItemStack(Blocks.DAMAGED_ANVIL), false).isEmpty(), "the depot takes the anvil");
        h.succeedWhen(() -> h.assertTrue(items.getStackInSlot(0).is(Blocks.CHIPPED_ANVIL.asItem()), "the arm welds the anvil one stage back, and the depot holds the result"));
    }

    @GameTest(template = "test_empty")
    public static void everyWireThreadsEveryCore(GameTestHelper h) {
        var t = net.createplant.arm.Trajectory.of(net.createplant.arm.ArmPattern.GRID_WIRE, null);
        int g = net.createplant.arm.Trajectory.GRID;
        for (int kind : new int[] {net.createplant.arm.Trajectory.X_WIRE, net.createplant.arm.Trajectory.Y_WIRE, net.createplant.arm.Trajectory.SENSE})
            for (int i = 0; i < g; i++)
                for (int j = 0; j < g; j++) {
                    Vec3 core = new Vec3(net.createplant.arm.Trajectory.gridAt(i), net.createplant.arm.Trajectory.CORE_Y, net.createplant.arm.Trajectory.gridAt(j));
                    int through = 0;
                    for (int s = 0; s < t.segments(); s++) if (t.kind[s] == kind && t.points[s + 1].distanceTo(core) < .01) through++;
                    h.assertTrue(through == 1, "wire kind " + kind + " passes core " + i + "," + j + " exactly once, not " + through + " times");
                }
        h.succeed();
    }
}
