package net.createplant.ponder;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.createplant.CreatePlant;

/** Shared catalogue data; intentionally safe to validate on a dedicated server. */
public final class PlantTutorialCatalog {
    public static final String PREFIX = "ponder." + CreatePlant.MOD_ID + ".guide.";
    public static final String RESOURCE = "/assets/" + CreatePlant.MOD_ID + "/ponder/tutorials.json";
    private static final List<Tutorial> TUTORIALS = read();

    private PlantTutorialCatalog() { }

    public record Process(String kind, String recipe, int offset, int width, int height, int[] machine, int[] input, int[] output, String feed) { }
    /** {@code smoke} is a block whose top lets out flue gas from that step on, as a running heater's chimney; {@code use} is an item shown right-clicked on the target. */
    public record Step(int[] target, String key, String item, int[] reveal, Float speed, String replace, Boolean wrench, Process process, int[] smoke, String use) { }
    /** {@code formed} names the step at which the multiblock the lesson builds completes; before it its parts look as placed. */
    public record Tutorial(String id, List<String> components, List<Step> steps, Integer size, Float scale, Integer height, String formed, String family) {
        public String translation(String suffix) { return PREFIX + id + "." + suffix; }
        public String storyboard() { return "plant/" + id; }
    }

    public static List<Tutorial> all() { return TUTORIALS; }

    private static List<Tutorial> read() {
        try (var stream = PlantTutorialCatalog.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IllegalStateException("Missing plant Ponder catalogue");
            var tutorials = new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Tutorial[].class);
            return List.of(tutorials);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Cannot read plant Ponder catalogue", exception);
        }
    }
}
