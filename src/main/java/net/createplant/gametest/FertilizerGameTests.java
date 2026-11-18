package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.createplant.CreatePlant;
import net.createplant.registry.CEItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class FertilizerGameTests {
    private static final BlockPos CENTRE = new BlockPos(2, 2, 2);

    private static void field(GameTestHelper h) {
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) {
            h.setBlock(new BlockPos(x, 1, z), Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7));
            h.setBlock(new BlockPos(x, 2, z), Blocks.WHEAT.defaultBlockState());
            for (int y = 3; y < 6; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
        }
    }

    private static int grownAround(GameTestHelper h) {
        int grown = 0;
        for (int x = 1; x <= 3; x++) for (int z = 1; z <= 3; z++) if (h.getBlockState(new BlockPos(x, 2, z)).getValue(CropBlock.AGE) > 0) grown++;
        return grown;
    }

    @GameTest(template = "test_reactor")
    public static void oneFertilizerGrowsTheThreeByThreeCropsAroundTheClickedOne(GameTestHelper h) {
        field(h);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CEItems.AMMONIUM_SULFATE.get(), 2));
        var hit = new BlockHitResult(Vec3.atCenterOf(h.absolutePos(CENTRE)), Direction.UP, h.absolutePos(CENTRE), false);
        player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        h.assertTrue(grownAround(h) == 9 && player.getMainHandItem().getCount() == 1, "All nine crops grow for one item");
        h.assertTrue(h.getBlockState(new BlockPos(0, 2, 2)).getValue(CropBlock.AGE) == 0, "Crops outside the 3 x 3 stay as they were");
        h.succeed();
    }

    @GameTest(template = "test_reactor", timeoutTicks = 160)
    public static void aCreateDeployerSpreadsFertilizerTheSameWay(GameTestHelper h) {
        field(h);
        BlockPos deployer = CENTRE.above(2);
        var state = AllBlocks.DEPLOYER.getDefaultState().setValue(BlockStateProperties.FACING, Direction.DOWN);
        h.setBlock(deployer, state);
        Direction drive = Direction.get(Direction.AxisDirection.POSITIVE, AllBlocks.DEPLOYER.get().getRotationAxis(state));
        BlockPos motor = deployer.relative(drive);
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, drive.getOpposite()));
        h.runAfterDelay(5, () -> {
            var items = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(deployer), drive);
            h.assertTrue(items != null && items.insertItem(0, new ItemStack(CEItems.AMMONIUM_NITRATE.get()), false).isEmpty(), "The deployer takes the fertilizer");
            ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(128);
        });
        h.runAfterDelay(120, () -> {
            h.assertTrue(grownAround(h) == 9, "The deployer's use grows all nine crops: " + grownAround(h));
            h.succeed();
        });
    }
}
