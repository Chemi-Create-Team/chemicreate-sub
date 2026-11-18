package net.createplant.client;

import com.google.gson.GsonBuilder;
import com.simibubi.create.AllBlocks;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.createplant.registry.CECarbonization;
import net.createplant.block.CharredWoodBlock;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Opt-in regression fixture for held carbonized wood and its inventory tooltip. */
final class CharredWoodClientCheck {
    private static final BlockPos ORIGIN = new BlockPos(0, 80, 0);
    private static final BlockPos PLACEMENT = ORIGIN.offset(-2, 0, 2);
    private static final BlockPos CHEST = ORIGIN.offset(2, 1, 2);
    private static final BlockPos BASIN = ORIGIN.offset(2, 1, 4);
    private static final Map<String, Boolean> checks = new LinkedHashMap<>();
    private static int ticks;
    private static int itemCount, offhandCount;
    private static volatile int pigId;
    private static CompletableFuture<Boolean> serverCheck;

    private CharredWoodClientCheck() { }

    static void start(Minecraft mc) {
        ticks = 0;pigId = -1;serverCheck = null;checks.clear();
        writeResult(mc, false);
        mc.setScreen(null);mc.options.hideGui = false;mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.getSingleplayerServer().execute(() -> {
            var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            var level = player.serverLevel();
            for (int x = -4; x <= 4; x++) for (int z = -4; z <= 6; z++) level.setBlockAndUpdate(ORIGIN.offset(x, 0, z), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(ORIGIN.above(), CECarbonization.CHARRED_WOOD.get().defaultBlockState());
            player.getInventory().selected = 0;
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CECarbonization.CHARRED_WOOD.get(), 64));
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(CECarbonization.CHARRED_WOOD.get()));
            player.connection.teleport(.5, 81, 4.5, 180, 25);
            player.getInventory().setChanged();player.containerMenu.broadcastChanges();
        });
    }

    static boolean tick(Minecraft mc) throws Exception {
        if (++ticks == 40) {
            if (!mc.player.getMainHandItem().is(CECarbonization.CHARRED_WOOD.get().asItem())) throw new IllegalStateException("Carbonized wood did not reach the main hand");
            capture(mc, "first-person");
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
        if (ticks == 70) {
            capture(mc, "third-person");mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.getSingleplayerServer().execute(() -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));player.containerMenu.broadcastChanges();
            });
        }
        if (ticks == 100) {
            capture(mc, "offhand");
            var stack = mc.player.getOffhandItem();
            stack.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL);
            stack.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.ADVANCED);
            mc.setScreen(new InventoryScreen(mc.player));
        }
        if (ticks == 130) capture(mc, "inventory");
        if (ticks == 150) {
            mc.setScreen(null);
            mc.getSingleplayerServer().execute(() -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                ItemStack stack = new ItemStack(CECarbonization.CHARRED_WOOD.get(), 64);
                stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(CharredWoodBlock.LOGS, true)
                    .with(CharredWoodBlock.STAGE, 1).with(CharredWoodBlock.AXIS, Direction.Axis.Z));
                var state = CECarbonization.CHARRED_WOOD.get().defaultBlockState();
                var entity = CECarbonization.CHARRED_WOOD.get().newBlockEntity(ORIGIN.east().above(), state);
                var tag = entity.saveWithFullMetadata(player.registryAccess());
                tag.putInt("CarbonizationRemaining", 360);
                tag.put("CarbonizationOriginal", new ItemStack(Items.OAK_LOG).save(player.registryAccess()));
                stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(tag));
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);player.containerMenu.broadcastChanges();
            });
        }
        if (ticks == 180) {
            capture(mc, "saved-data");
            mc.player.getMainHandItem().getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.ADVANCED);
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(ORIGIN.east()).add(0, .5, 0), Direction.UP, ORIGIN.east(), false));
        }
        if (ticks == 220) {
            if (!mc.level.getBlockState(ORIGIN.east().above()).is(CECarbonization.CHARRED_WOOD.get()))
                throw new IllegalStateException("Carbonized wood with saved data could not be placed");
            mc.getSingleplayerServer().execute(() -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                player.setGameMode(GameType.SURVIVAL);player.containerMenu.broadcastChanges();
            });
        }
        if (ticks == 260) {
            capture(mc, "survival");
            for (String check : new String[]{"heldFirstPerson", "heldThirdPerson", "offhand", "inventory", "tooltips", "savedData", "placement", "survival"})
                checks.put(check, true);
            writeResult(mc, false);
        }
        if (ticks == 280) {
            itemCount = mc.player.getMainHandItem().getCount();
            offhandCount = mc.player.getOffhandItem().getCount();
            mc.player.setXRot(-80);
            KeyMapping.click(mc.options.keyAttack.getKey());
            mc.options.keyAttack.setDown(true);
        }
        if (ticks == 282) {
            mc.options.keyAttack.setDown(false);
            require(mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.MISS, "Air attack did not target the sky");
            require(mc.player.swinging, "Air attack did not swing the held block");
            checks.put("airAttack", true);
        }
        if (ticks == 290) {
            KeyMapping.click(mc.options.keyUse.getKey());
            mc.options.keyUse.setDown(true);
        }
        if (ticks == 292) mc.options.keyUse.setDown(false);
        if (ticks == 310) {
            require(mc.player.getMainHandItem().getCount() == itemCount && mc.player.getOffhandItem().getCount() == offhandCount,
                "Air use consumed carbonized wood");
            checks.put("airUse", true);
            mc.player.setXRot(25);
            mc.getSingleplayerServer().execute(() -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                var level = player.serverLevel();
                level.setBlockAndUpdate(CHEST, Blocks.CHEST.defaultBlockState());
                level.setBlockAndUpdate(BASIN, AllBlocks.BASIN.getDefaultState());
                Pig pig = EntityType.PIG.create(level);
                pig.setNoAi(true);pig.setPersistenceRequired();pig.moveTo(.5, 81, 2.5, 0, 0);
                level.addFreshEntity(pig);pigId = pig.getId();
            });
        }
        if (ticks == 330) useOn(mc, InteractionHand.MAIN_HAND, PLACEMENT);
        if (ticks == 360) {
            require(mc.level.getBlockState(PLACEMENT.above()).is(CECarbonization.CHARRED_WOOD.get()), "Survival placement failed");
            require(mc.player.getMainHandItem().getCount() == itemCount - 1, "Survival placement did not consume exactly one block");
            checks.put("survivalPlacement", true);
            mc.gameMode.startDestroyBlock(PLACEMENT.above(), Direction.UP);
        }
        if (ticks > 360 && ticks < 445 && !mc.level.isEmptyBlock(PLACEMENT.above())) {
            mc.gameMode.continueDestroyBlock(PLACEMENT.above(), Direction.UP);
            if (ticks % 5 == 0) mc.player.swing(InteractionHand.MAIN_HAND);
        }
        if (ticks == 450) {
            require(mc.level.isEmptyBlock(PLACEMENT.above()), "Mining while holding carbonized wood failed");
            require(mc.player.getMainHandItem().is(CECarbonization.CHARRED_WOOD.get().asItem()), "Mining changed the held item");
            checks.put("blockMining", true);
            mc.gameMode.stopDestroyBlock();
        }
        if (ticks == 460) useOn(mc, InteractionHand.MAIN_HAND, CHEST);
        if (ticks == 480) {
            require(mc.player.containerMenu != mc.player.inventoryMenu, "Holding carbonized wood prevented opening a chest");
            checks.put("chestUse", true);
            mc.player.closeContainer();
        }
        if (ticks == 490) {
            itemCount = mc.player.getMainHandItem().getCount();
            mc.options.keyShift.setDown(true);
        }
        if (ticks == 500) {
            require(mc.player.isSecondaryUseActive(), "Sneak input did not reach the player");
            useOn(mc, InteractionHand.MAIN_HAND, CHEST);
        }
        if (ticks == 530) {
            mc.options.keyShift.setDown(false);
            require(mc.level.getBlockState(CHEST.above()).is(CECarbonization.CHARRED_WOOD.get()), "Sneak placement onto a chest failed");
            require(mc.player.getMainHandItem().getCount() == itemCount - 1, "Sneak placement did not consume exactly one block");
            checks.put("sneakPlacement", true);
            capture(mc, "actions-placement");
        }
        if (ticks == 550) useOn(mc, InteractionHand.OFF_HAND, ORIGIN.offset(-3, 0, 3));
        if (ticks == 580) {
            require(mc.level.getBlockState(ORIGIN.offset(-3, 1, 3)).is(CECarbonization.CHARRED_WOOD.get()), "Offhand placement failed");
            require(mc.player.getOffhandItem().isEmpty(), "Offhand placement did not consume the last block");
            checks.put("offhandPlacementLastItem", true);
        }
        if (ticks == 600) useOn(mc, InteractionHand.MAIN_HAND, BASIN);
        if (ticks == 620) {
            checks.put("createBasinUse", true);
            var pig = mc.level.getEntity(pigId);
            require(pig instanceof Pig, "Attack target did not reach the client");
            mc.gameMode.attack(mc.player, pig);
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
        if (ticks == 640) serverCheck = CompletableFuture.supplyAsync(() -> {
            var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            var pig = player.serverLevel().getEntity(pigId);
            return pig instanceof Pig target && target.getHealth() < target.getMaxHealth();
        }, mc.getSingleplayerServer());
        if (ticks == 660) {
            require(serverCheck.isDone() && serverCheck.join(), "Held-block attack did not damage its server-side target");
            checks.put("entityAttack", true);
            itemCount = mc.player.getMainHandItem().getCount();
            mc.setScreen(new InventoryScreen(mc.player));
            var menu = mc.player.containerMenu;
            require(menu.getSlot(9).getItem().isEmpty(), "Inventory transfer destination is occupied");
            mc.gameMode.handleInventoryMouseClick(menu.containerId, 36, 0, ClickType.PICKUP, mc.player);
            mc.gameMode.handleInventoryMouseClick(menu.containerId, 9, 0, ClickType.PICKUP, mc.player);
        }
        if (ticks == 690) {
            require(mc.player.getMainHandItem().isEmpty() && mc.player.containerMenu.getSlot(9).getItem().getCount() == itemCount,
                "Inventory transfer failed");
            mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, 9, 0, ClickType.QUICK_MOVE, mc.player);
        }
        if (ticks == 720) {
            require(mc.player.getMainHandItem().is(CECarbonization.CHARRED_WOOD.get().asItem())
                && mc.player.getMainHandItem().getCount() == itemCount, "Shift-click did not restore the held stack");
            checks.put("inventoryTransferAndQuickMove", true);
            capture(mc, "actions-inventory");
            mc.player.closeContainer();
        }
        if (ticks == 750) {
            itemCount = mc.player.getMainHandItem().getCount();
            require(mc.player.drop(false), "Dropping the held block failed");
        }
        if (ticks == 765) serverCheck = CompletableFuture.supplyAsync(() -> {
            var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            var drops = player.serverLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(8),
                entity -> ItemStack.isSameItemSameComponents(entity.getItem(), player.getMainHandItem()));
            if (drops.isEmpty()) return false;
            var drop = drops.getFirst();
            drop.setNoPickUpDelay();drop.teleportTo(player.getX(), player.getY(), player.getZ());
            return true;
        }, mc.getSingleplayerServer());
        if (ticks == 810) {
            require(serverCheck.isDone() && serverCheck.join(), "The server did not spawn a matching dropped stack");
            require(mc.player.getMainHandItem().getCount() == itemCount, "Picking the dropped block up did not restore its count");
            checks.put("dropAndPickup", true);
            capture(mc, "actions-complete");
        }
        if (ticks == 840) {
            writeResult(mc, true);
            return true;
        }
        return false;
    }

    private static void capture(Minecraft mc, String view) {
        Screenshot.grab(mc.gameDirectory, "quality-charred-wood-" + view + ".png", mc.getMainRenderTarget(), message -> { });
    }

    private static void useOn(Minecraft mc, InteractionHand hand, BlockPos pos) {
        mc.gameMode.useItemOn(mc.player, hand,
            new BlockHitResult(Vec3.atCenterOf(pos).add(0, .5, 0), Direction.UP, pos, false));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void writeResult(Minecraft mc, boolean completed) {
        try {
            Files.writeString(mc.gameDirectory.toPath().resolve("charred-wood-client-result.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("completed", completed, "checks", checks)) + "\n");
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Could not save carbonized-wood check results", exception);
        }
    }
}
