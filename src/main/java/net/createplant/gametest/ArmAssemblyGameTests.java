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
        h.assertTrue(memory.stages() == 4 && memory.steps().size() == 4 && memory.steps().get(0).lay(), "the memory starts from a laid iron sheet, in four stages");
        h.assertTrue(beam.steps().get(2).seamDirection() != null, "the weld knows which side its neighbour is on");
        h.assertTrue(memory.steps().get(1).materialCount() == 144, "the plane takes one ferrite core for each of its 12 x 12 cores");
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void laterStagesWaitAndSteps_goToOneArmNearestFirst(GameTestHelper h) {
        var memory = jig(h, MEMORY);
        BlockPos a = h.absolutePos(JIG.west(3)), b = h.absolutePos(JIG.east(3));
        h.assertTrue(memory.reserve(a, ArmTool.WELDING_TORCH, Vec3.atCenterOf(a)) == -1, "soldering waits for the wiring");
        h.assertTrue(memory.reserve(a, ArmTool.HAND, Vec3.atCenterOf(a)) == 0, "a bare arm lays the sheet first");
        h.assertTrue(memory.reserve(b, ArmTool.HAND, Vec3.atCenterOf(b)) == -1, "no step is given twice, and the cores wait for the sheet");
        var jig = jig(h, BEAM);
        int first = jig.reserve(a, ArmTool.HAND, Vec3.atCenterOf(a));
        h.assertTrue(first >= 0 && jig.recipe.steps().get(first).rel().getX() == 0, "the west arm takes the west cell first");
        int second = jig.reserve(b, ArmTool.HAND, Vec3.atCenterOf(b));
        h.assertTrue(second >= 0 && second != first && jig.recipe.steps().get(second).rel().getX() == 1, "the east arm takes the other one, the east cell");
        h.assertTrue(jig.reserve(a, ArmTool.HAND, Vec3.atCenterOf(a)) == -1, "nothing is left in the stage");
        h.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void aReservationWithoutArmReturns(GameTestHelper h) {
        var jig = jig(h, BEAM);
        BlockPos a = h.absolutePos(JIG.west(3));
        int s = jig.reserve(a, ArmTool.HAND, Vec3.atCenterOf(a));
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
            ArmTool tool = stage == 0 ? ArmTool.HAND : ArmTool.WELDING_TORCH;
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
        int s = jig.reserve(a, ArmTool.HAND, Vec3.atCenterOf(a));
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
        ((net.createplant.block.entity.IndustrialArmBlockEntity) h.getBlockEntity(arm)).toolSlot.setStackInSlot(0, toolStack(ArmTool.WELDING_TORCH));
        h.setBlock(depot, com.simibubi.create.AllBlocks.DEPOT.getDefaultState());
        ((net.createplant.block.entity.IndustrialArmBlockEntity) h.getBlockEntity(arm)).setPoints(java.util.List.of(h.absolutePos(depot)), java.util.List.of());
        var items = h.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, h.absolutePos(depot), null);
        h.assertTrue(items != null && items.insertItem(0, new net.minecraft.world.item.ItemStack(Blocks.DAMAGED_ANVIL), false).isEmpty(), "the depot takes the anvil");
        h.succeedWhen(() -> h.assertTrue(items.getStackInSlot(0).is(Blocks.CHIPPED_ANVIL.asItem()), "the arm welds the anvil one stage back, and the depot holds the result"));
    }

    @GameTest(template = "test_empty")
    public static void everyWireThreadsEveryCore(GameTestHelper h) {
        var t = net.createplant.arm.Trajectory.of(net.createplant.arm.ArmPattern.WIRE_THREAD, null);
        int g = net.createplant.arm.Trajectory.GRID;
        for (int kind : new int[] {net.createplant.arm.Trajectory.X_WIRE, net.createplant.arm.Trajectory.Y_WIRE, net.createplant.arm.Trajectory.SENSE})
            for (int i = 0; i < g; i++)
                for (int j = 0; j < g; j++) {
                    Vec3 core = new Vec3(net.createplant.arm.Trajectory.gridAt(i), net.createplant.arm.Trajectory.CORE_Y, net.createplant.arm.Trajectory.gridAt(j));
                    int through = 0;
                    for (int s = 0; s < t.segments(); s++) if (t.kind[s] == kind && t.points[s + 1].distanceTo(core) < .004) through++;
                    h.assertTrue(through == 1, "wire kind " + kind + " passes core " + i + "," + j + " exactly once, not " + through + " times");
                }
        h.succeed();
    }

    private static net.createplant.block.entity.IndustrialArmBlockEntity poweredArm(GameTestHelper h, BlockPos arm, ArmTool tool) {
        h.setBlock(arm.below(), com.simibubi.create.AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.UP));
        h.setBlock(arm, CEArm.ARM.get().defaultBlockState());
        var be = (net.createplant.block.entity.IndustrialArmBlockEntity) h.getBlockEntity(arm);
        be.toolSlot.setStackInSlot(0, toolStack(tool));
        return be;
    }

    /** The bare arm has nothing fitted; the welding torch is the only tool. */
    private static net.minecraft.world.item.ItemStack toolStack(ArmTool tool) {
        return tool == ArmTool.HAND ? net.minecraft.world.item.ItemStack.EMPTY : new net.minecraft.world.item.ItemStack(CEArm.WELDING_TORCH.get());
    }

    @GameTest(template = "test_empty", timeoutTicks = 200)
    public static void aRemovedFrameBlockTakesItsStepAndTheLaterWorkBack(GameTestHelper h) {
        var jig = jig(h, BEAM);
        BlockPos a = h.absolutePos(JIG.west(3));
        for (int stage = 0; stage < 2; stage++) {
            ArmTool tool = stage == 0 ? ArmTool.HAND : ArmTool.WELDING_TORCH;
            int s;
            while ((s = jig.reserve(a, tool, Vec3.atCenterOf(a))) >= 0) {
                if (jig.recipe.steps().get(s).place()) jig.place(s);
                else jig.complete(s);
            }
        }
        h.assertTrue(jig.allDone(), "all steps are done");
        h.setBlock(JIG.above(), Blocks.AIR.defaultBlockState());
        h.runAfterDelay(60, () -> {
            h.assertTrue(jig.currentStage() == 0 && !jig.allDone(), "the frame stage is open again");
            for (int i = 0; i < jig.status.length; i++)
                if (!jig.recipe.steps().get(i).place()) h.assertTrue(jig.status[i] == AssemblyJigBlockEntity.PENDING, "the weld on the broken frame is to be done again");
            var items = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(JIG)).inflate(3));
            h.assertTrue(items.stream().noneMatch(i -> i.getItem().is(CEArm.WELDED_IRON_BEAM.get())), "no product comes out of a broken frame");
            h.assertTrue(h.getBlockState(JIG.above().east()).is(Blocks.IRON_BLOCK), "the block still standing is kept");
            h.succeed();
        });
    }

    @GameTest(template = "test_empty", timeoutTicks = 200)
    public static void theArmLeavesMaterialItCannotReach(GameTestHelper h) {
        BlockPos arm = new BlockPos(6, 2, 6), chest = arm.offset(-5, -1, -5);
        jig(h, BEAM);
        poweredArm(h, arm, ArmTool.HAND);
        h.setBlock(chest, Blocks.CHEST.defaultBlockState());
        var box = (net.minecraft.world.level.block.entity.ChestBlockEntity) h.getBlockEntity(chest);
        box.setItem(0, new net.minecraft.world.item.ItemStack(Blocks.IRON_BLOCK, 3));
        h.runAfterDelay(120, () -> {
            h.assertTrue(box.getItem(0).getCount() == 3, "the iron in the chest out of the claw's reach stays there");
            h.succeed();
        });
    }

    @GameTest(template = "test_empty")
    public static void coreMemoryPartsHaveRecipes(GameTestHelper h) {
        var recipes = h.getLevel().getRecipeManager();
        for (String id : new String[] {"mixing/ferrite_powder", "pressing/unfired_ferrite_rings", "ferrite_core", "mixing/enamelled_copper_wire"})
            h.assertTrue(recipes.byKey(CreatePlant.location(id)).isPresent(), "the recipe " + id + " is loaded");
        h.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 600)
    public static void finishedDepotWorkIsCarriedToTheChosenOutput(GameTestHelper h) {
        BlockPos arm = new BlockPos(1, 2, 1), depot = new BlockPos(3, 2, 1), chest = new BlockPos(1, 2, 3);
        var be = poweredArm(h, arm, ArmTool.WELDING_TORCH);
        h.setBlock(depot, com.simibubi.create.AllBlocks.DEPOT.getDefaultState());
        h.setBlock(chest, Blocks.CHEST.defaultBlockState());
        h.assertTrue(be.setPoints(java.util.List.of(h.absolutePos(depot)), java.util.List.of(h.absolutePos(chest))) == 0, "both points are in reach");
        var items = h.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, h.absolutePos(depot), null);
        items.insertItem(0, new net.minecraft.world.item.ItemStack(Blocks.DAMAGED_ANVIL), false);
        var box = (net.minecraft.world.level.block.entity.ChestBlockEntity) h.getBlockEntity(chest);
        h.succeedWhen(() -> {
            h.assertTrue(box.getItem(0).is(Blocks.CHIPPED_ANVIL.asItem()), "the repaired anvil is put into the chosen chest");
            h.assertTrue(items.getStackInSlot(0).isEmpty() && be.held.isEmpty(), "nothing is left on the depot or in the claw");
        });
    }

    @GameTest(template = "test_empty", timeoutTicks = 200)
    public static void anArmWithoutChosenPointsLeavesDepotsAlone(GameTestHelper h) {
        BlockPos arm = new BlockPos(1, 2, 1), depot = new BlockPos(3, 2, 1), far = new BlockPos(6, 1, 6);
        var be = poweredArm(h, arm, ArmTool.WELDING_TORCH);
        h.setBlock(depot, com.simibubi.create.AllBlocks.DEPOT.getDefaultState());
        h.setBlock(far, Blocks.CHEST.defaultBlockState());
        h.assertTrue(be.setPoints(java.util.List.of(h.absolutePos(far)), java.util.List.of()) == 1 && be.inputs.isEmpty(), "a point out of reach is left out");
        var items = h.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, h.absolutePos(depot), null);
        items.insertItem(0, new net.minecraft.world.item.ItemStack(Blocks.DAMAGED_ANVIL), false);
        h.runAfterDelay(150, () -> {
            h.assertTrue(items.getStackInSlot(0).is(Blocks.DAMAGED_ANVIL.asItem()), "a depot that was not chosen is not worked");
            h.succeed();
        });
    }

    @GameTest(template = "test_empty", timeoutTicks = 200)
    public static void anAutomaticJigMakesWhatTheArmsMaterialsAllow(GameTestHelper h) {
        BlockPos jigPos = new BlockPos(3, 2, 3), grip = new BlockPos(6, 2, 2);
        h.setBlock(jigPos, CEArm.JIG.get().defaultBlockState());
        var jig = (AssemblyJigBlockEntity) h.getBlockEntity(jigPos);
        h.assertTrue(jig.auto && jig.recipe == null, "a new jig chooses by itself");
        // unpowered arms: they show their tools and reach but take nothing
        BlockPos[] arms = {new BlockPos(5, 2, 2), new BlockPos(5, 2, 5), new BlockPos(3, 2, 6)};
        ArmTool[] tools = {ArmTool.HAND, ArmTool.WELDING_TORCH, ArmTool.HAND};
        for (int i = 0; i < 2; i++) {
            h.setBlock(arms[i], CEArm.ARM.get().defaultBlockState());
            ((net.createplant.block.entity.IndustrialArmBlockEntity) h.getBlockEntity(arms[i])).toolSlot.setStackInSlot(0, toolStack(tools[i]));
        }
        h.setBlock(grip, Blocks.CHEST.defaultBlockState());
        var a = (net.minecraft.world.level.block.entity.ChestBlockEntity) h.getBlockEntity(grip);
        a.setItem(0, new net.minecraft.world.item.ItemStack(Blocks.IRON_BLOCK, 3));
        a.setItem(1, new net.minecraft.world.item.ItemStack(com.simibubi.create.AllItems.IRON_SHEET.get()));
        a.setItem(2, new net.minecraft.world.item.ItemStack(CEArm.FERRITE_CORE.get(), 64));
        a.setItem(3, new net.minecraft.world.item.ItemStack(CEArm.FERRITE_CORE.get(), 64));
        a.setItem(4, new net.minecraft.world.item.ItemStack(CEArm.FERRITE_CORE.get(), 16));
        a.setItem(5, new net.minecraft.world.item.ItemStack(CEArm.ENAMELLED_COPPER_WIRE.get(), 25));
        h.runAfterDelay(30, () -> {
            h.assertTrue(jig.recipe != null && jig.recipe.id().equals(CreatePlant.location(BEAM)), "one bare arm cannot thread alone: a beam");
            h.setBlock(arms[2], CEArm.ARM.get().defaultBlockState());
            h.runAfterDelay(30, () -> {
                h.assertTrue(jig.recipe != null && jig.recipe.id().equals(CreatePlant.location(MEMORY)), "with a second bare arm to hold the wire: core memory");
                a.setItem(4, net.minecraft.world.item.ItemStack.EMPTY);
                h.runAfterDelay(30, () -> {
                    h.assertTrue(jig.recipe.id().equals(CreatePlant.location(BEAM)), "short of cores: back to the beam");
                    h.assertTrue(jig.cycleRecipe() && !jig.auto, "the wrench pins a recipe");
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void bothHalvesOfALargeChestAreCountedOnce(GameTestHelper h) {
        BlockPos arm = new BlockPos(1, 2, 1), left = new BlockPos(3, 2, 1), right = new BlockPos(3, 2, 2);
        var be = poweredArm(h, arm, ArmTool.HAND);
        h.setBlock(left, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, net.minecraft.core.Direction.WEST)
                .setValue(net.minecraft.world.level.block.ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.RIGHT));
        h.setBlock(right, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, net.minecraft.core.Direction.WEST)
                .setValue(net.minecraft.world.level.block.ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.LEFT));
        h.assertTrue(be.setPoints(java.util.List.of(h.absolutePos(left), h.absolutePos(right)), java.util.List.of()) == 0, "both halves are points");
        var box = (net.minecraft.world.level.block.entity.ChestBlockEntity) h.getBlockEntity(left);
        box.setItem(0, new net.minecraft.world.item.ItemStack(CEArm.FERRITE_CORE.get(), 64));
        box.setItem(1, new net.minecraft.world.item.ItemStack(CEArm.FERRITE_CORE.get(), 36));
        var jig = (AssemblyJigBlockEntity) null;
        h.setBlock(new BlockPos(1, 2, 4), CEArm.JIG.get().defaultBlockState());
        var j = (AssemblyJigBlockEntity) h.getBlockEntity(new BlockPos(1, 2, 4));
        j.setRecipe(ArmRecipes.get(CreatePlant.location(MEMORY)));
        j.complete(0); // the sheet is laid
        h.runAfterDelay(60, () -> {
            h.assertTrue(be.phase == net.createplant.block.entity.IndustrialArmBlockEntity.IDLE && be.held.isEmpty(), "100 cores are not 144, however many sides the chest has");
            h.assertTrue(box.getItem(0).getCount() == 64 && box.getItem(1).getCount() == 36, "no core was taken");
            h.succeed();
        });
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void marksSentBeforeTheArmIsPlacedReachIt(GameTestHelper h) {
        BlockPos arm = new BlockPos(1, 2, 1), chest = new BlockPos(3, 2, 1);
        h.setBlock(chest, Blocks.CHEST.defaultBlockState());
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
        player.moveTo(h.absoluteVec(new Vec3(1.5, 2, 2.5)));
        new net.createplant.arm.ArmPoints(h.absolutePos(arm), java.util.List.of(h.absolutePos(chest)), java.util.List.of()).apply(player);
        h.setBlock(arm, CEArm.ARM.get().defaultBlockState());
        net.createplant.arm.ArmPoints.placed(player, h.absolutePos(arm));
        var be = (net.createplant.block.entity.IndustrialArmBlockEntity) h.getBlockEntity(arm);
        h.assertTrue(be.inputs.equals(java.util.List.of(h.absolutePos(chest))), "the marks the client sent first are given to the arm once the server places it");
        h.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 200)
    public static void aLaterStageBlockStillStandingIsNotPlacedAgain(GameTestHelper h) {
        var jig = jig(h, BEAM);
        BlockPos a = h.absolutePos(JIG.west(3));
        for (int stage = 0; stage < 2; stage++) {
            ArmTool tool = stage == 0 ? ArmTool.HAND : ArmTool.WELDING_TORCH;
            int s;
            while ((s = jig.reserve(a, tool, Vec3.atCenterOf(a))) >= 0) {
                if (jig.recipe.steps().get(s).place()) jig.place(s);
                else jig.complete(s);
            }
        }
        h.setBlock(JIG.above(), Blocks.AIR.defaultBlockState());
        h.runAfterDelay(5, () -> {
            h.assertTrue(jig.status[0] == AssemblyJigBlockEntity.PENDING && jig.status[1] == AssemblyJigBlockEntity.DONE, "only the missing block's step is open");
            h.setBlock(JIG.above(), Blocks.IRON_BLOCK.defaultBlockState());
            h.runAfterDelay(5, () -> {
                h.assertTrue(jig.status[0] == AssemblyJigBlockEntity.DONE, "a block put back by hand counts as placed");
                h.succeed();
            });
        });
    }

    @GameTest(template = "test_empty", timeoutTicks = 200)
    public static void aStoppedArmLetsGoOfTheDepotItem(GameTestHelper h) {
        BlockPos arm = new BlockPos(1, 2, 1), depot = new BlockPos(3, 2, 1);
        var be = poweredArm(h, arm, ArmTool.WELDING_TORCH);
        h.setBlock(depot, com.simibubi.create.AllBlocks.DEPOT.getDefaultState());
        be.setPoints(java.util.List.of(h.absolutePos(depot)), java.util.List.of());
        var items = h.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, h.absolutePos(depot), null);
        items.insertItem(0, new net.minecraft.world.item.ItemStack(Blocks.DAMAGED_ANVIL), false);
        h.runAfterDelay(30, () -> {
            h.assertTrue(be.phase != net.createplant.block.entity.IndustrialArmBlockEntity.IDLE, "the arm took the anvil");
            h.setBlock(arm.below(), Blocks.AIR.defaultBlockState());
            h.runAfterDelay(10, () -> {
                h.assertTrue(be.phase == net.createplant.block.entity.IndustrialArmBlockEntity.IDLE, "without power the arm lets go of the depot's item");
                h.succeed();
            });
        });
    }

    @GameTest(template = "test_empty", timeoutTicks = 400)
    public static void threadingTakesASecondBareArmThatHoldsTheWire(GameTestHelper h) {
        String json = "{\"size\":[1,1,1],\"stages\":[[{\"pos\":[0,0,0],\"action\":\"process\",\"tool\":\"hand\",\"pattern\":\"wire_thread\",\"helpers\":1,"
            + "\"consume\":{\"id\":\"create_refinery_n_reactors:enamelled_copper_wire\",\"count\":25},\"time\":800}]],\"result\":{\"id\":\"minecraft:stone\"}}";
        var recipe = net.createplant.arm.ArmRecipe.fromJson(CreatePlant.location("test_thread"), com.google.gson.JsonParser.parseString(json).getAsJsonObject());
        BlockPos jigPos = new BlockPos(2, 2, 2), lead = new BlockPos(4, 2, 2), chest = new BlockPos(5, 2, 2), second = new BlockPos(0, 2, 2);
        h.setBlock(jigPos, CEArm.JIG.get().defaultBlockState());
        ((AssemblyJigBlockEntity) h.getBlockEntity(jigPos)).setRecipe(recipe);
        var be = poweredArm(h, lead, ArmTool.HAND);
        h.setBlock(chest, Blocks.CHEST.defaultBlockState());
        be.setPoints(java.util.List.of(h.absolutePos(chest)), java.util.List.of());
        var box = (net.minecraft.world.level.block.entity.ChestBlockEntity) h.getBlockEntity(chest);
        box.setItem(0, new net.minecraft.world.item.ItemStack(CEArm.ENAMELLED_COPPER_WIRE.get(), 25));
        h.runAfterDelay(60, () -> {
            h.assertTrue(box.getItem(0).getCount() == 25 && be.phase == net.createplant.block.entity.IndustrialArmBlockEntity.IDLE, "alone, a bare arm takes no wire");
            var helper = poweredArm(h, second, ArmTool.HAND);
            // the lone arm waits up to 3 seconds before it looks again, then reaches for the wire and moves over
            h.runAfterDelay(140, () -> {
                h.assertTrue(be.phase == net.createplant.block.entity.IndustrialArmBlockEntity.WORK && box.getItem(0).isEmpty(), "with a second bare arm it takes the wire and threads");
                h.assertTrue(helper.phase == net.createplant.block.entity.IndustrialArmBlockEntity.ASSIST && helper.lead.equals(be.getBlockPos()), "the other arm helps");
                Vec3 c = Vec3.atBottomCenterOf(h.absolutePos(jigPos.above()));
                h.assertTrue(helper.tip != null && Math.max(Math.abs(helper.tip.x - c.x), Math.abs(helper.tip.z - c.z)) > .2, "the helper holds the wire's end at the edge of the plane");
                h.setBlock(second, Blocks.AIR.defaultBlockState());
                h.runAfterDelay(10, () -> {
                    h.assertTrue(be.phase == net.createplant.block.entity.IndustrialArmBlockEntity.IDLE && box.getItem(0).getCount() == 25, "without its helper the arm stops and puts the wire back");
                    h.succeed();
                });
            });
        });
    }
}
