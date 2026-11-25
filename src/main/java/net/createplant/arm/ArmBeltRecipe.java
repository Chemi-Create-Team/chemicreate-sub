package net.createplant.arm;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** One item on a mechanical belt, held still while the arm works it with {@code tool}, becomes {@code result}. Spray steps use {@code fluid}. */
public record ArmBeltRecipe(ResourceLocation id, ArmTool tool, Item input, Item result, int count, int time, ResourceLocation fluid, int fluidAmount) {
    public ItemStack resultStack() {
        return new ItemStack(result, count);
    }

    public static ArmBeltRecipe fromJson(ResourceLocation id, JsonObject json) {
        ArmTool tool = ArmTool.byId(GsonHelper.getAsString(json, "tool"));
        if (tool == null || tool == ArmTool.GRIPPER) throw new IllegalArgumentException("belt work needs a working tool, not the gripper");
        JsonObject result = GsonHelper.getAsJsonObject(json, "result");
        ResourceLocation fluid = null;
        int amount = 0;
        if (json.has("fluid")) {
            JsonObject f = GsonHelper.getAsJsonObject(json, "fluid");
            fluid = ResourceLocation.parse(GsonHelper.getAsString(f, "id"));
            if (!BuiltInRegistries.FLUID.containsKey(fluid)) throw new IllegalArgumentException("unknown fluid " + fluid);
            amount = GsonHelper.getAsInt(f, "amount");
        }
        if (tool == ArmTool.SPRAY_NOZZLE && fluid == null) throw new IllegalArgumentException("the spray nozzle needs a fluid");
        return new ArmBeltRecipe(id, tool, item(GsonHelper.getAsString(json, "input")), item(GsonHelper.getAsString(result, "id")),
            Math.max(1, GsonHelper.getAsInt(result, "count", 1)), Math.max(1, GsonHelper.getAsInt(json, "time", 40)), fluid, amount);
    }

    private static Item item(String id) {
        ResourceLocation rl = ResourceLocation.parse(id);
        if (!BuiltInRegistries.ITEM.containsKey(rl)) throw new IllegalArgumentException("unknown item " + id);
        return BuiltInRegistries.ITEM.get(rl);
    }
}
