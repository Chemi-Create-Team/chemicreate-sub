package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.createplant.CreatePlant;
import net.createplant.block.TransmissionTowerBlock;
import net.createplant.block.entity.TransmissionTowerBlockEntity;
import net.createplant.registry.CETransmission;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

@EventBusSubscriber(modid = CreatePlant.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class TransmissionTowerRenderer implements BlockEntityRenderer<TransmissionTowerBlockEntity> {
    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(CETransmission.TOWER_ENTITY.get(), context -> new TransmissionTowerRenderer());
    }

    @Override public void render(TransmissionTowerBlockEntity tower, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int light, int overlay) {
        var state = tower.getBlockState();
        var dispatcher = Minecraft.getInstance().getBlockRenderer();
        var renderType = RenderType.solid();
        int towerLight = tower.getLevel() == null ? light : LevelRenderer.getLightColor(tower.getLevel(), tower.getBlockPos().above());
        pose.pushPose();
        int step = state.getValue(TransmissionTowerBlock.ROTATION_STEP);
        if (step != 0) {
            pose.translate(.5, 0, .5);
            pose.mulPose(Axis.YN.rotationDegrees(step * TransmissionTowerBlock.DEGREES_PER_STEP));
            pose.translate(-.5, 0, -.5);
        }
        dispatcher.getModelRenderer().renderModel(pose.last(), buffers.getBuffer(renderType), state, dispatcher.getBlockModel(state),
            1, 1, 1, towerLight, overlay, ModelData.EMPTY, renderType);
        pose.popPose();
    }
    @Override public boolean shouldRenderOffScreen(TransmissionTowerBlockEntity tower) { return true; }
    @Override public int getViewDistance() { return 192; }
    @Override public AABB getRenderBoundingBox(TransmissionTowerBlockEntity tower) {
        return new AABB(tower.getBlockPos()).inflate(4.1, 0, 4.1).expandTowards(0, TransmissionTowerBlock.HEIGHT, 0);
    }
}
