package net.createplant.client;

import net.createplant.registry.SectionedCreativeTab;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;

public final class CreativeSectionBanners {
    private CreativeSectionBanners() {}

    public static void render(
            CreativeModeInventoryScreen screen,
            SectionedCreativeTab tab,
            float scroll,
            GuiGraphics graphics) {
        int firstRow = Math.max((int) (scroll * (tab.layoutItems().size() / 9 - 5) + .5), 0);
        int x = screen.getGuiLeft() + 8, top = screen.getGuiTop() + 17;
        var font = Minecraft.getInstance().font;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 220);
        String heading = tab.getDisplayName().getString();
        if (font.width(heading) > 171) heading = font.plainSubstrByWidth(heading, 161) + "…";
        graphics.drawString(font, heading, x, screen.getGuiTop() + 6, 0xFF404040, false);
        for (var section : tab.sections()) {
            int row = section.row() - firstRow;
            if (row < 0 || row >= 5) continue;
            int y = top + row * 18;
            graphics.blit(section.texture(), x, y, 0, 0, 162, 18, 162, 18);
            int labelWidth = Math.min(font.width(section.title()) + 8, 158);
            graphics.fill(x + 2, y + 2, x + labelWidth, y + 16, 0xD010151A);
            graphics.drawString(font, section.title(), x + 4, y + 5, 0xFFF0EBDD, true);
        }
        graphics.pose().popPose();
    }
}
