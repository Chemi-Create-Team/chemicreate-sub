package net.createplant.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.FormattedText;

/**
 * Line breaking for Japanese captions. Minecraft breaks a line only at an ASCII space, and in Japanese the only spaces sit between a number and
 * its unit, so a caption broke there with half its line empty. Here a line may break between any two Japanese characters, but not inside a run
 * of Latin letters and digits such as "160 Ω", not between a number and the counter after it ("2秒"), not before closing punctuation or small kana,
 * and not after an opening bracket.
 */
public final class JapaneseLineBreak {
    private static final String NO_START = "、。，．・：；？！ー）」』】〕〉》ぁぃぅぇぉっゃゅょゎァィゥェォッャュョヮヵヶ％";
    private static final String NO_END = "（「『【〔〈《";
    private static String lastText;
    private static int lastWidth;
    private static List<FormattedText> lastLines;

    private JapaneseLineBreak() { }

    public static boolean applies(String text) {
        return text.codePoints().anyMatch(JapaneseLineBreak::japanese);
    }

    /** The caption's lines; Ponder asks every frame, so the last answer is kept. */
    public static List<FormattedText> split(StringSplitter splitter, String text, int width) {
        if (text.equals(lastText) && width == lastWidth) return lastLines;
        List<FormattedText> lines = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            float used = 0;
            for (String token : tokens(paragraph)) {
                float size = splitter.stringWidth(token);
                if (used + size > width && !line.isEmpty()) {
                    used = newLine(lines, line);
                    token = token.stripLeading();
                    size = splitter.stringWidth(token);
                }
                if (size <= width) {
                    line.append(token);
                    used += size;
                    continue;
                }
                // A token wider than the whole line, such as a long Latin word, is cut where the line is full.
                for (char c : token.toCharArray()) {
                    float glyph = splitter.stringWidth(String.valueOf(c));
                    if (used + glyph > width && !line.isEmpty()) used = newLine(lines, line);
                    line.append(c);
                    used += glyph;
                }
            }
            lines.add(FormattedText.of(line.toString()));
        }
        lastText = text;
        lastWidth = width;
        lastLines = lines;
        return lines;
    }

    private static float newLine(List<FormattedText> lines, StringBuilder line) {
        lines.add(FormattedText.of(line.toString()));
        line.setLength(0);
        return 0;
    }

    /** Each Japanese character on its own, other runs whole, with closing marks joined to what they follow and opening marks to what they lead. */
    private static List<String> tokens(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder run = new StringBuilder();
        boolean open = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean japanese = japanese(c);
            if (japanese && !run.isEmpty()) {
                open = add(tokens, run.toString(), open);
                run.setLength(0);
            }
            if (japanese) open = add(tokens, String.valueOf(c), open);
            else run.append(c);
        }
        if (!run.isEmpty()) add(tokens, run.toString(), open);
        return tokens;
    }

    private static boolean add(List<String> tokens, String token, boolean open) {
        boolean join = !tokens.isEmpty() && (open || NO_START.indexOf(token.charAt(0)) >= 0 || Character.isDigit(tokens.getLast().charAt(tokens.getLast().length() - 1)));
        if (join) tokens.set(tokens.size() - 1, tokens.getLast() + token);
        else tokens.add(token);
        return NO_END.indexOf(token.charAt(token.length() - 1)) >= 0;
    }

    private static boolean japanese(int c) {
        Character.UnicodeScript script = Character.UnicodeScript.of(c);
        return script == Character.UnicodeScript.HIRAGANA || script == Character.UnicodeScript.KATAKANA || script == Character.UnicodeScript.HAN
            || c >= 0x3000 && c <= 0x303F || c >= 0xFF00 && c <= 0xFFEF;
    }
}
