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
    private final CTSpriteShiftEntry side, end;

    public HorizontalTankCTBehaviour(CTSpriteShiftEntry side, CTSpriteShiftEntry end, CTSpriteShiftEntry inner) {
        super(side, end, inner);
        this.side = side;
        this.end = end;
    }

    @Override public CTSpriteShiftEntry getShift(BlockState state, Direction direction, TextureAtlasSprite sprite) {
        if (!(state.getBlock() instanceof HorizontalFluidTankBlock)) return null;
        return direction.getAxis() == state.getValue(HorizontalFluidTankBlock.AXIS) ? end : side;
    }

    /** The way along the tank, which is the way the turned model's texture runs. */
    private static Direction along(BlockState state) { return Direction.fromAxisAndDirection(state.getValue(HorizontalFluidTankBlock.AXIS), Direction.AxisDirection.POSITIVE); }

    /** Across the face, at right angles to the tank's length (along x normal). */
    private static Direction across(BlockState state, Direction face) {
        var a = along(state).getNormal();
        var n = face.getNormal();
        return Direction.fromDelta(a.getY() * n.getZ() - a.getZ() * n.getY(), a.getZ() * n.getX() - a.getX() * n.getZ(), a.getX() * n.getY() - a.getY() * n.getX());
    }

    @Override protected Direction getUpDirection(BlockAndTintGetter reader, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == state.getValue(HorizontalFluidTankBlock.AXIS) ? super.getUpDirection(reader, pos, state, face) : along(state);
    }

    @Override protected Direction getRightDirection(BlockAndTintGetter reader, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == state.getValue(HorizontalFluidTankBlock.AXIS) ? super.getRightDirection(reader, pos, state, face) : across(state, face);
    }
}
