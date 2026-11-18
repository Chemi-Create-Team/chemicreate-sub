package net.createplant.nuclear;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Like Create's Display Link: a desk unit item right-clicked on a formed display board, or a display board item on a formed desk,
 * remembers that station; placing the item links the station it completes to the remembered one.
 */
public final class ReactorConsoleLink {
    /** Farthest a desk and its display board may be apart, in blocks between their masters. */
    public static final int RANGE = 32;
    private static final String KEY = "ReactorConsoleLink";

    private ReactorConsoleLink() {}

    public static void remember(ItemStack stack, BlockPos master, Player player) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putLong(KEY, master.asLong()));
        player.displayClientMessage(Component.translatable("reactor_control_console.create_refinery_n_reactors.link.stored", master.getX(), master.getY(), master.getZ()), true);
    }
    public static BlockPos remembered(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.contains(KEY) ? BlockPos.of(tag.getLong(KEY)) : null;
    }
    public static boolean inRange(BlockPos a, BlockPos b) {
        return a.closerThan(b, RANGE);
    }
}
