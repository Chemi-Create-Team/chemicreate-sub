package net.createplant.block.entity;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.List;
import net.createplant.assembly.AssemblyPlan;
import net.createplant.assembly.AssemblySchematic;
import net.createplant.block.FacilityConstructorBlock;
import net.createplant.inventory.FacilityConstructorMenu;
import net.createplant.registry.CEAssembly;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.RangedWrapper;

public class FacilityConstructorBlockEntity extends SchematicannonBlockEntity {
    private AssemblyPlan.Kind kind = AssemblyPlan.available(AssemblyPlan.Kind.RBMK) ? AssemblyPlan.Kind.RBMK : AssemblyPlan.Kind.COOLING_TOWER;
    private final int[] sizeIndices = {0, 0, 1, 0, 0};
    private int heightIndex = 1, clarifierHeightIndex, casingTankHeightIndex;
    private List<AssemblyPlan.Target> plan;
    private String planKey, preparedKey;
    private AABB bounds;
    private long retryAt;
    private boolean completed, resetOnStop;
    private boolean lifecycleInitialized;
    private final IItemHandler supplies = new RangedWrapper(inventory, 2, 5);

    public FacilityConstructorBlockEntity(BlockPos pos, BlockState state) { super(CEAssembly.CONSTRUCTOR_ENTITY.get(), pos, state); }

    public AssemblyPlan.Kind kind() { return kind; }
    public int sizeIndex() { return sizeIndex(kind); }
    public int sizeIndex(AssemblyPlan.Kind selection) { return sizeIndices[selection.ordinal()]; }
    public int coreSize() { return AssemblyPlan.sizes(AssemblyPlan.Kind.RBMK).get(sizeIndices[0]); }
    public int heightIndex() { return switch (kind) { case CLARIFIER -> clarifierHeightIndex; case CASING_TANK -> casingTankHeightIndex; default -> heightIndex; }; }
    public int planHeight() { return kind.heights().get(heightIndex()); }
    public int coreHeight() { return AssemblyPlan.CORE_HEIGHTS.get(heightIndex); }
    public int selectedSize() { return AssemblyPlan.sizes(kind).get(sizeIndex()); }
    public AssemblyPlan.Dimensions dimensions() { return AssemblyPlan.dimensions(kind, selectedSize(), planHeight()); }
    public int totalHeight() { return dimensions().height(); }
    public Direction facing() { return getBlockState().getValue(FacilityConstructorBlock.FACING); }
    public IItemHandler supplies() { return supplies; }
    public boolean canConfigure() { return state == State.STOPPED && flyingBlocks.isEmpty(); }

    public List<AssemblyPlan.Target> plan() {
        String key = AssemblyPlan.fileName(kind, facing(), selectedSize(), planHeight());
        if (!key.equals(planKey)) {
            plan = AssemblyPlan.targets(kind, worldPosition.relative(facing(), 2), facing(), selectedSize(), planHeight());
            planKey = key;
            bounds = new AABB(worldPosition);
            for (var target : plan) bounds = bounds.minmax(new AABB(target.pos()));
        }
        return plan;
    }

    public boolean configure(int selection, int index) {
        if (!canConfigure() || selection < 0 || selection >= AssemblyPlan.Kind.values().length
            || !AssemblyPlan.available(AssemblyPlan.Kind.values()[selection])
            || index < 0 || index >= AssemblyPlan.sizes(AssemblyPlan.Kind.values()[selection]).size()) return false;
        kind = AssemblyPlan.Kind.values()[selection];sizeIndices[selection] = index;
        planChanged();
        return true;
    }

    public boolean configureHeight(int index) {
        if (!canConfigure() || kind != AssemblyPlan.Kind.RBMK && !kind.isCircularTank() || index < 0 || index >= kind.heights().size()) return false;
        switch (kind) {
            case CLARIFIER -> clarifierHeightIndex = index;
            case CASING_TANK -> casingTankHeightIndex = index;
            default -> heightIndex = index;
        }
        planChanged();
        return true;
    }

    public void planChanged() {
        resetPrinter();
        state = State.STOPPED;completed = false;resetOnStop = false;
        plan = null;planKey = null;preparedKey = null;retryAt = 0;
        inventory.setStackInSlot(0, ItemStack.EMPTY);
        inventory.setStackInSlot(1, ItemStack.EMPTY);
        defaultYaw = -facing().toYRot();
        statusMsg = "idle";
        setChanged();sendData();
    }

    @Override public void initialize() {
        if (lifecycleInitialized) return;
        lifecycleInitialized = true;
        super.initialize();
    }

    @Override public void tick() {
        if (level == null || level.isClientSide) { super.tick();return; }
        if (!lifecycleInitialized) initialize();
        if (completed && state == State.RUNNING) { completed = false;preparedKey = null;findInventories(); }
        if (!completed) {
            String key = AssemblyPlan.fileName(kind, facing(), selectedSize(), planHeight());
            if (!key.equals(preparedKey) && level.getGameTime() >= retryAt) {
                ItemStack previous = inventory.getStackInSlot(0);
                if (!previous.isEmpty() && !key.equals(previous.get(AllDataComponents.SCHEMATIC_FILE))) {
                    if (!flyingBlocks.isEmpty()) { tickFlyingBlocks();setChanged();return; }
                    resetPrinter();
                    inventory.setStackInSlot(0, ItemStack.EMPTY);
                }
                ItemStack blueprint = AssemblySchematic.blueprint(level, key, plan());
                if (!blueprint.isEmpty()) { inventory.setStackInSlot(0, blueprint);preparedKey = key; }
                else { retryAt = level.getGameTime() + 100;statusMsg = "schematicErrored";sendUpdate = true; }
            }
        }
        if (state == State.STOPPED) {
            if (lazyTickCounter-- <= 0) { lazyTickCounter = lazyTickRate;lazyTick(); }
            forEachBehaviour(BlockEntityBehaviour::tick);
            if (resetOnStop) { resetPrinter();resetOnStop = false; }
            if (!flyingBlocks.isEmpty()) { tickFlyingBlocks();setChanged(); }
            if (!completed) {
                if (neighbourCheckCooldown == 0) { findInventories();neighbourCheckCooldown = 100; }
                tickPaperPrinter();
                // Create prepares the checklist by pausing the printer; selection remains available until Play is pressed.
                state = State.STOPPED;
            }
            if (sendUpdate) { sendUpdate = false;setChanged();sendData(); }
            return;
        }
        resetOnStop = true;
        super.tick();
    }

    @Override protected void initializePrinter(ItemStack blueprint) {
        State requested = state;
        super.initializePrinter(blueprint);
        if (requested == State.RUNNING && statusMsg.equals("ready")) { state = State.RUNNING;statusMsg = "running"; }
    }

    @Override public void finishedPrinting() {
        if (replaceMode == 3) printer.sendBlockUpdates(level);
        inventory.setStackInSlot(0, ItemStack.EMPTY);
        inventory.setStackInSlot(1, ItemStack.EMPTY);
        preparedKey = null;
        completed = true;resetOnStop = false;
        state = State.STOPPED;statusMsg = "finished";
        resetPrinter();
        AllSoundEvents.SCHEMATICANNON_FINISH.playOnServer(level, worldPosition);
        setChanged();
    }

    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new FacilityConstructorMenu(id, inventory, this); }
    @Override public Component getDisplayName() { return CEAssembly.CONSTRUCTOR.get().getName(); }

    @Override public void write(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        super.write(tag, lookup, packet);
        tag.putInt("Facility", kind.ordinal());tag.putInt("CoreSize", sizeIndices[0]);tag.putBoolean("Completed", completed);
        tag.putInt("ClarifierOddSize", sizeIndices[3]);tag.putInt("ClarifierHeight", clarifierHeightIndex);
        tag.putInt("CasingTankSize", sizeIndices[4]);tag.putInt("CasingTankHeight", casingTankHeightIndex);
        tag.putInt("FusionSize", sizeIndices[1]);tag.putInt("TowerSize", sizeIndices[2]);tag.putInt("CoreHeight", heightIndex);
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        super.read(tag, lookup, packet);
        kind = AssemblyPlan.Kind.values()[Math.clamp(tag.getInt("Facility"), 0, AssemblyPlan.Kind.values().length - 1)];
        if (!AssemblyPlan.available(kind)) kind = AssemblyPlan.Kind.COOLING_TOWER;
        sizeIndices[0] = Math.clamp(tag.getInt("CoreSize"), 0, AssemblyPlan.sizes(AssemblyPlan.Kind.RBMK).size() - 1);
        sizeIndices[1] = Math.clamp(tag.getInt("FusionSize"), 0, AssemblyPlan.sizes(AssemblyPlan.Kind.FUSION).size() - 1);
        sizeIndices[2] = tag.contains("TowerSize") ? Math.clamp(tag.getInt("TowerSize"), 0, AssemblyPlan.sizes(AssemblyPlan.Kind.COOLING_TOWER).size() - 1) : 1;
        int clarifierIndex = tag.contains("ClarifierOddSize") ? tag.getInt("ClarifierOddSize") : tag.getInt("ClarifierSize") / 2;
        sizeIndices[3] = Math.clamp(clarifierIndex, 0, AssemblyPlan.sizes(AssemblyPlan.Kind.CLARIFIER).size() - 1);
        sizeIndices[4] = Math.clamp(tag.getInt("CasingTankSize"), 0, AssemblyPlan.sizes(AssemblyPlan.Kind.CASING_TANK).size() - 1);
        casingTankHeightIndex = Math.clamp(tag.getInt("CasingTankHeight"), 0, AssemblyPlan.Kind.CASING_TANK.heights().size() - 1);
        heightIndex = tag.contains("CoreHeight") ? Math.clamp(tag.getInt("CoreHeight"), 0, AssemblyPlan.CORE_HEIGHTS.size() - 1) : 1;
        clarifierHeightIndex = Math.clamp(tag.getInt("ClarifierHeight"), 0, 1);
        completed = tag.getBoolean("Completed");
        if (!packet) resetOnStop = state != State.STOPPED;
    }

    @Override public AABB getRenderBoundingBox() {
        plan();
        return bounds.inflate(2);
    }
}
