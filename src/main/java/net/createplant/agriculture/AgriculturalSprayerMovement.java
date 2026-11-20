package net.createplant.agriculture;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.createplant.block.AgriculturalSprayerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public final class AgriculturalSprayerMovement implements MovementBehaviour {
    private static final String LAST_PULSE = "AgriculturalSprayerLastPulse";

    @Override public Vec3 getActiveAreaOffset(MovementContext context) {
        return facing(context).scale(AgriculturalSpraying.NOZZLE_OFFSET);
    }

    @Override public void visitNewPosition(MovementContext context, BlockPos pos) {
        spray(context, true);
    }

    @Override public void tick(MovementContext context) {
        spray(context, false);
    }

    private static void spray(MovementContext context, boolean newPosition) {
        if (!(context.world instanceof ServerLevel level) || context.disabled || context.stall
                || context.position == null || context.motion.lengthSqr() < 1e-8) return;
        long now = level.getGameTime();
        if (context.data.contains(LAST_PULSE)) {
            long elapsed = now - context.data.getLong(LAST_PULSE);
            if (elapsed == 0 || !newPosition && elapsed > 0 && elapsed < 5) return;
        }
        context.data.putLong(LAST_PULSE, now);
        var direction = context.rotation.apply(facing(context)).normalize();
        AgriculturalSpraying.spray(level, context.contraption.getStorage().getFluids(), context.position, direction);
    }

    private static Vec3 facing(MovementContext context) {
        return Vec3.atLowerCornerOf(context.state.getValue(AgriculturalSprayerBlock.FACING).getNormal());
    }
}
