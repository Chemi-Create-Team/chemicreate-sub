package net.createplant.logistics.client;

import com.simibubi.create.AllPartialModels;
import com.simibubi.create.AllSpriteShifts;
import com.simibubi.create.content.kinetics.transmission.SplitShaftBlockEntity;
import com.simibubi.create.content.kinetics.transmission.SplitShaftVisual;
import com.simibubi.create.content.processing.burner.ScrollInstance;
import com.simibubi.create.foundation.render.AllInstanceTypes;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import dev.engine_room.flywheel.lib.visual.util.InstanceRecycler;
import dev.engine_room.flywheel.lib.visual.util.SmartRecycler;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Objects;
import net.createplant.block.CraneStructure;
import net.createplant.logistics.block.entity.QuarryConveyor;
import net.createplant.logistics.block.entity.QuarryDriveBlockEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Draws a gantry quarry's shaft and gantry as Flywheel instances. The instances are placed again only when the gantry moves, the
 * frame changes or the light around it changes, so a standing quarry costs no vertex work per frame. {@link QuarryRenderer} still
 * draws the parcels, and the whole gantry where no backend runs.
 */
public class QuarryVisual extends SplitShaftVisual implements SimpleDynamicVisual {
    /** Movement below this many blocks is not worth placing the instances again while the drawn position eases to rest. */
    private static final double STILL = 1 / 1024d;
    private final QuarryDriveBlockEntity quarry;
    private final SmartRecycler<PartialModel, TransformedInstance> parts = new SmartRecycler<>(model ->
        instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(model)).createInstance());
    private final InstanceRecycler<ScrollInstance> belts = new InstanceRecycler<>(() ->
        instancerProvider().instancer(AllInstanceTypes.SCROLLING, Models.partial(QuarryRenderer.BRIDGE_BELT)).createInstance());
    private final BlockPos.MutableBlockPos lit = new BlockPos.MutableBlockPos();
    private final Quaternionf turn = new Quaternionf();
    private CraneStructure.Result shown;
    private double u, v, depth;
    private boolean drill, running;
    private TransformedInstance head;
    private Vec3 hook;
    private float spin;
    private long litAt = Long.MAX_VALUE;
    private int light, trackedBottom = Integer.MAX_VALUE;
    // Light changes arrive on another worker than the frame update.
    private volatile boolean relight = true;

    public QuarryVisual(VisualizationContext context, SplitShaftBlockEntity blockEntity, float partialTick) {
        super(context, blockEntity, partialTick);
        quarry = (QuarryDriveBlockEntity) blockEntity;
    }

    @Override public void beginFrame(DynamicVisual.Context context) {
        float pt = context.partialTick();
        var structure = quarry.visibleStructure();
        double u = quarry.renderedU(pt), v = quarry.renderedV(pt), depth = quarry.renderedDepth(pt);
        boolean drill = quarry.drill(), running = quarry.conveyorRunning(), reshaped = !Objects.equals(structure, shown);
        if (relight || reshaped || drill != this.drill || running != this.running
            || Math.abs(u - this.u) > STILL || Math.abs(v - this.v) > STILL || Math.abs(depth - this.depth) > STILL) {
            relight = false;
            shown = structure;this.u = u;this.v = v;this.depth = depth;this.drill = drill;this.running = running;
            place(pt, reshaped);
        }
        if (head != null && QuarryRenderer.headSpin(quarry) != spin) turnHead(QuarryRenderer.headSpin(quarry));
    }

    private void place(float pt, boolean reshaped) {
        parts.resetCount();
        belts.resetCount();
        head = null;
        litAt = Long.MAX_VALUE;
        if (shown != null) {
            var gantry = QuarryRenderer.Gantry.of(shown, pos, u, v, depth);
            QuarryRenderer.parts(shown, gantry, depth, drill, this::part);
            var shift = AllSpriteShifts.BELT;
            int direction = QuarryRenderer.beltDirection(shown);
            turn.rotationY(gantry.yaw() * Mth.DEG_TO_RAD);
            for (int i = -1; i <= shown.span(); i++) {
                Vec3 at = gantry.origin().add(gantry.across().scale(i));
                // A running belt scrolls on the GPU at the renderer's rate; a stopped one keeps the place it reached.
                belts.get().setSpriteShift(shift, 1, .5f).rotation(turn)
                    .position((float) (visualPos.getX() + at.x), (float) (visualPos.getY() + at.y), (float) (visualPos.getZ() + at.z))
                    .speed(0, running ? (float) (QuarryConveyor.SPEED * QuarryDriveBlockEntity.WORK_STEPS) * direction : 0)
                    .offset(0, running ? 0 : (float) (quarry.renderedBeltTravel(pt) % 1) * direction)
                    .light(light(at)).setChanged();
            }
            hook = gantry.hook();
            if (drill) {
                head = parts.get(AllPartialModels.DRILL_HEAD);
                head.light(light(hook));
                turnHead(QuarryRenderer.headSpin(quarry));
            }
            int bottom = SectionPos.blockToSectionCoord(pos.getY() + Mth.floor(hook.y));
            if (lightSections != null && (reshaped || bottom != trackedBottom)) track(bottom);
        }
        parts.discardExtra();
        belts.discardExtra();
    }

    private void part(PartialModel model, Vec3 at, float yaw) {
        parts.get(model).setIdentityTransform().translate(visualPos).translate(at).center().rotateYDegrees(yaw).uncenter().light(light(at)).setChanged();
    }

    /** The drill model points north; the head hangs point down and spins about the stem. */
    private void turnHead(float spin) {
        this.spin = spin;
        head.setIdentityTransform().translate(visualPos).translate(hook).center().rotateYDegrees(spin).rotateXDegrees(90).uncenter().setChanged();
    }

    private int light(Vec3 at) {
        lit.setWithOffset(pos, Mth.floor(at.x), Mth.floor(at.y), Mth.floor(at.z));
        if (lit.asLong() != litAt) {
            litAt = lit.asLong();
            light = LevelRenderer.getLightColor(level, lit);
        }
        return light;
    }

    /** Follows the light of every section the gantry can stand in, from the rails down to the drill head. */
    private void track(int bottom) {
        trackedBottom = bottom;
        var box = quarry.getRenderBoundingBox();
        var sections = new LongOpenHashSet();
        for (int x = SectionPos.blockToSectionCoord(box.minX); x <= SectionPos.blockToSectionCoord(box.maxX); x++)
            for (int z = SectionPos.blockToSectionCoord(box.minZ); z <= SectionPos.blockToSectionCoord(box.maxZ); z++)
                for (int y = bottom; y <= SectionPos.blockToSectionCoord(box.maxY); y++) sections.add(SectionPos.asLong(x, y, z));
        lightSections.sections(sections);
    }

    @Override public void updateLight(float partialTick) {
        super.updateLight(partialTick);
        relight = true;
    }

    @Override protected void _delete() {
        super._delete();
        parts.delete();
        belts.delete();
    }
}
