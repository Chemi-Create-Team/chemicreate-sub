package net.createplant.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import static net.createplant.gametest.SurvivalWorkshop.check;
import static net.createplant.gametest.SurvivalWorkshop.hit;

/** Uses the vanilla chest and crafting-table slots with the same clicks as a survival player. */
final class SurvivalCrafting {
    private final ServerLevel level;
    private final BlockPos table;
    private final ServerPlayer player;

    SurvivalCrafting(ServerLevel level, BlockPos table) {
        this.level = level;
        this.table = table;
        player = FakePlayerFactory.getMinecraft(level);
        check(!player.isCreative(), "The crafting player must be in survival");
    }

    void collect(BlockPos chest, Item item, int count) {
        var menu = (ChestMenu) menuAt(chest);
        int limit = menu.getRowCount() * 9;
        for (int slot = 0; slot < limit && count > 0; slot++) {
            var stack = menu.getSlot(slot).getItem();
            if (!stack.is(item)) continue;
            int amount = Math.min(count, stack.getCount());
            if (amount == stack.getCount()) {
                click(menu, slot, 0, ClickType.QUICK_MOVE);
            } else {
                int destination = inventorySlot(menu, item, limit, amount);
                click(menu, slot, 0, ClickType.PICKUP);
                for (int i = 0; i < amount; i++) click(menu, destination, 1, ClickType.PICKUP);
                click(menu, slot, 0, ClickType.PICKUP);
            }
            count -= amount;
        }
        check(count == 0, "Chest at " + chest + " is short of " + item);
        menu.removed(player);
    }

    void craft(Item product, int countPerCraft, int repetitions, Item... grid) {
        var menu = (CraftingMenu) menuAt(table);
        int before = player.getInventory().countItem(product);
        for (int pass = 0; pass < repetitions; pass++) {
            for (int i = 0; i < grid.length; i++) {
                if (grid[i] == null) continue;
                int source = inventorySlot(menu, grid[i], 10, 0);
                if (menu.getSlot(source).getItem().getCount() == 1) {
                    click(menu, source, 0, ClickType.PICKUP);
                    click(menu, i + 1, 0, ClickType.PICKUP);
                } else {
                    click(menu, source, 0, ClickType.PICKUP);
                    click(menu, i + 1, 1, ClickType.PICKUP);
                    click(menu, source, 0, ClickType.PICKUP);
                }
            }
            var result = menu.getSlot(0).getItem();
            check(result.is(product) && result.getCount() == countPerCraft, "Crafting table returned " + result + " for " + product);
            click(menu, 0, 0, ClickType.QUICK_MOVE);
        }
        menu.removed(player);
        check(player.getInventory().countItem(product) == before + countPerCraft * repetitions, "Crafting did not deliver " + product);
    }

    private int inventorySlot(AbstractContainerMenu menu, Item item, int firstSlot, int room) {
        int empty = -1;
        for (int slot = firstSlot; slot < menu.slots.size(); slot++) {
            var stack = menu.getSlot(slot).getItem();
            if (stack.is(item) && stack.getCount() + room <= stack.getMaxStackSize()) return slot;
            if (empty < 0 && stack.isEmpty()) empty = slot;
        }
        check(room > 0 && empty >= 0, "No inventory slot for " + item);
        return empty;
    }

    private AbstractContainerMenu menuAt(BlockPos pos) {
        player.setPos(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5);
        var provider = level.getBlockState(pos).getMenuProvider(level, pos);
        check(provider != null, "No vanilla menu at " + pos);
        var menu = provider.createMenu(1, player.getInventory(), player);
        check(menu != null && menu.stillValid(player), "Vanilla menu is unavailable at " + pos);
        return menu;
    }

    private void click(AbstractContainerMenu menu, int slot, int button, ClickType type) {
        menu.clicked(slot, button, type, player);
    }

    void placeHeld(Item item, BlockPos target) {
        placeHeld(item, target, Direction.UP);
    }

    void placeHeld(Item item, BlockPos target, Direction face) {
        select(item);
        var support = target.relative(face.getOpposite());
        var context = new UseOnContext(player, InteractionHand.MAIN_HAND,
            hit(support, face, Vec3.atCenterOf(support).add(Vec3.atLowerCornerOf(face.getNormal()).scale(.5))));
        int before = player.getMainHandItem().getCount();
        check(player.getMainHandItem().useOn(context).consumesAction() && player.getMainHandItem().getCount() == before - 1,
            "Could not place crafted " + item + " at " + target);
    }

    void useHeld(Item item, BlockPos target, Direction face) {
        select(item);
        var hand = player.getMainHandItem();
        int before = hand.getCount();
        check(level.getBlockState(target).useItemOn(hand, level, player, InteractionHand.MAIN_HAND,
            hit(target, face, Vec3.atCenterOf(target))).consumesAction() && hand.getCount() == before - 1,
            "Could not use crafted " + item + " at " + target);
    }

    void clickWithItem(Item item, BlockPos target, Vec3 point) {
        if (!player.getMainHandItem().is(item)) select(item);
        player.setPos(point.x, point.y + 1, point.z);
        var context = new UseOnContext(player, InteractionHand.MAIN_HAND, hit(target, Direction.UP, point));
        check(player.getMainHandItem().useOn(context).consumesAction(), "Item did not interact at " + target);
    }

    void dropOne(Item item, BlockPos hopper) {
        drop(item, hopper, false);
    }

    void dropStack(Item item, BlockPos hopper) {
        drop(item, hopper, true);
    }

    private void drop(Item item, BlockPos hopper, boolean wholeStack) {
        select(item);
        player.setPos(hopper.getX() + .5, hopper.getY() + .5, hopper.getZ() + .5);
        player.setXRot(90);
        check(player.drop(wholeStack), "Could not drop the collected " + item + " into the hopper");
        player.setXRot(0);
    }

    private void select(Item item) {
        int slot = player.getInventory().findSlotMatchingItem(item.getDefaultInstance());
        check(slot >= 0, "The survival player does not have " + item);
        if (slot < 9) player.getInventory().selected = slot;
        else player.getInventory().pickSlot(slot);
    }
}
