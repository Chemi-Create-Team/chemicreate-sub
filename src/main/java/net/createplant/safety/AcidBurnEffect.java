package net.createplant.safety;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/** Acid left on the skin after a spray: corrosive damage once a second, one more for each level. */
public class AcidBurnEffect extends MobEffect {
    public AcidBurnEffect() { super(MobEffectCategory.HARMFUL, 0xC8D25A); }
    @Override public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) { return duration % 20 == 0; }
    @Override public boolean applyEffectTick(LivingEntity living, int amplifier) {
        living.hurt(living.damageSources().source(CorrosiveContact.DAMAGE), amplifier + 1);
        return true;
    }
}
