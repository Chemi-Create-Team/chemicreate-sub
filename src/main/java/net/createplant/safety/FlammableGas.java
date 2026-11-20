package net.createplant.safety;

import java.util.Set;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import net.createplant.CreatePlant;
import net.createplant.thermal.FluidMaterials;
import net.createplant.transport.TransportConfig;
import net.createplant.cloud.GasCloud;
import net.createplant.cloud.GasCloudEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;

/** Burning gas clouds: which gases burn, what lights them, and the explosion when they do. */
@EventBusSubscriber(modid=CreatePlant.MOD_ID)
public final class FlammableGas {
    private static final String TAG=CreatePlant.MOD_ID+".flammable_gas",CONTENTS="FlammableContents",SETTLED="FlammableSettled";
    private static final Set<String> FUELS=Set.of("hydrogen_gas","pressurised_hydrogen","compressed_hydrogen","deuterium_gas","tritium_gas","carbon_monoxide",
        "synthesis_gas","water_gas","compressed_synthesis_feed","hot_synthesis_mixture","cold_synthesis_mixture","converter_gas",
        "ethylene","propylene","ammonia_gas","ethylene_oxide","vinyl_chloride","hydrogen_sulfide","hot_olefins","cooled_olefins",
        "purified_syngas","pressurised_carbon_monoxide","ft_offgas","butadiene");

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
    public static boolean ignition(BlockState state) {
        return state.is(BlockTags.FIRE) || state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)
            || state.is(Blocks.SOUL_TORCH) || state.is(Blocks.SOUL_WALL_TORCH) || state.getFluidState().is(FluidTags.LAVA)
            || state.is(BlockTags.CAMPFIRES) && state.getValue(BlockStateProperties.LIT)
            || state.getBlock() instanceof BlazeBurnerBlock && state.getValue(BlazeBurnerBlock.HEAT_LEVEL).isAtLeast(BlazeBurnerBlock.HeatLevel.SMOULDERING)
            || state.is(net.createplant.registry.CEFlare.TIP.get()) && state.getValue(net.createplant.block.FlareTipBlock.PILOT);
    }
    /** Each second a burning cloud looks for a flame in or beside it, and for a burning creature inside it. */
    @SubscribeEvent
    public static void sample(GasCloudEvent.Sample event) {
        var cloud=event.cloud;
        if (cloud.inert() || !fuel(cloud.content()) || !(cloud.level() instanceof ServerLevel level)) return;
        var at=new BlockPos.MutableBlockPos();
        for (var pos:cloud.volume().positions()) {
            if (level.hasChunkAt(pos) && ignition(level.getBlockState(pos))) { ignite(cloud);return; }
            for (var side:Direction.values()) {
                at.setWithOffset(pos,side);
                if (!cloud.contains(at) && level.hasChunkAt(at) && ignition(level.getBlockState(at))) { ignite(cloud);return; }
            }
        }
        if (!level.getEntitiesOfClass(LivingEntity.class,cloud.getBoundingBox(),living->living.isOnFire() && cloud.contains(living.position())).isEmpty()) ignite(cloud);
    }

    /** The whole cloud burns at once, centred where its gas is. */
    public static void ignite(GasCloud cloud) {
        if (!(cloud.level() instanceof ServerLevel level) || !cloud.isAlive()) return;
        var gas=cloud.content();var cells=cloud.volume().positions();
        double x=0,y=0,z=0;
        for (var pos:cells) { x+=pos.getX()+.5;y+=pos.getY()+.5;z+=pos.getZ()+.5; }
        int count=Math.max(1,cells.size());
        var at=cells.isEmpty() ? cloud.position() : new net.minecraft.world.phys.Vec3(x/count,y/count,z/count);
        cloud.discard();
        if (gas.isEmpty()) return;
        explode(level,at,gas.getAmount());
    }

    private static void explode(ServerLevel level,net.minecraft.world.phys.Vec3 at,int amount) {
        float radius=(float)Math.clamp(2*Math.cbrt(amount/1000d),1,12);
        if (TransportConfig.GAS_EXPLOSIONS.get()) level.explode(null,at.x,at.y,at.z,radius,true,Level.ExplosionInteraction.BLOCK);
        else level.sendParticles(ParticleTypes.FLAME,at.x,at.y,at.z,24,1,1,1,.02);
    }

    /** Clouds from before the gas cloud entity keep burning by their old rule until they fade. */
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
        if (!gas.isEmpty()) explode(level,at,gas.getAmount());
    }
}
