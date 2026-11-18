package net.createplant.safety;

import java.util.HashSet;
import net.createplant.block.FlareTipBlock;
import net.createplant.item.SprayToolItem;
import net.createplant.registry.CEWorldFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;

/** Bounded server-side spray; the first solid surface clips damage and effects. */
public final class SprayEffects {
    /** Twice the old two points while the flame touches, besides setting the target alight; a direct hit of acid, and the burn it leaves. */
    private static final float FLAME_DAMAGE=4, ACID_DAMAGE=8;
    private static final int ACID_BURN_TICKS=100;
    private SprayEffects() { }
    public static void spray(ServerLevel level,Player player,ItemStack held,FluidStack fluid,SprayToolItem tool) {
        Vec3 start=player.getEyePosition(),direction=player.getLookAngle();
        Vec3 requested=loadedEnd(level,start,start.add(direction.scale(tool.range())));
        var hit=level.clip(new ClipContext(start,requested,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        Vec3 end=hit.getLocation();
        boolean flame=tool.kind()==SprayToolItem.Kind.FLAME;
        boolean extinguish=tool.kind()==SprayToolItem.Kind.EXTINGUISHER || fluid.is(Fluids.WATER);
        double radius=tool.kind()==SprayToolItem.Kind.EXTINGUISHER ? 1 : .45;
        var area=new AABB(start,end).inflate(radius);
        for(var target:level.getEntitiesOfClass(LivingEntity.class,area,e->e!=player && e.isAlive())) {
            if(!touches(start,end,target.getBoundingBox().inflate(radius)) || !player.hasLineOfSight(target))continue;
            boolean protectedPlayer=target instanceof Player other && (!level.getServer().isPvpAllowed() || !player.canHarmPlayer(other));
            if(protectedPlayer && !extinguish)continue;
            if(flame) {target.hurt(level.damageSources().source(net.minecraft.world.damagesource.DamageTypes.IN_FIRE,player),FLAME_DAMAGE);target.igniteForSeconds(5);}
            else if(extinguish) {target.clearFire();}
            else if(CorrosiveContact.harmful(fluid.getFluid().defaultFluidState())) {
                target.hurt(level.damageSources().source(CorrosiveContact.DAMAGE,player),ACID_DAMAGE);
                target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.createplant.registry.CESafety.ACID_BURN,ACID_BURN_TICKS),player);
            }
            if(tool.kind()!=SprayToolItem.Kind.EXTINGUISHER && !protectedPlayer) {target.push(direction.x*.12,.02,direction.z*.12);target.hasImpulse=true;}
        }
        if(extinguish) {
            var visited=new HashSet<BlockPos>();
            int steps=Math.max(1,(int)Math.ceil(start.distanceTo(end)*2));
            for(int i=1;i<=steps;i++) {
                var at=BlockPos.containing(start.lerp(end,(double)i/steps));
                for(var pos:BlockPos.betweenClosed(at.offset(-1,-1,-1),at.offset(1,1,1))) {
                    var fixed=pos.immutable();
                    if(visited.add(fixed) && level.hasChunkAt(fixed) && burning(level.getBlockState(fixed)) && visible(level,player,start,fixed))extinguish(level,player,held,fixed);
                }
            }
        }
        if(flame) {
            for(var cloud:level.getEntitiesOfClass(AreaEffectCloud.class,area,FlammableGas::cloud))
                if(touches(start,end,cloud.getBoundingBox()))FlammableGas.ignite(cloud);
            if(hit.getType()==HitResult.Type.BLOCK && level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)) {
                var pos=hit.getBlockPos().relative(hit.getDirection());
                if(level.hasChunkAt(pos) && level.mayInteract(player,pos) && player.mayUseItemAt(pos,hit.getDirection(),held)
                    && level.isEmptyBlock(pos) && BaseFireBlock.canBePlacedAt(level,pos,player.getDirection()))level.setBlock(pos,BaseFireBlock.getState(level,pos),Block.UPDATE_ALL);
            }
        }
        ParticleOptions particle=flame ? ParticleTypes.FLAME : extinguish ? tool.kind()==SprayToolItem.Kind.EXTINGUISHER ? ParticleTypes.CLOUD : ParticleTypes.SPLASH : tint(fluid);
        for(int i=1;i<=8;i++) {
            var at=start.lerp(end,i/8d);
            level.sendParticles(particle,at.x,at.y-.1,at.z,2,.08,.08,.08,.01);
        }
    }

    /** The rapid hiss or roar of a tool in use, every other tick. */
    public static void sound(ServerLevel level,Player player,SprayToolItem tool) {
        boolean flame=tool.kind()==SprayToolItem.Kind.FLAME;
        level.playSound(null,player.blockPosition(),flame ? SoundEvents.BLAZE_SHOOT : SoundEvents.FIRE_EXTINGUISH,SoundSource.PLAYERS,.25f,
            (flame ? .8f : 1.2f)+(level.random.nextFloat()-.5f)*.2f);
    }

    private static boolean burning(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(BlockTags.FIRE) || (state.is(BlockTags.CAMPFIRES) || state.is(BlockTags.CANDLES) || state.is(BlockTags.CANDLE_CAKES)) && state.getValue(BlockStateProperties.LIT)
            || state.getBlock() instanceof FlareTipBlock && state.getValue(FlareTipBlock.PILOT);
    }
    private static Vec3 loadedEnd(ServerLevel level,Vec3 start,Vec3 end) {
        Vec3 previous=start;int steps=Math.max(1,(int)Math.ceil(start.distanceTo(end)*4));
        for(int i=1;i<=steps;i++) {
            var point=start.lerp(end,(double)i/steps);
            if(!level.hasChunkAt(BlockPos.containing(point)))return previous;
            previous=point;
        }
        return end;
    }
    private static boolean touches(Vec3 start,Vec3 end,AABB bounds) { return bounds.contains(start) || bounds.clip(start,end).isPresent(); }
    private static boolean visible(ServerLevel level,Player player,Vec3 start,BlockPos pos) {
        if(!level.hasChunkAt(pos))return false;
        var end=Vec3.atCenterOf(pos);
        if(loadedEnd(level,start,end).distanceToSqr(end)>1e-6)return false;
        var hit=level.clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        return hit.getType()==HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }
    public static boolean extinguish(ServerLevel level,Player player,ItemStack held,BlockPos pos) {
        if(!level.hasChunkAt(pos) || !level.mayInteract(player,pos) || !player.mayUseItemAt(pos,Direction.UP,held))return false;
        var state=level.getBlockState(pos);
        if(state.is(BlockTags.FIRE))return level.setBlock(pos,Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL);
        if((state.is(BlockTags.CAMPFIRES) || state.is(BlockTags.CANDLES) || state.is(BlockTags.CANDLE_CAKES)) && state.getValue(BlockStateProperties.LIT))
            return level.setBlock(pos,state.setValue(BlockStateProperties.LIT,false),Block.UPDATE_ALL);
        if(state.getBlock() instanceof FlareTipBlock && state.getValue(FlareTipBlock.PILOT))
            return level.setBlock(pos,state.setValue(FlareTipBlock.PILOT,false),Block.UPDATE_ALL);
        return false;
    }
    private static ParticleOptions tint(FluidStack fluid) {
        int color=0x95d8d3;
        for(var entry:CEWorldFluids.entries())if(entry.fluid().get().isSame(fluid.getFluid())){color=entry.color();break;}
        return new DustParticleOptions(new org.joml.Vector3f((color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f),.8f);
    }
}
