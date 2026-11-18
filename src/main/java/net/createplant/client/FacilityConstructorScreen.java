package net.createplant.client;

import com.simibubi.create.content.schematics.cannon.SchematicannonMenu;
import com.simibubi.create.content.schematics.cannon.SchematicannonScreen;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.resources.ResourceLocation;
import net.createplant.CreatePlant;
import net.createplant.assembly.AssemblyPlan;
import net.createplant.block.entity.FacilityConstructorBlockEntity;
import net.createplant.inventory.FacilityConstructorMenu;
import net.createplant.nuclear.ReactorCoreVariants;
import net.createplant.registry.CEAssembly;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = CreatePlant.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class FacilityConstructorScreen extends SchematicannonScreen {
    private static final ResourceLocation BACKGROUND = CreatePlant.location("textures/gui/facility_constructor.png");
    private final List<Button> kinds = new ArrayList<>();
    private final List<Button> sizes = new ArrayList<>();
    private final List<Button> diameterControls = new ArrayList<>();
    private final List<Button> heights = new ArrayList<>();

    public FacilityConstructorScreen(SchematicannonMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    @SubscribeEvent public static void register(RegisterMenuScreensEvent event) { event.register(CEAssembly.MENU.get(), FacilityConstructorScreen::new); }
    private FacilityConstructorBlockEntity facility() { return ((FacilityConstructorMenu) menu).facility(); }
    private void select(int button) { minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button); }

    @Override protected void init() {
        super.init();
        leftPos -= 75;
        for (var child : children()) if (child instanceof AbstractWidget widget) widget.setX(widget.getX() - 75);
        kinds.clear();sizes.clear();heights.clear();diameterControls.clear();
        for (var kind : AssemblyPlan.Kind.values()) {
            kinds.add(addRenderableWidget(Button.builder(Component.translatable("facility.create_refinery_n_reactors." + kind.id), button -> select(kind.ordinal()))
                .bounds(leftPos + 234, topPos + 22 + kind.ordinal() * 16, 140, 15).build()));
        }
        for (int i = 0; i < ReactorCoreVariants.RECOMMENDED_DIAMETERS.size(); i++) {
            int index = i;
            sizes.add(addRenderableWidget(Button.builder(Component.literal(ReactorCoreVariants.RECOMMENDED_DIAMETERS.get(i).toString()), button -> select(10 + index))
                .bounds(leftPos + 234 + i * 28, topPos + 119, 26, 20).build()));
        }
        for (int i = 0; i < 2; i++) {
            int index = i;
            diameterControls.add(addRenderableWidget(Button.builder(Component.literal(i == 0 ? "−" : "+"), button -> select(30 + index))
                .bounds(leftPos + 234 + i * 112, topPos + 119, 28, 20).build()));
        }
        for (int i = 0; i < AssemblyPlan.CORE_HEIGHTS.size(); i++) {
            int index = i;
            heights.add(addRenderableWidget(Button.builder(Component.literal(AssemblyPlan.CORE_HEIGHTS.get(i).toString()), button -> select(20 + index))
                .bounds(leftPos + 234 + i * 72, topPos + 158, 68, 20).build()));
        }
    }

    @Override protected void containerTick() {
        super.containerTick();
        var be = facility();
        for (int i = 0; i < kinds.size(); i++) kinds.get(i).active = be.canConfigure() && i != be.kind().ordinal();
        for (int i = 0; i < sizes.size(); i++) {
            var options = AssemblyPlan.sizes(be.kind());
            var button = sizes.get(i);
            button.visible = be.kind() != AssemblyPlan.Kind.CLARIFIER && i < options.size();
            if (button.visible) {
                int spacing = 140 / options.size();
                button.setX(leftPos + 234 + i * spacing);button.setWidth(spacing - 2);
                button.setMessage(Component.literal(options.get(i).toString()));
            }
            button.active = be.canConfigure() && i != be.sizeIndex();
        }
        for (int i = 0; i < diameterControls.size(); i++) {
            var button = diameterControls.get(i);
            button.visible = be.kind() == AssemblyPlan.Kind.CLARIFIER;
            button.active = be.canConfigure() && (i == 0 ? be.sizeIndex() > 0 : be.sizeIndex() < AssemblyPlan.sizes(be.kind()).size() - 1);
        }
        for (int i = 0; i < heights.size(); i++) {
            heights.get(i).visible = be.kind() == AssemblyPlan.Kind.RBMK || be.kind() == AssemblyPlan.Kind.CLARIFIER;
            heights.get(i).setMessage(Component.literal((be.kind() == AssemblyPlan.Kind.CLARIFIER ? net.createplant.block.WetProcessStructure.CLARIFIER_HEIGHTS : AssemblyPlan.CORE_HEIGHTS).get(i).toString()));
            heights.get(i).active = be.canConfigure() && i != be.heightIndex();
        }
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        var be = facility();
        int inventoryY = topPos + AllGuiTextures.SCHEMATICANNON_TOP.getHeight() + AllGuiTextures.SCHEMATICANNON_BOTTOM.getHeight() + 2;
        renderPlayerInventory(graphics, getLeftOfCentered(AllGuiTextures.PLAYER_INVENTORY.getWidth()), inventoryY);
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, 213, 141, 213, 141);
        AllGuiTextures.SCHEMATIC_TITLE.render(graphics, leftPos, topPos - 2);
        renderPrintingProgress(graphics, leftPos, topPos, be.schematicProgress);
        renderFuelBar(graphics, leftPos, topPos, (float) be.remainingFuel / Math.max(1, be.getShotsPerGunpowder()));
        renderChecklistPrinterProgress(graphics, leftPos, topPos, be.bookPrintingProgress);
        graphics.drawString(font, title, leftPos + (213 - font.width(title)) / 2, topPos + 2, 0x505050, false);
        Component status = CreateLang.translateDirect("schematicannon.status." + be.statusMsg);
        graphics.drawString(font, status, leftPos + 103 - font.width(status) / 2, topPos + 53, 0xDDDFFF, false);
        if (be.missingItem != null) graphics.renderItem(be.missingItem, leftPos + 128, topPos + 49);
        int x = leftPos + 224, y = topPos;
        graphics.fill(x, y, x + 160, y + 218, 0xFF373532);
        graphics.fill(x + 1, y + 1, x + 159, y + 217, 0xFFDBD6C9);
        graphics.fill(x + 3, y + 3, x + 157, y + 215, 0xFFB8B4A8);
        graphics.drawString(font, Component.translatable("menu.create_refinery_n_reactors.facility_constructor.facility"), x + 10, y + 10, 0x383830, false);
        String label = be.kind() == AssemblyPlan.Kind.CLARIFIER ? "basin_diameter" : be.kind() == AssemblyPlan.Kind.RBMK ? "diameter" : "width";
        graphics.drawString(font, Component.translatable("menu.create_refinery_n_reactors.facility_constructor." + label, be.selectedSize()), x + 10, y + 104, 0x383830, false);
        if (be.kind() == AssemblyPlan.Kind.CLARIFIER)
            graphics.drawCenteredString(font, Component.literal(Integer.toString(be.selectedSize())), x + 80, y + 125, 0xFFFFFF);
        if (be.kind() == AssemblyPlan.Kind.RBMK || be.kind() == AssemblyPlan.Kind.CLARIFIER)
            graphics.drawString(font, Component.translatable("menu.create_refinery_n_reactors.facility_constructor." + (be.kind() == AssemblyPlan.Kind.CLARIFIER ? "basin_height" : "core_height"), be.planHeight()), x + 10, y + 145, 0x383830, false);
        int width = be.kind() == AssemblyPlan.Kind.RBMK ? ReactorCoreVariants.outerDiameter(be.coreSize()) : be.selectedSize();
        graphics.drawString(font, Component.translatable("menu.create_refinery_n_reactors.facility_constructor.dimensions", width, width, be.totalHeight()), x + 10, y + 187, 0x383830, false);
        graphics.drawString(font, Component.translatable("menu.create_refinery_n_reactors.facility_constructor." + (be.canConfigure() ? "preview" : "stop_to_configure")), x + 10, y + 202, 0x383830, false);
    }

    @Override protected void renderBlueprintHighlight(GuiGraphics graphics, int x, int y) { }
    @Override public List<Rect2i> getExtraAreas() { return List.of(new Rect2i(leftPos + 224, topPos, 160, 218)); }
}
