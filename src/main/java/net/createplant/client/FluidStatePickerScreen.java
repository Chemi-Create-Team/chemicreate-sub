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

/** Creative-only picker: one bucket per substance, with its temperature and pressure chosen here. 2026-11-24 user-designated exception to the no-custom-GUI rule. */
public final class FluidStatePickerScreen extends Screen {
    private static final String TEXT = "gui." + CreatePlant.MOD_ID + ".fluid_state.";
    private final CreativeModeInventoryScreen parent;
    private final ItemStack bucket;
    private final FluidStack nominal;
    private int temperature, pressure;

    private FluidStatePickerScreen(CreativeModeInventoryScreen parent, ItemStack bucket, FluidStack contents) {
        super(Component.translatable(TEXT + "title"));
        this.parent = parent;
        this.bucket = bucket;
        nominal = contents;
        temperature = FluidState.temperature(contents);
        pressure = FluidState.pressure(contents);
    }

    /** Right-clicking a fluid bucket in the Create-Plant creative tab opens the picker. */
    public static boolean open(CreativeModeInventoryScreen screen, ItemStack stack) {
        if (!(stack.getItem() instanceof BucketItem item) || item.content == Fluids.EMPTY
            || item != Items.WATER_BUCKET && !BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(CreatePlant.MOD_ID)) return false;
        var contents = StatefulBuckets.contents(stack, item.content);
        if (net.createplant.mixture.MixtureHelper.isMixture(contents) || net.createplant.thermal.TwoPhase.is(contents)) return false;
        Minecraft.getInstance().setScreen(new FluidStatePickerScreen(screen, stack, contents));
        return true;
    }

    private FluidStack chosen() { return FluidState.with(nominal, temperature, pressure); }

    @Override protected void init() {
        int x = width / 2 - 100, y = height / 2 - 40;
        addRenderableWidget(new StateSlider(x, y, true));
        addRenderableWidget(new StateSlider(x, y + 24, false));
        addRenderableWidget(Button.builder(Component.translatable(TEXT + "give"), button -> give()).bounds(x, y + 72, 98, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose()).bounds(x + 102, y + 72, 98, 20).build());
    }

    private void give() {
        var stack = new ItemStack(bucket.getItem());
        var fluid = chosen();
        stack.remove(CEWorldFluids.BUCKET_CONTENTS.get());
        StatefulBuckets.write(fluid, stack);
        onClose();
        if (minecraft.screen == parent) parent.getMenu().setCarried(stack);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int x = width / 2;
        int y = height / 2 - 40;
        graphics.drawCenteredString(font, title, x, y - 28, 0xFFFFFFFF);
        graphics.drawCenteredString(font, nominal.getHoverName(), x, y - 16, 0xFFFFD27F);
        graphics.drawCenteredString(font, Component.translatable(TEXT + "phase", FluidState.phaseName(chosen())), x, y + 52, 0xFFFFFFFF);
    }

    @Override public boolean isPauseScreen() { return false; }

    /** A 17-step slider over the fluid state levels, 0 to 16. */
    private final class StateSlider extends AbstractSliderButton {
        private final boolean heat;

        StateSlider(int x, int y, boolean heat) {
            super(x, y, 200, 20, Component.empty(), (heat ? temperature : pressure) / (double) FluidState.MAX);
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
            value = level() / (double) FluidState.MAX;
            if (heat) temperature = level();
            else pressure = level();
        }
    }
}
