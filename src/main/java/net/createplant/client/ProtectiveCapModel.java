package net.createplant.client;

import net.createplant.registry.CENuclear;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/** Helmets are drawn a pixel or more off the head; the cloth cap is drawn only on the hat layer, just over a skin's own hat. */
public final class ProtectiveCapModel {
    private static final float CLOTH_OFFSET = 0.1f;
    private static HumanoidModel<LivingEntity> model;

    private ProtectiveCapModel() { }

    public static void register(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity wearer, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
                if (model == null) model = new HumanoidModel<>(LayerDefinition.create(HumanoidModel.createMesh(new CubeDeformation(CLOTH_OFFSET), 0), 64, 32).bakeRoot());
                return model;
            }
        }, CENuclear.PROTECTIVE_CAP.get());
    }
}
