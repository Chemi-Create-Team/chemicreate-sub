package net.createplant.compat.jei;

import java.util.List;
import java.util.Locale;
import com.simibubi.create.AllBlocks;
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
import net.createplant.client.SynthesisGuide;
import net.createplant.registry.CEReciprocating;
import net.createplant.thermal.Grade;
import net.createplant.transport.FluidHazards;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.fluids.FluidStack;

/** Inputs in a grid of three columns, an arrow with the kept items under it, outputs in a grid, the machine underneath, then the note. */
public class PlantProcessCategory implements IRecipeCategory<PlantProcess> {
    private static final int WIDTH = 178, HEIGHT = 148, STEP = 19, OUTPUTS = 100, SCENE = 60, SCENE_HEIGHT = 64, NOTE = 128;
    private static final int DETAIL_OUTPUTS = 97, DETAIL_STEP = 27, DETAIL_ROW = 29, CAPTION = 139;
    private static final String CONDITION = "jei." + CreatePlant.MOD_ID + ".condition.";
    private final RecipeType<PlantProcess> type;
    private final Component title;
    private final IDrawable icon, arrow, heat, pressure, clock, ratio;
    private final MachineScene scene;

    public PlantProcessCategory(RecipeType<PlantProcess> type, ItemLike icon, String storyboard, IGuiHelper gui) {
        this.type = type;
        scene = new MachineScene(storyboard);
        title = Component.translatable("jei." + CreatePlant.MOD_ID + "." + type.getUid().getPath());
        this.icon = gui.createDrawableItemLike(icon);
        arrow = gui.getRecipeArrow();
        heat = gui.createDrawableItemLike(AllBlocks.BLAZE_BURNER.get());
        pressure = gui.createDrawableItemLike(CEReciprocating.COMPRESSOR.get());
        clock = gui.createDrawableItemLike(Items.CLOCK);
        ratio = gui.createDrawableItemLike(Items.COMPARATOR);
    }

    @Override public RecipeType<PlantProcess> getRecipeType() { return type; }
    @Override public Component getTitle() { return title; }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return WIDTH; }
    @Override public int getHeight() { return HEIGHT; }

    @Override public void setRecipe(IRecipeLayoutBuilder builder, PlantProcess recipe, IFocusGroup focuses) {
        if (recipe.conditions() != null) {
            setDetailedRecipe(builder, recipe);
            return;
        }
        int slot = 0;
        for (var item : recipe.items()) builder.addSlot(RecipeIngredientRole.INPUT, x(0, slot), y(slot++)).addItemStacks(item).setStandardSlotBackground();
        for (var fluids : recipe.fluidOptions()) fluid(builder.addSlot(RecipeIngredientRole.INPUT, x(0, slot), y(slot++)), fluids);
        for (int i = 0; i < recipe.kept().size(); i++)
            builder.addSlot(RecipeIngredientRole.CATALYST, 64 + i * STEP, 42).addItemStack(recipe.kept().get(i)).setStandardSlotBackground();
        slot = 0;
        for (var item : recipe.results()) builder.addSlot(RecipeIngredientRole.OUTPUT, x(OUTPUTS, slot), y(slot++)).addItemStack(item).setOutputSlotBackground();
        for (FluidStack fluid : recipe.fluidResults()) fluid(builder.addSlot(RecipeIngredientRole.OUTPUT, x(OUTPUTS, slot), y(slot++)), fluid);
    }

    private static void setDetailedRecipe(IRecipeLayoutBuilder builder, PlantProcess recipe) {
        for (int i = 0; i < recipe.fluidOptions().size(); i++)
            fluid(builder.addInputSlot(detailX(3, i), detailY(i)), recipe.fluidOptions().get(i));
        for (int i = 0; i < recipe.fluidResults().size(); i++)
            fluid(builder.addOutputSlot(detailX(DETAIL_OUTPUTS, i), detailY(i)), recipe.fluidResults().get(i));
        for (int i = 0; i < recipe.kept().size(); i++) {
            var catalyst = recipe.kept().get(i);
            builder.addSlot(RecipeIngredientRole.CATALYST, 72 + i * STEP, 34)
                .setSlotName("catalyst").setOutputSlotBackground().addItemStack(catalyst)
                .addRichTooltipCallback((slot, tooltip) -> tooltip.add(Component.translatable(
                    CONDITION + (catalyst.isDamageableItem() ? "wear" : "kept"))));
            builder.addInvisibleIngredients(RecipeIngredientRole.INPUT).addItemStack(catalyst);
        }
    }

    private static void fluid(mezz.jei.api.gui.builder.IRecipeSlotBuilder slot, FluidStack fluid) {
        fluid(slot,List.of(fluid));
    }
    private static void fluid(mezz.jei.api.gui.builder.IRecipeSlotBuilder slot,List<FluidStack> fluids) {
        slot.addRichTooltipCallback((view, tooltip) -> view.getDisplayedIngredient(NeoForgeTypes.FLUID_STACK).ifPresent(shown -> {
            tooltip.add(net.createplant.thermal.FluidThermal.readout(shown));
            var hint = SynthesisGuide.fluidTooltip(shown.getFluid());
            if (hint != null) tooltip.add(hint);
        }));
        slot.addIngredients(NeoForgeTypes.FLUID_STACK,fluids).setFluidRenderer(Math.max(1,fluids.stream().mapToInt(FluidStack::getAmount).max().orElse(1)),false,16,16).setStandardSlotBackground();
    }

    private static int x(int left, int slot) { return left + 1 + slot % 3 * STEP; }
    private static int y(int slot) { return 1 + slot / 3 * STEP; }
    private static int detailX(int left, int slot) { return left + slot % 3 * DETAIL_STEP; }
    private static int detailY(int slot) { return 2 + slot / 3 * DETAIL_ROW; }

    @Override public void draw(PlantProcess recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        if (recipe.conditions() != null) {
            drawConditions(recipe, graphics);
            return;
        }
        arrow.draw(graphics, 66, 20);
        scene.draw(graphics, 0, SCENE, WIDTH, SCENE_HEIGHT);
        var font = Minecraft.getInstance().font;
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(recipe.note(), WIDTH);
        for (int i = 0; i < Math.min(2, lines.size()); i++) graphics.drawString(font, lines.get(i), 0, NOTE + i * 10, 0xFF5F5F5F, false);
    }

    private void drawConditions(PlantProcess recipe, GuiGraphics graphics) {
        var conditions = recipe.conditions();
        var font = Minecraft.getInstance().font;
        arrow.draw(graphics, 63, 4);
        drawAmounts(graphics, recipe.fluids(), 3);
        drawAmounts(graphics, recipe.fluidResults(), DETAIL_OUTPUTS);
        if (!recipe.kept().isEmpty()) {
            boolean wears = recipe.kept().getFirst().isDamageableItem();
            graphics.drawCenteredString(font, Component.translatable(CONDITION + (wears ? "catalyst_wear" : "catalyst")),
                80, 54, 0xFF7F6126);
        }

        graphics.hLine(0, WIDTH - 1, 65, 0xFF9A9A9A);
        scene.draw(graphics, 114, 70, 63, 62);
        metric(graphics, heat, 70, Component.translatable(CONDITION + "heat." + conditions.heat().name().toLowerCase(Locale.ROOT)));
        Grade.Pressure inputGrade = Grade.Pressure.ATMOSPHERIC;
        for (var stack : recipe.fluids()) {
            var grade = FluidHazards.of(stack).pressure();
            if (grade.compareTo(inputGrade) > 0) inputGrade = grade;
        }
        metric(graphics, pressure, 86, inputGrade.label());
        metric(graphics, clock, 102, Component.translatable(CONDITION + "duration", conditions.ticks()));
        if (recipe.fluids().size() == 2 && recipe.fluids().stream().allMatch(stack -> FluidHazards.of(stack).gas())) {
            int a = recipe.fluids().get(0).getAmount(), b = recipe.fluids().get(1).getAmount();
            int divisor = java.math.BigInteger.valueOf(a).gcd(java.math.BigInteger.valueOf(b)).intValue();
            metric(graphics, ratio, 118, Component.translatable(CONDITION + "ratio", a / divisor, b / divisor));
        }
        graphics.drawString(font, Component.translatable(CONDITION + "batch", conditions.minimumLevels()), 1, CAPTION, 0xFF4F4F4F, false);
    }

    private static void drawAmounts(GuiGraphics graphics, List<FluidStack> fluids, int left) {
        var font = Minecraft.getInstance().font;
        for (int i = 0; i < fluids.size(); i++) {
            String amount = Integer.toString(fluids.get(i).getAmount());
            graphics.drawString(font, amount, detailX(left, i) + (16 - font.width(amount)) / 2,
                detailY(i) + 18, 0xFF4F4F4F, false);
        }
    }

    private static void metric(GuiGraphics graphics, IDrawable icon, int y, Component value) {
        icon.draw(graphics, 1, y - 1);
        graphics.drawString(Minecraft.getInstance().font, value, 20, y + 3, 0xFF4F4F4F, false);
    }
}
