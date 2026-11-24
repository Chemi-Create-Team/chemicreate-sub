package net.createplant.client;

import net.createplant.CreatePlant;
import net.createplant.item.StatefulBuckets;
import net.createplant.registry.CEWorldFluids;
import net.createplant.thermal.FluidState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.lwjgl.glfw.GLFW;

/** Creative-only picker: one bucket per substance, with its temperature and pressure chosen here. 2026-11-24 user-designated exception to the no-custom-GUI rule. */
public final class FluidStatePickerScreen extends Screen {
    public static final String TEXT = "gui." + CreatePlant.MOD_ID + ".fluid_state.";
    private static final int SLIDER_WIDTH = 244;
    private final CreativeModeInventoryScreen parent;
    private final Runnable restoreCatalogue;
    private final ItemStack bucket;
    private final FluidStack nominal;
    private int temperature, pressure;
    private ItemStack preview;
    private Component phase;

    private FluidStatePickerScreen(CreativeModeInventoryScreen parent, ItemStack bucket, FluidStack contents, Runnable restoreCatalogue) {
        super(Component.translatable(TEXT + "title"));
        this.parent = parent;
        this.restoreCatalogue = restoreCatalogue;
        this.bucket = bucket;
        nominal = contents;
        temperature = FluidState.temperature(contents);
        pressure = FluidState.pressure(contents);
        updatePreview();
    }

    public static boolean canConfigure(ItemStack stack) {
        return contents(stack) != null;
    }

    private static FluidStack contents(ItemStack stack) {
        if (!(stack.getItem() instanceof BucketItem item) || item.content == Fluids.EMPTY
            || item != Items.WATER_BUCKET && item != com.jesz.createdieselgenerators.CDGFluids.CRUDE_OIL.getBucket().get()
                && !BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(CreatePlant.MOD_ID)) return null;
        var contents = StatefulBuckets.contents(stack, item.content);
        return net.createplant.mixture.MixtureHelper.isMixture(contents) || net.createplant.thermal.TwoPhase.is(contents) ? null : contents;
    }

    public static boolean open(CreativeModeInventoryScreen screen, ItemStack stack, Runnable restoreCatalogue) {
        var contents = contents(stack);
        if (contents == null) return false;
        Minecraft.getInstance().setScreen(new FluidStatePickerScreen(screen, stack, contents, restoreCatalogue));
        return true;
    }

    @Override protected void init() {
        int x = width / 2 - SLIDER_WIDTH / 2, y = height / 2 - 40;
        addRenderableWidget(new StateSlider(x, y, true));
        addRenderableWidget(new StateSlider(x, y + 24, false));
        addRenderableWidget(Button.builder(Component.translatable(TEXT + "give"), button -> give()).bounds(x, y + 78, 120, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose()).bounds(x + 124, y + 78, 120, 20).build());
    }

    private void updatePreview() {
        var fluid = FluidState.with(nominal, temperature, pressure);
        preview = bucket.copyWithCount(1);
        preview.remove(CEWorldFluids.BUCKET_CONTENTS.get());
        StatefulBuckets.write(fluid, preview);
        phase = Component.translatable(TEXT + "phase", FluidState.phaseName(fluid));
    }

    private void give() {
        onClose();
        if (minecraft.screen == parent) parent.getMenu().setCarried(preview.copy());
    }

    @Override public void onClose() {
        minecraft.setScreen(parent);
        if (minecraft.screen == parent) restoreCatalogue.run();
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int x = width / 2;
        int y = height / 2 - 40;
        graphics.drawCenteredString(font, title, x, y - 28, 0xFFFFFFFF);
        graphics.drawCenteredString(font, nominal.getHoverName(), x, y - 16, 0xFFFFD27F);
        int phaseLeft = x - (font.width(phase) + 22) / 2;
        graphics.renderItem(preview, phaseLeft, y + 50);
        graphics.drawString(font, phase, phaseLeft + 22, y + 54, 0xFFFFFFFF);
    }

    @Override public boolean isPauseScreen() { return false; }

    /** A 17-step slider over the fluid state levels, 0 to 16. */
    private final class StateSlider extends AbstractSliderButton {
        private final boolean heat;

        StateSlider(int x, int y, boolean heat) {
            super(x, y, SLIDER_WIDTH, 20, Component.empty(), (heat ? temperature : pressure) / (double) FluidState.MAX);
            this.heat = heat;
            updateMessage();
        }

        private int level() { return Mth.clamp(Math.round((float) (value * FluidState.MAX)), 0, FluidState.MAX); }

        @Override protected void updateMessage() {
            int level = level();
            setMessage(Component.translatable(TEXT + (heat ? "temperature" : "pressure"),
                heat ? FluidState.temperatureName(level) : FluidState.pressureName(level)));
        }

        @Override protected void applyValue() {
            int level = level();
            value = level / (double) FluidState.MAX;
            if (heat) temperature = level;
            else pressure = level;
            updatePreview();
        }

        @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
            if (key != GLFW.GLFW_KEY_LEFT && key != GLFW.GLFW_KEY_RIGHT) return super.keyPressed(key, scanCode, modifiers);
            value = FluidState.clamp(level() + (key == GLFW.GLFW_KEY_LEFT ? -1 : 1)) / (double) FluidState.MAX;
            applyValue();
            updateMessage();
            return true;
        }
    }
}
