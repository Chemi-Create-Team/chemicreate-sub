package net.createplant.item;

import com.simibubi.create.content.fluids.tank.*;
import net.minecraft.core.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

public class PlantTankItem extends FluidTankItem {
    public PlantTankItem(Block block,Properties properties) { super(block,properties); }
    /** Placing on a wide tank adds a whole layer. */
    public static boolean bulkPlaces(Block block,int width) { return width>1; }
    @Override public InteractionResult place(BlockPlaceContext ctx) {
        var player=ctx.getPlayer();var level=ctx.getLevel();var face=ctx.getClickedFace();
        if (player!=null && !player.isShiftKeyDown() && face.getAxis().isVertical()
            && !com.simibubi.create.content.equipment.symmetryWand.SymmetryWandItem.presentInHotbar(player)
            && level.getBlockEntity(ctx.getClickedPos().relative(face.getOpposite())) instanceof FluidTankBlockEntity part) {
            var root=part.getControllerBE();
            if (root!=null && root.getBlockState().is(getBlock()) && bulkPlaces(getBlock(),root.getWidth())) {
                BlockPos start=face==Direction.DOWN ? root.getBlockPos().below() : root.getBlockPos().above(root.getHeight());
                if (start.getY()==ctx.getClickedPos().getY()) {
                    int needed=0;
                    for (int x=0;x<root.getWidth();x++) for (int z=0;z<root.getWidth();z++) {
                        BlockPos at=start.offset(x,0,z);
                        if (!level.hasChunkAt(at)) return InteractionResult.FAIL;
                        var state=level.getBlockState(at);if (state.is(getBlock())) continue;
                        if (!state.canBeReplaced() || !player.mayUseItemAt(at,face,ctx.getItemInHand())) return InteractionResult.FAIL;
                        var placement=BlockPlaceContext.at(ctx,at,face);var next=getBlock().getStateForPlacement(placement);
                        if (next==null || !canPlace(placement,next)) return InteractionResult.FAIL;
                        needed++;
                    }
                    if (!player.isCreative() && ctx.getItemInHand().getCount()<needed) return InteractionResult.FAIL;
                }
            }
        }
        return super.place(ctx);
    }
}
