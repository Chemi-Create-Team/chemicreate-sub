package net.createplant.safety;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/** Breathing a toxic gas: 1 damage a second for an irritant, 2 for a toxic gas and 3 for fluorine, through any armour. */
public class ToxicGasEffect extends MobEffect {
    public ToxicGasEffect() { super(MobEffectCategory.HARMFUL, 0x8A9A6A); }
    @Override public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) { return duration % 20 == 0; }
    @Override public boolean applyEffectTick(LivingEntity living, int amplifier) {
        living.hurt(living.damageSources().source(ToxicGas.DAMAGE), amplifier + 1);
        return true;
    }
}
