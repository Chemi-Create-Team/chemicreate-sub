package net.createplant.compat.jei;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.compat.jei.category.sequencedAssembly.SequencedAssemblySubCategory;
import com.simibubi.create.content.processing.sequenced.SequencedRecipe;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Items;

public final class ForgingBlastingSubCategory extends SequencedAssemblySubCategory {
    public ForgingBlastingSubCategory() {
        super(25);
    }

    @Override
    public void draw(
            SequencedRecipe<?> recipe,
            GuiGraphics graphics,
            double mouseX,
            double mouseY,
            int index) {
        graphics.renderItem(AllBlocks.ENCASED_FAN.asStack(), 4, 38);
        graphics.renderItem(Items.LAVA_BUCKET.getDefaultInstance(), 4, 57);
    }
}
