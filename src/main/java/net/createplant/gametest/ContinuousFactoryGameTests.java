package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.logistics.funnel.AbstractFunnelBlock;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import java.util.List;
import net.createplant.CreatePlant;
import net.createplant.block.ContinuousFactoryBlock;
import net.createplant.block.FactoryPortBlock;
import net.createplant.block.FactoryStageBlock;
import net.createplant.block.entity.ContinuousFactoryBlockEntity;
import net.createplant.contraptions.FactoryBodyEntity;
import net.createplant.contraptions.FactoryContraption;
import net.createplant.factory.FactoryGeometry;
import net.createplant.factory.FactoryLayout;
import net.createplant.factory.FactoryPose;
import net.createplant.factory.FactoryProcess;
import net.createplant.registry.CEArm;
import net.createplant.registry.CEContinuousFactory;
import net.createplant.registry.CEFusion;
import net.createplant.registry.CEItems;
import net.createplant.registry.CEMetals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3f;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public class ContinuousFactoryGameTests {
    private static final BlockPos CONTROL = new BlockPos(8, 3, 5);
    private static final Direction FLOW = Direction.SOUTH;

    @GameTest(template = "test_wet_processing", timeoutTicks = 615)
    public static void kilnFeedsThreeChargesAndPersistsTheCurrentCharge(GameTestHelper h) {
        var machine = kiln(h, 16);
        var furnace = CONTROL.south(8);
        var funnel = CONTROL.north();
        h.setBlock(funnel, AllBlocks.ANDESITE_FUNNEL.getDefaultState().setValue(FunnelBlock.FACING, Direction.NORTH)
            .setValue(FunnelBlock.EXTRACTING, false));
        h.runAfterDelay(18, () -> {
            var material = new ItemStack(Items.CALCITE, 3);
            h.assertTrue(AbstractFunnelBlock.tryInsert(h.getLevel(), h.absolutePos(funnel), material, false).isEmpty(), "Create funnel feeds the kiln");
        });
        h.runAfterDelay(100, () -> {
            var drum = bodies(h).stream().filter(body -> body.geometry() == FactoryGeometry.DRUM).findFirst().orElseThrow();
            var collision = drum.getContraption().getSimplifiedEntityColliders();
            h.assertTrue(collision.size == FactoryGeometry.DRUM.pieces().size(), "The native contraption uses every mesh cuboid for collision");
            var local = new Vec3(.65, 1.2, 2.7);
            h.assertTrue(drum.reverseRotation(drum.applyRotation(local, 1), 1).distanceTo(local) < .001, "Tilt and rotation are inverse in world-to-local collision");
            h.assertTrue(bodies(h).stream().filter(body -> body.geometry() == FactoryGeometry.CHARGE).count() >= 2, "Two independently tracked charges travel in one drum");
            var lookup = h.getLevel().registryAccess();
            var tag = machine.saveWithFullMetadata(lookup);
            var saved = BlockEntity.loadStatic(machine.getBlockPos(), machine.getBlockState(), tag, lookup);
            h.assertTrue(saved instanceof ContinuousFactoryBlockEntity && tag.getList("Batches", 10).size() >= 2,
                "Controller save stores material in flight instead of saving duplicate entities");
        });
        h.runAfterDelay(601, () -> {
            h.assertTrue(count(h, furnace, CEItems.QUICKLIME.get()) == 3, "Three feed items yield exactly three units of quicklime");
            h.succeed();
        });
    }

    @GameTest(template = "test_wet_processing", timeoutTicks = 185)
    public static void fastKilnDischargesUnfiredStock(GameTestHelper h) {
        var machine = kiln(h, 64);
        insert(h, machine, Direction.UP, new ItemStack(Items.CALCITE));
        h.runAfterDelay(155, () -> {
            h.assertTrue(count(h, CONTROL.south(8), Items.CALCITE) == 1 && count(h, CONTROL.south(8), CEItems.QUICKLIME.get()) == 0,
                "An underfired charge exits as the original material");
            h.succeed();
        });
    }

    @GameTest(template = "test_wet_processing", timeoutTicks = 640)
    public static void slowKilnJamsAndCanBeClearedWhileLocked(GameTestHelper h) {
        var machine = kiln(h, 4);
        insert(h, machine, Direction.UP, new ItemStack(Items.CALCITE));
        h.runAfterDelay(615, () -> {
            h.assertTrue(machine.status().equals("kiln_jam") && count(h, CONTROL.south(8), Items.CALCITE) == 0, "Overheated stock is stuck inside the kiln");
            h.setBlock(CONTROL.west(), Blocks.REDSTONE_BLOCK);
            var player = h.makeMockPlayer(GameType.SURVIVAL);
            machine.recover(player);
            h.assertTrue(player.getInventory().contains(CEContinuousFactory.SCRAP.toStack()), "Clearing the jam yields one piece of damaged stock");
            h.setBlock(CONTROL.west(), Blocks.AIR);
        });
        h.runAfterDelay(631, () -> {
            h.assertTrue(machine.status().equals("idle"), "After clearing and removing the lock the kiln can receive material again");
            h.succeed();
        });
    }

    @GameTest(template = "test_wet_processing", timeoutTicks = 165)
    public static void tandemMillMakesAPlateWithCounterRotatingColliders(GameTestHelper h) {
        var machine = mill(h, 30, 40);
        insert(h, machine, Direction.UP, net.createplant.registry.CEConverter.CAST_STEEL_SLAB.toStack());
        h.runAfterDelay(43, () -> {
            var rolls = bodies(h).stream().filter(body -> body.geometry() == FactoryGeometry.ROLLER).toList();
            h.assertTrue(rolls.size() == 4, "Two stands each create both roller contraptions");
            var slab = bodies(h).stream().filter(body -> body.geometry() == FactoryGeometry.SLAB).findFirst().orElseThrow();
            h.assertTrue(slab.pose(1).height() * slab.pose(1).length() > .66 && slab.pose(1).height() * slab.pose(1).length() < .68,
                "Slab elongation conserves volume inside the line");
            h.assertTrue(slab.getContraption().bounds.getYsize() < .78, "The collision box thins together with the moving slab");
        });
        h.runAfterDelay(146, () -> {
            h.assertTrue(count(h, CONTROL.south(7), CEContinuousFactory.THICK_STEEL_PLATE.get()) == 3, "Aligned stands stamp the expected steel plates");
            h.succeed();
        });
    }

    /** The same stock becomes a different product with the number of stands and the burner: plate or beam, sheet or foil, hot or cold. */
    @GameTest(template = "test_wet_processing")
    public static void standCountAndBurnerChooseTheRolledProduct(GameTestHelper h) {
        var steel = CEMetals.STEEL_BLOCK.toStack();
        var quartz = new ItemStack(Items.QUARTZ, 2);
        h.assertTrue(rolled(h, steel, ItemStack.EMPTY, 2, 1).is(CEContinuousFactory.THICK_STEEL_PLATE.get()), "Two hot stands roll plate");
        h.assertTrue(rolled(h, steel, ItemStack.EMPTY, 4, 1).is(CEContinuousFactory.STEEL_H_BEAM.get()), "Four hot stands roll beam");
        h.assertTrue(rolled(h, steel, quartz, 4, 0).is(CEContinuousFactory.ELECTRICAL_STEEL_SHEET.get()), "Four cold stands with silicon roll electrical steel");
        h.assertTrue(rolled(h, steel, quartz, 4, 1).is(CEContinuousFactory.STEEL_H_BEAM.get()), "A lit burner never cold-rolls");
        var waiting = ContinuousFactoryBlockEntity.pick(candidates(h, steel, ItemStack.EMPTY), 2, 0);
        h.assertTrue(waiting != null && waiting.heat() > 0, "Cold steel on two stands waits for heat instead of finding another product");
        var copper = new ItemStack(Items.COPPER_BLOCK);
        h.assertTrue(rolled(h, copper, ItemStack.EMPTY, 3, 1).is(com.simibubi.create.AllItems.COPPER_SHEET.get()), "Few stands roll copper sheet");
        h.assertTrue(rolled(h, copper, ItemStack.EMPTY, 5, 1).is(CEContinuousFactory.COPPER_FOIL.get()), "Many stands roll copper foil");
        var gold = new ItemStack(Items.GOLD_BLOCK);
        h.assertTrue(ContinuousFactoryBlockEntity.pick(candidates(h, gold, ItemStack.EMPTY), 4, 1) == null, "Gold foil needs the full five stands");
        h.assertTrue(rolled(h, gold, ItemStack.EMPTY, 5, 1).getCount() == 32, "Five stands beat gold into foil");
        h.assertTrue(rolled(h, new ItemStack(CEMetals.LEAD_INGOT.get()), ItemStack.EMPTY, 2, 0).is(CEContinuousFactory.LEAD_SHEET.get()), "Soft lead rolls cold");
        h.assertTrue(rolled(h, new ItemStack(CEContinuousFactory.THICK_STEEL_PLATE.get()), new ItemStack(CEItems.STAINLESS_STEEL_SHEET.get()), 2, 1)
            .is(CEContinuousFactory.STAINLESS_CLAD_PLATE.get()), "A stainless sheet on a thick plate bonds into clad plate");
        h.succeed();
    }

    /** Zircaloy allows only 6% speed error: a ratio that steel would forgive buckles the tube stock. */
    @GameTest(template = "test_wet_processing", timeoutTicks = 140)
    public static void zircaloyBucklesWhereSteelIsForgiven(GameTestHelper h) {
        var machine = mill(h, 30, 37);
        h.assertTrue(Math.abs(FactoryProcess.millRatio(30, 37) - 1) < .10, "Steel would tolerate this ratio");
        insert(h, machine, Direction.UP, new ItemStack(CEItems.ZIRCONIUM_INGOT.get(), 3));
        insert(h, machine, Direction.WEST, new ItemStack(CEItems.NICKEL_INGOT.get()));
        h.runAfterDelay(120, () -> {
            h.assertTrue(machine.status().equals("buckled"), "Zircaloy buckles at 30 → 37 RPM: " + machine.status());
            h.succeed();
        });
    }

    /** With the burner removed the line cold-rolls lead into sheet. */
    @GameTest(template = "test_wet_processing", timeoutTicks = 165)
    public static void coldLineRollsLeadSheet(GameTestHelper h) {
        var machine = mill(h, 30, 40);
        h.setBlock(CONTROL.below(), Blocks.AIR);
        insert(h, machine, Direction.UP, new ItemStack(CEMetals.LEAD_INGOT.get()));
        h.runAfterDelay(146, () -> {
            h.assertTrue(count(h, CONTROL.south(7), CEContinuousFactory.LEAD_SHEET.get()) == 2, "Cold stands roll two lead sheets: " + machine.status());
            h.succeed();
        });
    }

    private static List<net.createplant.recipe.ContinuousFactoryRecipe> candidates(GameTestHelper h, ItemStack feed, ItemStack auxiliary) {
        var input = new net.minecraft.world.item.crafting.RecipeInput() {
            @Override public ItemStack getItem(int slot) { return slot == 0 ? feed : auxiliary; }
            @Override public int size() { return 2; }
        };
        return h.getLevel().getRecipeManager().getAllRecipesFor(net.createplant.registry.CERecipeTypes.CONTINUOUS_PROCESSING
                .<net.minecraft.world.item.crafting.RecipeInput, net.createplant.recipe.ContinuousFactoryRecipe>getType()).stream()
            .map(holder -> holder.value()).filter(r -> r.machine() == net.createplant.factory.FactoryKind.MILL && r.matches(input, h.getLevel())).toList();
    }

    private static ItemStack rolled(GameTestHelper h, ItemStack feed, ItemStack auxiliary, int stands, int heat) {
        var recipe = ContinuousFactoryBlockEntity.pick(candidates(h, feed, auxiliary), stands, heat);
        h.assertTrue(recipe != null && heat >= recipe.heat(), "A recipe runs " + feed + " on " + stands + " stands at heat " + heat);
        return recipe.result();
    }

    @GameTest(template = "test_wet_processing", timeoutTicks = 140)
    public static void slowStandBucklesInsteadOfIssuingProduct(GameTestHelper h) { checkMillFault(h, 25, "buckled", 0); }

    @GameTest(template = "test_wet_processing", timeoutTicks = 140)
    public static void fastStandTearsAndDischargesScrap(GameTestHelper h) { checkMillFault(h, 60, "torn", 1); }

    private static void checkMillFault(GameTestHelper h, int second, String expected, int scrap) {
        var machine = mill(h, 30, second);
        insert(h, machine, Direction.UP, CEMetals.STEEL_BLOCK.toStack());
        h.runAfterDelay(120, () -> {
            h.assertTrue(machine.status().equals(expected), "Wrong RPM ratio trips the matching fault: " + machine.status());
            h.assertTrue(count(h, CONTROL.south(7), CEContinuousFactory.THICK_STEEL_PLATE.get()) == 0
                && count(h, CONTROL.south(7), CEContinuousFactory.SCRAP.get()) == scrap, "Fault cannot output a good plate");
            h.assertTrue(bodies(h).stream().anyMatch(p -> p.geometry() == FactoryGeometry.BUCKLED_SLAB) == "buckled".equals(expected),
                "Only the buckled failure has a retained, bent collision body");
            h.succeed();
        });
    }

    @GameTest(template = "test_wet_processing", timeoutTicks = 170)
    public static void winderOrbitsBobbinsAndGrowsTheCoil(GameTestHelper h) {
        var machine = winder(h, 16);
        woundFeed(h, machine);
        h.runAfterDelay(30, () -> {
            var parts = bodies(h);
            h.assertTrue(parts.stream().filter(p -> p.geometry() == FactoryGeometry.SPOOL).count() == 4
                && parts.stream().filter(p -> p.geometry() == FactoryGeometry.WIRE).count() == 4, "Bobbins and wire spans are native moving entities");
            var coil = parts.stream().filter(p -> p.geometry() == FactoryGeometry.COIL).findFirst().orElseThrow();
            h.assertTrue(coil.pose(1).width() > .65 && coil.getContraption().bounds.getXsize() > .77, "Coil expands in both mesh and collision");
        });
        h.runAfterDelay(150, () -> {
            h.assertTrue(count(h, CONTROL.south(6), CEFusion.TOROIDAL_FIELD_COIL.asItem()) == 8, "Normal tension produces the fusion coil blocks");
            h.succeed();
        });
    }

    @GameTest(template = "test_wet_processing", timeoutTicks = 160)
    public static void weakBrakeDischargesLooseWindings(GameTestHelper h) {
        var machine = winder(h, 3);
        woundFeed(h, machine);
        h.runAfterDelay(145, () -> {
            h.assertTrue(count(h, CONTROL.south(6), CEContinuousFactory.LOOSE_WINDING.get()) == 1, "Low tension yields only a loose winding");
            h.succeed();
        });
    }

    @GameTest(template = "test_wet_processing", timeoutTicks = 75)
    public static void strongBrakeSnapsWireAndStops(GameTestHelper h) {
        var machine = winder(h, 28);
        woundFeed(h, machine);
        h.runAfterDelay(55, () -> {
            h.assertTrue(machine.status().equals("snapped") && count(h, CONTROL.south(6), CEFusion.TOROIDAL_FIELD_COIL.asItem()) == 0,
                "Excess tension latches the broken-wire fault");
            h.assertTrue(bodies(h).stream().noneMatch(p -> p.geometry() == FactoryGeometry.WIRE), "A snapped wire leaves no invisible collision span");
            h.succeed();
        });
    }

    @GameTest(template = "test_wet_processing", timeoutTicks = 90)
    public static void removingAFrameStopsAndInvalidatesTheOutlet(GameTestHelper h) {
        var machine = kiln(h, 16);
        var anchor = CONTROL.south(8);
        var support = CONTROL.south(1);
        h.runAfterDelay(15, () -> {
            var lease = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(anchor), Direction.SOUTH);
            h.assertTrue(lease != null && !bodies(h).isEmpty(), "The assembled factory has an outlet and native bodies");
            h.setBlock(support, Blocks.AIR);
            h.runAfterDelay(3, () -> {
                h.assertTrue(machine.status().equals("unformed") && bodies(h).isEmpty(), "The edited support invalidates the entire assembly");
                h.assertTrue(h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(anchor), Direction.SOUTH) == null,
                    "The old outlet is no longer published");
                h.setBlock(support, CEContinuousFactory.FRAME.get());
            });
        });
        h.runAfterDelay(28, () -> {
            h.assertTrue(machine.formedLength() == 8 && !bodies(h).isEmpty(), "Replacing the support recreates the same layout");
            h.succeed();
        });
    }

    @GameTest(template = "test_wet_processing")
    public static void regenerationMatchesOnlyUsedCatalysts(GameTestHelper h) {
        var holder = h.getLevel().getRecipeManager().byKey(CreatePlant.location("continuous_processing/restore_oxidation_catalyst")).orElseThrow();
        var recipe = (net.createplant.recipe.ContinuousFactoryRecipe) holder.value();
        var catalyst = CEItems.OXIDATION_CATALYST.toStack();
        h.assertTrue(!recipe.matches(new SingleRecipeInput(catalyst), h.getLevel()), "A fresh oxidation catalyst cannot be regenerated again");
        catalyst.setDamageValue(CEItems.CATALYST_BATCHES);
        var input = new SingleRecipeInput(catalyst);
        h.assertTrue(recipe.matches(input, h.getLevel()) && recipe.assemble(input, h.getLevel().registryAccess()).getDamageValue() == 0,
            "A worn reaction-tower catalyst becomes an unused one");
        h.succeed();
    }

    @GameTest(template = "test_wet_processing")
    public static void sharedTransformsAndWireEndpointsAgree(GameTestHelper h) {
        var pose = new FactoryPose(new Vec3(14, 8, 13), 90, 2.7f, 63, 1, 1, 13);
        var vector = new Vec3(1.2, 1.45, 7.7);
        Vector3f visual = pose.rotation().transform(vector.toVector3f());
        Vec3 physical = pose.collisionRotation().asMatrix().transformTransposed(vector);
        h.assertTrue(physical.distanceTo(new Vec3(visual.x(), visual.y(), visual.z())) < .002, "Flywheel quaternion and native rotated collision agree");
        var end = new Vec3(10, 12, 13);
        var wire = FactoryPose.wire(pose.center(), end);
        var wireTip = wire.collisionRotation().asMatrix().transformTransposed(new Vec3(0, 0, wire.length()/2)).add(wire.center());
        h.assertTrue(wireTip.distanceTo(end) < .002, "The end of a rotated span is the commanded guide or spool position");
        var hollow = new FactoryContraption(BlockPos.ZERO, FactoryGeometry.DRUM);
        var colliders = hollow.getSimplifiedEntityColliders();
        for (int i = 0; i < colliders.size; i++) h.assertTrue(Math.abs(colliders.centerX[i] - .5) > colliders.extentsX[i]
            || Math.abs(colliders.centerY[i] - .5) > colliders.extentsY[i], "No fake collider fills the centre of the drum");
        h.assertTrue(Math.abs(FactoryProcess.requiredNextSpeed(27) - 36) < 1e-8, "The mill speed ratio follows volume conservation");
        h.succeed();
    }

    private static ContinuousFactoryBlockEntity kiln(GameTestHelper h, int rpm) {
        controller(h, CEContinuousFactory.KILN.get(), 8);
        for (int z : new int[]{1,4,6}) h.setBlock(CONTROL.south(z), CEContinuousFactory.FRAME.get());
        h.setBlock(CONTROL.south(8).above(), CEContinuousFactory.KILN_STACK.get());
        burner(h, CONTROL.south(8).below());
        motor(h, CONTROL.below(), Direction.UP, rpm);
        return h.getBlockEntity(CONTROL);
    }

    private static ContinuousFactoryBlockEntity mill(GameTestHelper h, int first, int second) {
        controller(h, CEContinuousFactory.MILL.get(), 7);
        for (int z : new int[]{2,5}) {
            for (int x : new int[]{-2, 2}) for (int y = 0; y <= 3; y++) {
                if (x == 2 && y == 1) continue;
                h.setBlock(at(x, y, z), CEContinuousFactory.FRAME.get());
            }
            for (int x = -1; x <= 1; x++) h.setBlock(at(x, 3, z), CEContinuousFactory.FRAME.get());
            h.setBlock(at(2, 1, z), CEContinuousFactory.STAND.get().defaultBlockState().setValue(FactoryStageBlock.FACING, Direction.EAST));
            motor(h, at(3, 1, z), Direction.WEST, z == 2 ? first : second);
        }
        burner(h, CONTROL.below());
        return h.getBlockEntity(CONTROL);
    }

    private static ContinuousFactoryBlockEntity winder(GameTestHelper h, int brake) {
        controller(h, CEContinuousFactory.WINDER.get(), 6);
        for (var p : List.of(at(-2,0,2), at(-2,1,2), at(2,0,2), at(0,0,4))) h.setBlock(p, CEContinuousFactory.FRAME.get());
        h.setBlock(at(2, 1, 2), CEContinuousFactory.BRAKE.get().defaultBlockState().setValue(FactoryStageBlock.FACING, Direction.EAST));
        motor(h, at(3, 1, 2), Direction.WEST, brake);
        motor(h, CONTROL.below(), Direction.UP, 32);
        return h.getBlockEntity(CONTROL);
    }

    private static void controller(GameTestHelper h, ContinuousFactoryBlock block, int length) {
        h.setBlock(CONTROL, block.defaultBlockState().setValue(ContinuousFactoryBlock.FACING, FLOW));
        h.setBlock(CONTROL.south(length), CEContinuousFactory.OUTLET.get().defaultBlockState().setValue(FactoryPortBlock.FACING, FLOW));
    }

    private static void motor(GameTestHelper h, BlockPos pos, Direction side, int rpm) {
        h.setBlock(pos, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, side));
        ((CreativeMotorBlockEntity) h.getBlockEntity(pos)).generatedSpeed.setValue(rpm);
    }

    private static void burner(GameTestHelper h, BlockPos pos) {
        h.setBlock(pos, AllBlocks.BLAZE_BURNER.getDefaultState().setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.KINDLED));
        ((BlazeBurnerBlockEntity) h.getBlockEntity(pos)).isCreative = true;
    }

    private static BlockPos at(int x, int y, int z) { return CONTROL.offset(x, y, z); }

    private static void woundFeed(GameTestHelper h, ContinuousFactoryBlockEntity machine) {
        insert(h, machine, Direction.UP, CEArm.ENAMELLED_COPPER_WIRE.toStack(16));
        insert(h, machine, Direction.WEST, AllBlocks.BRASS_CASING.asStack(2));
    }

    private static void insert(GameTestHelper h, ContinuousFactoryBlockEntity machine, Direction face, ItemStack stack) {
        var port = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, machine.getBlockPos(), face);
        h.assertTrue(port != null && port.insertItem(0, stack, false).isEmpty(), "Standard capability accepts the factory charge");
    }

    private static int count(GameTestHelper h, BlockPos at, net.minecraft.world.item.Item item) {
        var port = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(at), Direction.SOUTH);
        if (port == null) return 0;
        int count = 0;
        for (int i = 0; i < port.getSlots(); i++) if (port.getStackInSlot(i).is(item)) count += port.getStackInSlot(i).getCount();
        return count;
    }

    private static List<FactoryBodyEntity> bodies(GameTestHelper h) {
        return h.getLevel().getEntitiesOfClass(FactoryBodyEntity.class, new AABB(h.absolutePos(CONTROL)).inflate(12));
    }
}
