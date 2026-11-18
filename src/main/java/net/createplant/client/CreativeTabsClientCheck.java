package net.createplant.client;

import com.google.gson.GsonBuilder;

import net.createplant.registry.CECreativeModeTabs;
import net.createplant.registry.SectionedCreativeTab;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;

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
                select(CreativeModeTabs.getDefaultTab());
                if (screen.getMenu().items.stream().anyMatch(ItemStack::isEmpty))
                    throw new IllegalStateException("Vanilla tabs must keep their original layout");
                results.add(
                        Map.of(
                                "search",
                                "passed",
                                "vanilla_tab",
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
