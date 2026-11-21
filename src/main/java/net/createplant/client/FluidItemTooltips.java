package net.createplant.client;

import com.mojang.datafixers.util.Either;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import net.createplant.CreatePlant;
import net.createplant.chemistry.ChemicalReadout;
import net.createplant.chemistry.FluidChemicalRegistry;
import net.createplant.chemistry.FluidItemChemistry;
import net.createplant.mixture.MixtureGoggleHelper;
import net.createplant.mixture.MixtureHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.fluids.FluidStack;

@EventBusSubscriber(modid = CreatePlant.MOD_ID, value = Dist.CLIENT)
public final class FluidItemTooltips {
    record Preview(MoleculeStructures.Model model, boolean solute) implements TooltipComponent { }

    private FluidItemTooltips() { }

    @SubscribeEvent
    public static void formulas(ItemTooltipEvent event) {
        var lines = FluidItemChemistry.formulaLines(event.getItemStack());
        if (lines.isEmpty()) {
            var model = MoleculeStructures.forItem(event.getItemStack());
            if (model != null) lines = List.of(Component.translatable(ChemicalReadout.KEY + "item_formula", model.formula()).withStyle(ChatFormatting.AQUA));
        }
        if (!lines.isEmpty()) event.getToolTip().addAll(Math.min(1, event.getToolTip().size()), lines);
    }

    @SubscribeEvent
    public static void diagram(RenderTooltipEvent.GatherComponents event) {
        var contents = FluidItemChemistry.contents(event.getItemStack());
        TooltipComponent preview = null;
        if (contents.size() == 1) preview = fluidDiagram(contents.getFirst());
        else if (contents.isEmpty()) {
            var model = MoleculeStructures.forItem(event.getItemStack());
            if (model != null) preview = new Preview(model, false);
        }
        if (preview != null) event.getTooltipElements().add(Either.right(preview));
    }

    public static List<Component> describeFluid(FluidStack stack) {
        var fluid = FluidItemChemistry.normalize(stack);
        var lines = new ArrayList<>(FluidItemChemistry.formulaLines(fluid));
        if (fluid.isEmpty()) return lines;
        if (MixtureHelper.isMixture(fluid)) {
            try { lines.addAll(MixtureGoggleHelper.describe(fluid)); }
            catch (IllegalArgumentException ignored) { }
            return lines;
        }
        var info = FluidChemicalRegistry.base(fluid.getFluid());
        if (info != null && info.aqueous()) lines.add(Component.translatable(ChemicalReadout.KEY + "composition", info.formula()).withStyle(ChatFormatting.GRAY));
        if (info != null && !info.compositionKey().isEmpty())
            lines.add(Component.translatable(ChemicalReadout.KEY + "composition." + info.compositionKey()).withStyle(ChatFormatting.GRAY));
        var model = info != null && !info.aqueous() ? MoleculeStructures.forFluid(fluid) : null;
        if (model != null) {
            var counts = new TreeMap<String, Integer>();
            for (var atom : model.spatial().atoms()) counts.merge(atom.element(), 1, Integer::sum);
            var elements = counts.entrySet().stream().map(entry -> entry.getKey() + " ×" + entry.getValue()).toList();
            lines.add(Component.translatable(ChemicalReadout.KEY + "composition", String.join(" · ", elements)).withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    public static TooltipComponent fluidDiagram(FluidStack stack) {
        var fluid = FluidItemChemistry.normalize(stack);
        var model = MoleculeStructures.forFluid(fluid);
        if (model == null) return null;
        return new Preview(model, FluidChemicalRegistry.base(fluid.getFluid()).aqueous());
    }

    @EventBusSubscriber(modid = CreatePlant.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Factories {
        private Factories() { }

        @SubscribeEvent
        public static void register(RegisterClientTooltipComponentFactoriesEvent event) {
            event.register(Preview.class, preview -> new MoleculeTooltip(preview));
        }
    }
}
