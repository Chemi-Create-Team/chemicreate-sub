package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.createplant.block.entity.ReactorConsoleDisplayBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/** A formed display board shows the readings of the desk it is linked to; an unlinked board shows only its static panels. */
public class ReactorConsoleDisplayRenderer extends SafeBlockEntityRenderer<ReactorConsoleDisplayBlockEntity> {
    public ReactorConsoleDisplayRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    protected void renderSafe(ReactorConsoleDisplayBlockEntity board, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!board.isMaster())
            return;
        var desk = board.linkedConsole();
        if (desk != null && desk.walled())
            ReactorControlConsoleRenderer.drawBoard(desk, board, pose, buffers, light);
    }
    @Override
    public boolean shouldRenderOffScreen(ReactorConsoleDisplayBlockEntity board) {
        return board.isMaster();
    }
    @Override
    public int getViewDistance() {
        return 64;
    }
}
