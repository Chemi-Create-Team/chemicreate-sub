package net.createplant.block.entity;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.createplant.registry.CEProcessBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Large fin-fan rotor; its 3x3 blades sweep over the surrounding shroud blocks. */
public class CoolingFanBlockEntity extends KineticBlockEntity {
    public static final float STRESS_PER_RPM=8;
    public CoolingFanBlockEntity(BlockPos pos, BlockState state) { super(CEProcessBlocks.COOLING_FAN_ENTITY.get(),pos,state); }
    @Override public float calculateStressApplied() { return lastStressApplied=STRESS_PER_RPM; }
    @Override protected AABB createRenderBoundingBox() { return new AABB(worldPosition).inflate(1,0,1); }
    @Override public void tick() {
        super.tick();
        float rpm=Math.abs(getSpeed());
        // Air drawn up through the bank leaves the guard; more often and faster as the fan speeds up.
        if (level.isClientSide() && rpm>=32 && level.random.nextFloat()<rpm/512) {
            double angle=level.random.nextDouble()*Math.PI*2, radius=0.3+level.random.nextDouble()*1.0;
            level.addParticle(ParticleTypes.WHITE_SMOKE,worldPosition.getX()+0.5+Math.cos(angle)*radius,worldPosition.getY()+1.05,
                worldPosition.getZ()+0.5+Math.sin(angle)*radius,0,0.03+rpm/2048,0);
        }
    }
}
