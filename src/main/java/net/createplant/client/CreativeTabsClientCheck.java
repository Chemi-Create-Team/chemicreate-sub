package net.createplant.client;

import com.google.gson.GsonBuilder;

import net.createplant.item.StatefulBuckets;
import net.createplant.registry.CECreativeModeTabs;
import net.createplant.registry.CEItems;
import net.createplant.registry.SectionedCreativeTab;
import net.createplant.thermal.FluidState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
import net.minecraft.world.item.Items;

import org.lwjgl.glfw.GLFW;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;

/**
 * Opt-in checks through the native creative screen, including its own tab clicks and scroll
 * handling.
 */
public final class CreativeTabsClientCheck {
    private static CreativeModeInventoryScreen screen;
    private static Slot waterSlot;
    private static int ticks, phase;
    private static final ArrayList<Map<String, Object>> results = new ArrayList<>();

    private CreativeTabsClientCheck() {}

    public static void start(Minecraft mc) {
        screen = new CreativeModeInventoryScreen(mc.player, mc.level.enabledFeatures(), false);
        mc.setScreen(screen);
        select(CECreativeModeTabs.BASE.get());
        check((SectionedCreativeTab) CECreativeModeTabs.BASE.get());
        ready("unified");
    }

    public static boolean tick(Minecraft mc) throws Exception {
        if (++ticks < Integer.getInteger("createplant.creativeTabsCaptureTicks", 400)) return false;
        ticks = 0;
        switch (++phase) {
            case 1 -> {
                screen.mouseScrolled(screen.getGuiLeft() + 20, screen.getGuiTop() + 45, 0, -6);
                ready("unified_scrolled");
            }
            case 2 -> {
                screen.mouseScrolled(screen.getGuiLeft() + 20, screen.getGuiTop() + 45, 0, -100);
                ready("unified_bottom");
            }
            case 3 -> {
                select(CreativeModeTabs.searchTab());
                String query = System.getProperty("createplant.creativeTabsSearchQuery",
                    net.createplant.registry.CEItems.POLYETHYLENE.toStack().getHoverName().getString());
                for (char c : query.toCharArray()) screen.charTyped(c, 0);
                if (screen.getMenu().items.isEmpty()
                        || screen.getMenu().items.stream().anyMatch(ItemStack::isEmpty))
                    throw new IllegalStateException(
                            "Search must contain real items without decorative rows");
                ready("search");
            }
            case 4 -> {
                openWaterPicker(mc);
                ready("water_state_picker");
            }
            case 5 -> {
                checkFluidPicker(mc);
                select(CreativeModeTabs.getDefaultTab());
                if (screen.getMenu().items.stream().anyMatch(ItemStack::isEmpty))
                    throw new IllegalStateException("Vanilla tabs must keep their original layout");
                results.add(
                        Map.of(
                                "search",
                                "passed",
                                "vanilla_tab",
                                "passed",
                                "fluid_picker",
                                "passed",
                                "native_scroll",
                                "passed"));
                Files.writeString(
                        Path.of("creative-tabs-result.json"),
                        new GsonBuilder().setPrettyPrinting().create().toJson(results));
                LoggerFactory.getLogger(CreativeTabsClientCheck.class).info("CREATIVE TABS PASSED");
                return true;
            }
        }
        return false;
    }

    private static void openWaterPicker(Minecraft mc) {
        select(CECreativeModeTabs.BASE.get());
        var menu = screen.getMenu();
        int row = menu.items.indexOf(menu.items.stream().filter(stack -> stack.is(Items.WATER_BUCKET)).findFirst().orElseThrow()) / 9;
        screen.mouseScrolled(screen.getGuiLeft() + 20, screen.getGuiTop() + 45, 0, -row);
        var slot = menu.slots.subList(0, 45).stream().filter(s -> s.getItem().is(Items.WATER_BUCKET)).findFirst().orElseThrow();
        double x = screen.getGuiLeft() + slot.x + 7, y = screen.getGuiTop() + slot.y + 7;

        menu.setCarried(new ItemStack(Items.STONE, 2));
        screen.mouseClicked(x, y, 1);
        screen.mouseReleased(x, y, 1);
        if (mc.screen != screen || !menu.getCarried().is(Items.STONE))
            throw new IllegalStateException("A carried item must keep the native right-click action");
        menu.setCarried(ItemStack.EMPTY);
        waterSlot = slot;
        configure(mc, Items.WATER_BUCKET, 12, 12);
    }

    private static void checkFluidPicker(Minecraft mc) {
        var menu = screen.getMenu();
        take(mc, Items.WATER_BUCKET, 12, 12, true);
        if (!waterSlot.getItem().is(Items.WATER_BUCKET)) throw new IllegalStateException("The picker lost the catalogue scroll position");
        var hotbar = menu.slots.subList(45, 54).stream().filter(slot -> !slot.hasItem()).findFirst().orElseThrow();
        double hotbarX = screen.getGuiLeft() + hotbar.x + 7, hotbarY = screen.getGuiTop() + hotbar.y + 7;
        screen.mouseClicked(hotbarX, hotbarY, 0);
        screen.mouseReleased(hotbarX, hotbarY, 0);
        if (!hotbar.getItem().is(Items.WATER_BUCKET) || !menu.getCarried().isEmpty())
            throw new IllegalStateException("The selected bucket did not reach the hotbar: slot=" + hotbar.getItem() + ", cursor=" + menu.getCarried());
        assertState(hotbar.getItem(), 12, 12, true);

        select(CreativeModeTabs.searchTab());
        var nitrogen = CEItems.NITROGEN_GAS_BUCKET.get();
        for (char c : Component.translatable(nitrogen.getDescriptionId()).getString().toCharArray()) screen.charTyped(c, 0);
        if (menu.items.stream().filter(stack -> stack.is(nitrogen)).count() != 1)
            throw new IllegalStateException("Search listed more than one nitrogen state or no nitrogen bucket");
        var searchResults = new ArrayList<>(menu.items);
        configure(mc, nitrogen, 1, 4);
        take(mc, nitrogen, 1, 4, false);
        if (!searchResults.equals(menu.items)) throw new IllegalStateException("The picker lost the creative search query");
        menu.setCarried(ItemStack.EMPTY);
    }

    private static void configure(Minecraft mc, Item bucket, int temperature, int pressure) {
        var menu = screen.getMenu();
        var slot = menu.slots.subList(0, 45).stream().filter(s -> s.getItem().is(bucket)).findFirst().orElseThrow();
        double x = screen.getGuiLeft() + slot.x + 7, y = screen.getGuiTop() + slot.y + 7;
        screen.mouseClicked(x, y, 1);
        if (mc.screen == screen) screen.mouseReleased(x, y, 1);
        if (!(mc.screen instanceof FluidStatePickerScreen picker))
            throw new IllegalStateException("Right-clicking " + bucket + " did not open the picker");
        var sliders = picker.children().stream().filter(AbstractSliderButton.class::isInstance).map(AbstractSliderButton.class::cast).toList();
        slide(picker, sliders.get(0), temperature - 1);
        picker.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0);
        slide(picker, sliders.get(1), pressure);
    }

    private static void take(Minecraft mc, Item bucket, int temperature, int pressure, boolean gas) {
        var picker = (FluidStatePickerScreen) mc.screen;
        int x = picker.width / 2 - 40, y = picker.height / 2 + 45;
        picker.mouseClicked(x, y, 0);
        mc.screen.mouseReleased(x, y, 0);
        var chosen = screen.getMenu().getCarried();
        if (mc.screen != screen || !chosen.is(bucket)) throw new IllegalStateException("The picker did not return the bucket to the creative cursor");
        assertState(chosen, temperature, pressure, gas);
    }

    private static void slide(FluidStatePickerScreen picker, AbstractSliderButton slider, int level) {
        double x = slider.getX() + 4 + (slider.getWidth() - 8) * (level / (double) FluidState.MAX);
        double y = slider.getY() + 10;
        picker.mouseClicked(x, y, 0);
        picker.mouseReleased(x, y, 0);
    }

    private static void assertState(ItemStack stack, int temperature, int pressure, boolean gas) {
        var contents = StatefulBuckets.contents(stack, ((BucketItem) stack.getItem()).content);
        if (FluidState.temperature(contents) != temperature || FluidState.pressure(contents) != pressure || FluidState.gas(contents) != gas)
            throw new IllegalStateException("Unexpected picked fluid state: " + contents);
    }

    private static void ready(String name) {
        LoggerFactory.getLogger(CreativeTabsClientCheck.class).info("CREATIVE TAB READY {}", name);
    }

    private static void select(CreativeModeTab tab) {
        for (int direction : new int[] {1, -1}) {
            for (int i = 0; i < 8 && !screen.getCurrentPage().getVisibleTabs().contains(tab); i++) {
                double x = screen.getGuiLeft() + (direction > 0 ? screen.getXSize() - 10 : 10);
                screen.mouseClicked(x, screen.getGuiTop() - 40, 0);
                screen.mouseReleased(x, screen.getGuiTop() - 40, 0);
            }
        }
        var page = screen.getCurrentPage();
        if (!page.getVisibleTabs().contains(tab))
            throw new IllegalStateException("Creative tab is missing from native pages");
        double x = screen.getGuiLeft() + 27 * page.getColumn(tab) + 12;
        double y = screen.getGuiTop() + (page.isTop(tab) ? -20 : screen.getYSize() + 12);
        screen.mouseClicked(x, y, 0);
        screen.mouseReleased(x, y, 0);
    }

    private static void check(SectionedCreativeTab tab) {
        if (!screen.getMenu().items.equals(tab.layoutItems()))
            throw new IllegalStateException("Native menu did not use category rows");
        var real = ItemStackLinkedSet.createTypeAndComponentsSet();
        real.addAll(tab.layoutItems().stream().filter(stack -> !stack.isEmpty()).toList());
        if (real.size() != tab.getDisplayItems().size()
                || !real.containsAll(tab.getDisplayItems())
                || tab.getSearchTabDisplayItems().stream().anyMatch(ItemStack::isEmpty))
            throw new IllegalStateException("Creative styling changed the actual item catalogue");
        for (var section : tab.sections()) {
            if (Minecraft.getInstance()
                    .getResourceManager()
                    .getResource(section.texture())
                    .isEmpty())
                throw new IllegalStateException("Missing category banner: " + section.texture());
            for (int i = 0; i < 9; i++)
                if (!tab.layoutItems().get(section.row() * 9 + i).isEmpty())
                    throw new IllegalStateException("Banner overlaps an item");
        }
        results.add(
                Map.of(
                        "tab",
                        tab.getDisplayName().getString(),
                        "real_items",
                        real.size(),
                        "sections",
                        tab.sections().size(),
                        "display_slots",
                        tab.layoutItems().size(),
                        "catalogue",
                        "passed"));
    }
}
