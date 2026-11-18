package net.createplant.client;

import net.createplant.CreatePlant;
import net.createplant.inventory.VaultHatchMenu;
import net.createplant.registry.CEVault;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = CreatePlant.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class VaultHatchScreen extends AbstractContainerScreen<VaultHatchMenu> {
    private static final ResourceLocation CHEST = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private Button previous;
    private Button next;

    public VaultHatchScreen(VaultHatchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 242;
        inventoryLabelY = 148;
    }

    @SubscribeEvent public static void register(RegisterMenuScreensEvent event) { event.register(CEVault.MENU.get(), VaultHatchScreen::new); }

    @Override protected void init() {
        super.init();
        previous = addRenderableWidget(Button.builder(Component.literal("<"), button -> turnPage(0))
            .bounds(leftPos + 8, topPos + 17, 20, 18).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), button -> turnPage(1))
            .bounds(leftPos + 148, topPos + 17, 20, 18).build());
    }

    private void turnPage(int button) { minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button); }

    @Override protected void containerTick() {
        super.containerTick();
        previous.active = menu.page() > 0 && menu.getCarried().isEmpty();
        next.active = menu.page() + 1 < menu.pages() && menu.getCarried().isEmpty();
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(CHEST, leftPos, topPos, 0, 0, imageWidth, 17);
        graphics.blit(CHEST, leftPos, topPos + 17, 0, 0, imageWidth, 20);
        graphics.blit(CHEST, leftPos, topPos + 37, 0, 17, imageWidth, 108);
        graphics.blit(CHEST, leftPos, topPos + 145, 0, 126, imageWidth, 96);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        Component page = Component.translatable("menu.create_refinery_n_reactors.vault_hatch.page", menu.page() + 1, menu.pages());
        graphics.drawString(font, page, (imageWidth - font.width(page)) / 2, 22, 0x404040, false);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
