package net.createplant.mixture;

import java.util.*;
import net.minecraft.resources.ResourceLocation;

public final class MixtureContentsTest {
    public static void main(String[] args) {
        var a = ResourceLocation.parse("test:a");var b = ResourceLocation.parse("test:b");var c = ResourceLocation.parse("other:c");
        var random = new Random(7112026);int checks = 0;
        for (int i = 0; i < 2000; i++) {
            var original = new MixtureContents(Map.of(a, random.nextInt(10000) + 1, b, random.nextInt(10000) + 1, c, random.nextInt(10000) + 1));
            var split = original.split(random.nextInt(original.totalAmount() + 1));
            require(split.extracted().merge(split.remaining()).equals(original), "Component conservation");
            require(split.extracted().totalAmount() + split.remaining().totalAmount() == original.totalAmount(), "Volume conservation");
            var reversed = new LinkedHashMap<ResourceLocation, Integer>();reversed.put(c, original.amounts().get(c));reversed.put(b, original.amounts().get(b));reversed.put(a, original.amounts().get(a));
            require(new MixtureContents(reversed).split(split.extracted().totalAmount()).equals(split), "Deterministic ties independent of insertion order");checks += 3;
        }
        var large = new MixtureContents(Map.of(a, Integer.MAX_VALUE - 1, b, 1));
        require(large.split(Integer.MAX_VALUE - 1).extracted().totalAmount() == Integer.MAX_VALUE - 1, "Multiplication uses long intermediates");
        require(large.split(Integer.MAX_VALUE).remaining().amounts().isEmpty(), "Full drain empties the ledger");
        require(new MixtureContents(Map.of(a, 0, b, -1)).equals(MixtureContents.EMPTY), "Non-positive values normalize away");
        boolean immutable = false;try { large.amounts().clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
        require(immutable, "The map is immutable");checks += 4;
        System.out.println("MixtureContentsTest: " + checks + " checks passed");
    }
    private static void require(boolean result, String message) { if (!result) throw new AssertionError(message); }
}
