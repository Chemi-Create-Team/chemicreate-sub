package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.createplant.CreatePlant;
import net.createplant.block.TankFootprint;
import net.createplant.block.entity.TankCasingBlockEntity;
import net.createplant.registry.CEBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TankGameTests {
    /** Snow cannot settle on tank casings, so a snowfall never fills the open tank's interior and unforms it. */
    @GameTest(template = "test_empty")
    public static void snowDoesNotSettleOnTankCasing(GameTestHelper helper) {
        BlockPos casing = new BlockPos(2, 2, 2);
        helper.setBlock(casing, CEBlocks.TANK_CASING.get());
        helper.assertFalse(Blocks.SNOW.defaultBlockState().canSurvive(helper.getLevel(), helper.absolutePos(casing.above())),
            "A snow layer cannot rest on a tank casing");
        helper.setBlock(casing, Blocks.STONE);
        helper.assertTrue(Blocks.SNOW.defaultBlockState().canSurvive(helper.getLevel(), helper.absolutePos(casing.above())),
            "Control: snow rests on stone");
        helper.succeed();
    }

    @GameTest(template = "test_empty")
    public static void tankCasingUsesCreateBasinSound(GameTestHelper helper) {
        SoundType basinSound = AllBlocks.BASIN.get().defaultBlockState().getSoundType();
        helper.assertTrue(basinSound == SoundType.NETHERITE_BLOCK
                && CEBlocks.TANK_CASING.get().defaultBlockState().getSoundType() == basinSound,
            "Tank casing uses the Create Basin placement sound");
        helper.succeed();
    }

    @GameTest(template = "test_empty")
    public static void placedCylinderFormsWithoutController(GameTestHelper helper) {
        BlockPos origin = new BlockPos(2, 1, 2);
        buildCylinder(helper, origin, 4, 2);
        helper.runAfterDelay(2, () -> {
            TankCasingBlockEntity center = casing(helper, origin.offset(1, 0, 1));
            helper.assertTrue(center.isFormed() && center.getDiameter() == 4 && center.getHeight() == 2,
                "Placing the last casing forms the tank without a controller: " + center.getInvalidReason());
            helper.succeed();
        });
    }

    @GameTest(template = "test_empty")
    public static void openCylinderFormsAndBrokenWallRetainsFluid(GameTestHelper helper) {
        BlockPos origin = new BlockPos(2, 1, 2);
        int diameter = 4;
        buildCylinder(helper, origin, diameter, 2);
        TankCasingBlockEntity wall = casing(helper, origin.offset(0, 2, (diameter - 1) / 2));
        wall.refresh();
        TankCasingBlockEntity tank = wall.getTank();
        helper.assertTrue(tank != null && tank.getDiameter() == diameter && tank.getHeight() == 2,
            "An upper wall casing recognizes an open-top integer-grid circle: " + wall.getInvalidReason());
        helper.assertTrue(tank.getCapacity() == TankFootprint.blocks(diameter) * 3 * 8_000,
            "Capacity counts the interior and every casing at 8,000 mB per block");
        int capacity = tank.getCapacity();

        IFluidHandler floor = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,
            helper.absolutePos(origin.offset(1, 0, 1)), Direction.DOWN);
        IFluidHandler side = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,
            helper.absolutePos(origin.offset(3, 1, 1)), Direction.EAST);
        helper.assertTrue(floor.fill(new FluidStack(Fluids.WATER, capacity + 1_000), IFluidHandler.FluidAction.EXECUTE)
                == capacity && side.getFluidInTank(0).getAmount() == capacity,
            "Every casing shares one tank inventory limited to its measured capacity");
        helper.setBlock(origin.offset(3, 1, 1), Blocks.AIR);
        wall.refresh();
        helper.assertTrue(!tank.isFormed() && tank.getFluidAmount() == capacity
                && wall.getInvalidReason().equals("wall")
                && floor.fill(new FluidStack(Fluids.WATER, 1_000), IFluidHandler.FluidAction.EXECUTE) == 0,
            "A broken wall disables transfer without deleting stored fluid");
        helper.succeed();
    }

    @GameTest(template = "test_empty")
    public static void removedCenterKeepsFluidUntilTheTankForms(GameTestHelper helper) {
        BlockPos origin = new BlockPos(2, 1, 2);
        BlockPos centerPos = origin.offset(1, 0, 1);
        buildCylinder(helper, origin, 4, 2);
        TankCasingBlockEntity center = casing(helper, centerPos);
        center.refresh();
        helper.assertTrue(center.isFormed(), "The center casing owns the formed tank");
        helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(centerPos), Direction.UP)
            .fill(new FluidStack(Fluids.WATER, 3_000), IFluidHandler.FluidAction.EXECUTE);
        helper.setBlock(centerPos, Blocks.AIR);
        helper.assertTrue(casing(helper, centerPos.north()).getFluidAmount() == 3_000,
            "Removing the center casing hands its fluid to an adjacent casing");
        helper.setBlock(centerPos, CEBlocks.TANK_CASING.get());
        helper.runAfterDelay(2, () -> {
            TankCasingBlockEntity restored = casing(helper, centerPos);
            helper.assertTrue(restored.isFormed() && restored.getFluidAmount() == 3_000
                    && casing(helper, centerPos.north()).getFluidAmount() == 0,
                "The re-formed tank absorbs the retained fluid exactly once");
            helper.succeed();
        });
    }

    @GameTest(template = "test_empty")
    public static void squareFootprintDoesNotFormAsCylinder(GameTestHelper helper) {
        BlockPos origin = new BlockPos(2, 1, 2);
        int diameter = 4;
        for (int x = 0; x < diameter; x++) {
            for (int z = 0; z < diameter; z++) {
                helper.setBlock(origin.offset(x, 0, z), CEBlocks.TANK_CASING.get());
                if (x == 0 || x == diameter - 1 || z == 0 || z == diameter - 1) {
                    helper.setBlock(origin.offset(x, 1, z), CEBlocks.TANK_CASING.get());
                    helper.setBlock(origin.offset(x, 2, z), CEBlocks.TANK_CASING.get());
                }
            }
        }
        TankCasingBlockEntity wall = casing(helper, origin.offset(0, 2, (diameter - 1) / 2));
        wall.refresh();
        helper.assertTrue(wall.getTank() == null, "A square casing enclosure is rejected");
        helper.succeed();
    }

    @GameTest(template = "test_empty")
    public static void ponderSizedTanksFormAtEverySmallDiameterAndHeight(GameTestHelper helper) {
        BlockPos origin = new BlockPos(1, 1, 1);
        for (int diameter = 4; diameter <= 10; diameter++) {
            for (int height = 2; height <= 3; height++) {
                for (int x = 0; x <= 10; x++) {
                    for (int y = 0; y <= 4; y++) {
                        for (int z = 0; z <= 10; z++)
                            helper.setBlock(origin.offset(x, y, z), Blocks.AIR);
                    }
                }

                buildCylinder(helper, origin, diameter, height);
                TankCasingBlockEntity wall = casing(helper, origin.offset(diameter - 1, 2, (diameter - 1) / 2));
                wall.refresh();
                TankCasingBlockEntity tank = wall.getTank();
                helper.assertTrue(tank != null && tank.getDiameter() == diameter && tank.getHeight() == height,
                    "Ponder sized tanks must form for diameter " + diameter + " and height " + height
                        + ": " + wall.getInvalidReason());
            }
        }
        helper.succeed();
    }

    @GameTest(template = "test_petrochemical", timeoutTicks = 200)
    public static void mechanicalPumpFillsTankThroughCasing(GameTestHelper helper) {
        BlockPos origin = new BlockPos(3, 1, 1);
        buildCylinder(helper, origin, 4, 2);
        helper.setBlock(new BlockPos(0, 2, 2), AllBlocks.FLUID_TANK.getDefaultState());
        FluidTankBlockEntity source = (FluidTankBlockEntity) helper.getBlockEntity(new BlockPos(0, 2, 2));
        source.getTankInventory().fill(new FluidStack(Fluids.WATER, 4_000), IFluidHandler.FluidAction.EXECUTE);
        helper.setBlock(new BlockPos(1, 2, 2), AllBlocks.MECHANICAL_PUMP.getDefaultState()
            .setValue(BlockStateProperties.FACING, Direction.EAST));
        BlockState pipe = AllBlocks.FLUID_PIPE.getDefaultState();
        for (Direction side : Direction.values())
            pipe = pipe.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side), side.getAxis() == Direction.Axis.X);
        helper.setBlock(new BlockPos(2, 2, 2), pipe);
        helper.setBlock(new BlockPos(1, 2, 3), AllBlocks.COGWHEEL.getDefaultState()
            .setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        BlockPos motor = new BlockPos(0, 2, 3);
        helper.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState()
            .setValue(BlockStateProperties.FACING, Direction.EAST));
        helper.runAfterDelay(5, () -> ((CreativeMotorBlockEntity) helper.getBlockEntity(motor)).generatedSpeed.setValue(64));
        helper.runAfterDelay(160, () -> {
            TankCasingBlockEntity center = casing(helper, origin.offset(1, 0, 1));
            int stored = center.getFluidAmount();
            helper.assertTrue(center.isFormed() && stored > 0
                    && stored + source.getTankInventory().getFluidAmount() == 4_000,
                "A driven Create mechanical pump fills the tank through a wall casing without losing volume: " + stored);
            helper.succeed();
        });
    }

    @GameTest(template = "test_petrochemical", timeoutTicks = 200)
    public static void pumpRestingOnTheRimFillsTankFromAbove(GameTestHelper helper) {
        BlockPos origin = new BlockPos(3, 1, 1);
        buildCylinder(helper, origin, 4, 2);
        BlockPos pump = origin.offset(0, 3, 1);
        helper.setBlock(pump, AllBlocks.MECHANICAL_PUMP.getDefaultState()
            .setValue(BlockStateProperties.FACING, Direction.DOWN));
        helper.setBlock(pump.above(), AllBlocks.FLUID_TANK.getDefaultState());
        FluidTankBlockEntity source = (FluidTankBlockEntity) helper.getBlockEntity(pump.above());
        source.getTankInventory().fill(new FluidStack(Fluids.WATER, 4_000), IFluidHandler.FluidAction.EXECUTE);
        helper.setBlock(pump.north(), AllBlocks.COGWHEEL.getDefaultState()
            .setValue(BlockStateProperties.AXIS, Direction.Axis.Y));
        BlockPos motor = pump.north().above();
        helper.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState()
            .setValue(BlockStateProperties.FACING, Direction.DOWN));
        helper.runAfterDelay(5, () -> ((CreativeMotorBlockEntity) helper.getBlockEntity(motor)).generatedSpeed.setValue(64));
        helper.runAfterDelay(160, () -> {
            TankCasingBlockEntity center = casing(helper, origin.offset(1, 0, 1));
            int stored = center.getFluidAmount();
            helper.assertTrue(center.isFormed() && center.getHeight() == 2 && stored > 0
                    && stored + source.getTankInventory().getFluidAmount() == 4_000,
                "A pump resting on the rim keeps the open top valid and fills the tank: " + stored
                    + " " + center.getInvalidReason());
            helper.succeed();
        });
    }

    @GameTest(template = "test_empty")
    public static void blockItemsPlaceAgainstCasingsWithoutSneaking(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, CEBlocks.TANK_CASING.get());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos absolute = helper.absolutePos(pos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
        BlockState state = helper.getBlockState(pos);
        helper.assertTrue(state.useItemOn(new ItemStack(CEBlocks.TANK_CASING.get()), helper.getLevel(), player,
                InteractionHand.MAIN_HAND, hit) == ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION
                && state.useItemOn(ItemStack.EMPTY, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit)
                    == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION,
            "Non-fluid items skip the status interaction, so they are placed or used normally");
        helper.succeed();
    }

    @GameTest(template = "test_empty")
    public static void clientUpdateCarriesTheVisibleFluidLevel(GameTestHelper helper) {
        BlockPos origin = new BlockPos(2, 1, 2);
        BlockPos centerPos = origin.offset(1, 0, 1);
        buildCylinder(helper, origin, 4, 2);
        TankCasingBlockEntity center = casing(helper, centerPos);
        center.refresh();
        int half = center.getCapacity() / 2;
        helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(centerPos), Direction.UP)
            .fill(new FluidStack(Fluids.WATER, half), IFluidHandler.FluidAction.EXECUTE);
        TankCasingBlockEntity view = new TankCasingBlockEntity(center.getBlockPos(), center.getBlockState());
        view.loadWithComponents(center.getUpdateTag(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertTrue(view.getShape() != null && view.getShape().height() == 2
                && view.getFluid().getAmount() == half && Math.abs(view.getFluidLevel(1) - .5f) < 1e-4f,
            "The owner's client update carries its shape, fluid and fill fraction");
        TankCasingBlockEntity member = casing(helper, origin.offset(0, 1, 1));
        view.loadWithComponents(member.getUpdateTag(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertTrue(view.getShape() == null, "A member's update clears any previously drawn tank");
        helper.succeed();
    }

    @GameTest(template = "test_empty")
    public static void circleFootprintsStaySymmetricAndInsideTheirDiameter(GameTestHelper helper) {
        for (int diameter = TankFootprint.MIN_DIAMETER;
                diameter <= TankFootprint.MAX_DIAMETER; diameter++) {
            int count = 0;
            for (int x = 0; x < diameter; x++) {
                for (int z = 0; z < diameter; z++) {
                    boolean cell = TankFootprint.contains(x, z, diameter);
                    helper.assertTrue(cell == TankFootprint.contains(diameter - x - 1, z, diameter)
                            && cell == TankFootprint.contains(x, diameter - z - 1, diameter)
                            && cell == TankFootprint.contains(z, x, diameter),
                        "Circle footprints are symmetric for diameter " + diameter);
                    if (cell)
                        count++;
                }
            }
            helper.assertTrue(count > 0 && count < diameter * diameter,
                "Circle footprints are bounded and reject square corners for diameter " + diameter);
        }
        helper.succeed();
    }

    private static TankCasingBlockEntity casing(GameTestHelper helper, BlockPos pos) {
        return (TankCasingBlockEntity) helper.getBlockEntity(pos);
    }

    private static void buildCylinder(GameTestHelper helper, BlockPos origin, int diameter, int height) {
        for (int x = 0; x < diameter; x++) {
            for (int z = 0; z < diameter; z++) {
                if (!TankFootprint.contains(x, z, diameter))
                    continue;
                helper.setBlock(origin.offset(x, 0, z), CEBlocks.TANK_CASING.get());
                if (TankFootprint.isWall(x, z, diameter)) {
                    for (int y = 1; y <= height; y++)
                        helper.setBlock(origin.offset(x, y, z), CEBlocks.TANK_CASING.get());
                }
            }
        }
    }
}
