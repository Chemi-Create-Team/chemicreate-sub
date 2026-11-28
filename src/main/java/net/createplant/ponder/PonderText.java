package net.createplant.ponder;

import net.createplant.CreatePlant;
import net.createplant.ResourceStack;
import net.createmod.ponder.api.registration.SharedTextRegistrationHelper;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;

/** Ponder uses namespace-first keys and ignores .text() fallbacks outside editing mode. */
public final class PonderText {
    private static final String AUTHOR_PREFIX = "ponder." + CreatePlant.MOD_ID + ".";
    public static final String SHARED_PREFIX = CreatePlant.MOD_ID + ".ponder.shared.";
    private PonderText() { }

    public static ResourceLocation key(String authorKey) {
        if (!authorKey.startsWith(AUTHOR_PREFIX)) throw new IllegalArgumentException(authorKey);
        return CreatePlant.location(authorKey.substring(AUTHOR_PREFIX.length()));
    }

    public static String title(String authorKey, String fallback) {
        String sharedKey = SHARED_PREFIX + key(authorKey).getPath();
        return I18n.exists(sharedKey) ? I18n.get(sharedKey) : fallback;
    }

    public static void caption(SceneBuilder scene, Vec3 target, String authorKey, int ticks, Object... arguments) {
        String shared = SHARED_PREFIX + key(authorKey).getPath();
        int count = I18n.exists(shared + ".lesson_pages.count") ? Integer.parseInt(I18n.get(shared + ".lesson_pages.count")) : 1;
        for (int page = 0; page < count; page++) {
            String selected = I18n.exists(shared + ".lesson_pages.count") ? authorKey + ".lesson_pages." + page : authorKey;
            String text = I18n.get(SHARED_PREFIX + key(selected).getPath(), arguments);
            int readingTime = 35 + text.codePoints().map(point -> point > 255 ? 2 : 1).sum();
            int duration = Math.max(Math.max(70, ticks / count), Math.min(220, readingTime));
            var window = scene.overlay().showText(duration).sharedText(key(selected), arguments);
            if (target == null) window.independent(40); else window.pointAt(target);
            window.placeNearTarget().attachKeyFrame();
            scene.idle(duration + 12);
        }
    }

    public static void build(SceneBuilder scene, SceneBuildingUtil util, int x0, int y0, int z0, int x1, int y1, int z1, Direction direction) {
        for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
            scene.world().showSection(util.select().fromTo(x0, y, z0, x1, y, z1), direction);
            scene.idle(4);
        }
    }

    public static void register(SharedTextRegistrationHelper helper) {
        ResourceStack.object(PonderText.class, "/assets/" + CreatePlant.MOD_ID + "/lang/en_us.json").entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(SHARED_PREFIX))
            .forEach(entry -> helper.registerSharedText(entry.getKey().substring(SHARED_PREFIX.length()), entry.getValue().getAsString()));
    }
}
