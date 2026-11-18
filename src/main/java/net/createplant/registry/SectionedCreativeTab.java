package net.createplant.registry;

import net.createplant.CreatePlant;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Decorative rows belong to the client layout; the tab and search keep their real item collections.
 */
public class SectionedCreativeTab extends CreativeModeTab {
    public record Section(String key, Component title, int row, ResourceLocation texture) {}

    private List<ItemStack> layoutItems = List.of();
    private List<Section> sections = List.of();

    public SectionedCreativeTab(Builder builder) {
        super(builder);
    }

    @Override
    public void buildContents(ItemDisplayParameters parameters) {
        super.buildContents(parameters);
        var groups = new LinkedHashMap<String, List<ItemStack>>();
        for (var stack : getDisplayItems()) {
            if (!stack.isEmpty())
                groups.computeIfAbsent(
                                CECreativeModeTabs.section(stack.getItem()),
                                key -> new ArrayList<>())
                        .add(stack);
        }
        var items = new ArrayList<ItemStack>();
        var headings = new ArrayList<Section>();
        for (var group : groups.entrySet()) {
            headings.add(
                    new Section(
                            group.getKey(),
                            Component.translatable(
                                    "creative_section.create_refinery_n_reactors."
                                            + group.getKey()),
                            items.size() / 9,
                            CreatePlant.location(
                                    "textures/gui/creative/" + group.getKey() + ".png")));
            for (int i = 0; i < 9; i++) items.add(ItemStack.EMPTY);
            items.addAll(group.getValue());
            while (items.size() % 9 != 0) items.add(ItemStack.EMPTY);
        }
        layoutItems = List.copyOf(items);
        sections = List.copyOf(headings);
    }

    public List<ItemStack> layoutItems() {
        return layoutItems;
    }

    public List<Section> sections() {
        return sections;
    }
}
