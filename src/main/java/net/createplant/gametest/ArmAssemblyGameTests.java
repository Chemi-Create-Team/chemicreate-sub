package net.createplant.gametest;

import net.createplant.CreatePlant;
import net.createplant.arm.ArmRecipe;
import net.createplant.arm.ArmTool;
import net.createplant.block.entity.IndustrialArmBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
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
    private static final String MEMORY = "arm_assembly/magnetic_core_memory";

    private static net.minecraft.world.item.crafting.RecipeHolder<ArmRecipe> recipe(GameTestHelper h, String id) {
        return ArmRecipe.all(h.getLevel()).stream().filter(r -> r.id().equals(CreatePlant.location(id))).findFirst().orElseThrow();
    }

    private static IndustrialArmBlockEntity arm(GameTestHelper h, BlockPos pos) {
        return (IndustrialArmBlockEntity) h.getBlockEntity(pos);
    }

    private static net.neoforged.neoforge.items.IItemHandler depot(GameTestHelper h, BlockPos pos, ItemStack on) {
        h.setBlock(pos, com.simibubi.create.AllBlocks.DEPOT.getDefaultState());
        var items = h.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, h.absolutePos(pos), null);
        h.assertTrue(items != null && items.insertItem(0, on, false).isEmpty(), "the depot takes the item");
        return items;
    }

    private static ChestBlockEntity chest(GameTestHelper h, BlockPos pos, ItemStack... content) {
        h.setBlock(pos, Blocks.CHEST.defaultBlockState());
        var box = (ChestBlockEntity) h.getBlockEntity(pos);
        for (int i = 0; i < content.length; i++) box.setItem(i, content[i]);
        return box;
    }

    private static ItemStack[] memoryParts() {
        return new ItemStack[] {new ItemStack(CEArm.FERRITE_CORE.get(), 64), new ItemStack(CEArm.FERRITE_CORE.get(), 64), new ItemStack(CEArm.FERRITE_CORE.get(), 16),
            new ItemStack(CEArm.ENAMELLED_COPPER_WIRE.get(), 25)};
    }

    @GameTest(template = "test_empty")
    public static void theMemoryRecipeStepsAnItemThroughItsStages(GameTestHelper h) {
        var holder = recipe(h, MEMORY);
        ArmRecipe memory = holder.value();
        h.assertTrue(memory.steps().size() == 3 && memory.steps().get(0).consume().get().count() == 144, "cores first, one for each of the plane's 12 x 12");
        h.assertTrue(memory.steps().get(1).helpers() == 1 && memory.steps().get(1).tool() == ArmTool.HAND, "two bare arms thread the wire");
        h.assertTrue(memory.steps().get(2).tool() == ArmTool.WELDING_TORCH, "the torch solders");
        var fresh = ArmRecipe.next(h.getLevel(), new ItemStack(com.simibubi.create.AllItems.IRON_SHEET.get()));
        h.assertTrue(fresh.stream().anyMatch(n -> n.recipe().id().equals(holder.id()) && n.step() == 0), "an iron sheet starts the memory");
        ItemStack partway = memory.after(holder.id(), 0);
        h.assertTrue(partway.is(CEArm.INCOMPLETE_MAGNETIC_CORE_MEMORY.get()), "after the cores it is the incomplete memory");
        var next = ArmRecipe.next(h.getLevel(), partway);
        h.assertTrue(next.size() == 1 && next.get(0).step() == 1, "which carries its next step");
        h.assertTrue(memory.after(holder.id(), 2).is(CEArm.MAGNETIC_CORE_MEMORY.get()), "the last step makes the memory");
        h.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 1500)
    public static void twoBareArmsAndATorchMakeCoreMemoryOnADepot(GameTestHelper h) {
        BlockPos depot = new BlockPos(3, 2, 3), lead = new BlockPos(5, 2, 3), parts = new BlockPos(6, 2, 3), second = new BlockPos(1, 2, 3),
            torch = new BlockPos(3, 2, 5), out = new BlockPos(3, 2, 6);
        var onDepot = depot(h, depot, new ItemStack(com.simibubi.create.AllItems.IRON_SHEET.get()));
        var box = chest(h, parts, memoryParts());
        var store = chest(h, out);
        poweredArm(h, lead, ArmTool.HAND).setPoints(java.util.List.of(h.absolutePos(depot), h.absolutePos(parts)), java.util.List.of());
        poweredArm(h, second, ArmTool.HAND);
        h.assertTrue(poweredArm(h, torch, ArmTool.WELDING_TORCH).setPoints(java.util.List.of(h.absolutePos(depot)), java.util.List.of(h.absolutePos(out))) == 0, "the torch arm reaches both");
        h.succeedWhen(() -> {
            h.assertTrue(store.getItem(0).is(CEArm.MAGNETIC_CORE_MEMORY.get()), "the finished memory is carried to the chosen chest");
            h.assertTrue(box.isEmpty() && onDepot.getStackInSlot(0).isEmpty(), "every core and wire and the sheet are used up");
        });
    }

    @GameTest(template = "test_empty", timeoutTicks = 450)
    public static void selectedSupplyDepotsFeedCoreMemoryAssembly(GameTestHelper h) {
        BlockPos work = new BlockPos(3, 2, 3), lead = new BlockPos(5, 2, 3), assistant = new BlockPos(1, 2, 3),
            torch = new BlockPos(3, 2, 5), out = new BlockPos(3, 2, 6);
        var supply = java.util.List.of(new BlockPos(5, 2, 1), new BlockPos(6, 2, 2), new BlockPos(6, 2, 3), new BlockPos(6, 2, 4));
        var material = memoryParts();
        var handlers = new java.util.ArrayList<net.neoforged.neoforge.items.IItemHandler>();
        for (int i = 0; i < supply.size(); i++) handlers.add(depot(h, supply.get(i), material[i]));
        var workDepot = depot(h, work, new ItemStack(com.simibubi.create.AllItems.IRON_SHEET.get()));
        var store = chest(h, out);
        var inputs = new java.util.ArrayList<>(supply.stream().map(h::absolutePos).toList());
        inputs.addFirst(h.absolutePos(work));
        h.assertTrue(poweredArm(h, lead, ArmTool.HAND).setPoints(inputs, java.util.List.of()) == 0, "the lead reaches all five depots");
        poweredArm(h, assistant, ArmTool.HAND);
        h.assertTrue(poweredArm(h, torch, ArmTool.WELDING_TORCH).setPoints(java.util.List.of(h.absolutePos(work)), java.util.List.of(h.absolutePos(out))) == 0,
            "the torch reaches the work depot and delivery chest");
        h.succeedWhen(() -> {
            h.assertTrue(store.getItem(0).is(CEArm.MAGNETIC_CORE_MEMORY.get()), "the finished memory reaches the chest using only depot inputs");
            h.assertTrue(workDepot.getStackInSlot(0).isEmpty() && handlers.stream().allMatch(d -> d.getStackInSlot(0).isEmpty()),
                "the arms use all supplied material exactly once");
        });
    }

    @GameTest(template = "test_empty", timeoutTicks = 200)
    public static void theWorkDepotDoesNotSupplyItsOwnBaseItem(GameTestHelper h) {
        BlockPos arm = new BlockPos(1, 2, 1), work = new BlockPos(3, 2, 1), supply = new BlockPos(1, 2, 2);
        var workDepot = depot(h, work, new ItemStack(Blocks.IRON_BLOCK));
        var supplyDepot = depot(h, supply, ItemStack.EMPTY);
        var be = poweredArm(h, arm, ArmTool.HAND);
        be.setPoints(java.util.List.of(h.absolutePos(work), h.absolutePos(supply)), java.util.List.of());
        h.runAfterDelay(30, () -> {
            h.assertTrue(be.held.isEmpty() && workDepot.getStackInSlot(0).is(Blocks.IRON_BLOCK.asItem()),
                "the base iron block stays on the work depot until a second one is supplied");
            supplyDepot.insertItem(0, new ItemStack(Blocks.IRON_BLOCK), false);
        });
        h.succeedWhen(() -> h.assertTrue(supplyDepot.getStackInSlot(0).isEmpty()
                && ArmRecipe.next(h.getLevel(), workDepot.getStackInSlot(0)).stream().anyMatch(next -> next.step() == 1),
            "the extra iron block is used and the workpiece advances to welding"));
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void bothHalvesOfALargeChestAreCountedOnce(GameTestHelper h) {
        BlockPos arm = new BlockPos(1, 2, 1), left = new BlockPos(3, 2, 1), right = new BlockPos(3, 2, 2), depot = new BlockPos(1, 2, 3);
        var be = poweredArm(h, arm, ArmTool.HAND);
        h.setBlock(left, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, net.minecraft.core.Direction.WEST)
                .setValue(net.minecraft.world.level.block.ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.RIGHT));
        h.setBlock(right, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, net.minecraft.core.Direction.WEST)
                .setValue(net.minecraft.world.level.block.ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.LEFT));
        var box = (ChestBlockEntity) h.getBlockEntity(left);
        box.setItem(0, new ItemStack(CEArm.FERRITE_CORE.get(), 64));
        box.setItem(1, new ItemStack(CEArm.FERRITE_CORE.get(), 36));
        var items = depot(h, depot, new ItemStack(com.simibubi.create.AllItems.IRON_SHEET.get()));
        h.assertTrue(be.setPoints(java.util.List.of(h.absolutePos(left), h.absolutePos(right), h.absolutePos(depot)), java.util.List.of()) == 0, "all three are points");
        h.runAfterDelay(60, () -> {
            h.assertTrue(be.phase == IndustrialArmBlockEntity.IDLE && be.held.isEmpty(), "100 cores are not 144, however many sides the chest has");
            h.assertTrue(box.getItem(0).getCount() == 64 && box.getItem(1).getCount() == 36 && items.getStackInSlot(0).is(com.simibubi.create.AllItems.IRON_SHEET.get()), "nothing was taken or changed");
            h.succeed();
        });
    }

    @GameTest(template = "test_empty", timeoutTicks = 600)
    public static void threadingTakesASecondBareArmThatHoldsTheWire(GameTestHelper h) {
        var holder = recipe(h, MEMORY);
        BlockPos depot = new BlockPos(2, 2, 2), lead = new BlockPos(4, 2, 2), parts = new BlockPos(5, 2, 2), second = new BlockPos(0, 2, 2);
        depot(h, depot, holder.value().after(holder.id(), 0)); // the cores are set
        var box = chest(h, parts, new ItemStack(CEArm.ENAMELLED_COPPER_WIRE.get(), 25));
        var be = poweredArm(h, lead, ArmTool.HAND);
        be.setPoints(java.util.List.of(h.absolutePos(depot), h.absolutePos(parts)), java.util.List.of());
        IndustrialArmBlockEntity[] helper = {null};
        boolean[] checked = {false};
        h.runAfterDelay(60, () -> {
            h.assertTrue(box.getItem(0).getCount() == 25 && be.phase == IndustrialArmBlockEntity.IDLE, "alone, a bare arm takes no wire");
            helper[0] = poweredArm(h, second, ArmTool.HAND);
        });
        h.onEachTick(() -> {
            if (helper[0] == null || checked[0] || be.phase != IndustrialArmBlockEntity.WORK || be.progress < .1f) return;
            checked[0] = true;
            h.assertTrue(box.getItem(0).isEmpty() && helper[0].phase == IndustrialArmBlockEntity.ASSIST && helper[0].lead.equals(be.getBlockPos()), "with a second bare arm it takes the wire and the other helps");
            Vec3 c = Vec3.atCenterOf(h.absolutePos(depot));
            h.assertTrue(helper[0].tip != null && Math.max(Math.abs(helper[0].tip.x - c.x), Math.abs(helper[0].tip.z - c.z)) > .2, "the helper holds the wire's end at the edge of the plane");
            h.setBlock(second, Blocks.AIR.defaultBlockState());
            h.runAfterDelay(10, () -> {
                h.assertTrue(be.phase == IndustrialArmBlockEntity.IDLE && box.getItem(0).getCount() == 25, "without its helper the arm stops and puts the wire back");
                h.succeed();
            });
        });
    }

    @GameTest(template = "test_empty")
    public static void theArmAimsWithCreatesAnglesWithinItsReach(GameTestHelper h) {
        BlockPos arm = h.absolutePos(new BlockPos(1, 2, 1));
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
        var t = net.createplant.arm.Trajectory.of(net.createplant.arm.ArmPattern.WIRE_THREAD);
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

}
