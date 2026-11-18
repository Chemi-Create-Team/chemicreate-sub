package net.createplant.safety;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import net.createplant.CreatePlant;
import net.createplant.thermal.FluidMaterials;
import net.createplant.thermal.FluidThermal;
import net.createplant.transport.TransportConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;

/** A finite atmospheric release, separate from the inhalation effect of the same gas. */
@EventBusSubscriber(modid=CreatePlant.MOD_ID)
public final class FlammableGas {
    private static final String TAG=CreatePlant.MOD_ID+".flammable_gas",CONTENTS="FlammableContents",SETTLED="FlammableSettled";
    private static final Set<String> FUELS=Set.of("hydrogen_gas","deuterium_gas","tritium_gas","carbon_monoxide",
        "synthesis_gas","water_gas","compressed_synthesis_feed","hot_synthesis_mixture","cold_synthesis_mixture","converter_gas",
        "ethylene","propylene","ammonia_gas","ethylene_oxide","vinyl_chloride","hydrogen_sulfide","hot_olefins","cooled_olefins");
    public static final float MAX_RADIUS=4;

    private FlammableGas() { }

    public static boolean fuel(FluidStack fluid) {
        if (fluid.isEmpty() || !FluidMaterials.gas(fluid.getFluid())) return false;
        if (net.createplant.chemistry.WasteGas.contaminated(fluid)) return (net.createplant.chemistry.WasteGas.of(fluid).hazards()&net.createplant.chemistry.WasteGas.FUEL)!=0;
        var id=BuiltInRegistries.FLUID.getKey(fluid.getFluid());
        return id.getNamespace().equals(CreatePlant.MOD_ID) && FUELS.contains(id.getPath().replace("flowing_",""));
    }
    public static boolean cloud(AreaEffectCloud cloud) { return cloud.getTags().contains(TAG); }
    public static FluidStack contents(AreaEffectCloud cloud) {
        return cloud.level() instanceof ServerLevel level ? FluidStack.parseOptional(level.registryAccess(),cloud.getPersistentData().getCompound(CONTENTS)) : FluidStack.EMPTY;
    }
    private static void store(AreaEffectCloud cloud,FluidStack fluid) {
        cloud.getPersistentData().put(CONTENTS,fluid.save(cloud.level().registryAccess()));
        cloud.getPersistentData().putInt(SETTLED,cloud.tickCount);
    }
    private static void settle(AreaEffectCloud cloud) {
        var fluid=contents(cloud);int previous=cloud.getPersistentData().getInt(SETTLED);
        int life=Math.max(1,cloud.getDuration()-previous),left=Math.max(0,cloud.getDuration()-cloud.tickCount);
        if (!fluid.isEmpty() && cloud.tickCount>previous) {
            int amount=(int)((long)fluid.getAmount()*left/life);
            if (amount<=0) { cloud.discard();return; }
            store(cloud,fluid.copyWithAmount(amount));
        }
    }
    public static void release(Level world,BlockPos pos,FluidStack fluid) {
        if (!(world instanceof ServerLevel level) || !fuel(fluid)) return;
        int duration=Math.clamp(400+fluid.getAmount()/5,400,2400)/(level.canSeeSky(pos.above()) ? 3 : 1);
        float radius=(float)Math.clamp(1.5+Math.sqrt(fluid.getAmount()/1000d),1.5,MAX_RADIUS);
        for (var cloud:level.getEntitiesOfClass(AreaEffectCloud.class,new AABB(pos).inflate(2),FlammableGas::cloud)) {
            settle(cloud);if (!cloud.isAlive()) continue;
            var stored=contents(cloud);
            if (!FluidThermal.compatible(stored,fluid) || (long)stored.getAmount()+fluid.getAmount()>Integer.MAX_VALUE) continue;
            store(cloud,FluidThermal.mix(stored,fluid,fluid.getAmount()));
            cloud.setRadius(Math.max(cloud.getRadius(),radius));
            cloud.setDuration(Math.max(cloud.getDuration(),cloud.tickCount+duration));
            cloud.setRadiusPerTick(-cloud.getRadius()/2/Math.max(1,cloud.getDuration()-cloud.tickCount));
            return;
        }
        var cloud=new AreaEffectCloud(level,pos.getX()+.5,pos.getY()+.2,pos.getZ()+.5);
        cloud.addTag(TAG);cloud.setRadius(radius);cloud.setDuration(duration);cloud.setWaitTime(0);
        cloud.setRadiusPerTick(-radius/2/duration);
        cloud.setPotionContents(new PotionContents(Optional.empty(),Optional.of(0xD7D2C3),List.of()));
        store(cloud,fluid.copy());level.addFreshEntity(cloud);
    }
    public static boolean ignition(BlockState state) {
        return state.is(BlockTags.FIRE) || state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)
            || state.is(Blocks.SOUL_TORCH) || state.is(Blocks.SOUL_WALL_TORCH) || state.getFluidState().is(FluidTags.LAVA)
            || state.is(BlockTags.CAMPFIRES) && state.getValue(BlockStateProperties.LIT)
            || state.getBlock() instanceof BlazeBurnerBlock && state.getValue(BlazeBurnerBlock.HEAT_LEVEL).isAtLeast(BlazeBurnerBlock.HeatLevel.SMOULDERING)
            || state.is(net.createplant.registry.CEFlare.TIP.get()) && state.getValue(net.createplant.block.FlareTipBlock.PILOT);
    }
    @SubscribeEvent
    public static void tick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof AreaEffectCloud cloud) || !(cloud.level() instanceof ServerLevel level)
            || !cloud(cloud) || Math.floorMod(cloud.tickCount+cloud.getId(),10)!=0) return;
        settle(cloud);if (!cloud.isAlive()) return;
        int radius=(int)Math.ceil(cloud.getRadius());BlockPos centre=cloud.blockPosition();
        boolean lit=cloud.isOnFire();
        for (int x=-radius;x<=radius && !lit;x++) for (int z=-radius;z<=radius && !lit;z++) {
            if (x*x+z*z>(cloud.getRadius()+.5)*(cloud.getRadius()+.5)) continue;
            for (int y=-1;y<=2;y++) {
                var at=centre.offset(x,y,z);
                if (level.hasChunkAt(at) && ignition(level.getBlockState(at))) { lit=true;break; }
            }
        }
        if (lit) ignite(cloud);
    }
    public static void ignite(AreaEffectCloud cloud) {
        if (!(cloud.level() instanceof ServerLevel level) || !cloud(cloud) || !cloud.isAlive()) return;
        var gas=contents(cloud);var at=cloud.position();cloud.discard();
        if (gas.isEmpty()) return;
        float radius=(float)Math.clamp(2*Math.cbrt(gas.getAmount()/1000d),1,12);
        if (TransportConfig.GAS_EXPLOSIONS.get()) level.explode(null,at.x,at.y,at.z,radius,true,Level.ExplosionInteraction.BLOCK);
        else level.sendParticles(ParticleTypes.FLAME,at.x,at.y,at.z,24,1,1,1,.02);
    }
}
