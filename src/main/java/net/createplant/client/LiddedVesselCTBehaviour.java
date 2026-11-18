package net.createplant.client;

import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;
import net.createplant.block.LiddedVesselBlock;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The outer silver plate of a lidded vessel joins across every block of the same vessel, so a 3x3 side four layers high reads as one wall with its
 * frame and bands only round the edge.
 */
public class LiddedVesselCTBehaviour extends ConnectedTextureBehaviour.Base {
    private final CTSpriteShiftEntry side, cap;

    public LiddedVesselCTBehaviour(CTSpriteShiftEntry side, CTSpriteShiftEntry cap) { this.side = side;this.cap = cap; }

    @Override public CTSpriteShiftEntry getShift(BlockState state, Direction direction, TextureAtlasSprite sprite) {
        if (direction.getAxis() == Direction.Axis.Y)
            return state.getValue(LiddedVesselBlock.DRIVE) && (sprite == null || sprite == cap.getOriginal()) ? cap : null;
        if (sprite != null && sprite != side.getOriginal()) return null;
        return side;
    }

    @Override public boolean connectsTo(BlockState state, BlockState other, BlockAndTintGetter reader, BlockPos pos, BlockPos otherPos, Direction face) {
        return state.getBlock() == other.getBlock() && ConnectivityHandler.isConnected(reader, pos, otherPos);
    }
}
