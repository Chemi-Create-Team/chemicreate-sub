package net.createplant.client;

import com.mojang.math.Axis;
import net.createplant.chemistry.ChemicalReadout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;

final class MoleculeTooltip implements ClientTooltipComponent {
    private static final int PANEL_WIDTH = 92, PANEL_HEIGHT = 78, GAP = 3;
    private static final int PANEL = 0xff202333, BORDER = 0xff41465d, INK = 0xffb7c1d6, BOND = 0xffbfc6d7;

    private final MoleculeStructures.Model model;

    MoleculeTooltip(FluidItemTooltips.Preview preview) { model = preview.model(); }

    @Override public int getHeight() { return PANEL_HEIGHT + 4; }
    @Override public int getWidth(Font font) { return PANEL_WIDTH * 2 + GAP; }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        y += 2;
        graphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, BORDER);
        graphics.fill(x + 1, y + 1, x + PANEL_WIDTH - 1, y + PANEL_HEIGHT - 1, PANEL);
        graphics.drawCenteredString(font, Component.translatable(ChemicalReadout.KEY + "model_flat"), x + PANEL_WIDTH / 2, y + 5, INK);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x + PANEL_WIDTH / 2f, y + 46, 0);
        drawFlat(graphics, font, model.flat());
        pose.popPose();

        int right = x + PANEL_WIDTH + GAP;
        graphics.fill(right, y, right + PANEL_WIDTH, y + PANEL_HEIGHT, BORDER);
        graphics.fillGradient(right + 1, y + 1, right + PANEL_WIDTH - 1, y + PANEL_HEIGHT - 1, 0xfff3f3f6, 0xffd8dbe3);
        var title = Component.translatable(ChemicalReadout.KEY + "model_spatial");
        graphics.drawString(font, title, right + (PANEL_WIDTH - font.width(title)) / 2, y + 5, 0xff2b303b, false);
        MolecularModelRenderer.draw(graphics, right + PANEL_WIDTH / 2f, y + 45, model.spatial());
    }

    private static void drawFlat(GuiGraphics graphics, Font font, MoleculeStructures.Drawing drawing) {
        var atoms = drawing.atoms();
        for (var bond : drawing.bonds()) drawBond(graphics, atoms.get(bond.start()), atoms.get(bond.end()), bond.order());
        for (var atom : atoms) {
            if (atom.element().equals("C") && atoms.size() > 2) continue;
            int x = Math.round(x(atom)), y = Math.round(y(atom));
            int width = font.width(atom.element());
            graphics.fill(x - width / 2 - 1, y - 5, x + (width + 1) / 2 + 1, y + 4, PANEL);
            graphics.drawString(font, atom.element(), x - width / 2, y - 4, elementColor(atom.element()), false);
        }
    }

    private static void drawBond(GuiGraphics graphics, MoleculeStructures.Atom first, MoleculeStructures.Atom second, int order) {
        float x0 = x(first), y0 = y(first), dx = x(second) - x0, dy = y(second) - y0;
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x0, y0, 0);
        pose.mulPose(Axis.ZP.rotation((float) Math.atan2(dy, dx)));
        int length = Math.round((float) Math.hypot(dx, dy));
        for (int line = 0; line < order; line++) {
            int offset = 2 * line - order + 1;
            graphics.fill(0, offset, length, offset + 1, BOND);
        }
        pose.popPose();
    }

    private static float x(MoleculeStructures.Atom atom) { return atom.x() * 47; }
    private static float y(MoleculeStructures.Atom atom) { return atom.y() * 47; }

    private static int elementColor(String element) {
        return switch (element) {
            case "N" -> 0xff7099f3;
            case "O" -> 0xfff26f70;
            case "F", "Cl" -> 0xff7fd395;
            case "S" -> 0xffedd15e;
            case "P" -> 0xffffa660;
            case "H", "D", "T" -> 0xfff2f4f9;
            default -> INK;
        };
    }
}
