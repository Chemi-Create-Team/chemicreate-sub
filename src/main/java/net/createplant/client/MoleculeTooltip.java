package net.createplant.client;

import com.mojang.math.Axis;
import net.createplant.chemistry.ChemicalReadout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;

final class MoleculeTooltip implements ClientTooltipComponent {
    private static final int PANEL_WIDTH = 86, PANEL_HEIGHT = 69, GAP = 3;
    private static final int PANEL = 0xff202333, BORDER = 0xff41465d, INK = 0xffb7c1d6, BOND = 0xffbfc6d7;

    private final MoleculeStructures.Model model;
    private final int diagramTop;

    MoleculeTooltip(FluidItemTooltips.Preview preview) {
        model = preview.model();
        diagramTop = preview.component() ? 13 : 2;
    }

    @Override public int getHeight() { return PANEL_HEIGHT + diagramTop + 2; }
    @Override public int getWidth(Font font) { return PANEL_WIDTH * 2 + GAP; }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        if (diagramTop > 2) graphics.drawString(font, Component.translatable(ChemicalReadout.KEY + "model_component", model.formula()),
            x + 2, y + 2, INK, false);
        panel(graphics, font, x, y + diagramTop, model.flat(), false);
        panel(graphics, font, x + PANEL_WIDTH + GAP, y + diagramTop, model.spatial(), true);
    }

    private static void panel(GuiGraphics graphics, Font font, int x, int y, MoleculeStructures.Drawing drawing, boolean spatial) {
        graphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, BORDER);
        graphics.fill(x + 1, y + 1, x + PANEL_WIDTH - 1, y + PANEL_HEIGHT - 1, PANEL);
        var title = Component.translatable(ChemicalReadout.KEY + (spatial ? "model_spatial" : "model_flat"));
        graphics.drawCenteredString(font, title, x + PANEL_WIDTH / 2, y + 5, INK);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x + PANEL_WIDTH / 2f, y + 42, 0);
        if (spatial) drawSpatial(graphics, drawing);
        else drawFlat(graphics, font, drawing);
        pose.popPose();
    }

    private static void drawFlat(GuiGraphics graphics, Font font, MoleculeStructures.Drawing drawing) {
        for (var bond : drawing.bonds()) drawBond(graphics, bond, BOND, BOND, 1);
        for (var atom : drawing.atoms()) {
            if (atom.element().equals("C") && drawing.atoms().size() > 2) continue;
            int x = Math.round(x(atom)), y = Math.round(y(atom));
            int width = font.width(atom.element());
            graphics.fill(x - width / 2 - 1, y - 5, x + (width + 1) / 2 + 1, y + 4, PANEL);
            graphics.drawString(font, atom.element(), x - width / 2, y - 4, color(atom.element()), false);
        }
    }

    private static void drawSpatial(GuiGraphics graphics, MoleculeStructures.Drawing drawing) {
        for (var bond : drawing.bonds())
            drawBond(graphics, bond, shade(color(bond.start().element()), 0.78f), shade(color(bond.end().element()), 0.78f), 2);
        for (var atom : drawing.atoms()) {
            int x = Math.round(x(atom)), y = Math.round(y(atom)), size = isHydrogen(atom.element()) ? 3 : 5;
            int color = color(atom.element());
            disk(graphics, x, y, size, shade(color, .60f));
            disk(graphics, x, y - 1, size - 1, color);
            graphics.fill(x - 1, y - 2, x + 1, y - 1, shade(color, 1.25f));
        }
    }

    private static void drawBond(GuiGraphics graphics, MoleculeStructures.Bond bond, int startColor, int endColor, int stroke) {
        float x0 = x(bond.start()), y0 = y(bond.start()), dx = x(bond.end()) - x0, dy = y(bond.end()) - y0;
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x0, y0, 0);
        pose.mulPose(Axis.ZP.rotation((float)Math.atan2(dy, dx)));
        int length = Math.round((float)Math.hypot(dx, dy));
        for (int line = 0; line < bond.order(); line++) {
            int offset = (2 * line - bond.order() + 1) * (stroke == 1 ? 1 : 2);
            graphics.fill(0, offset, length / 2, offset + stroke, startColor);
            graphics.fill(length / 2, offset, length, offset + stroke, endColor);
        }
        pose.popPose();
    }

    private static void disk(GuiGraphics graphics, int x, int y, int radius, int color) {
        for (int row = -radius; row <= radius; row++) {
            int edge = (int)Math.sqrt(radius * radius - row * row);
            graphics.fill(x - edge, y + row, x + edge + 1, y + row + 1, color);
        }
    }

    private static float x(MoleculeStructures.Atom atom) { return atom.x() * 45; }
    private static float y(MoleculeStructures.Atom atom) { return atom.y() * 45; }

    private static boolean isHydrogen(String element) { return element.equals("H") || element.equals("D") || element.equals("T"); }

    private static int color(String element) {
        return switch (element) {
            case "H" -> 0xfff2f4f9;
            case "D", "T" -> 0xff9adbe5;
            case "C" -> 0xffaeb9c9;
            case "N" -> 0xff7099f3;
            case "O" -> 0xfff26f70;
            case "F", "Cl" -> 0xff7fd395;
            case "S" -> 0xffedd15e;
            case "P" -> 0xffffa660;
            case "He", "Ar", "U" -> 0xffd3a5ee;
            default -> 0xffc0bbf5;
        };
    }

    private static int shade(int color, float scale) {
        int red = Math.min(255, Math.round((color >> 16 & 255) * scale));
        int green = Math.min(255, Math.round((color >> 8 & 255) * scale));
        int blue = Math.min(255, Math.round((color & 255) * scale));
        return 0xff000000 | red << 16 | green << 8 | blue;
    }
}
