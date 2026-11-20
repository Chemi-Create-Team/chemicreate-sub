package net.createplant.client;

import java.util.Set;
import net.createplant.CreatePlant;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = CreatePlant.MOD_ID, value = Dist.CLIENT)
public final class SynthesisGuide {
    private static final String KEY = "synthesis." + CreatePlant.MOD_ID + ".guide.";
    private static final Set<String> STREAMS = Set.of("water_gas", "caustic_soda_solution", "sulfur_scrubber_liquor",
        "purified_syngas", "carbon_monoxide", "hydrogen_gas", "pressurised_carbon_monoxide", "pressurised_hydrogen",
        "compressed_hydrogen", "carbon_dioxide", "synthetic_crude_oil", "ft_offgas", "synthetic_naphtha",
        "low_sulfur_diesel", "molten_paraffin", "btx", "methanol", "ethylene", "propylene",
        "molten_polyethylene", "molten_polypropylene", "warm_water", "lubricating_oil",
        "benzene", "toluene", "xylene", "ethylbenzene", "styrene", "butadiene", "cumene", "cumene_process_liquor",
        "phenol", "acetone", "formaldehyde_solution", "chlorobenzene", "chlorotoluene", "hexane", "recovered_solvent",
        "liquid_fertilizer", "ethylene_dichloride", "vinyl_chloride", "chlorine", "hydrogen_chloride", "heavy_oil");
    private static final Set<String> ITEMS = Set.of("shift_catalyst_core", "iron_ft_catalyst_core", "cobalt_ft_catalyst_core",
        "zsm5_catalyst_core", "industrial_resin", "paraffin_wax", "polyethylene", "polypropylene",
        "platinum_zeolite_core", "cumene_catalyst_core", "polymerization_catalyst", "phenolic_resin", "phenolic_board",
        "molded_polymer", "pvc_resin", "synthetic_rubber", "phosphate_compound", "flame_retardant_pellet",
        "heat_resistant_coating", "fire_resistant_conductor", "fire_resistant_cable");

    private SynthesisGuide() { }

    public static boolean linked(Fluid fluid) {
        var id = BuiltInRegistries.FLUID.getKey(fluid);
        return id.getNamespace().equals(CreatePlant.MOD_ID) && STREAMS.contains(id.getPath());
    }

    @SubscribeEvent
    public static void itemTooltip(ItemTooltipEvent event) {
        var item = event.getItemStack().getItem();
        if (item instanceof BucketItem bucket) {
            var hint = fluidTooltip(bucket.content);
            if (hint != null) event.getToolTip().add(hint);
            return;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id.getNamespace().equals(CreatePlant.MOD_ID) && ITEMS.contains(id.getPath()))
            event.getToolTip().add(hint(id.getPath()));
    }

    public static Component fluidTooltip(Fluid fluid) {
        return linked(fluid) ? hint(BuiltInRegistries.FLUID.getKey(fluid).getPath()) : null;
    }

    private static Component hint(String path) {
        var text = Screen.hasShiftDown() ? Component.translatable(KEY + path) : Component.translatable(KEY + "shift");
        return text.withStyle(ChatFormatting.GRAY);
    }
}
