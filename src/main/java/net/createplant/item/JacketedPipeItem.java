package net.createplant.item;

import com.simibubi.create.AllBlocks;
import net.createplant.block.ThermalCasingBlock;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** A jacket that closes around a straight, level Create fluid pipe; the pipe becomes the jacketed pipe's core and it is not placed on its own. */
public class JacketedPipeItem extends BlockItem {
    public JacketedPipeItem(Block block, Properties properties) { super(block, properties); }

    @Override public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var pipe = level.getBlockState(pos);
        if (!AllBlocks.FLUID_PIPE.has(pipe) && !AllBlocks.GLASS_FLUID_PIPE.has(pipe)) return InteractionResult.PASS;
        Direction.Axis axis = axis(pipe, context.getHorizontalDirection().getAxis());
        var player = context.getPlayer();
        if (axis == null) {
            if (player != null && !level.isClientSide) player.displayClientMessage(Component.translatable("message.create_refinery_n_reactors.jacketed_pipe.straight"), true);
            return InteractionResult.FAIL;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        Direction along = context.getHorizontalDirection().getAxis() == axis ? context.getHorizontalDirection() : Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        level.setBlock(pos, getBlock().defaultBlockState().setValue(ThermalCasingBlock.FACING, along), 3);
        level.playSound(null, pos, SoundEvents.NETHERITE_BLOCK_PLACE, SoundSource.BLOCKS, .8f, 1.2f);
        if (player == null || !player.isCreative()) stack.shrink(1);
        return InteractionResult.CONSUME;
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() != null && !context.getLevel().isClientSide)
            context.getPlayer().displayClientMessage(Component.translatable("message.create_refinery_n_reactors.jacketed_pipe.straight"), true);
        return InteractionResult.FAIL;
    }

    /** The level axis a pipe runs along, the player's when it has no connections, or null for a bend, a branch or a riser. */
    private static Direction.Axis axis(BlockState pipe, Direction.Axis looking) {
        if (pipe.hasProperty(BlockStateProperties.AXIS)) {
            var axis = pipe.getValue(BlockStateProperties.AXIS);
            return axis.isHorizontal() ? axis : null;
        }
        Direction.Axis found = null;
        for (Direction side : Direction.values()) {
            if (!pipe.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side))) continue;
            if (side.getAxis().isVertical() || found != null && found != side.getAxis()) return null;
            found = side.getAxis();
        }
        return found == null ? looking : found;
    }
}
