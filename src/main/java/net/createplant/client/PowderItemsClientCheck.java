package net.createplant.client;

import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.createplant.CreatePlant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Items;

/** Opt-in developer fixture using the vanilla chest screen to draw actual baked item models. */
final class PowderItemsClientCheck {
    private static final List<String> POWDERS = List.of(
        "alumina", "ammonium_nitrate", "ammonium_sulfate", "anfo",
        "borax", "carbon_powder", "cement_powder", "chromium_oxide", "depleted_uranium_dioxide",
        "enriched_uranium_dioxide", "ground_clinker", "gypsum",
        "iron_oxide_red", "molybdenum_blue", "molybdenum_trioxide", "mox_powder",
        "nickel_oxide", "nickel_titanium_yellow", "plutonium_dioxide", "pvc_resin",
        "salt", "sulfur_dust", "titanium_dioxide",
        "treatment_sludge", "uranium_dioxide", "uranium_tetrafluoride",
        "uranium_trioxide", "water_treatment_flocculant", "yellowcake", "zirconia", "compact_uranium_dioxide",
        "zirconium_sulfate_hydrate", "zirconyl_sulfate", "titanyl_sulfate_hydrate", "aluminium_sulfate_hydrate", "iron_sulfate_hydrate", "copper_sulfate_crystals", "zinc_sulfate_hydrate", "tin_sulfate_crystals", "lead_chloride_crystals", "uranyl_nitrate_hydrate", "plutonium_nitrate_crystals", "sodium_aluminate_crystals", "caustic_soda_flakes", "ammonium_chloride_crystals");
    private static ContainerScreen screen;
    private static int ticks;

    private PowderItemsClientCheck() { }

    static void start(Minecraft mc) {
        if (mc.player == null) throw new IllegalStateException("The powder fixture requires a disposable client world");
        var items = new SimpleContainer(54);
        items.setItem(0, Items.GUNPOWDER.getDefaultInstance());
        for (int i = 0; i < POWDERS.size(); i++) {
            var id = CreatePlant.location(POWDERS.get(i));
            if (!BuiltInRegistries.ITEM.containsKey(id)) throw new IllegalStateException("Missing registered powder: " + id);
            items.setItem(9 + i, BuiltInRegistries.ITEM.get(id).getDefaultInstance());
        }
        var menu = ChestMenu.sixRows(123, mc.player.getInventory(), items);
        mc.player.containerMenu = menu;
        screen = new ContainerScreen(menu, mc.player.getInventory(), Component.literal("Powders - gunpowder first"));
        mc.setScreen(screen);
        ticks = 0;
    }

    static boolean tick(Minecraft mc) throws Exception {
        if (mc.screen != screen) throw new IllegalStateException("The vanilla powder item fixture was closed before capture");
        if (++ticks < 50) return false;
        var output = mc.gameDirectory.toPath();
        Files.createDirectories(output.resolve("screenshots"));
        Screenshot.grab(mc.gameDirectory, "quality-powder-items.png", mc.getMainRenderTarget(), message -> { });
        Map<Integer, String> slots = new LinkedHashMap<>();
        slots.put(0, "minecraft:gunpowder");
        for (int i = 0; i < POWDERS.size(); i++) slots.put(9 + i, CreatePlant.MOD_ID + ":" + POWDERS.get(i));
        Files.writeString(output.resolve("powder-items-client-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(
            Map.of("render", "vanilla_chest_actual_item_models", "screenshot", "screenshots/quality-powder-items.png", "slots", slots)));
        mc.player.closeContainer();
        mc.setScreen(null);
        screen = null;
        return true;
    }
}
