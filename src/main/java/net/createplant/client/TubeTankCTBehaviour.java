package net.createplant.client;

import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;
import net.createplant.block.ExchangerStructure;
import net.createplant.block.ThermalCasingBlock;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The tube-piped tank: Create's tank side laid along the run and a tube sheet on each end, which a formed exchanger covers with its
 * channel heads. The textures run on only between cells of one formed shell-and-tube exchanger, so a loose tank, or a run not yet long
 * enough, shows each block on its own.
 */
public class TubeTankCTBehaviour extends ConnectedTextureBehaviour.Base {
    private final CTSpriteShiftEntry side, sheet, head;

    public TubeTankCTBehaviour(CTSpriteShiftEntry side, CTSpriteShiftEntry sheet, CTSpriteShiftEntry head) {
        this.side = side;
        this.sheet = sheet;
        this.head = head;
    }

    @Override public CTSpriteShiftEntry getShift(BlockState state, Direction direction, TextureAtlasSprite sprite) {
        if (!state.hasProperty(ThermalCasingBlock.FACING)) return null;
        if (direction.getAxis() != state.getValue(ThermalCasingBlock.FACING).getAxis()) return side;
        return ExchangerStructure.cell(state) == null ? sheet : head;
    }

    @Override public boolean connectsTo(BlockState state, BlockState other, BlockAndTintGetter reader, BlockPos pos, BlockPos otherPos, Direction face) {
        return ExchangerStructure.joined(state, pos, other, otherPos);
    }

    /** The way along the run, which is the way the turned model's tank texture runs. */
    private static Direction along(BlockState state) {
        return Direction.fromAxisAndDirection(state.getValue(ThermalCasingBlock.FACING).getAxis(), Direction.AxisDirection.POSITIVE);
    }

    /** Across the face, at right angles to the run (along x normal). */
    private static Direction across(BlockState state, Direction face) {
        var a = along(state).getNormal();
        var n = face.getNormal();
        return Direction.fromDelta(a.getY() * n.getZ() - a.getZ() * n.getY(), a.getZ() * n.getX() - a.getX() * n.getZ(), a.getX() * n.getY() - a.getY() * n.getX());
    }

    @Override protected Direction getUpDirection(BlockAndTintGetter reader, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == along(state).getAxis() ? super.getUpDirection(reader, pos, state, face) : along(state);
    }

    @Override protected Direction getRightDirection(BlockAndTintGetter reader, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == along(state).getAxis() ? super.getRightDirection(reader, pos, state, face) : across(state, face);
    }
}
