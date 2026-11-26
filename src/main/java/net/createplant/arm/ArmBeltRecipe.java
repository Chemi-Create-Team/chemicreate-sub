package net.createplant.arm;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** One item on a mechanical belt, held still while the arm works it with {@code tool}, becomes {@code result}. */
public record ArmBeltRecipe(ResourceLocation id, ArmTool tool, Item input, Item result, int count, int time) {
    public ItemStack resultStack() {
        return new ItemStack(result, count);
    }

    public static ArmBeltRecipe fromJson(ResourceLocation id, JsonObject json) {
        ArmTool tool = ArmTool.byId(GsonHelper.getAsString(json, "tool"));
        if (tool != ArmTool.WELDING_TORCH) throw new IllegalArgumentException("belt work needs the welding torch");
        JsonObject result = GsonHelper.getAsJsonObject(json, "result");
        return new ArmBeltRecipe(id, tool, item(GsonHelper.getAsString(json, "input")), item(GsonHelper.getAsString(result, "id")),
            Math.max(1, GsonHelper.getAsInt(result, "count", 1)), Math.max(1, GsonHelper.getAsInt(json, "time", 40)));
    }

    private static Item item(String id) {
        ResourceLocation rl = ResourceLocation.parse(id);
        if (!BuiltInRegistries.ITEM.containsKey(rl)) throw new IllegalArgumentException("unknown item " + id);
        return BuiltInRegistries.ITEM.get(rl);
    }
}
