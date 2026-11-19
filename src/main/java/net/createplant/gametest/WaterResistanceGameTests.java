package net.createplant.gametest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.createplant.CreatePlant;
import net.createplant.registry.CEBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WaterResistanceGameTests {
    private record Placed(Block block,BlockPos pos,ResourceLocation id) { }

    @GameTest(template = "test_reactor", timeoutTicks = 100)
    public static void nonOccludingEquipmentSurvivesFlowingWater(GameTestHelper h) {
        List<Block> candidates=BuiltInRegistries.BLOCK.stream()
            .filter(block->CreatePlant.MOD_ID.equals(BuiltInRegistries.BLOCK.getKey(block).getNamespace()))
            .filter(block->!(block instanceof LiquidBlock) && !block.defaultBlockState().canOcclude()
                && block!=CEBlocks.HIGH_PRESSURE_TURBINE_END.get() && block!=CEBlocks.LOW_PRESSURE_TURBINE_END.get())
            .sorted(Comparator.comparing(block->BuiltInRegistries.BLOCK.getKey(block).toString())).toList();
        h.assertTrue(candidates.size()>=50,"The audit covers every non-occluding Create-Plant block: "+candidates.size());
        h.assertTrue(candidates.size()<=162,"The two-layer audit must fit inside the 32x8x32 structure: "+candidates.size());
        List<Placed> placed=new ArrayList<>();
        for (int i=0;i<candidates.size();i++) {
            Block block=candidates.get(i);BlockPos pos=new BlockPos(2+3*(i%9),2+3*(i/81),2+3*((i/9)%9));
            for (var support:List.of(pos.below(),pos.north(),pos.south(),pos.east(),pos.west())) h.setBlock(support,Blocks.IRON_BLOCK);
            h.setBlock(pos,block.defaultBlockState());
            var id=BuiltInRegistries.BLOCK.getKey(block);placed.add(new Placed(block,pos,id));
            if (block.defaultBlockState().hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
                h.setBlock(pos.above(),block.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.UPPER));
                placed.add(new Placed(block,pos.above(),id));
                h.setBlock(pos.above().north(),Blocks.WATER);
            } else h.setBlock(pos.above(),Blocks.WATER);
        }
        h.runAfterDelay(40,()->{
            List<String> failures=new ArrayList<>();
            for (Placed entry:placed) {
                var state=h.getBlockState(entry.pos());
                boolean formed=state.getBlock() instanceof net.createplant.block.ExchangerControllerBlock controller && controller.kind.casing()==entry.block();
                if (!state.is(entry.block()) && !formed) failures.add(entry.id().getPath()+"->"+BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath());
            }
            h.assertTrue(failures.isEmpty(),"Non-occluding equipment washed away: "+failures);
            h.succeed();
        });
    }

    @GameTest(template = "test_fractionation", timeoutTicks = 100)
    public static void formedTurbineEndsSurviveFlowingWater(GameTestHelper h) {
        TurbineThermalGameTests.build(h.getLevel(),h.absolutePos(BlockPos.ZERO));
        BlockPos lowTop=new BlockPos(4,6,10),highTop=new BlockPos(16,5,10);
        h.assertTrue(h.getBlockState(lowTop).is(CEBlocks.LOW_PRESSURE_TURBINE_END.get())
                && h.getBlockState(highTop).is(CEBlocks.HIGH_PRESSURE_TURBINE_END.get()),
            "The valid low- and high-pressure end rings are present before water flows");
        h.setBlock(lowTop.above(),Blocks.WATER);h.setBlock(highTop.above(),Blocks.WATER);
        h.runAfterDelay(40,()->{
            h.assertTrue(h.getBlockState(lowTop).is(CEBlocks.LOW_PRESSURE_TURBINE_END.get())
                    && h.getBlockState(highTop).is(CEBlocks.HIGH_PRESSURE_TURBINE_END.get()),
                "Formed turbine end rings survive water flowing over their top parts");
            h.succeed();
        });
    }
}
