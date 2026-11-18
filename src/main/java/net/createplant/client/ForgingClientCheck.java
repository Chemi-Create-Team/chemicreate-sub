package net.createplant.client;

import com.google.gson.GsonBuilder;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;

import net.createplant.CreatePlant;
import net.createplant.registry.CEItems;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

import java.nio.file.Files;
import java.util.LinkedHashMap;

/**
 * Opt-in verification of recipe synchronization, native assembly tooltips and the three item
 * models.
 */
final class ForgingClientCheck {
    private static int ticks;
    private static volatile Throwable failure;
    private static final LinkedHashMap<String, Object> result = new LinkedHashMap<>();

    private ForgingClientCheck() {}

    static void start(Minecraft mc) {
        ticks = 0;
        failure = null;
        result.clear();
        mc.setScreen(null);
        mc.options.hideGui = false;
        mc.options.pauseOnLostFocus = false;
        mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        mc.gameRenderer.setRenderHand(true);
        mc.getSingleplayerServer()
                .execute(
                        () -> {
                            try {
                                var level = mc.getSingleplayerServer().overworld();
                                level.setDayTime(6000);
                                for (int x = -5; x <= 5; x++)
                                    for (int z = -5; z <= 5; z++)
                                        level.setBlockAndUpdate(
                                                new BlockPos(x, 80, z),
                                                Blocks.SMOOTH_STONE.defaultBlockState());
                                var player =
                                        mc.getSingleplayerServer()
                                                .getPlayerList()
                                                .getPlayer(mc.player.getUUID());
                                player.setGameMode(GameType.CREATIVE);
                                var hot = CEItems.HEATED_UNFINISHED_STEEL.toStack();
                                hot.set(
                                        AllDataComponents.SEQUENCED_ASSEMBLY,
                                        new SequencedAssemblyRecipe.SequencedAssembly(
                                                CreatePlant.location(
                                                        "sequenced_assembly/forged_steel"),
                                                1,
                                                1f / 9));
                                player.setItemInHand(InteractionHand.MAIN_HAND, hot);
                                player.setItemInHand(
                                        InteractionHand.OFF_HAND, CEItems.STEEL_SLAG.toStack());
                                player.getInventory().setItem(1, CEItems.PIG_IRON.toStack());
                                player.getInventory().setItem(2, CEItems.STEEL_INGOT.toStack());
                                player.connection.teleport(.5, 81, .5, 0, 0);
                            } catch (Throwable error) {
                                failure = error;
                            }
                        });
    }

    static boolean tick(Minecraft mc) throws Exception {
        if (failure != null) throw new IllegalStateException("Forging client fixture", failure);
        if (++ticks == 80) {
            var recipe =
                    (SequencedAssemblyRecipe)
                            mc.level
                                    .getRecipeManager()
                                    .byKey(CreatePlant.location("sequenced_assembly/forged_steel"))
                                    .orElseThrow()
                                    .value();
            if (recipe.getLoops() != 3
                    || recipe.getSequence().size() != 3
                    || recipe.getOutputChance() != 1)
                throw new IllegalStateException(
                        "The assembly must synchronize its complete nine-step recipe");
            for (var step : recipe.getSequence())
                if (step.getAsAssemblyRecipe().getJEISubCategory().get().get().getWidth() <= 0)
                    throw new IllegalStateException("Missing native JEI assembly subcategory");
            for (var item :
                    java.util.List.of(
                            CEItems.PIG_IRON.get(),
                            CEItems.HEATED_UNFINISHED_STEEL.get(),
                            CEItems.STEEL_SLAG.get()))
                if (mc.getItemRenderer().getModel(item.getDefaultInstance(), mc.level, mc.player, 0)
                        == mc.getModelManager().getMissingModel())
                    throw new IllegalStateException("Missing forging item model: " + item);
            var hot = mc.player.getMainHandItem();
            var tooltip =
                    hot.getTooltipLines(
                            net.minecraft.world.item.Item.TooltipContext.of(mc.level),
                            mc.player,
                            net.minecraft.world.item.TooltipFlag.NORMAL);
            String next =
                    net.minecraft.network.chat.Component.translatable(
                                    "recipe.create_refinery_n_reactors.assembly.blasting")
                            .getString();
            if (tooltip.stream().noneMatch(line -> line.getString().contains(next)))
                throw new IllegalStateException(
                        "The native assembly tooltip must describe the heating step");
            if ((mc.getItemColors().getColor(hot, 0) & 0xFFFFFF) != 0xFF7A32)
                throw new IllegalStateException("The red-hot ingot tint must load");
            result.put("loops", 3);
            result.put("operations", 9);
            result.put("guaranteedSteel", true);
            result.put(
                    "slagChance",
                    recipe.getSequence()
                            .get(2)
                            .getRecipe()
                            .getRollableResults()
                            .get(1)
                            .getChance());
            result.put("nativeTooltip", next);
            result.put("itemModelsLoaded", 3);
            result.put("hotTint", "#FF7A32");
            result.put("jeiSubcategoriesLoaded", 3);
        }
        if (ticks < Integer.getInteger("createplant.forgingClientHold", 800)) return false;
        Files.writeString(
                mc.gameDirectory.toPath().resolve("forging-client-result.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(result));
        return true;
    }
}
