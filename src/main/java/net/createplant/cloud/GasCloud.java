package net.createplant.cloud;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.common.NeoForge;

public final class GasCloud extends Entity {
    private static final int REBUILD_INTERVAL = 100, SYNC_INTERVAL = 20;
    private static final EntityDataAccessor<CompoundTag> CELLS = SynchedEntityData.defineId(GasCloud.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<String> FLUID_ID = SynchedEntityData.defineId(GasCloud.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> CHARGE = SynchedEntityData.defineId(GasCloud.class, EntityDataSerializers.INT);

    private CloudVolume volume = CloudVolume.empty(BlockPos.ZERO);
    private FluidStack fluid = FluidStack.EMPTY;
    private int age, sampleAge, expires, peak, nextRebuild;
    private boolean outdoors;

    public GasCloud(EntityType<? extends GasCloud> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    void initialize(BlockPos origin, FluidStack stack) {
        setPos(Vec3.atCenterOf(origin));
        volume = CloudVolume.empty(origin);
        fluid = stack.copy();
        syncFluidId();
        refreshDuration();
        syncCharge();
    }

    public static GasCloud preview(Level level, BlockPos origin, FluidStack stack) {
        var cloud = new GasCloud(net.createplant.registry.CESafety.GAS_CLOUD.get(), level);
        cloud.initialize(origin, stack);
        cloud.rebuild();
        return cloud;
    }

    public void refreshPreview(int amount) {
        if (!level().isClientSide) return;
        fluid.setAmount(amount);
        refreshDuration();
        rebuild();
        syncCharge();
    }

    public CloudVolume volume() { return volume; }
    public String fluidId() { return entityData.get(FLUID_ID); }
    public GasCloudStyles.Style style() { return GasCloudStyles.find(fluidId()); }
    public float topHeight() {
        var style = style();
        return style == null || style.ceiling() ? 1 : .25f + .75f * (float) charge();
    }
    public boolean contains(BlockPos pos) { return volume.contains(pos); }
    public boolean contains(Vec3 pos) { return volume.contains(pos, topHeight()); }
    public int age() { return age; }
    public int lifetime() { return expires; }
    public int amount() {
        if (age >= expires || fluid.isEmpty()) return 0;
        return (int) Math.ceil(fluid.getAmount() * ((expires - age) / (double) (expires - sampleAge)));
    }
    public double charge() {
        if (level().isClientSide) return entityData.get(CHARGE) / 100d;
        return peak == 0 ? 0 : Math.min(1, Math.min(peak, 1000) / 1000d * (amount() / (double) peak));
    }

    public FluidStack content() { return fluid.copyWithAmount(amount()); }
    boolean compatible(FluidStack other) { return FluidStack.isSameFluidSameComponents(fluid, other); }

    boolean touchesSource(BlockPos pos) {
        if (blockPosition().equals(pos) || contains(pos)) return true;
        if (!level().getBlockState(pos).isCollisionShapeFullBlock(level(), pos)) return false;
        for (var side : net.minecraft.core.Direction.values()) if (contains(pos.relative(side))) return true;
        return false;
    }

    void merge(FluidStack extra) {
        int held = amount();
        fluid.setAmount(held + Math.min(Integer.MAX_VALUE - held, extra.getAmount()));
        refreshDuration();
    }

    public FluidStack extract(int limit) {
        if (level().isClientSide || limit <= 0 || !isAlive()) return FluidStack.EMPTY;
        int held = amount(), taken = Math.min(held, limit);
        var captured = fluid.copyWithAmount(taken);
        fluid.setAmount(held - taken);
        sampleAge = age;
        if (held == taken) discard();
        return captured;
    }

    private void refreshDuration() {
        sampleAge = age;
        peak = fluid.getAmount();
        int duration = Math.min(2400, 400 + peak / 5);
        expires = age + Math.max(1, outdoors ? duration / style().outdoorDivisor() : duration);
    }

    void rebuild() {
        var style = style();
        if (style == null) return;
        int divisor = style.outdoorDivisor();
        var next = CloudVolume.build(level(), blockPosition(), amount(), style.ceiling());
        boolean sky = next.seesSky(level());
        if (sky != outdoors) {
            var remaining = amount();
            int duration = expires - age;
            expires = age + Math.max(1, sky ? duration / divisor : Math.min(2400, duration * divisor));
            sampleAge = age;
            fluid.setAmount(remaining);
            outdoors = sky;
        }
        if (!next.sameCells(volume)) {
            volume = next;
            setBoundingBox(volume.bounds());
            entityData.set(CELLS, next.save());
        }
        nextRebuild = age + REBUILD_INTERVAL;
        if (level() instanceof ServerLevel) NeoForge.EVENT_BUS.post(new GasCloudEvent.Rebuilt(this));
    }

    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        age++;
        if (amount() == 0) {
            discard();
            return;
        }
        if (age >= nextRebuild && GasClouds.takeRebuildSlot(level)) rebuild();
        if (age % SYNC_INTERVAL == 0 && isAlive()) {
            syncCharge();
            NeoForge.EVENT_BUS.post(new GasCloudEvent.Sample(this));
        }
    }

    private void syncFluidId() {
        entityData.set(FLUID_ID, fluid.isEmpty() ? "" : GasCloudStyles.id(fluid.getFluid()));
    }

    private void syncCharge() {
        int charge = (int) Math.ceil(100 * charge());
        if (level().isClientSide) charge = Math.min(100, (int) Math.ceil(fluid.getAmount() / 10d));
        entityData.set(CHARGE, charge);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(CELLS, new CompoundTag());
        builder.define(FLUID_ID, "");
        builder.define(CHARGE, 0);
    }

    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (level().isClientSide && CELLS.equals(key)) {
            volume = CloudVolume.load(entityData.get(CELLS));
            if (volume.size() > 0) setBoundingBox(volume.bounds());
        }
    }

    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        if (!fluid.isEmpty()) tag.put("Fluid", fluid.save(registryAccess()));
        tag.putInt("Age", age);
        tag.putInt("SampleAge", sampleAge);
        tag.putInt("Expires", expires);
        tag.putInt("Peak", peak);
        tag.putInt("NextRebuild", nextRebuild);
        tag.putBoolean("Outdoors", outdoors);
        tag.put("Volume", volume.save());
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        fluid = FluidStack.parseOptional(registryAccess(), tag.getCompound("Fluid"));
        if (!GasClouds.supported(fluid)) fluid = FluidStack.EMPTY;
        syncFluidId();
        age = Math.clamp(tag.getInt("Age"), 0, Integer.MAX_VALUE - 4800);
        expires = Math.clamp(tag.getInt("Expires"), age, age + 2400);
        sampleAge = Math.clamp(tag.getInt("SampleAge"), 0, age);
        peak = Math.max(fluid.getAmount(), tag.getInt("Peak"));
        nextRebuild = Math.clamp(tag.getInt("NextRebuild"), age, age + REBUILD_INTERVAL);
        outdoors = tag.getBoolean("Outdoors");
        volume = CloudVolume.load(tag.getCompound("Volume"));
        if (volume.size() > 0 && volume.origin().equals(blockPosition())) {
            setBoundingBox(volume.bounds());
            entityData.set(CELLS, volume.save());
        } else {
            volume = CloudVolume.empty(blockPosition());
            nextRebuild = age;
        }
        syncCharge();
    }

    @Override public boolean isIgnoringBlockTriggers() { return true; }
    @Override public boolean isAttackable() { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < 48 * 48; }
}
