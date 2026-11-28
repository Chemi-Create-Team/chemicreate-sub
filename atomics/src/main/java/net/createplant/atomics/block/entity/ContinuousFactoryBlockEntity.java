package net.createplant.atomics.block.entity;
import net.createplant.atomics.registry.CEAtomicsRecipeTypes;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.createplant.CreatePlant;
import net.createplant.atomics.block.ContinuousFactoryBlock;
import net.createplant.atomics.contraptions.FactoryBodyEntity;
import net.createplant.atomics.factory.FactoryBatch;
import net.createplant.atomics.factory.FactoryGeometry;
import net.createplant.atomics.factory.FactoryKind;
import net.createplant.atomics.factory.FactoryLayout;
import net.createplant.atomics.factory.FactoryMotion;
import net.createplant.atomics.factory.FactoryPose;
import net.createplant.atomics.factory.FactoryProcess;
import net.createplant.atomics.factory.FactoryWatch;
import net.createplant.atomics.recipe.ContinuousFactoryRecipe;
import net.createplant.atomics.registry.CEContinuousFactory;
import net.createplant.registry.CERecipeTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

public final class ContinuousFactoryBlockEntity extends KineticBlockEntity {
    private static final String LANG = "continuous_factory." + CreatePlant.MOD_ID + ".";
    private static final int MAX_BATCHES = 5;
    private final ItemStackHandler input = inventory(2);
    private final ItemStackHandler output = inventory(4);
    private final IItemHandler feedPort = new Port(null, 0);
    private final IItemHandler auxiliaryPort = new Port(null, 1);
    private IItemHandler outputPort;
    private final List<FactoryBatch> batches = new ArrayList<>(MAX_BATCHES);
    private final FactoryBodyEntity[] moving = new FactoryBodyEntity[24];
    private final float[] stageRpm = new float[5], stageAngles = new float[5];
    private FactoryLayout layout;
    private Direction watchedFacing;
    private boolean clear, needsModels = true, layoutDirty = true;
    private int activeBodies;
    private int syncedLength;
    private int heat;
    private int workInventoryVersion;
    private int sampledInventoryVersion = -1;
    private long recipeLookupAt = Long.MIN_VALUE;
    private ContinuousFactoryRecipe matchingRecipe;
    private String refusal = "";
    private int sampledLine = -1;
    private String status = "unformed", fault = "";
    private String notice = "";
    private int noticeTicks;
    private float angle, brake;
    private double tension, progress;
    private int previewLength;
    private boolean previewRunning;

    public ContinuousFactoryBlockEntity(BlockPos pos, BlockState state) {
        super(CEContinuousFactory.CONTROLLER_ENTITY.get(), pos, state);
        setLazyTickRate(12);
    }

    private ItemStackHandler inventory(int slots) {
        return new ItemStackHandler(slots) {
            @Override protected void onContentsChanged(int slot) { workInventoryVersion++; setChanged(); }
        };
    }

    public FactoryKind kind() { return ((ContinuousFactoryBlock) getBlockState().getBlock()).kind(); }
    public Direction facing() { return getBlockState().getValue(ContinuousFactoryBlock.FACING); }
    public String status() { return status; }
    public Component statusText() { return Component.translatable(LANG + status); }
    public double progress() { return progress; }
    public int formedLength() { return syncedLength; }
    public boolean owns(FactoryBodyEntity body) { for (var part : moving) if (part == body) return true; return false; }
    public void structureChanged() { layoutDirty = true; }

    public IItemHandler inlet(Direction side) {
        if (side == null || side == Direction.UP || side == facing().getOpposite()) return feedPort;
        if (kind() != FactoryKind.KILN && side == facing().getClockWise()) return auxiliaryPort;
        return null;
    }

    public boolean isOutlet(BlockPos pos) {
        if (level == null || isRemoved()) return false;
        if (level.isClientSide) return syncedLength > 0 && pos.equals(worldPosition.relative(facing(), syncedLength));
        return layout != null && pos.equals(layout.outlet());
    }

    public IItemHandler outlet(BlockPos pos) { return isOutlet(pos) ? outputPort : null; }
    public void takeOutput(Player player) {
        for (int i = 0; i < output.getSlots(); i++) {
            var stack = output.extractItem(i, 64, false);
            if (!stack.isEmpty()) player.getInventory().placeItemBackInInventory(stack);
        }
    }

    @Override public void initialize() {
        super.initialize();
        if (level instanceof ServerLevel) refreshLayout();
    }

    @Override public void lazyTick() {
        super.lazyTick();
        if (level instanceof ServerLevel) sendData();
    }

    private void refreshLayout() {
        layoutDirty = false;
        if (level instanceof ServerLevel server && watchedFacing != facing()) {
            if (watchedFacing != null) FactoryWatch.update(server, worldPosition, watchedFacing, false);
            watchedFacing = facing();
            FactoryWatch.update(server, worldPosition, watchedFacing, true);
        }
        FactoryLayout next = FactoryLayout.find(level, worldPosition, facing(), kind());
        boolean newClear = next != null && next.workingSpaceClear(level, kind());
        if (Objects.equals(layout, next) && newClear == clear) return;
        FactoryLayout before = layout;
        layout = next;
        clear = newClear;
        syncedLength = layout == null ? 0 : layout.length();
        if (before != null) level.invalidateCapabilities(before.outlet());
        outputPort = layout == null ? null : new Port(layout.outlet(), 0);
        if (layout != null) level.invalidateCapabilities(layout.outlet());
        if (!clear || !Objects.equals(before, layout)) discardBodies();
        needsModels = true;
    }

    @Override public void tick() {
        super.tick();
        if (isVirtual() && previewRunning) {
            angle = Mth.wrapDegrees(angle + 8);
            for (int i = 0; i < stageAngles.length; i++) stageAngles[i] = Mth.wrapDegrees(stageAngles[i] + 8 + 3 * i);
            progress = (progress + .006) % 1;
        }
        if (!(level instanceof ServerLevel server)) return;
        if (noticeTicks > 0) noticeTicks--;
        if (layoutDirty || layout != null && !layout.loaded(level)
            || layout == null && Math.floorMod(level.getGameTime() + worldPosition.asLong(), 40) == 0) refreshLayout();
        if (layout == null || !layout.loaded(level)) { stop("unformed"); discardBodies(); return; }
        if (!clear) { stop("obstructed"); return; }
        if (level.hasNeighborSignal(worldPosition)) { stop("locked"); return; }
        if (!fault.isEmpty()) { stop(fault); return; }
        if (!batches.isEmpty() && batches.getFirst().length != layout.length()) { stop("changed"); return; }
        float main = kind() == FactoryKind.MILL ? stageSpeed(0) : getSpeed();
        if (!sampleDrives(main)) { stop("power"); return; }
        heat = heat();
        if (kind() != FactoryKind.MILL) angle = Mth.wrapDegrees(angle + main * .3f);
        for (int i = 0; kind() == FactoryKind.MILL && i < layout.drives().size(); i++) stageAngles[i] = Mth.wrapDegrees(stageAngles[i] + stageRpm[i] * .3f);
        status = batches.isEmpty() ? "idle" : "running";
        if (main <= 0) {
            status = "reverse";
            updateBodies();
            return;
        }
        admit();
        for (var iterator = batches.iterator(); iterator.hasNext();) {
            FactoryBatch batch = iterator.next();
            if (batch.finished.isEmpty()) advance(server, batch, main);
            if (!batch.finished.isEmpty()) {
                if (fits(batch.finished)) { ItemHandlerHelper.insertItemStacked(output, batch.finished, false); iterator.remove(); }
                else status = "output_full";
            }
            if (!fault.isEmpty()) break;
        }
        int travelLength = kind() == FactoryKind.KILN ? layout.length() - 1 : layout.length();
        progress = batches.isEmpty() ? 0 : Math.clamp(batches.getFirst().travel / travelLength, 0, 1);
        if ("idle".equals(status) && noticeTicks > 0) status = notice;
        if (!batches.isEmpty()) setChanged();
        updateBodies();
        if (kind() == FactoryKind.KILN && heat > 0 && level.getGameTime() % 10 == 0) {
            int stack = 1;
            while (stack < 3 && level.getBlockState(layout.outlet().above(stack + 1)).is(CEContinuousFactory.KILN_STACK.get())) stack++;
            var outlet = layout.point(0, stack + .55, layout.length());
            server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, outlet.x, outlet.y, outlet.z, 2, .11, .05, .11, .017);
        }
    }

    public void preview(int length, boolean running) {
        if (!isVirtual()) return;
        previewLength = length;
        previewRunning = running;
        if (!running) progress = 0;
    }

    public void previewParts(float partial, FactoryMotion.Parts draw) {
        if (!isVirtual() || previewLength == 0) return;
        var axis = new FactoryLayout(worldPosition, facing(), previewLength, List.of());
        var drives = new ArrayList<BlockPos>();
        if (kind() == FactoryKind.MILL) for (int z = 2; z < previewLength - 1; z += FactoryLayout.MILL_SPACING) drives.add(axis.at(2, 1, z));
        var preview = new FactoryLayout(worldPosition, facing(), previewLength, drives);
        var materials = new ArrayList<FactoryBatch>();
        float[] rotation = stageAngles.clone();
        if (previewRunning) {
            var batch = FactoryBatch.preview(previewLength);
            double end = kind() == FactoryKind.KILN ? previewLength - 1 : previewLength;
            batch.travel = ((progress + partial * .006) % 1) * end;
            materials.add(batch);
            for (int i = 0; i < rotation.length; i++) rotation[i] += partial * (8 + 3 * i);
        }
        FactoryMotion.parts(kind(), preview, angle + (previewRunning ? partial * 8 : 0), rotation, materials, draw);
    }

    private boolean sampleDrives(float main) {
        if (kind() == FactoryKind.MILL) {
            boolean moving = true;
            for (int i = 0; i < layout.drives().size(); i++) {
                stageRpm[i] = stageSpeed(i);
                moving &= stageRpm[i] != 0;
            }
            return moving;
        }
        if (kind() == FactoryKind.WINDER) brake = stageSpeed(0);
        return main != 0;
    }

    private float stageSpeed(int i) {
        var at = layout.drives().get(i);
        if (!(level.getBlockEntity(at) instanceof KineticBlockEntity shaft)) return 0;
        return -shaft.getSpeed() * shaft.getBlockState().getValue(net.createplant.atomics.block.FactoryStageBlock.FACING).getAxisDirection().getStep();
    }

    private int heat() {
        if (kind() == FactoryKind.WINDER) return 0;
        var state = level.getBlockState((kind() == FactoryKind.KILN ? layout.outlet() : worldPosition).below());
        if (!AllBlocks.BLAZE_BURNER.has(state)) return 0;
        var blaze = state.getValue(BlazeBurnerBlock.HEAT_LEVEL);
        return blaze == BlazeBurnerBlock.HeatLevel.SEETHING ? 2 : blaze == BlazeBurnerBlock.HeatLevel.KINDLED ? 1 : 0;
    }

    private void stop(String reason) {
        status = reason;
        if (needsModels && layout != null && clear && layout.loaded(level)) updateBodies();
    }

    private void admit() {
        if (batches.size() >= (kind() == FactoryKind.WINDER ? 1 : MAX_BATCHES)) return;
        if (!batches.isEmpty()) {
            var last = batches.getLast();
            if (last.travel < (kind() == FactoryKind.MILL ? 3.5 : 1.15) || !last.finished.isEmpty()) return;
        }
        var recipe = recipe();
        if (recipe == null) { if (!refusal.isEmpty() && batches.isEmpty()) status = refusal; return; }
        if (heat < recipe.heat()) { status = "heat"; return; }
        if (!fits(recipe.result())) { status = "output_full"; return; }
        int slot = 0;
        while (usesSlot(slot)) slot++;
        ItemStack stock = input.extractItem(0, recipe.feed().count(), false);
        ItemStack auxiliary = recipe.auxiliary().map(ingredient -> input.extractItem(1, ingredient.count(), false)).orElse(ItemStack.EMPTY);
        batches.add(new FactoryBatch(slot, layout.length(), recipe, stock, auxiliary));
        status = "running";
    }

    private boolean usesSlot(int slot) { for (var batch : batches) if (batch.slot == slot) return true; return false; }

    private ContinuousFactoryRecipe recipe() {
        long time = level.getGameTime();
        int stands = kind() == FactoryKind.MILL ? layout.drives().size() : 1;
        // The burner and the stand count pick the product, so a change to either looks the recipe up again at once.
        if (sampledInventoryVersion == workInventoryVersion && sampledLine == heat * 16 + stands && time - recipeLookupAt < 20) return matchingRecipe;
        recipeLookupAt = time;
        sampledLine = heat * 16 + stands;
        sampledInventoryVersion = workInventoryVersion;
        refusal = "";
        if (input.getStackInSlot(0).isEmpty()) return matchingRecipe = null;
        RecipeInput stock = new RecipeInput() {
            @Override public ItemStack getItem(int slot) { return input.getStackInSlot(slot); }
            @Override public int size() { return 2; }
        };
        var candidates = level.getRecipeManager().getAllRecipesFor(CEAtomicsRecipeTypes.CONTINUOUS_PROCESSING.<RecipeInput, ContinuousFactoryRecipe>getType())
            .stream().map(holder -> holder.value()).filter(r -> r.machine() == kind() && r.matches(stock, level)).toList();
        matchingRecipe = pick(candidates, stands, heat);
        if (matchingRecipe == null && !candidates.isEmpty())
            refusal = candidates.stream().anyMatch(r -> stands >= r.minStands() && stands <= r.maxStands()) ? "too_hot" : "stands";
        return matchingRecipe;
    }

    /** The recipe the line runs now; failing that, one it could run with more heat (shown as "heat"); else null. */
    public static ContinuousFactoryRecipe pick(List<ContinuousFactoryRecipe> candidates, int stands, int heat) {
        ContinuousFactoryRecipe colder = null;
        for (var recipe : candidates) {
            if (!recipe.fitsLine(stands, heat)) continue;
            if (heat >= recipe.heat()) return recipe;
            if (colder == null) colder = recipe;
        }
        return colder;
    }

    /** The side port takes only what one of this machine's recipes uses as its second material, so a wrong item cannot jam it. */
    private boolean usedAsAuxiliary(ItemStack stack) {
        if (level == null) return false;
        return level.getRecipeManager().getAllRecipesFor(CEAtomicsRecipeTypes.CONTINUOUS_PROCESSING.<RecipeInput, ContinuousFactoryRecipe>getType()).stream()
            .map(holder -> holder.value()).anyMatch(r -> r.machine() == kind() && r.auxiliary().isPresent() && r.auxiliary().get().ingredient().test(stack));
    }

    private boolean fits(ItemStack stack) { return ItemHandlerHelper.insertItemStacked(output, stack, true).isEmpty(); }

    private void advance(ServerLevel server, FactoryBatch batch, float main) {
        switch (kind()) {
            case KILN -> kiln(server, batch, main);
            case MILL -> roll(server, batch);
            case WINDER -> wind(server, batch, main);
        }
    }

    private void kiln(ServerLevel server, FactoryBatch batch, float rpm) {
        batch.travel += FactoryProcess.kilnTravel(rpm);
        batch.dose += FactoryProcess.bakeRate(heat >= batch.heat ? heat : 0);
        if (FactoryProcess.overfired(batch.dose, batch.work)) { batch.damaged = true; trip(server, "kiln_jam", batch.travel + 1); return; }
        if (batch.travel >= layout.length() - 1) {
            if (FactoryProcess.underfired(batch.dose, batch.work)) { batch.finished = batch.feed.copy(); warn(server, "underfired"); }
            else batch.finished = batch.result.copy();
        }
    }

    private void roll(ServerLevel server, FactoryBatch batch) {
        int stages = layout.drives().size();
        int passing = Math.clamp((int) Math.floor((batch.travel - 2) / FactoryLayout.MILL_SPACING), 0, stages - 1);
        double next = batch.travel + Math.abs(stageRpm[passing]) / 410d;
        for (int i = 0; i < stages; i++) {
            double bite = 2 + i * FactoryLayout.MILL_SPACING;
            if (batch.travel < bite && next >= bite) {
                if (stageRpm[i] < 0 || i > 0 && FactoryProcess.millRatio(stageRpm[i - 1], stageRpm[i]) < 1 - batch.tolerance) {
                    batch.travel = bite - .18;
                    batch.damaged = true;
                    trip(server, "buckled", bite);
                    return;
                }
                if (i > 0 && FactoryProcess.millRatio(stageRpm[i - 1], stageRpm[i]) > 1 + batch.tolerance) {
                    batch.finished = CEContinuousFactory.SCRAP.toStack();
                    trip(server, "torn", bite);
                    return;
                }
            }
        }
        batch.travel = Math.min(next, layout.length());
        if (batch.travel >= layout.length()) batch.finished = batch.result.copy();
    }

    private void wind(ServerLevel server, FactoryBatch batch, float rpm) {
        double radius = FactoryProcess.coilingRadius(batch.travel / layout.length());
        tension = FactoryProcess.tension(rpm, Math.abs(brake), radius);
        if (tension > FactoryProcess.MAX_TENSION) { batch.damaged = true; trip(server, "snapped", 3); return; }
        double displacement = rpm * radius / Math.max(110d, batch.work * .45);
        if (tension < FactoryProcess.MIN_TENSION) batch.slack += displacement / layout.length();
        batch.travel = Math.min(layout.length(), batch.travel + displacement);
        if (batch.travel >= layout.length()) {
            batch.finished = batch.slack > .17 ? CEContinuousFactory.LOOSE_WINDING.toStack() : batch.result.copy();
            if (batch.slack > .17) warn(server, "loose");
        }
    }

    private void warn(ServerLevel server, String reason) {
        notice = reason;
        noticeTicks = 65;
        status = reason;
        Vec3 at = layout.point(0, 1.1, layout.length());
        server.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 7, .2, .14, .2, .016);
        level.playSound(null, BlockPos.containing(at), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, .55f, 1.3f);
        sendData();
    }

    private void trip(ServerLevel server, String reason, double z) {
        fault = reason;
        status = reason;
        setChanged();
        sendData();
        Vec3 at = layout.point(0, 1.4, z);
        server.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 14, .45, .27, .35, .035);
        server.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 10, .35, .20, .33, .06);
        level.playSound(null, BlockPos.containing(at), SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, .75f, "snapped".equals(reason) ? 1.8f : .70f);
    }

    public void recover(Player player) {
        if (level == null || level.isClientSide || batches.isEmpty() && fault.isEmpty()) return;
        if (!level.hasNeighborSignal(worldPosition) && (kind() == FactoryKind.MILL && layout != null && layout.drives().stream()
            .filter(level::hasChunkAt).anyMatch(pos -> level.getBlockEntity(pos) instanceof KineticBlockEntity drive && drive.getSpeed() != 0)
            || kind() != FactoryKind.MILL && getSpeed() != 0)) {
            player.displayClientMessage(Component.translatable(LANG + "recover_power"), true);
            return;
        }
        if (!fault.isEmpty()) {
            if (batches.removeIf(batch -> batch.damaged))
                player.getInventory().placeItemBackInInventory(CEContinuousFactory.SCRAP.toStack());
        } else {
            for (var batch : batches) refund(batch);
            batches.clear();
        }
        fault = "";
        progress = 0;
        needsModels = true;
        setChanged();
        sendData();
    }

    private void updateBodies() {
        activeBodies = 0;
        FactoryMotion.parts(kind(), layout, angle, stageAngles, batches, this::part);
        for (int i = 0; i < moving.length; i++) if ((activeBodies & 1 << i) == 0 && moving[i] != null) {
            moving[i].discard(); moving[i] = null;
        }
        needsModels = false;
    }

    private void part(int slot, FactoryGeometry geometry, FactoryPose pose) {
        activeBodies |= 1 << slot;
        var body = moving[slot];
        if (body == null || !body.isAlive() || body.geometry() != geometry) {
            if (body != null) body.discard();
            body = FactoryBodyEntity.create(level, worldPosition, geometry, pose);
            moving[slot] = body;
            if (!level.addFreshEntity(body)) { moving[slot] = null; body.discard(); }
        } else body.target(pose);
    }

    private void discardBodies() {
        for (int i = 0; i < moving.length; i++) if (moving[i] != null) { moving[i].discard(); moving[i] = null; }
    }

    private void unwatch() {
        if (level instanceof ServerLevel server && watchedFacing != null) FactoryWatch.update(server, worldPosition, watchedFacing, false);
        watchedFacing = null;
    }

    @Override public void onChunkUnloaded() { unwatch(); discardBodies(); super.onChunkUnloaded(); }
    @Override public void destroy() {
        if (level != null && !level.isClientSide) {
            unwatch();
            discardBodies();
            for (int i = 0; i < input.getSlots(); i++) drop(input.getStackInSlot(i));
            for (int i = 0; i < output.getSlots(); i++) drop(output.getStackInSlot(i));
            for (var batch : batches) refund(batch);
        }
        super.destroy();
    }

    private void refund(FactoryBatch batch) {
        if (!batch.finished.isEmpty()) drop(batch.finished);
        else if (batch.damaged) drop(CEContinuousFactory.SCRAP.toStack());
        else { drop(batch.feed); drop(batch.auxiliary); }
    }

    private void drop(ItemStack stack) {
        if (!stack.isEmpty()) Containers.dropItemStack(level, worldPosition.getX() + .5, worldPosition.getY() + 1, worldPosition.getZ() + .5, stack.copy());
    }

    @Override public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        if (kind() != FactoryKind.MILL) super.addToGoggleTooltip(tooltip, sneaking);
        tooltip.add(Component.literal("    ").append(statusText()).withStyle("running".equals(status) ? ChatFormatting.GREEN : ChatFormatting.GOLD));
        if (syncedLength > 0) tooltip.add(Component.translatable(LANG + "progress", Math.round(progress * 100)));
        if (kind() == FactoryKind.KILN && syncedLength > 0) tooltip.add(Component.translatable(LANG + "kiln_length", syncedLength - 1, heat));
        if (kind() == FactoryKind.WINDER) tooltip.add(Component.translatable(LANG + "tension", String.format(Locale.ROOT, "%.2f", tension), Math.round(brake)));
        if (kind() == FactoryKind.MILL && syncedLength > 0) {
            StringBuilder rpm = new StringBuilder();
            for (int i = 0; i < (syncedLength - 1) / FactoryLayout.MILL_SPACING; i++) {
                if (i > 0) rpm.append(" → ");
                rpm.append(Mth.floor(stageRpm[i]));
            }
            tooltip.add(Component.translatable(LANG + "speed", rpm.toString()));
        }
        return true;
    }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        super.write(tag, lookup, packet);
        tag.putString("Status", status);
        tag.putInt("LayoutLength", syncedLength);
        tag.putDouble("Progress", progress);
        tag.putInt("Heat", heat);
        tag.putDouble("Tension", tension);
        tag.putFloat("Brake", brake);
        for (int i = 0; i < stageRpm.length; i++) tag.putFloat("RPM" + i, stageRpm[i]);
        if (packet) return;
        tag.put("FactoryFeed", input.serializeNBT(lookup));
        tag.put("FactoryOutput", output.serializeNBT(lookup));
        tag.putString("Fault", fault);
        tag.putFloat("Angle", angle);
        for (int i = 0; i < stageAngles.length; i++) tag.putFloat("Angle" + i, stageAngles[i]);
        var saved = new ListTag();
        for (var batch : batches) saved.add(batch.save(lookup));
        tag.put("Batches", saved);
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        super.read(tag, lookup, packet);
        status = tag.contains("Status") ? tag.getString("Status") : "unformed";
        syncedLength = Math.clamp(tag.getInt("LayoutLength"), 0, FactoryLayout.MAX_LENGTH);
        progress = tag.getDouble("Progress");
        heat = tag.getInt("Heat");
        tension = tag.getDouble("Tension");
        brake = tag.getFloat("Brake");
        for (int i = 0; i < stageRpm.length; i++) stageRpm[i] = tag.getFloat("RPM" + i);
        if (packet) return;
        if (tag.contains("FactoryFeed")) input.deserializeNBT(lookup, tag.getCompound("FactoryFeed"));
        if (tag.contains("FactoryOutput")) output.deserializeNBT(lookup, tag.getCompound("FactoryOutput"));
        fault = tag.getString("Fault");
        angle = tag.getFloat("Angle");
        for (int i = 0; i < stageAngles.length; i++) stageAngles[i] = tag.getFloat("Angle" + i);
        batches.clear();
        for (var saved : tag.getList("Batches", Tag.TAG_COMPOUND)) {
            if (batches.size() >= MAX_BATCHES) break;
            var batch = FactoryBatch.load(lookup, (CompoundTag) saved);
            if (!batch.feed.isEmpty() && !batch.result.isEmpty() && !usesSlot(batch.slot)) batches.add(batch);
        }
        needsModels = true;
        layoutDirty = true;
    }

    private final class Port implements IItemHandler {
        private final BlockPos endpoint;
        private final int inputSlot;
        private Port(BlockPos endpoint, int inputSlot) { this.endpoint = endpoint; this.inputSlot = inputSlot; }
        private boolean linked() {
            return endpoint != null && isOutlet(endpoint) && level.hasChunkAt(endpoint)
                && level.getBlockState(endpoint).is(CEContinuousFactory.OUTLET.get());
        }
        @Override public int getSlots() { return endpoint != null ? output.getSlots() : 1; }
        @Override public ItemStack getStackInSlot(int slot) { return linked() ? output.getStackInSlot(slot) : ItemStack.EMPTY; }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return isItemValid(slot, stack) ? input.insertItem(inputSlot, stack, simulate) : stack;
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return linked() ? output.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }
        @Override public int getSlotLimit(int slot) { return 64; }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return endpoint == null && !isRemoved() && slot == 0 && (inputSlot == 0 || usedAsAuxiliary(stack));
        }
    }
}
