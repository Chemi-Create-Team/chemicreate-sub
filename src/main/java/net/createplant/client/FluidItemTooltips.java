package net.createplant.client;

import com.mojang.datafixers.util.Either;
import net.createplant.CreatePlant;
import net.createplant.chemistry.FluidChemicalRegistry;
import net.createplant.chemistry.FluidItemChemistry;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = CreatePlant.MOD_ID, value = Dist.CLIENT)
public final class FluidItemTooltips {
    record Preview(MoleculeStructures.Model model, boolean component) implements TooltipComponent { }

    private FluidItemTooltips() { }

    @SubscribeEvent
    public static void formulas(ItemTooltipEvent event) {
        var lines = FluidItemChemistry.formulaLines(event.getItemStack());
        if (!lines.isEmpty()) event.getToolTip().addAll(Math.min(1, event.getToolTip().size()), lines);
    }

    @SubscribeEvent
    public static void diagram(RenderTooltipEvent.GatherComponents event) {
        var contents = FluidItemChemistry.contents(event.getItemStack());
        for (var fluid : contents) {
            var model = MoleculeStructures.forFluid(fluid);
            if (model != null) {
                var info = FluidChemicalRegistry.base(fluid.getFluid());
                boolean component = contents.size() > 1 || info != null && !model.formula().equals(info.formula());
                event.getTooltipElements().add(Either.right(new Preview(model, component)));
                return;
            }
        }
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
