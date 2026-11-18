package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import net.createplant.CreatePlant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

final class SurvivalWorkshop {
    private SurvivalWorkshop() { }

    static void place(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlockAndUpdate(pos, state);
    }

    static void wheel(ServerLevel level, BlockPos wheel, Direction waterSide) {
        var shaftFace = waterSide.getAxis() == Direction.Axis.Z ? Direction.EAST : Direction.SOUTH;
        place(level, wheel, AllBlocks.WATER_WHEEL.getDefaultState().setValue(BlockStateProperties.FACING, shaftFace));
        var fall = wheel.relative(waterSide);
        for (int y = -1; y <= 1; y++) {
            var at = fall.above(y);
            for (var wall : new BlockPos[]{at.relative(shaftFace), at.relative(shaftFace.getOpposite()), at.relative(waterSide)})
                place(level, wall, Blocks.STONE_BRICKS.defaultBlockState());
        }
        place(level, fall.below(), Blocks.STONE_BRICKS.defaultBlockState());
        place(level, fall.above(), Blocks.WATER.defaultBlockState());
    }

    static BlockPos verticalDrive(ServerLevel level, BlockPos wheel, Direction waterSide) {
        wheel(level, wheel, waterSide);
        var gearbox = wheel.east();
        place(level, gearbox, axis(AllBlocks.GEARBOX.getDefaultState(), Direction.Axis.Z));
        var first = gearbox.above();
        var second = first.east().south();
        var third = second.above();
        var last = third.east().south();
        place(level, first, axis(AllBlocks.LARGE_COGWHEEL.getDefaultState(), Direction.Axis.Y));
        place(level, second, axis(AllBlocks.COGWHEEL.getDefaultState(), Direction.Axis.Y));
        place(level, third, axis(AllBlocks.LARGE_COGWHEEL.getDefaultState(), Direction.Axis.Y));
        place(level, last, axis(AllBlocks.COGWHEEL.getDefaultState(), Direction.Axis.Y));
        return last;
    }

    static BlockPos horizontalDrive(ServerLevel level, BlockPos wheel, Direction waterSide) {
        wheel(level, wheel, waterSide);
        var first = wheel.east();
        var second = first.above().south();
        var third = second.east();
        var last = third.above().south();
        place(level, first, axis(AllBlocks.LARGE_COGWHEEL.getDefaultState(), Direction.Axis.X));
        place(level, second, axis(AllBlocks.COGWHEEL.getDefaultState(), Direction.Axis.X));
        place(level, third, axis(AllBlocks.LARGE_COGWHEEL.getDefaultState(), Direction.Axis.X));
        place(level, last, axis(AllBlocks.COGWHEEL.getDefaultState(), Direction.Axis.X));
        return last;
    }

    static BlockState axis(BlockState state, Direction.Axis axis) {
        return state.setValue(BlockStateProperties.AXIS, axis);
    }

    static void burner(ServerLevel level, BlockPos pos) {
        place(level, pos, AllBlocks.BLAZE_BURNER.getDefaultState().setValue(BlazeBurnerBlock.HEAT_LEVEL,
            BlazeBurnerBlock.HeatLevel.SMOULDERING));
    }

    static void fuel(ServerLevel level, BlockPos burner, Item item, BlazeBurnerBlock.HeatLevel heat) {
        var player = FakePlayerFactory.getMinecraft(level);
        player.setItemInHand(InteractionHand.MAIN_HAND, item.getDefaultInstance());
        level.getBlockState(burner).useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND,
            hit(burner, Direction.UP, Vec3.atCenterOf(burner).add(0, .5, 0)));
        check(level.getBlockState(burner).getValue(BlazeBurnerBlock.HEAT_LEVEL) == heat
            && player.getMainHandItem().isEmpty(), "Burner did not consume " + item + " for " + heat);
    }

    static BlockHitResult hit(BlockPos pos, Direction face, Vec3 point) {
        return new BlockHitResult(point, face, pos, false);
    }

    static void drop(ServerLevel level, BlockPos above, ItemStack stack) {
        var item = new ItemEntity(level, above.getX() + .5, above.getY() + .1, above.getZ() + .5, stack);
        item.setDeltaMovement(Vec3.ZERO);
        item.setPickUpDelay(32767);
        level.addFreshEntity(item);
    }

    static float rpm(ServerLevel level, BlockPos at) {
        return Math.abs(entity(level, at, KineticBlockEntity.class).getSpeed());
    }

    static Item item(String name) {
        return BuiltInRegistries.ITEM.get(CreatePlant.location(name));
    }

    static IItemHandler items(ServerLevel level, BlockPos pos, Direction side) {
        var handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
        check(handler != null, "No item port at " + pos + " " + side);
        return handler;
    }

    static IFluidHandler fluids(ServerLevel level, BlockPos pos, Direction side) {
        var handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
        check(handler != null, "No fluid port at " + pos + " " + side);
        return handler;
    }

    static int total(IItemHandler inv, Item item) {
        int count = 0;
        for (int slot = 0; slot < inv.getSlots(); slot++)
            if (inv.getStackInSlot(slot).is(item)) count += inv.getStackInSlot(slot).getCount();
        return count;
    }

    static <T> T entity(ServerLevel level, BlockPos at, Class<T> type) {
        var be = level.getBlockEntity(at);
        check(type.isInstance(be), "Missing " + type.getSimpleName() + " at " + at);
        return type.cast(be);
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
