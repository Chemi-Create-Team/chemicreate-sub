package net.createplant.item;

import net.createplant.block.TurbineUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

public final class TurbineUnitItem extends Item {
    private final TurbineUnit.Stage stage;

    public TurbineUnitItem(Properties properties, TurbineUnit.Stage stage) {
        super(properties);
        this.stage = stage;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        BlockPos base = context.getClickedPos();
        if (!level.getBlockState(base).canBeReplaced())
            base = base.relative(context.getClickedFace());
        Direction forward = context.getHorizontalDirection();
        int radius = stage.radius();
        BlockPos first = base.above(radius);
        // Casing clicks extend the nearest exposed end of the existing shaft.
        var previous = TurbineUnit.at(level, context.getClickedPos());
        if (previous != null && previous.stage() == stage) {
            var sections = TurbineUnit.connectedSections(level, previous);
            if (sections.isEmpty()) return InteractionResult.FAIL;
            forward = context.getClickedFace();
            if (forward.getAxis() != previous.axis()) {
                var eye = context.getPlayer() == null
                        ? context.getClickLocation() : context.getPlayer().getEyePosition();
                var center = sections.getFirst().origin().getCenter()
                        .lerp(sections.getLast().origin().getCenter(), .5);
                double offset = previous.axis().choose(eye.x - center.x, eye.y - center.y, eye.z - center.z);
                forward = Direction.fromAxisAndDirection(previous.axis(), offset < 0
                        ? Direction.AxisDirection.NEGATIVE : Direction.AxisDirection.POSITIVE);
            }
            first = (forward.getAxisDirection() == Direction.AxisDirection.NEGATIVE
                    ? sections.getFirst() : sections.getLast()).origin().relative(forward);
        }
        if (!TurbineUnit.place(
                level,
                new TurbineUnit.Layout(first, forward.getAxis(), stage),
                context.getPlayer(),
                context.getItemInHand())) {
            if (!level.isClientSide() && context.getPlayer() != null)
                context.getPlayer()
                        .displayClientMessage(
                                Component.translatable(
                                        "message.create_refinery_n_reactors.turbine_unit.space",
                                        2 * radius + 1,
                                        2 * radius + 1,
                                        1),
                                true);
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide()) {
            if (context.getPlayer() == null || !context.getPlayer().isCreative())
                context.getItemInHand().shrink(1);
            level.playSound(
                    null, base, SoundEvents.NETHERITE_BLOCK_PLACE, SoundSource.BLOCKS, 1, 1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
