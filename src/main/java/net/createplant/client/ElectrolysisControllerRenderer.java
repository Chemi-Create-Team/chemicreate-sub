package net.createplant.client;

import java.util.Locale;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.createplant.CreatePlant;
import net.createplant.block.ElectrolysisControllerBlock;
import net.createplant.block.entity.ElectrolysisControllerBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import static net.createplant.client.ElectrolysisDisplayState.*;

public class ElectrolysisControllerRenderer extends SafeBlockEntityRenderer<ElectrolysisControllerBlockEntity> {
    private static final PartialModel NEEDLE = part("needle"), DIAL = part("dial"), PUSH_BUTTON = part("push_button");
    private static final ResourceLocation READOUT = texture("readout"), GLASS = texture("glass_reflection");
    private static final String CHARACTERS = "0123456789.%VA";
    private static PartialModel part(String name) { return PartialModel.of(CreatePlant.location("block/electrolysis_cell/" + name)); }
    private static ResourceLocation texture(String name) { return CreatePlant.location("textures/block/electrolysis_cell/" + name + ".png"); }
    public static void init() { }
    public ElectrolysisControllerRenderer(BlockEntityRendererProvider.Context context) { }

    @Override protected void renderSafe(ElectrolysisControllerBlockEntity be, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var state = be.getBlockState();int panel = state.getValue(ElectrolysisControllerBlock.PART);
        var owner = be.master();if (owner == null) owner = be;
        var display = ElectrolysisDisplayState.sample(owner, AnimationTickHolder.getRenderTime(be.getLevel()));
        pose.pushPose();pose.translate(.5, 0, .5);
        pose.mulPose(Axis.YP.rotationDegrees(180 - state.getValue(ElectrolysisControllerBlock.FACING).toYRot()));pose.translate(-.5, 0, -.5);
        if (panel == 1) {
            needle(pose, buffers, state, 4.5f, 10.6f, .94f, -50 + 100 * display.value(VOLTS), 2.65f, light);
            needle(pose, buffers, state, 11.5f, 10.6f, .94f, -50 + 100 * display.value(AMPS), 2.65f, light);
            for (int n = 0; n < 2; n++) {
                pose.pushPose();at(pose, n == 0 ? 4 : 12, 3.4f, owner.pressed(n == 0) ? .4f : .8f);pose.scale(1.25f,1.25f,1);
                CachedBuffers.partial(PUSH_BUTTON, state).color(n == 0 ? 0x50a066 : 0xba493e).light(light).renderInto(pose, buffers.getBuffer(RenderType.solid()));pose.popPose();
            }
            readout(pose, buffers, String.format(Locale.ROOT, "%.0f", owner.volts()), 4.5f, 8.61f, 4.3f, .93f, .92f, light);
            readout(pose, buffers, String.format(Locale.ROOT, "%.1f", owner.amps()), 11.5f, 8.61f, 4.3f, .93f, .92f, light);
            reflection(pose, buffers, 1.19f, 10.09f, 7.81f, 14.79f, 1.02f, light);
            reflection(pose, buffers, 8.19f, 10.09f, 14.81f, 14.79f, 1.02f, light);
        } else {
            pose.pushPose();at(pose, 8, 7, .5f);pose.mulPose(Axis.ZP.rotationDegrees(-135+270*display.value(LOAD)));
            CachedBuffers.partial(DIAL, state).light(light).renderInto(pose, buffers.getBuffer(RenderType.solid()));pose.popPose();
            readout(pose, buffers, owner.limit()+"%", 8, 1.35f, 5.6f, 1.15f, .1f, light);
        }
        pose.popPose();
    }

    private static void at(PoseStack pose, float x, float y, float proud) { pose.translate((16-x)/16, y/16, -proud/16); }

    private static void needle(PoseStack pose, MultiBufferSource buffers, BlockState state, float x, float y, float proud, float degrees, float length, int light) {
        pose.pushPose();at(pose, x, y, proud);pose.mulPose(Axis.ZP.rotationDegrees(degrees));pose.scale(1, length/4, 1);
        CachedBuffers.partial(NEEDLE, state).light(light).renderInto(pose, buffers.getBuffer(RenderType.solid()));pose.popPose();
    }

    private static void readout(PoseStack pose, MultiBufferSource buffers, String text, float x, float y, float width, float height, float proud, int light) {
        float advance = Math.min(width / Math.max(1, text.length()), height * .54f);
        float start = x - text.length()*advance/2;
        for (int n = 0; n < text.length(); n++) {
            int glyph = CHARACTERS.indexOf(text.charAt(n));if (glyph < 0) continue;
            quad(pose, buffers, RenderType.entityCutoutNoCull(READOUT), start+n*advance, y, start+(n+1)*advance, y+height, proud,
                glyph/16f, 0, (glyph+1)/16f, 1, 0xffffffff, light);
        }
    }

    private static void reflection(PoseStack pose, MultiBufferSource buffers, float x0, float y0, float x1, float y1, float proud, int light) {
        float tilt = Math.min(1, Math.abs(pose.last().normal().m20()));
        int alpha = 95 + (int) (tilt*90);
        quad(pose, buffers, RenderType.entityTranslucent(GLASS), x0, y0, x1, y1, proud,
            .08f+.04f*tilt,.03f,.92f+.04f*tilt,.97f,(alpha<<24)|0xf4f3e8,light);
    }

    private static void quad(PoseStack pose, MultiBufferSource buffers, RenderType type, float x0, float y0, float x1, float y1, float proud,
        float u0, float v0, float u1, float v1, int colour, int light) {
        var vertices = buffers.getBuffer(type);var frame=pose.last();
        vertices.addVertex(frame,(16-x0)/16,y0/16,-proud/16).setColor(colour).setUv(u0,v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(frame,0,0,-1);
        vertices.addVertex(frame,(16-x1)/16,y0/16,-proud/16).setColor(colour).setUv(u1,v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(frame,0,0,-1);
        vertices.addVertex(frame,(16-x1)/16,y1/16,-proud/16).setColor(colour).setUv(u1,v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(frame,0,0,-1);
        vertices.addVertex(frame,(16-x0)/16,y1/16,-proud/16).setColor(colour).setUv(u0,v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(frame,0,0,-1);
    }

    @Override public AABB getRenderBoundingBox(ElectrolysisControllerBlockEntity be) { return new AABB(be.getBlockPos()).inflate(.2); }
}
