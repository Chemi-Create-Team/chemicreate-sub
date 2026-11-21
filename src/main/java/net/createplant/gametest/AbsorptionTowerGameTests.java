package net.createplant.gametest;

import net.createplant.CreatePlant;
import net.createplant.block.ExtractionColumnSectionBlock;
import net.createplant.block.ExtractionColumnStructureBlock;
import net.createplant.block.entity.AbsorptionTowerBlockEntity;
import net.createplant.registry.CEChlorAlkali;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CEOstwaldFluids;
import net.createplant.registry.CEReactor;
import net.createplant.registry.CEUranium;
import net.createplant.registry.CEWaterTreatment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

/** The counter-current absorption tower in the supported large size: its height limits, the faces of its four ports and the three acids. */
@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class AbsorptionTowerGameTests {
    /** The middle of the 3x3 square the tower stands on. */
    private static final BlockPos CENTRE = new BlockPos(5, 2, 5);

    /** One storey: its center and the eight ring cells pointing in towards it. */
    public static void storey(ServerLevel level, BlockPos centre, boolean large) {
        level.setBlock(centre, CEReactor.LARGE_ABSORPTION_TOWER.get().defaultBlockState(), 3);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) if (x != 0 || z != 0)
            level.setBlock(centre.offset(x, 0, z), CEReactor.LARGE_ABSORPTION_RING.get().defaultBlockState().setValue(ExtractionColumnStructureBlock.FACING,
                ExtractionColumnSectionBlock.ring(x, z).getValue(ExtractionColumnStructureBlock.FACING)), 3);
    }

    /** A tower of the given storeys whose bottom storey stands at {@code centre}, returned validated. */
    public static AbsorptionTowerBlockEntity tower(ServerLevel level, BlockPos centre, int storeys, boolean large) {
        for (int y = 0; y < storeys; y++) storey(level, centre.above(y), large);
        var tower = (AbsorptionTowerBlockEntity) level.getBlockEntity(centre);tower.validateStructure();return tower;
    }

    private static AbsorptionTowerBlockEntity tower(GameTestHelper h, int storeys, boolean large) { return tower(h.getLevel(), h.absolutePos(CENTRE), storeys, large); }

    private static IFluidHandler port(GameTestHelper h, BlockPos pos, Direction side) {
        return h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
    }

    private static void run(AbsorptionTowerBlockEntity tower, int ticks) { for (int i = 0; i < ticks; i++) tower.tickProcess(); }

    @GameTest(template = "test_reactor")
    public static void fourToEightStoreysOfOneSizeFormWithoutRotation(GameTestHelper h) {
        var tower = tower(h, 3, false);var level = h.getLevel();var bottom = tower.getBlockPos();
        h.assertTrue(!tower.formed() && tower.status().equals("column"), "Three storeys are too short");
        storey(level, bottom.above(3), false);tower.validateStructure();
        h.assertTrue(tower.formed() && tower.sections() == 4 && tower.calculateStressApplied() == 0 && tower.rotationSpeed() == 32,
            "Four large storeys form a tower that needs no rotation");
        for (int y = 4; y < 9; y++) storey(level, bottom.above(y), false);tower.validateStructure();
        h.assertTrue(!tower.formed() && tower.status().equals("column"), "Nine storeys are too tall");
        level.removeBlock(bottom.above(8), false);tower.validateStructure();
        h.assertTrue(tower.formed() && tower.sections() == 8, "Eight storeys form");
        h.succeed();
    }

    @GameTest(template = "test_reactor", timeoutTicks = 40)
    public static void gasEntersBelowWaterAboveAndEachOutletGivesOnlyItsProduct(GameTestHelper h) {
        var tower = tower(h, 4, false);var bottom = tower.getBlockPos();var top = bottom.above(3);
        var gasIn = port(h, bottom.east(), Direction.EAST);var waterIn = port(h, top, Direction.UP);
        h.assertTrue(gasIn.fill(new FluidStack(Fluids.WATER, 100), SIMULATE) == 0 && waterIn.fill(new FluidStack(CEOstwaldFluids.NITROGEN_DIOXIDE.get(), 300), SIMULATE) == 0,
            "Water goes in only at the top and gas only at the bottom");
        h.assertTrue(port(h, bottom.above().east(), Direction.EAST) == null && port(h, bottom, Direction.DOWN) == null, "The middle storeys and the underside have no port");
        h.assertTrue(port(h, bottom.north().east(), Direction.NORTH) == null && port(h, bottom.north().east(), Direction.EAST) == null
            && port(h, top.south().west(), Direction.WEST) == null, "The diagonal faces of the octagon have no port");
        h.assertTrue(port(h, bottom, Direction.EAST) == null && port(h, bottom.east(), Direction.NORTH) == null && port(h, top.east(), Direction.UP) == null
            && port(h, top.east(), Direction.EAST).fill(new FluidStack(Fluids.WATER, 100), SIMULATE) == 0, "Water enters only at the top of the top storey's centre");
        h.assertTrue(gasIn.fill(new FluidStack(CEOstwaldFluids.NITROGEN_DIOXIDE.get(), 300), EXECUTE) == 300 && waterIn.fill(new FluidStack(Fluids.WATER, 100), EXECUTE) == 100,
            "Nitrogen dioxide and water enter their own ends");
        run(tower, 101);
        var acid = port(h, bottom.west(), Direction.WEST).drain(new FluidStack(CEFluids.NITRIC_ACID.get(), 1000), EXECUTE);
        var monoxide = port(h, top.north(), Direction.NORTH).drain(new FluidStack(CEOstwaldFluids.NITRIC_OXIDE.get(), 1000), EXECUTE);
        h.assertTrue(acid.getAmount() == 200 && monoxide.getAmount() == 100, "Nitric acid leaves at the bottom and the unabsorbed nitric oxide at the top");
        h.assertTrue(port(h, bottom.west(), Direction.WEST).drain(new FluidStack(CEOstwaldFluids.NITRIC_OXIDE.get(), 1000), SIMULATE).isEmpty(), "The bottom never gives the top's gas");
        h.succeed();
    }

    @GameTest(template = "test_reactor", timeoutTicks = 40)
    public static void hydrogenChlorideBecomesHydrochloricAcidAndAFullOutletStopsTheTower(GameTestHelper h) {
        var tower = tower(h, 4, false);var bottom = tower.getBlockPos();var top = bottom.above(3);
        var gasIn = port(h, bottom.south(), Direction.SOUTH);var waterIn = port(h, top, Direction.UP);
        gasIn.fill(new FluidStack(CEChlorAlkali.HYDROGEN_CHLORIDE.get(), 500), EXECUTE);waterIn.fill(new FluidStack(Fluids.WATER, 500), EXECUTE);run(tower, 101);
        h.assertTrue(gasIn.drain(new FluidStack(CEChlorAlkali.HYDROCHLORIC_ACID.get(), 10000), SIMULATE).getAmount() == 1000, "500 mB hydrogen chloride and 500 mB water give 1000 mB hydrochloric acid");
        for (int i = 0; i < 8; i++) { gasIn.fill(new FluidStack(CEChlorAlkali.HYDROGEN_CHLORIDE.get(), 500), EXECUTE);waterIn.fill(new FluidStack(Fluids.WATER, 500), EXECUTE);run(tower, 101); }
        h.assertTrue(gasIn.drain(new FluidStack(CEChlorAlkali.HYDROCHLORIC_ACID.get(), 100000), SIMULATE).getAmount() == 8000
            && tower.port(Direction.NORTH).getFluidInTank(1).getAmount() == 500, "A full acid buffer keeps the next feed untouched");
        h.succeed();
    }

    @GameTest(template = "test_reactor", timeoutTicks = 40)
    public static void sulfurTrioxideBecomesSulfuricAcid(GameTestHelper h) {
        var tower = tower(h, 4, false);var bottom = tower.getBlockPos();
        port(h, bottom.north(), Direction.NORTH).fill(new FluidStack(CEUranium.SULFUR_TRIOXIDE.get(), 500), EXECUTE);
        port(h, bottom.above(3), Direction.UP).fill(new FluidStack(Fluids.WATER, 500), EXECUTE);run(tower, 101);
        h.assertTrue(port(h, bottom.north(), Direction.NORTH).drain(new FluidStack(CEUranium.SULFURIC_ACID.get(), 10000), SIMULATE).getAmount() == 500,
            "Two 250 mB batches run in parallel: 500 mB sulfur trioxide and water give 500 mB sulfuric acid");
        h.succeed();
    }

    @GameTest(template = "test_reactor", timeoutTicks = 40)
    public static void sulfurDioxideBecomesSulfurousAcid(GameTestHelper h) {
        var tower = tower(h, 4, false);var bottom = tower.getBlockPos();
        port(h, bottom.north(), Direction.NORTH).fill(new FluidStack(CEUranium.SULFUR_DIOXIDE.get(), 500), EXECUTE);
        port(h, bottom.above(3), Direction.UP).fill(new FluidStack(Fluids.WATER, 500), EXECUTE);run(tower, 101);
        h.assertTrue(port(h, bottom.north(), Direction.NORTH).drain(new FluidStack(CEUranium.SULFUROUS_ACID.get(), 10000), SIMULATE).getAmount() == 500,
            "Two 250 mB batches run in parallel: 500 mB sulfur dioxide and water give 500 mB sulfurous acid");
        h.succeed();
    }

    @GameTest(template = "test_reactor", timeoutTicks = 40)
    public static void causticWashCapturesAnIndustrialGasAndRecoversGypsum(GameTestHelper h) {
        var tower = tower(h, 4, true);
        var bottom = tower.getBlockPos();
        var top = bottom.above(3);
        var gas = new FluidStack(CEUranium.SULFUR_DIOXIDE.get(), 250);
        var wash = new FluidStack(CEChlorAlkali.CAUSTIC_SODA.get(), 250);
        var inlet = port(h, bottom.east(), Direction.EAST);
        var topPort = port(h, top.west(), Direction.WEST);
        h.assertTrue(inlet.fill(gas, EXECUTE) == 250 && inlet.fill(wash, SIMULATE) == 0 && topPort.fill(gas, SIMULATE) == 0 && topPort.fill(wash, SIMULATE) == 0,
            "Only the bottom ring takes exhaust, and the top ring takes no wash liquid");
        h.assertTrue(port(h, top, Direction.UP).fill(wash, EXECUTE) == 250, "A formed tower accepts caustic wash at the top of its centre");
        run(tower, 101);
        var recovered = inlet.drain(500, EXECUTE);
        h.assertTrue(recovered.is(CEWaterTreatment.SULFUR_SCRUBBER_LIQUOR.get()) && recovered.getAmount() == 500
            && topPort.drain(500, SIMULATE).isEmpty(), "A finite batch is captured as sulfur scrubber liquor with no exhaust at the top");
        h.assertTrue(net.createplant.cloud.GasClouds.in(h.getLevel(), new net.minecraft.world.phys.AABB(bottom).inflate(6)).isEmpty(), "Washing the closed feed creates no gas cloud");

        var basinPos = new BlockPos(9, 2, 5);
        h.setBlock(basinPos, com.simibubi.create.AllBlocks.BASIN.get());
        var basin = (com.simibubi.create.content.processing.basin.BasinBlockEntity) h.getBlockEntity(basinPos);
        basin.getInputInventory().insertItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CALCITE), false);
        basin.inputTank.getCapability().fill(recovered, EXECUTE);
        var recovery = h.getLevel().getRecipeManager().byKey(CreatePlant.location("mixing/scrubber_gypsum")).orElseThrow().value();
        h.assertTrue(com.simibubi.create.content.processing.basin.BasinRecipe.apply(basin, recovery)
            && basin.getOutputInventory().getStackInSlot(0).is(net.createplant.registry.CEFuelCycle.GYPSUM.get()),
            "Create's basin consumes the recovered wash liquid and yields gypsum");
        h.succeed();
    }

    @GameTest(template = "test_reactor", timeoutTicks = 40)
    public static void theLargeTowerTakesPipesAtItsRingAndRunsLikeTheSmallOne(GameTestHelper h) {
        var tower = tower(h, 4, true);var bottom = tower.getBlockPos();var top = bottom.above(3);
        h.assertTrue(tower.formed() && tower.sections() == 4 && tower.calculateStressApplied() == 0, "Four large storeys form the tower");
        var gasIn = port(h, bottom.east(), Direction.EAST);var waterIn = port(h, top, Direction.UP);
        h.assertTrue(gasIn != null && waterIn != null && port(h, bottom.above().east(), Direction.EAST) == null, "The ring hands on its storey's ports, and the middle storeys have none");
        h.assertTrue(gasIn.fill(new FluidStack(Fluids.WATER, 100), SIMULATE) == 0, "The bottom ring takes no water");
        gasIn.fill(new FluidStack(CEOstwaldFluids.NITROGEN_DIOXIDE.get(), 300), EXECUTE);waterIn.fill(new FluidStack(Fluids.WATER, 100), EXECUTE);run(tower, 101);
        h.assertTrue(port(h, bottom.south(), Direction.SOUTH).drain(new FluidStack(CEFluids.NITRIC_ACID.get(), 1000), EXECUTE).getAmount() == 200
            && port(h, top.west(), Direction.WEST).drain(new FluidStack(CEOstwaldFluids.NITRIC_OXIDE.get(), 1000), EXECUTE).getAmount() == 100,
            "Nitric acid leaves the bottom ring and nitric oxide the top ring");
        var saved = tower.saveWithoutMetadata(h.getLevel().registryAccess());tower.loadWithComponents(saved, h.getLevel().registryAccess());tower.validateStructure();run(tower, 101);
        h.assertTrue(tower.port(Direction.NORTH).getFluidInTank(2).isEmpty(), "A reloaded tower makes nothing without feed");
        h.succeed();
    }

    @GameTest(template = "test_reactor")
    public static void aLargeStoreyClickedOnTheDeckStacksOnTheCentre(GameTestHelper h) {
        var level = h.getLevel();var bottom = h.absolutePos(CENTRE);storey(level, bottom, true);
        var player = h.makeMockPlayer(GameType.SURVIVAL);var stack = CEReactor.LARGE_ABSORPTION_TOWER.asItem().getDefaultInstance();
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var corner = bottom.north().east();
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(corner).add(0, .5, 0), Direction.UP, corner, false)));
        h.assertTrue(level.getBlockState(bottom.above()).is(CEReactor.LARGE_ABSORPTION_TOWER.get()) && level.getBlockState(corner.above()).is(CEReactor.LARGE_ABSORPTION_RING.get()),
            "Clicking a corner of the deck places the next storey over the centre with its ring");
        h.succeed();
    }
}
