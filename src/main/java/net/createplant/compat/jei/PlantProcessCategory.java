package net.createplant.compat.jei;

import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.createplant.CreatePlant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.fluids.FluidStack;

/** Inputs in a grid of three columns, an arrow with the kept items under it, outputs in a grid, the machine underneath, then the note. */
public class PlantProcessCategory implements IRecipeCategory<PlantProcess> {
    private static final int WIDTH = 178, HEIGHT = 148, STEP = 19, OUTPUTS = 100, SCENE = 60, SCENE_HEIGHT = 64, NOTE = 128;
    private final RecipeType<PlantProcess> type;
    private final Component title;
    private final IDrawable icon, arrow;
    private final MachineScene scene;

    public PlantProcessCategory(RecipeType<PlantProcess> type, ItemLike icon, String storyboard, IGuiHelper gui) {
        this.type = type;
        scene = new MachineScene(storyboard);
        title = Component.translatable("jei." + CreatePlant.MOD_ID + "." + type.getUid().getPath());
        this.icon = gui.createDrawableItemLike(icon);
        arrow = gui.getRecipeArrow();
    }

    @Override public RecipeType<PlantProcess> getRecipeType() { return type; }
    @Override public Component getTitle() { return title; }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return WIDTH; }
    @Override public int getHeight() { return HEIGHT; }

    @Override public void setRecipe(IRecipeLayoutBuilder builder, PlantProcess recipe, IFocusGroup focuses) {
        int slot = 0;
        for (var item : recipe.items()) builder.addSlot(RecipeIngredientRole.INPUT, x(0, slot), y(slot++)).addItemStacks(item).setStandardSlotBackground();
        for (var fluids : recipe.fluidOptions()) fluid(builder.addSlot(RecipeIngredientRole.INPUT, x(0, slot), y(slot++)), fluids);
        for (int i = 0; i < recipe.kept().size(); i++)
            builder.addSlot(RecipeIngredientRole.CATALYST, 64 + i * STEP, 42).addItemStack(recipe.kept().get(i)).setStandardSlotBackground();
        slot = 0;
        for (var item : recipe.results()) builder.addSlot(RecipeIngredientRole.OUTPUT, x(OUTPUTS, slot), y(slot++)).addItemStack(item).setOutputSlotBackground();
        for (FluidStack fluid : recipe.fluidResults()) fluid(builder.addSlot(RecipeIngredientRole.OUTPUT, x(OUTPUTS, slot), y(slot++)), fluid);
    }

    private static void fluid(mezz.jei.api.gui.builder.IRecipeSlotBuilder slot, FluidStack fluid) {
        fluid(slot,List.of(fluid));
    }
    private static void fluid(mezz.jei.api.gui.builder.IRecipeSlotBuilder slot,List<FluidStack> fluids) {
        slot.addTooltipCallback((view,tooltip)->view.getDisplayedIngredient(NeoForgeTypes.FLUID_STACK).ifPresent(shown->tooltip.add(net.createplant.thermal.FluidThermal.readout(shown))));
        slot.addIngredients(NeoForgeTypes.FLUID_STACK,fluids).setFluidRenderer(Math.max(1,fluids.stream().mapToInt(FluidStack::getAmount).max().orElse(1)),false,16,16).setStandardSlotBackground();
    }

    private static int x(int left, int slot) { return left + 1 + slot % 3 * STEP; }
    private static int y(int slot) { return 1 + slot / 3 * STEP; }

    @Override public void draw(PlantProcess recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        arrow.draw(graphics, 66, 20);
        scene.draw(graphics, 0, SCENE, WIDTH, SCENE_HEIGHT);
        var font = Minecraft.getInstance().font;
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(recipe.note(), WIDTH);
        for (int i = 0; i < Math.min(2, lines.size()); i++) graphics.drawString(font, lines.get(i), 0, NOTE + i * 10, 0xFF5F5F5F, false);
    }
}
