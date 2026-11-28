package net.createplant;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Reads a resource that core and each addon jar ship their own part of at the same path. */
public final class ResourceStack {
    private ResourceStack() { }

    /** The JSON objects of every jar at this path merged into one. */
    public static JsonObject object(Class<?> owner, String path) {
        var merged = new JsonObject();
        for (var text : read(owner, path)) merged.asMap().putAll(JsonParser.parseString(text).getAsJsonObject().asMap());
        return merged;
    }

    /** Like {@code Class.getResourceAsStream}, but also finds resources inside the addon jars; null when absent. */
    public static InputStream open(Class<?> owner, String path) {
        return owner.getClassLoader().getResourceAsStream(path.substring(1));
    }

    public static boolean exists(Class<?> owner, String path) {
        return owner.getClassLoader().getResource(path.substring(1)) != null;
    }

    public static List<String> read(Class<?> owner, String path) {
        var urls = new LinkedHashSet<URL>();
        try {
            owner.getClassLoader().getResources(path.substring(1)).asIterator().forEachRemaining(urls::add);
            var texts = new ArrayList<String>();
            for (var url : urls) {
                try (var stream = url.openStream()) {
                    texts.add(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
            if (texts.isEmpty()) throw new IllegalStateException("Missing " + path);
            return texts;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + path, e);
        }
    }
}
