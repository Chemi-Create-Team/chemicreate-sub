package net.createplant.client;

import com.simibubi.create.content.fluids.tank.FluidTankCTBehaviour;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import net.createplant.block.HorizontalFluidTankBlock;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Create's tank connections for a tank lying on its side. The baked model is the vertical one turned onto its side, so the end faces are the
 * ones along the block's axis and the textures of the other faces read along that axis, as on Create's item vault.
 */
public class HorizontalTankCTBehaviour extends FluidTankCTBehaviour {
    private final CTSpriteShiftEntry side, end, inner;

    public HorizontalTankCTBehaviour(CTSpriteShiftEntry side, CTSpriteShiftEntry end, CTSpriteShiftEntry inner) {
        super(side, end, inner);
        this.side = side;
        this.end = end;
        this.inner = inner;
    }

    @Override public CTSpriteShiftEntry getShift(BlockState state, Direction direction, TextureAtlasSprite sprite) {
        if (!(state.getBlock() instanceof HorizontalFluidTankBlock)) return null;
        if (direction.getAxis() != state.getValue(HorizontalFluidTankBlock.AXIS)) return side;
        return sprite == inner.getOriginal() ? inner : end;
    }

    /** The way along the tank, which is the way the turned model's texture runs. */
    private static Direction along(BlockState state) { return Direction.fromAxisAndDirection(state.getValue(HorizontalFluidTankBlock.AXIS), Direction.AxisDirection.POSITIVE); }

    @Override protected Direction getUpDirection(BlockAndTintGetter reader, BlockPos pos, BlockState state, Direction face) {
        Direction axis = along(state);
        if (face == axis) return Direction.UP;
        if (face == axis.getOpposite()) return Direction.DOWN;
        // Create flips the neighbour directions for a downward-facing quad.
        return face == Direction.DOWN ? axis.getOpposite() : axis;
    }

    @Override protected Direction getRightDirection(BlockAndTintGetter reader, BlockPos pos, BlockState state, Direction face) {
        Direction axis = along(state);
        Direction right;
        if (face.getAxis() == axis.getAxis()) {
            // Both ends use the model's original east for increasing U.
            right = axis == Direction.EAST ? Direction.NORTH : Direction.EAST;
        } else {
            var a = axis.getNormal();
            var n = face.getNormal();
            right = Direction.fromDelta(a.getY() * n.getZ() - a.getZ() * n.getY(), a.getZ() * n.getX() - a.getX() * n.getZ(), a.getX() * n.getY() - a.getY() * n.getX());
        }
        return face.getAxisDirection() == Direction.AxisDirection.POSITIVE || face == Direction.DOWN ? right.getOpposite() : right;
    }
}
