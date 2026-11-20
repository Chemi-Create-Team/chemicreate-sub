package net.createplant.gametest;

import java.util.ArrayList;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.foundation.utility.BlockHelper;
import net.createplant.CreatePlant;
import net.createplant.agriculture.FertilizedCrops;
import net.createplant.agriculture.FertilizerProfile;
import net.createplant.registry.CEItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class FertilizerGameTests {
    private static final BlockPos CENTRE = new BlockPos(2, 2, 2);
    private static final String DATA_KEY = "createplant_fertilized_crops";
    private static final SavedData.Factory<FertilizedCrops> DATA = new SavedData.Factory<>(FertilizedCrops::new, FertilizedCrops::load);

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

    @GameTest(template = "test_reactor")
    public static void naturalGrowthGetsABonusAndBoneMealWorksAsUsual(GameTestHelper h) {
        field(h);
        var level = h.getLevel();
        var pos = h.absolutePos(CENTRE);
        h.assertTrue(FertilizedCrops.apply(level, pos, FertilizerProfile.NPK), "A fresh seedling accepts NPK");
        h.assertTrue(!FertilizedCrops.apply(level, pos, FertilizerProfile.NITRATE)
            && h.getBlockState(CENTRE).getValue(CropBlock.AGE) == 1, "A second fertilizer gives no immediate growth");

        var wheat = (CropBlock) Blocks.WHEAT;
        var random = RandomSource.create(15);
        for (int i = 0; i < 160 && wheat.getAge(level.getBlockState(pos)) == 1; i++)
            level.getBlockState(pos).randomTick(level, pos, random);
        h.assertTrue(wheat.getAge(level.getBlockState(pos)) == 4, "One vanilla growth tick adds the normal stage and two NPK stages");

        var data = level.getDataStorage().get(DATA, DATA_KEY);
        var beforeBoneMeal = data.save(new CompoundTag(), level.registryAccess());
        wheat.performBonemeal(level, RandomSource.create(6), pos, level.getBlockState(pos));
        h.assertTrue(wheat.getAge(level.getBlockState(pos)) >= 6
                && beforeBoneMeal.equals(data.save(new CompoundTag(), level.registryAccess())),
            "Bone meal advances wheat normally and leaves the stored treatment untouched");
        h.succeed();
    }

    @GameTest(template = "test_reactor")
    public static void matureVanillaHarvestAddsProduceOnlyOnce(GameTestHelper h) {
        field(h);
        var level = h.getLevel();
        var pos = h.absolutePos(CENTRE);
        h.assertTrue(FertilizedCrops.apply(level, pos, FertilizerProfile.NPK), "The crop receives NPK");
        BlockState mature = ((CropBlock) Blocks.WHEAT).getStateForAge(7);
        level.setBlockAndUpdate(pos, mature);
        h.assertTrue(level.destroyBlock(pos, true), "The crop breaks through the vanilla drop path");
        int first = wheatDrops(h, pos);
        h.assertTrue(first >= 3 && first <= 4, "The original wheat and exactly 2–3 bonus wheat drop, found " + first);

        level.setBlockAndUpdate(pos, mature);
        level.destroyBlock(pos, true);
        h.assertTrue(wheatDrops(h, pos) == first + 1, "A new crop at the same position pays only its ordinary drop");
        h.succeed();
    }

    @GameTest(template = "test_reactor")
    public static void createHarvesterCollectsTheBonusAfterSavedDataReload(GameTestHelper h) {
        field(h);
        var level = h.getLevel();
        var pos = h.absolutePos(CENTRE);
        var expired = pos.east();
        h.assertTrue(FertilizedCrops.apply(level, pos, FertilizerProfile.NPK)
            && FertilizedCrops.apply(level, expired, FertilizerProfile.NPK), "Both plots can be treated independently");
        var storage = level.getDataStorage();
        var snapshot = storage.get(DATA, DATA_KEY).save(new CompoundTag(), level.registryAccess());
        snapshot.getList("Crops", Tag.TAG_COMPOUND).stream().map(CompoundTag.class::cast)
            .filter(row -> row.getLong("Pos") == expired.asLong())
            .forEach(row -> row.putLong("Expires", level.getGameTime()));
        storage.set(DATA_KEY, FertilizedCrops.load(snapshot, level.registryAccess()));

        var mature = ((CropBlock) Blocks.WHEAT).getStateForAge(7);
        level.setBlockAndUpdate(pos, mature);
        level.setBlockAndUpdate(expired, mature);
        int collected = collectWithHarvester(h, pos);
        h.assertTrue(collected >= 3 && collected <= 4, "The contraption collects normal and bonus wheat from the saved crop, found " + collected);
        h.assertTrue(collectWithHarvester(h, expired) == 1, "An expired treatment cannot add drops after being restored");
        level.setBlockAndUpdate(pos, mature);
        h.assertTrue(collectWithHarvester(h, pos) == 1, "The automated harvest spends the treatment");
        h.succeed();
    }

    private static int wheatDrops(GameTestHelper h, BlockPos absolute) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(1)).stream()
            .map(ItemEntity::getItem).filter(stack -> stack.is(Items.WHEAT)).mapToInt(ItemStack::getCount).sum();
    }

    private static int collectWithHarvester(GameTestHelper h, BlockPos absolute) {
        var items = new ArrayList<ItemStack>();
        BlockHelper.destroyBlockAs(h.getLevel(), absolute, null, ItemStack.EMPTY, 0, items::add);
        return items.stream().filter(stack -> stack.is(Items.WHEAT)).mapToInt(ItemStack::getCount).sum();
    }
}
