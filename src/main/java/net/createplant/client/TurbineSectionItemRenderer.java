package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.createplant.CreatePlant;
import net.createplant.block.TurbineUnit;
import net.createplant.registry.CEItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/** Inventory views use the same closed sections, casing and end faces as the placed turbine. */
@EventBusSubscriber(modid = CreatePlant.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TurbineSectionItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static TurbineSectionItemRenderer renderer;

    private TurbineSectionItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @SubscribeEvent public static void register(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new TurbineSectionItemRenderer();
                return renderer;
            }
        }, CEItems.HIGH_PRESSURE_TURBINE_UNIT.get(), CEItems.LOW_PRESSURE_TURBINE_UNIT.get(),
            CEItems.HIGH_PRESSURE_TURBINE_CASING.get(), CEItems.LOW_PRESSURE_TURBINE_CASING.get(),
            CEItems.TARBIN_END.get(), CEItems.LOW_PRESSURE_TURBINE_END.get());
    }

    @Override public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        boolean low = stack.is(CEItems.LOW_PRESSURE_TURBINE_UNIT.get())
                || stack.is(CEItems.LOW_PRESSURE_TURBINE_CASING.get()) || stack.is(CEItems.LOW_PRESSURE_TURBINE_END.get());
        boolean casing = stack.is(CEItems.HIGH_PRESSURE_TURBINE_CASING.get()) || stack.is(CEItems.LOW_PRESSURE_TURBINE_CASING.get());
        boolean end = stack.is(CEItems.TARBIN_END.get()) || stack.is(CEItems.LOW_PRESSURE_TURBINE_END.get());
        TurbineSectionRenderer.renderItem(low ? TurbineUnit.Stage.LOW : TurbineUnit.Stage.HIGH,
                casing, end, pose, buffers, light);
    }
}
