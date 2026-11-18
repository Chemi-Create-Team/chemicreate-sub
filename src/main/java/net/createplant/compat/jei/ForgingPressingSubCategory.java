package net.createplant.compat.jei;

import com.simibubi.create.compat.jei.category.sequencedAssembly.SequencedAssemblySubCategory;
import com.simibubi.create.content.processing.sequenced.SequencedRecipe;

import net.createplant.registry.CEItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class ForgingPressingSubCategory
        extends SequencedAssemblySubCategory.AssemblyPressing {
    @Override
    public void draw(
            SequencedRecipe<?> recipe,
            GuiGraphics graphics,
            double mouseX,
            double mouseY,
            int index) {
        super.draw(recipe, graphics, mouseX, mouseY, index);
        var outputs = recipe.getRecipe().getRollableResults();
        if (outputs.size() < 2) return;
        graphics.renderItem(CEItems.STEEL_SLAG.toStack(), 4, 70);
        graphics.drawString(
                Minecraft.getInstance().font,
                Math.round(outputs.get(1).getChance() * 100) + "%",
                1,
                88,
                0x808080,
                false);
    }
}
