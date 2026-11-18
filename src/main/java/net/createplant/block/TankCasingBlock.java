package net.createplant.block;

import net.createplant.block.entity.TankCasingBlockEntity;
import net.createplant.registry.CEBlockEntityTypes;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Floor and wall block of a hollow, open-top cylindrical tank that forms when its last casing is placed. */
public class TankCasingBlock extends Block implements EntityBlock {
    public TankCasingBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TankCasingBlockEntity(pos, state);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        // Defer recognition so a batch of placements in one tick is checked after the blocks exist.
        if (!level.isClientSide() && !oldState.is(this))
            level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof TankCasingBlockEntity casing && casing.getTank() == null)
            casing.refresh();
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide() && !newState.is(this) && level.getBlockEntity(pos) instanceof TankCasingBlockEntity casing)
            casing.onCasingRemoved();
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != CEBlockEntityTypes.TANK_CASING.get())
            return null;
        if (level.isClientSide()) {
            return (world, pos, blockState, entity) ->
                TankCasingBlockEntity.clientTick(world, pos, blockState, (TankCasingBlockEntity) entity);
        }
        return (world, pos, blockState, entity) ->
            TankCasingBlockEntity.tick(world, pos, blockState, (TankCasingBlockEntity) entity);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty())
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        // Other items keep their own use, so casings and pipes can be placed against the tank without sneaking.
        if (stack.getCapability(Capabilities.FluidHandler.ITEM) == null
                || !(level.getBlockEntity(pos) instanceof TankCasingBlockEntity casing))
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide())
            return ItemInteractionResult.SUCCESS;

        // Validate immediately, so a bucket can be used as soon as the structure is finished.
        casing.refresh();
        // Use the same NeoForge fluid-container interaction used by the other Create-Plant tanks.
        return FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection())
            ? ItemInteractionResult.SUCCESS : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TankCasingBlockEntity casing) {
            casing.refresh();
            TankCasingBlockEntity tank = casing.getTank();
            if (tank != null) {
                player.displayClientMessage(Component.translatable(
                    "message.create_refinery_n_reactors.tank_casing.formed",
                    tank.getDiameter(), tank.getHeight(), tank.getCapacity(), tank.getFluidAmount()), false);
                if (tank.getSalt() > 0)
                    player.displayClientMessage(Component.translatable("message.create_refinery_n_reactors.tank_casing.salt", tank.getSalt()), false);
            } else {
                player.displayClientMessage(Component.translatable(
                    "message.create_refinery_n_reactors.tank_casing.invalid",
                    Component.translatable("message.create_refinery_n_reactors.tank_casing.reason." + casing.getInvalidReason())), false);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
