package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import net.createplant.CreatePlant;
import net.createplant.chemistry.ElectrolysisProcess;
import net.createplant.registry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** What the plant's products are for once made: fertiliser, road salt, soap, grease and the nickel electrode. */
@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class ProductUseGameTests {
    private static Player holding(GameTestHelper h, ItemStack stack) {
        Player player = FakePlayerFactory.getMinecraft(h.getLevel());
        player.getAbilities().instabuild = false;
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return player;
    }

    private static void useOn(GameTestHelper h, Player player, BlockPos pos) {
        BlockPos at = h.absolutePos(pos);
        player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false)));
    }

    @GameTest(template = "test_empty")
    public static void fertilisersGrowCropsLikeBoneMeal(GameTestHelper h) {
        for (int i = 0; i < 2; i++) {
            BlockPos crop = new BlockPos(2 + i * 2, 2, 2);
            h.setBlock(crop.below(), Blocks.FARMLAND);
            h.setBlock(crop, Blocks.WHEAT);
            var stack = new ItemStack(i == 0 ? CEItems.AMMONIUM_NITRATE.get() : CEItems.AMMONIUM_SULFATE.get(), 3);
            useOn(h, holding(h, stack), crop);
            int age = h.getBlockState(crop).getValue(CropBlock.AGE);
            h.assertTrue(age > 0 && stack.getCount() == 2, stack.getItem() + " grows the wheat with one item: age " + age + ", " + stack.getCount() + " left");
        }
        var stack = new ItemStack(CEItems.AMMONIUM_SULFATE.get(), 3);
        h.setBlock(new BlockPos(7, 2, 2), Blocks.STONE);
        useOn(h, holding(h, stack), new BlockPos(7, 2, 2));
        h.assertTrue(stack.getCount() == 3, "Fertiliser on stone is not used up");
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void saltMeltsIceAndClearsSnowButNotPackedIce(GameTestHelper h) {
        BlockPos ice = new BlockPos(2, 2, 2), snow = new BlockPos(4, 2, 2), packed = new BlockPos(6, 2, 2);
        h.setBlock(ice, Blocks.ICE);h.setBlock(snow, Blocks.SNOW_BLOCK);h.setBlock(packed, Blocks.PACKED_ICE);
        var stack = new ItemStack(CEChlorAlkali.SALT.get(), 4);
        Player player = holding(h, stack);
        useOn(h, player, ice);useOn(h, player, snow);useOn(h, player, packed);
        h.assertTrue(h.getBlockState(ice).is(Blocks.WATER) && h.getBlockState(snow).isAir() && h.getBlockState(packed).is(Blocks.PACKED_ICE),
            "Salt melts ice to water and clears snow, but packed ice is too cold");
        h.assertTrue(stack.getCount() == 2, "Each melted block takes one salt: " + stack.getCount());
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void soapWashesOffEffectsLikeMilk(GameTestHelper h) {
        var stack = new ItemStack(CEChlorAlkali.SOAP.get(), 2);
        Player player = holding(h, stack);
        player.removeAllEffects();
        stack.use(h.getLevel(), player, InteractionHand.MAIN_HAND);
        h.assertTrue(stack.getCount() == 2, "Soap is not used without an effect to wash off");
        player.addEffect(new MobEffectInstance(CESafety.TOXIC_GAS, 200));player.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
        stack.use(h.getLevel(), player, InteractionHand.MAIN_HAND);
        h.assertTrue(player.getActiveEffects().isEmpty() && stack.getCount() == 1, "Soap washes off the toxic gas and poison: " + player.getActiveEffects());
        h.succeed();
    }


    @GameTest(template = "test_petrochemical")
    public static void nickelPlatesTakeLessWorkAndComeBackOutAndDrop(GameTestHelper h) {
        h.assertTrue(ElectrolysisProcess.work(10, 4) == 4 * ElectrolysisProcess.NICKEL_JOULES_PER_PLATE + 6 * ElectrolysisProcess.JOULES_PER_PLATE, "Nickel saves real electrical work");
        var owner = ElectrolysisCellGameTests.assemble(h.getLevel(), h.absolutePos(new BlockPos(3, 1, 3)), 1, Direction.NORTH);
        var head = owner.heads().getFirst();
        var hit = new BlockHitResult(Vec3.atCenterOf(head.getBlockPos()).add(0, .5, 0), Direction.UP, head.getBlockPos(), false);
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var stack = CEElectrolysis.NICKEL_ELECTRODE_PLATE.toStack(2);
        head.getBlockState().useItemOn(stack, h.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        head.getBlockState().useItemOn(CEElectrolysis.ELECTRODE_PLATE.toStack(), h.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        h.assertTrue(head.plates() == 2 && head.nickel() == 1 && stack.getCount() == 1, "The raised head accepts and debits both electrode materials");
        player.setShiftKeyDown(true);head.getBlockState().useWithoutItem(h.getLevel(), player, hit);player.setShiftKeyDown(false);
        h.assertTrue(head.plates() == 1 && head.nickel() == 0 && player.getInventory().countItem(CEElectrolysis.NICKEL_ELECTRODE_PLATE.get()) >= 1, "The nickel electrode comes back out");
        var at = head.getBlockPos();h.getLevel().destroyBlock(at, true);
        var dropped = h.getLevel().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(at).inflate(2));
        h.assertTrue(dropped.stream().anyMatch(item -> item.getItem().is(CEElectrolysis.ELECTRODE_PLATE.get())), "A broken head drops the plate it still carried");h.succeed();
    }
}
