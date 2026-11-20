package net.createplant.item;

import com.simibubi.create.AllItems;
import net.createplant.CreatePlant;
import net.createplant.transport.HoseSocket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = CreatePlant.MOD_ID)
public final class FlexibleRubberHoseItem extends Item {
    private static final String START = "CreatePlantHoseStart";

    public FlexibleRubberHoseItem(Properties properties) { super(properties); }

    @Override public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        var player = context.getPlayer();
        var pos = context.getClickedPos();
        var to = HoseSocket.endpoint(level, pos);
        if (player == null || to == null || to.hoseFace() != context.getClickedFace()) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!level.mayInteract(player, pos) || !to.hoseReady() || to.hose().connected()) return InteractionResult.FAIL;
        var item = context.getItemInHand();
        var data = item.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        var start = data.getCompound(START);
        var dimension = level.dimension().location().toString();
        if (!start.getString("Dimension").equals(dimension) || player.isShiftKeyDown()) {
            var picked = new CompoundTag();
            picked.putString("Dimension", dimension);
            picked.putLong("Pos", pos.asLong());
            data.put(START, picked);
            item.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
            player.displayClientMessage(Component.translatable(HoseSocket.KEY + "start", pos.toShortString()), true);
            return InteractionResult.CONSUME;
        }
        var from = BlockPos.of(start.getLong("Pos"));
        var first = HoseSocket.endpoint(level, from);
        if (first == null || !level.mayInteract(player, from) || !first.hose().connect(to)) {
            player.displayClientMessage(Component.translatable(HoseSocket.KEY + "failed", HoseSocket.MAX_DISTANCE), true);
            return InteractionResult.FAIL;
        }
        data.remove(START);
        if (data.isEmpty()) item.remove(DataComponents.CUSTOM_DATA);
        else item.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        if (!player.isCreative()) item.shrink(1);
        player.displayClientMessage(Component.translatable(HoseSocket.KEY + "connected"), true);
        return InteractionResult.CONSUME;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void cut(PlayerInteractEvent.RightClickBlock event) {
        var stack = event.getItemStack();
        if (!stack.is(Items.SHEARS) && !stack.is(AllItems.WRENCH.get())) return;
        var socket = HoseSocket.endpoint(event.getLevel(), event.getPos());
        if (socket == null || !socket.hose().connected() || socket.hoseFace() != event.getFace()
                || !event.getLevel().mayInteract(event.getEntity(), event.getPos())) return;
        if (!event.getLevel().isClientSide) socket.hose().disconnect(!event.getEntity().isCreative());
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        event.setCanceled(true);
    }
}
