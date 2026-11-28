package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.createplant.CreatePlant;
import net.createplant.block.entity.QuarryDriveBlockEntity;
import net.createplant.registry.CEQuarry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemHandlerHelper;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class QuarrySpeedGameTests {
    @GameTest(template = "test_rbmk", timeoutTicks = 2400)
    public static void threeLayersAtSixtyFourRpm(GameTestHelper h) {
        var drive = new BlockPos(3, 8, 3);
        h.setBlock(drive, CEQuarry.QUARRY_DRIVE.get().defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
        h.setBlock(drive.south(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        ((QuarryDriveBlockEntity) h.getBlockEntity(drive)).size(5, 3);
        for (int x = 4; x <= 8; x++) for (int z = 4; z <= 6; z++) for (int y = 5; y <= 7; y++) h.setBlock(new BlockPos(x, y, z), Blocks.DIRT);
        var quarry = (QuarryDriveBlockEntity) h.getBlockEntity(drive);
        ItemHandlerHelper.insertItem(quarry.store(), AllBlocks.METAL_GIRDER.asStack(34), false);
        ItemHandlerHelper.insertItem(quarry.store(), AllItems.BELT_CONNECTOR.asStack(), false);
        ItemHandlerHelper.insertItem(quarry.store(), AllBlocks.SHAFT.asStack(2), false);
        ItemHandlerHelper.insertItem(quarry.store(), AllBlocks.ENCASED_CHAIN_DRIVE.asStack(6), false);
        h.setBlock(drive.above(), Blocks.REDSTONE_BLOCK);
        h.runAfterDelay(2, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(drive.south())).generatedSpeed.setValue(64));
        long[] start = {0, h.getLevel().getGameTime()};
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(quarry.structure() != null && quarry.status().equals("drill"), "The built frame and belt await the drill"))
            .thenExecute(() -> {
                start[0] = h.getLevel().getGameTime();
                long buildTicks = start[0] - start[1];
                h.assertTrue(buildTicks <= 60, "The twenty-two frame parts and belt are ready within sixty ticks: " + buildTicks);
                System.out.println("QUARRY_BUILD steps=" + QuarryDriveBlockEntity.WORK_STEPS + " parts=22 buildTicks=" + buildTicks);
                quarry.fitDrill();
            })
            .thenWaitUntil(() -> {
                int remaining = 0;
                for (int x = 4; x <= 8; x++) for (int z = 4; z <= 6; z++) for (int y = 5; y <= 7; y++)
                    if (h.getBlockState(new BlockPos(x, y, z)).is(Blocks.DIRT)) remaining++;
                h.assertTrue(remaining == 0, "Three complete layers have been mined: " + remaining);
            })
            .thenExecute(() -> {
                long elapsed = h.getLevel().getGameTime() - start[0];
                System.out.println("QUARRY_SPEED steps=" + QuarryDriveBlockEntity.WORK_STEPS + " blocks=45 rpm=64 miningTicks=" + elapsed);
                h.assertTrue(elapsed >= 82 && elapsed <= 84, "Eight work steps mine the 658-step reference within one game tick of 83: " + elapsed);
                int held = quarry.conveyor().contents().stream().filter(stack -> stack.is(Items.DIRT)).mapToInt(net.minecraft.world.item.ItemStack::getCount).sum();
                h.assertTrue(held <= 45, "No more ore than the forty-five mined blocks is held");
            })
            .thenSucceed();
    }
}
