package net.createplant.item;

import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.content.equipment.symmetryWand.SymmetryWandItem;
import net.createplant.block.LiddedVesselBlock;
import net.createplant.block.entity.LiddedVesselBlockEntity;
import net.createplant.registry.CETransport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

/** Like Create's fluid tank: placed on the top or bottom of a 2x2 or 3x3 vessel, it fills the whole layer there, unless the player sneaks. */
public class LiddedVesselItem extends BlockItem {
    public LiddedVesselItem(Block block, Properties properties) { super(block, properties); }

    @Override public InteractionResult place(BlockPlaceContext context) {
        var player = context.getPlayer();
        Direction face = context.getClickedFace();
        if (player == null || player.isShiftKeyDown() || !face.getAxis().isVertical() || SymmetryWandItem.presentInHotbar(player)) return super.place(context);
        var level = context.getLevel();
        BlockPos pos = context.getClickedPos(), on = pos.relative(face.getOpposite());
        if (!(level.getBlockState(on).getBlock() instanceof LiddedVesselBlock)) return super.place(context);
        LiddedVesselBlockEntity part = ConnectivityHandler.partAt(CETransport.LIDDED_VESSEL_ENTITY.get(), level, on);
        var owner = part == null ? null : part.getControllerBE();
        if (owner == null || owner.getWidth() == 1) return super.place(context);
        int width = owner.getWidth();
        BlockPos start = face == Direction.DOWN ? owner.getBlockPos().below() : owner.getBlockPos().above(owner.getHeight());
        if (start.getY() != pos.getY()) return super.place(context);
        int needed = 0;
        for (int x = 0; x < width; x++) for (int z = 0; z < width; z++) {
            BlockPos at = start.offset(x, 0, z);
            if (!level.hasChunkAt(at)) return InteractionResult.FAIL;
            var state = level.getBlockState(at);
            if (state.getBlock() instanceof LiddedVesselBlock) continue;
            if (!state.canBeReplaced() || !player.mayUseItemAt(at, face, context.getItemInHand())) return InteractionResult.FAIL;
            var placement = BlockPlaceContext.at(context, at, face);
            var next = getBlock().getStateForPlacement(placement);
            if (next == null || !canPlace(placement, next)) return InteractionResult.FAIL;
            needed++;
        }
        if (!player.isCreative() && context.getItemInHand().getCount() < needed) return InteractionResult.FAIL;
        InteractionResult result = super.place(context);
        if (!result.consumesAction()) return result;
        for (int x = 0; x < width; x++) for (int z = 0; z < width; z++) {
            BlockPos at = start.offset(x, 0, z);
            if (!(level.getBlockState(at).getBlock() instanceof LiddedVesselBlock)) super.place(BlockPlaceContext.at(context, at, face));
        }
        return result;
    }
}
