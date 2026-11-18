package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.content.logistics.vault.ItemVaultBlock;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
import net.createplant.CreatePlant;
import net.createplant.block.VaultHatchBlock;
import net.createplant.block.entity.VaultHatchBlockEntity;
import net.createplant.inventory.VaultHatchMenu;
import net.createplant.registry.CEVault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public class VaultHatchGameTests {
    private static VaultHatchBlockEntity fixture(GameTestHelper h) {
        BlockPos origin = h.absolutePos(new BlockPos(3, 2, 4));
        var state = AllBlocks.ITEM_VAULT.getDefaultState().setValue(ItemVaultBlock.HORIZONTAL_AXIS, Direction.Axis.X);
        for (int x = 0; x < 3; x++) h.getLevel().setBlockAndUpdate(origin.offset(x, 0, 0), state);
        ConnectivityHandler.formMulti((ItemVaultBlockEntity) h.getLevel().getBlockEntity(origin));
        BlockPos at = origin.north();
        h.getLevel().setBlockAndUpdate(at, CEVault.HATCH.get().defaultBlockState());
        return (VaultHatchBlockEntity) h.getLevel().getBlockEntity(at);
    }

    private static Player player(GameTestHelper h, VaultHatchBlockEntity hatch) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(Vec3.atCenterOf(hatch.getBlockPos()).add(0, 0, -1));
        return player;
    }

    @GameTest(template = "test_reactor", timeoutTicks = 80)
    public static void pagesShareVaultAndPreserveComponents(GameTestHelper h) {
        var hatch = fixture(h);
        h.runAfterDelay(10, () -> {
            var inventory = VaultHatchMenu.inventoryAt(hatch);
            h.assertTrue(inventory != null && inventory.getSlots() > 54, "Combined vault has a second page");
            var named = new ItemStack(Items.DIAMOND, 13);
            named.set(DataComponents.CUSTOM_NAME, Component.literal("Vault sample"));
            inventory.setStackInSlot(54, named.copy());
            inventory.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 60));
            var player = player(h, hatch);
            var menu = new VaultHatchMenu(1, player.getInventory(), hatch);
            player.containerMenu = menu;
            h.assertTrue(menu.pages() == (inventory.getSlots() + 53) / 54 && menu.getSlot(0).getItem().getCount() == 60, "First page uses real inventory");
            player.getInventory().setItem(9, new ItemStack(Items.IRON_INGOT, 4));
            menu.quickMoveStack(player, 54);
            h.assertTrue(inventory.getStackInSlot(0).getCount() == 64 && player.getInventory().getItem(9).isEmpty(), "Shift insert merges into actual vault");
            h.assertTrue(menu.clickMenuButton(player, 1), "Next page opens");
            h.assertTrue(ItemStack.matches(menu.getSlot(0).getItem(), named), "Second page preserves stack components");
            h.assertTrue(!menu.clickMenuButton(player, 1) && !menu.clickMenuButton(player, 8), "Reject out-of-range pages and unknown buttons");
            int tail = inventory.getSlots() - 54;
            h.assertTrue(!menu.getSlot(tail).mayPlace(new ItemStack(Items.GOLD_INGOT)), "Unused final-page slots reject items");
            menu.clicked(0, 0, ClickType.PICKUP, player);
            h.assertTrue(inventory.getStackInSlot(54).isEmpty() && menu.getCarried().getCount() == 13, "Pickup extracts actual vault contents");
            h.assertTrue(!menu.clickMenuButton(player, 0), "Cursor stack blocks page changes");
            menu.clicked(1, 0, ClickType.PICKUP, player);
            h.assertTrue(menu.getCarried().isEmpty() && ItemStack.matches(inventory.getStackInSlot(55), named), "Deposit preserves components");
            menu.quickMoveStack(player, 1);
            h.assertTrue(inventory.getStackInSlot(55).isEmpty() && player.getInventory().countItem(Items.DIAMOND) == 13, "Shift extraction does not duplicate");
            inventory.setStackInSlot(54, new ItemStack(Items.EMERALD, 7));
            h.assertTrue(menu.getSlot(0).getItem().getCount() == 7, "External transport appears in current page");
            menu.removed(player);
            h.assertTrue(!hatch.getBlockState().getValue(VaultHatchBlock.OPEN), "Last viewer closes the lid");
            h.succeed();
        });
    }

    @GameTest(template = "test_reactor", timeoutTicks = 80)
    public static void topologyChangesRejectOldMenuAndHatchStoresNothing(GameTestHelper h) {
        var hatch = fixture(h);
        h.runAfterDelay(10, () -> {
            var inventory = VaultHatchMenu.inventoryAt(hatch);
            inventory.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 17));
            var player = player(h, hatch);
            var menu = new VaultHatchMenu(1, player.getInventory(), hatch);
            player.containerMenu = menu;
            var vault = (ItemVaultBlockEntity) h.getLevel().getBlockEntity(hatch.getBlockPos().south());
            var saved = vault.saveWithoutMetadata(h.getLevel().registryAccess());
            vault.getInventoryOfBlock().setStackInSlot(0, ItemStack.EMPTY);
            vault.loadWithComponents(saved, h.getLevel().registryAccess());
            h.assertTrue(vault.getInventoryOfBlock().getStackInSlot(0).getCount() == 17, "Create saves and reloads the actual contents");
            vault.notifyMultiUpdated();
            h.assertTrue(!menu.stillValid(player), "Changed capability invalidates open menu");
            menu.clicked(0, 0, ClickType.PICKUP, player);
            h.assertTrue(menu.getCarried().isEmpty() && menu.quickMoveStack(player, 0).isEmpty(), "Old menu cannot move items");
            menu.removed(player);
            h.getLevel().removeBlock(hatch.getBlockPos(), false);
            h.assertTrue(vault.getInventoryOfBlock().getStackInSlot(0).getCount() == 17, "Removing hatch leaves contents in vault");
            h.succeed();
        });
    }

    @GameTest(template = "test_reactor", timeoutTicks = 80)
    public static void allFacesNeedVaultSupportAndMultipleViewersShareLid(GameTestHelper h) {
        var hatch = fixture(h);
        h.runAfterDelay(10, () -> {
            var player = player(h, hatch);
            BlockPos support = hatch.getBlockPos().south();
            var block = CEVault.HATCH.get();
            for (Direction face : Direction.values()) {
                BlockPos at = support.relative(face);
                if (!h.getLevel().isEmptyBlock(at) && !at.equals(hatch.getBlockPos())) continue;
                var hit = new BlockHitResult(Vec3.atCenterOf(support), face, support, false);
                var context = new BlockPlaceContext(h.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(block), hit);
                var placed = block.getStateForPlacement(context);
                h.assertTrue(placed != null && placed.getValue(VaultHatchBlock.FACING) == face, "Hatch attaches facing " + face);
            }
            BlockPos empty = support.offset(0, 3, 0);
            var hit = new BlockHitResult(Vec3.atCenterOf(empty), Direction.UP, empty, false);
            var context = new BlockPlaceContext(h.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(block), hit);
            h.assertTrue(block.getStateForPlacement(context) == null, "Hatch cannot attach to arbitrary blocks");
            var other = player(h, hatch);
            var first = new VaultHatchMenu(1, player.getInventory(), hatch);
            var second = new VaultHatchMenu(2, other.getInventory(), hatch);
            h.assertTrue(hatch.getBlockState().getValue(VaultHatchBlock.OPEN), "Opening a hatch opens the lid");
            first.removed(player);
            h.assertTrue(hatch.getBlockState().getValue(VaultHatchBlock.OPEN), "One viewer leaving keeps lid open");
            second.removed(other);
            h.assertTrue(!hatch.getBlockState().getValue(VaultHatchBlock.OPEN), "Last viewer closes lid");
            h.getLevel().setBlockAndUpdate(support, Blocks.AIR.defaultBlockState());
            h.assertTrue(!h.getLevel().getBlockState(hatch.getBlockPos()).is(block), "Removing support detaches hatch");
            h.succeed();
        });
    }
}
