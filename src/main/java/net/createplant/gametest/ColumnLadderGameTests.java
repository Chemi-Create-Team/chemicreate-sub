package net.createplant.gametest;

import net.createplant.CreatePlant;
import net.createplant.block.FractionatingColumnBlock;
import net.createplant.block.FractionatingColumnBlock.Section;
import net.createplant.registry.CEDistillation;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class ColumnLadderGameTests {
    private static void cell(GameTestHelper h, BlockPos at, int width, int cell, Section section) {
        h.getLevel().setBlock(h.absolutePos(at), CEDistillation.COLUMN.get().defaultBlockState()
            .setValue(FractionatingColumnBlock.WIDTH, width).setValue(FractionatingColumnBlock.CELL, cell).setValue(FractionatingColumnBlock.SECTION, section), 2);
    }

    @GameTest(template = "test_fractionation")
    public static void onlyTheWestLadderCellsOfAColumnAboveItsBaseCanBeClimbed(GameTestHelper h) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        BlockPos west = new BlockPos(4, 2, 4);
        cell(h, west.east(), 2, 3, Section.BODY);
        player.setPos(h.absolutePos(west).getX() + .5, h.absolutePos(west).getY(), h.absolutePos(west).getZ() + .5);
        h.assertTrue(player.onClimbable(), "A body cell on the ladder side of the column can be climbed from the west");
        cell(h, west.east(), 2, 3, Section.BASE);
        h.assertTrue(!player.onClimbable(), "The base has no ladder");
        cell(h, west.east(), 2, 0, Section.BODY);
        h.assertTrue(!player.onClimbable(), "Only the south-most cell of the west wall has the ladder");
        cell(h, west.east(), 1, 0, Section.SINGLE);
        h.assertTrue(player.onClimbable(), "A one-wide column has its ladder in its only cell");
        h.getLevel().setBlock(h.absolutePos(west.east()), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
        h.assertTrue(!player.onClimbable(), "Air is not climbable");
        h.succeed();
    }
}
