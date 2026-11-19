package net.createplant.safety;

import net.createplant.CreatePlant;
import net.createplant.registry.CENuclear;
import net.createplant.transport.FluidHazards;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

public final class CorrosiveContact {
    public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, CreatePlant.location("corrosive_contact"));
    private static final String LAST_HIT = CreatePlant.MOD_ID + ".corrosive_contact_tick";

    private CorrosiveContact() { }

    public static void protectWithClothing(LivingIncomingDamageEvent event) {
        if (!event.getSource().is(DAMAGE)) return;
        var living = event.getEntity();
        if (living.getItemBySlot(EquipmentSlot.CHEST).is(CENuclear.PROTECTIVE_COAT.get())
            && living.getItemBySlot(EquipmentSlot.LEGS).is(CENuclear.PROTECTIVE_TROUSERS.get())
            && living.getItemBySlot(EquipmentSlot.FEET).is(CENuclear.PROTECTIVE_SHOE_COVERS.get())) event.setCanceled(true);
    }

    public static boolean harmful(FluidState state) {
        if (state.isEmpty()) return false;
        var id = BuiltInRegistries.FLUID.getKey(state.getType());
        String name = id.getPath();
        if (name.startsWith("flowing_")) name = name.substring(8);
        // Brine corrodes metal, but does not cause an acid or alkali skin burn.
        if (!id.getNamespace().equals(CreatePlant.MOD_ID) || name.equals("brine")) return false;
        var properties = FluidHazards.of(new FluidStack(state.getType(), 1));
        return properties.corrosive() && !properties.gas();
    }

    public static void touch(Level level, BlockPos pos, Entity entity, FluidState state) {
        if (level.isClientSide || !(entity instanceof LivingEntity living)
            || (living.tickCount + living.getId()) % 20 != 0 || !harmful(state)) return;
        var box = living.getBoundingBox();
        if (box.maxX <= pos.getX() || box.minX >= pos.getX() + 1
            || box.maxZ <= pos.getZ() || box.minZ >= pos.getZ() + 1
            || box.maxY <= pos.getY() || box.minY >= pos.getY() + state.getHeight(level, pos)) return;
        var data = living.getPersistentData();
        long now = level.getGameTime();
        if (data.contains(LAST_HIT) && data.getLong(LAST_HIT) == now) return;
        data.putLong(LAST_HIT, now);
        living.hurt(living.damageSources().source(DAMAGE), 2);
    }
}
