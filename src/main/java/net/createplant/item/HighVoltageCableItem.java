package net.createplant.item;

import com.george_vi.electroenergetics.CEEDataComponents;
import com.george_vi.electroenergetics.content.wire_spool.WireSpoolItem;
import com.george_vi.electroenergetics.simulation.WireType;
import java.util.function.Supplier;
import net.createplant.block.TransmissionTowerBlock;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class HighVoltageCableItem extends WireSpoolItem {
    public HighVoltageCableItem(Properties properties, Supplier<WireType> wire) {
        super(properties, wire);
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        var player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var state = level.getBlockState(pos);
        if (player.isShiftKeyDown() || !(state.getBlock() instanceof TransmissionTowerBlock tower)) return super.useOn(context);
        if (context.getClickedFace() != Direction.UP) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("transmission_tower.create_refinery_n_reactors.use_top"), true);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        int terminal = tower.terminalAt(state, context.getClickLocation().subtract(Vec3.atLowerCornerOf(pos)));
        var anchor = tower.getNodePosition(level, pos, state, terminal).add(Vec3.atLowerCornerOf(pos));
        var hit = new BlockHitResult(anchor, Direction.UP, pos, false);
        var result = super.useOn(new UseOnContext(player, context.getHand(), hit));
        if (result.consumesAction() && !level.isClientSide && context.getItemInHand().has(CEEDataComponents.SELECTED_NODE))
            player.displayClientMessage(tower.getNodeLabel(level, pos, state, terminal), true);
        return result;
    }
}
