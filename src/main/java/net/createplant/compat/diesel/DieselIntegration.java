package net.createplant.compat.diesel;

import com.google.gson.JsonElement;
import net.createplant.CreatePlant;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

@EventBusSubscriber(modid=CreatePlant.MOD_ID,bus=EventBusSubscriber.Bus.MOD)
public final class DieselIntegration {
    private DieselIntegration() { }
    public static boolean disabledItem(ItemStack stack) {
        return disabledId(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }
    private static boolean disabledId(String id) {
        return id.equals("createdieselgenerators:distillation_controller") || id.equals("createdieselgenerators:distillation_tank");
    }
    public static boolean disabledRecipe(JsonElement json) {
        if (!json.isJsonObject()) return false;
        var recipe=json.getAsJsonObject();
        if (recipe.has("type") && recipe.get("type").isJsonPrimitive()
                && recipe.get("type").getAsString().equals("createdieselgenerators:distillation")) return true;
        JsonElement result=recipe.get("result");
        if (result==null) return false;
        if (result.isJsonPrimitive()) return disabledId(result.getAsString());
        if (!result.isJsonObject()) return false;
        var output=result.getAsJsonObject();
        var id=output.has("id") ? output.get("id") : output.get("item");
        return id!=null && id.isJsonPrimitive() && disabledId(id.getAsString());
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void creativeContents(BuildCreativeModeTabContentsEvent event) {
        event.remove(new ItemStack(com.jesz.createdieselgenerators.CDGItems.DISTILLATION_CONTROLLER.get()),net.minecraft.world.item.CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
    }
}
