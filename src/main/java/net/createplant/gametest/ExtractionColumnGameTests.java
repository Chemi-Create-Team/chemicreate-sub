package net.createplant.gametest;

import net.createplant.CreatePlant;
import net.createplant.block.ExtractionColumnStructureBlock;
import net.createplant.registry.CEFuelCycle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class ExtractionColumnGameTests {
    private static InteractionResult place(GameTestHelper h, BlockPos below) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CEFuelCycle.PULSE_PLATE.get()));
        BlockPos absolute = h.absolutePos(below);
        return player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(absolute).relative(Direction.UP, .5), Direction.UP, absolute, false)));
    }

    @GameTest(template = "test_reactor")
    public static void aStoreyIsPlacedAndBrokenAsOneOctagon(GameTestHelper h) {
        BlockPos centre = new BlockPos(3, 2, 3);
        h.setBlock(centre.below(), Blocks.STONE);
        h.assertTrue(place(h, centre.below()).consumesAction(), "A pulse storey is placed in the open");
        h.assertBlockPresent(CEFuelCycle.PULSE_PLATE.get(), centre);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            if (x == 0 && z == 0) continue;
            BlockPos ring = h.absolutePos(centre.offset(x, 0, z));
            h.assertTrue(h.absolutePos(centre).equals(ExtractionColumnStructureBlock.centre(h.getLevel(), ring, h.getLevel().getBlockState(ring))),
                "Every ring cell leads to the storey at the centre: " + x + ", " + z);
        }
        BlockPos corner = h.absolutePos(centre.offset(1, 0, 1));
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        h.getLevel().getBlockState(corner).getBlock().playerWillDestroy(h.getLevel(), corner, h.getLevel().getBlockState(corner), player);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) h.assertBlockPresent(Blocks.AIR, centre.offset(x, 0, z));
        h.succeed();
    }

    @GameTest(template = "test_reactor")
    public static void aStoreyNeedsTheWholeRingFree(GameTestHelper h) {
        BlockPos centre = new BlockPos(3, 2, 3);
        h.setBlock(centre.below(), Blocks.STONE);
        h.setBlock(centre.offset(-1, 0, 1), Blocks.STONE);
        h.assertFalse(place(h, centre.below()).consumesAction(), "A blocked ring refuses the storey");
        h.assertBlockPresent(Blocks.AIR, centre);
        h.succeed();
    }
}
