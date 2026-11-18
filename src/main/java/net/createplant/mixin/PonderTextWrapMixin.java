package net.createplant.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.TextWindowElement;
import net.createplant.CreatePlant;
import net.createplant.client.JapaneseLineBreak;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Ponder lays out a caption with Minecraft's splitter, which breaks only at ASCII spaces; in this mod's Japanese captions those stand before units.
 * The caption's lines and the height of its window come from the Japanese line breaker instead; other text and other mods' scenes are left alone.
 */
@Mixin(value = TextWindowElement.class, remap = false)
public class PonderTextWrapMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/StringSplitter;splitLines(Ljava/lang/String;ILnet/minecraft/network/chat/Style;)Ljava/util/List;"))
    private List<FormattedText> createPlant$japaneseLines(StringSplitter splitter, String text, int width, Style style, Operation<List<FormattedText>> original,
            @Local(argsOnly = true) PonderScene scene) {
        return japanese(scene, text) ? JapaneseLineBreak.split(splitter, text, width) : original.call(splitter, text, width, style);
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;wordWrapHeight(Ljava/lang/String;I)I"))
    private int createPlant$japaneseHeight(Font font, String text, int width, Operation<Integer> original, @Local(argsOnly = true) PonderScene scene) {
        return japanese(scene, text) ? JapaneseLineBreak.split(font.getSplitter(), text, width).size() * font.lineHeight : original.call(font, text, width);
    }

    private static boolean japanese(PonderScene scene, String text) {
        return CreatePlant.MOD_ID.equals(scene.getNamespace()) && JapaneseLineBreak.applies(text);
    }
}
