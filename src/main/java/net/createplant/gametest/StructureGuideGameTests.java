package net.createplant.gametest;

import net.createplant.CreatePlant;
import net.createplant.block.entity.LiddedVesselBlockEntity;
import net.createplant.chemistry.ReactorModule;
import net.createplant.guide.GuideSelector;
import net.createplant.guide.ReactorGuide;
import net.createplant.registry.CEBlocks;
import net.createplant.registry.CEMetals;
import net.createplant.registry.CEReactor;
import net.createplant.registry.CETransport;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StructureGuideGameTests {
    private static final BlockPos BASE = new BlockPos(21, 2, 4);

    private static void pile(GameTestHelper helper, BlockPos at) { helper.setBlock(at, CEBlocks.TRIIRON_TETROXIDE_PILE.get().defaultBlockState()); }


    @GameTest(template = "test_empty")
    public static void reactorGuideOffersEverySectionCountTheScanAccepts(GameTestHelper helper) {
        BlockPos base = new BlockPos(1, 1, 1);
        var vessel = ReactorGuide.layouts(base, ReactorModule.STIRRED);
        helper.assertTrue(vessel.size() == 4 && vessel.get(0).cells().size() == 9 && vessel.get(3).cells().size() == 4 * 9, "Vessel: a 3x3 stirred vessel of one to four layers on the drive");
        helper.assertTrue(ReactorGuide.layouts(base, ReactorModule.ABSORBER).size() == 3, "The absorber needs at least two layers of trays");
        helper.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void storageVesselsDoNotShowProcessExpansionGhosts(GameTestHelper helper) {
        var guide = new ReactorGuide();var level = helper.getLevel();
        for (int width = 2; width <= 3; width++) {
            BlockPos origin = BASE.east((width - 2) * 8);
            for (int x = 0; x < width; x++) for (int z = 0; z < width; z++) helper.setBlock(origin.offset(x, 0, z), CETransport.LIDDED_VESSEL.get());
            var owner = ((LiddedVesselBlockEntity) helper.getBlockEntity(origin)).getControllerBE();
            helper.assertTrue(guide.anchor(owner) == null && guide.layouts(level, owner.getBlockPos()).isEmpty(), "Plain storage has no process assembly preview at width " + width);
            if (width == 2) {
                owner.setInternal(CEReactor.INTERNALS.get(ReactorModule.STIRRED).get().getDefaultInstance());
                helper.assertTrue(guide.anchor(owner) == null && guide.layouts(level, owner.getBlockPos()).isEmpty(), "A saved 2x2 internal cannot request a process preview");
            } else {
                helper.assertTrue(!owner.canTakeInternal(net.createplant.registry.CEHighStressMachines.CENTRIFUGE.asItem().getDefaultInstance()), "The standalone centrifuge is not a vessel internal");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void tankGuideStartsFromOneCasingAndReusesWhatIsBuilt(GameTestHelper helper) {
        var casing = CEBlocks.TANK_CASING.get().defaultBlockState();
        var level = helper.getLevel();
        var guide = new net.createplant.guide.TankGuide();
        BlockPos first = BASE.offset(0, 0, 1);
        helper.setBlock(first, casing);
        var layouts = guide.layouts(level, helper.absolutePos(first));
        helper.assertTrue(layouts.size() == (net.createplant.block.TankFootprint.MAX_DIAMETER - net.createplant.block.TankFootprint.MIN_DIAMETER + 1) * 1, "One casing offers every diameter at the minimum height");
        var plan = GuideSelector.choose(level, layouts);
        int ghosts = (int) plan.layout().cells().stream().filter(cell -> cell.ghost() != null).count();
        helper.assertTrue(plan.layout().name().equals("4 wide, 2 high") && plan.missing().size() == ghosts - 1, "The smallest tank is asked for, minus the casing already there: " + plan.layout().name());
        // A foreign block in the first candidate's wall rules it out; the guide moves on instead of drawing through it.
        var blocked = plan.missing().get(plan.missing().size() / 2).pos();
        level.setBlock(blocked, Blocks.STONE.defaultBlockState(), 3);
        var next = GuideSelector.choose(level, guide.layouts(level, helper.absolutePos(first)));
        helper.assertTrue(next == null || !next.layout().name().equals("4 wide, 2 high"), "A blocked candidate is not guided");
        level.setBlock(blocked, Blocks.AIR.defaultBlockState(), 3);
        helper.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void tankGuideLeavesAFormedTankAlone(GameTestHelper helper) {
        var casing = CEBlocks.TANK_CASING.get().defaultBlockState();
        int d = net.createplant.block.TankFootprint.MIN_DIAMETER;
        var casings = new java.util.ArrayList<net.minecraft.world.level.block.entity.BlockEntity>();
        for (int x = 0; x < d; x++) for (int z = 0; z < d; z++) {
            if (!net.createplant.block.TankFootprint.contains(x, z, d)) continue;
            for (int y = 0; y <= (net.createplant.block.TankFootprint.isWall(x, z, d) ? 2 : 0); y++) helper.setBlock(BASE.offset(x, y, z), casing);
        }
        helper.runAfterDelay(5, () -> {
            for (int x = 0; x < d; x++) for (int z = 0; z < d; z++) if (net.createplant.block.TankFootprint.contains(x, z, d)) casings.add(helper.getBlockEntity(BASE.offset(x, 0, z)));
            ((net.createplant.block.entity.TankCasingBlockEntity) casings.get(0)).refresh();
            var guide = new net.createplant.guide.TankGuide();
            helper.assertTrue(casings.stream().anyMatch(be -> be instanceof net.createplant.block.entity.TankCasingBlockEntity tank && tank.isFormed()), "The tank forms");
            helper.assertTrue(casings.stream().allMatch(be -> guide.anchor(be, casings) == null), "No casing of a formed tank starts a guide");
            helper.succeed();
        });
    }

    @GameTest(template = "test_fractionation")
    public static void aSteelSphereStaysOrdinaryBuildingBlocks(GameTestHelper helper) {
        int diameter = 6;
        java.util.function.Predicate<BlockPos> inside = pos -> {
            if (pos.getX() < 0 || pos.getY() < 0 || pos.getZ() < 0
                || pos.getX() >= diameter || pos.getY() >= diameter || pos.getZ() >= diameter) return false;
            int x = 2 * pos.getX() - diameter + 1, y = 2 * pos.getY() - diameter + 1, z = 2 * pos.getZ() - diameter + 1;
            return x * x + y * y + z * z <= diameter * diameter;
        };
        var positions = new java.util.ArrayList<BlockPos>();
        for (int x = 0; x < diameter; x++) for (int y = 0; y < diameter; y++) for (int z = 0; z < diameter; z++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!inside.test(pos) || java.util.Arrays.stream(net.minecraft.core.Direction.values()).allMatch(side -> inside.test(pos.relative(side)))) continue;
            positions.add(BASE.offset(pos));
            helper.setBlock(BASE.offset(pos), CEMetals.STEEL_BLOCK.get().getBlock());
        }
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(!positions.isEmpty(), "The test builds a complete sphere");
            for (BlockPos pos : positions) helper.assertTrue(helper.getBlockState(pos).is(CEMetals.STEEL_BLOCK.get().getBlock())
                && helper.getLevel().getBlockEntity(helper.absolutePos(pos)) == null, "Steel stays steel without forming a retired gas holder");
            helper.succeed();
        });
    }
}
