package net.createplant.block.entity;

import java.util.ArrayList;
import java.util.List;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import net.createmod.catnip.animation.LerpedFloat;
import net.createplant.block.ReactionTowerBlock;
import net.createplant.chemistry.ProcessModule;
import net.createplant.chemistry.ProcessRecipe;
import net.createplant.chemistry.ReactorModule;
import net.createplant.compat.display.MachineReadings;
import net.createplant.recipe.PressureReactionRecipe;
import net.createplant.registry.CEReactor;
import net.createplant.registry.CERecipeTypes;
import net.createplant.registry.CETransport;
import net.createplant.thermal.FluidMaterials;
import net.createplant.thermal.FluidThermal;
import net.createplant.thermal.Grade;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * One block of a reaction tower, a 3x3 Create tank three to twelve layers high, with a sight glass in the middle of each side. The centre block of each
 * layer keeps that layer's reaction tray and what lies on it: a catalyst, or the solid feed of a heated gas-solid reaction and what it has made. The
 * bottom centre owns the process and its fluids, so they stay put when the tower grows. Each layer whose tray holds a working catalyst runs one batch of
 * the catalytic recipe its catalyst and feed select; without catalysts, each tray holding a reaction's solid runs one batch of it (the "absorber"
 * pressure reactions). Without trays the tower is a high-pressure gas-liquid separator, one batch per layer. Fluid feed enters the outer sides of the
 * bottom layer and, in a reactor, rises along the shell to meet the beds from the top; products leave the bottom layer, at its outer sides or under
 * its centre, except a separator's gases, which leave the top layer. Solid feed goes in and solid products come out at the outer sides of the bottom
 * layer; catalysts go in and spent ones come out through the top. The owner also keeps the readings of the tower's gauges: the loop pressure and the
 * temperature of each layer.
 */
public class ReactionTowerBlockEntity extends PlantFluidTankBlockEntity {
    public static final int MIN_LAYERS = 3, MAX_LAYERS = 12;
    /** Liquid each layer adds to every feed and product buffer; gas is held at high pressure, 64 times as much. */
    public static final int VOLUME_PER_LAYER = 2000;
    /** Gauge scales: a full feed buffer reads 25 MPa; beds stand at the heat source's temperature and rise by the reaction heat, at most 600 degrees. */
    public static final float FULL_PRESSURE = 25, MAX_PRESSURE = 30, AMBIENT = 20, BED_RISE = 150, MAX_TEMPERATURE = 600;
    /** Rate constant of a fresh third of the tower: most of a fresh tower's conversion, and so its heat, comes in the first beds the gas meets. */
    private static final float FRESH_THIRD_RATE = 1.6f;
    private static List<ProcessRecipe> recipes;
    /** Feeds in slots 0 and 1, products in 2 and 3, as the recipe lists them. Kept by the bottom centre. */
    private final FluidStack[] tanks = {FluidStack.EMPTY, FluidStack.EMPTY, FluidStack.EMPTY, FluidStack.EMPTY};
    private final IFluidHandler bottomSide = new Port(Port.BOTTOM_SIDE), bottomCentre = new Port(Port.BOTTOM_CENTRE), topSide = new Port(Port.TOP_SIDE);
    private final IItemHandler catalystPort = new Catalysts(), solidPort = new Solids();
    /** Solid feed a tray holds, and as much of what it has made. */
    public static final int TRAY_LOAD = 16;
    private int progress, clock;
    private boolean syncNeeded, glazed, hatchOpen;
    /** 0 closed to 1 fully open, eased on the client. Kept by the owner. */
    private final LerpedFloat lid = LerpedFloat.linear().startWithValue(0);
    /** Loop pressure in MPa and the temperature of each layer, from the bottom, in degrees Celsius, as the gauges read them. Kept by the owner. */
    private float pressure;
    private final float[] temperatures = new float[MAX_LAYERS];
    private String status = "unformed";
    /** Kept by the centre block of each layer: whether it has a tray, and the solid feed and products on it. */
    private boolean tray;
    private ItemStack load = ItemStack.EMPTY, made = ItemStack.EMPTY;
    /** The length of the batch under way, for the goggles. Kept by the owner. */
    private int cycle;
    /** The side that faces whoever placed this block; the bottom centre's is the side its gauges are on. */
    private Direction front = Direction.NORTH;
    private ItemStack catalyst = ItemStack.EMPTY;

    public ReactionTowerBlockEntity(BlockPos pos, BlockState state) { super(pos, state);window = false;java.util.Arrays.fill(temperatures, AMBIENT); }

    /** The shell has no Create windows: neither Create's connectivity nor the wrench may add them; the sight glasses are its own. */
    @Override public void setWindows(boolean windows) { super.setWindows(false); }
    @Override public void toggleWindows() { }
    @Override protected void addVesselBehaviour(List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) { }
    @Override public void updateBoilerState() { }

    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CETransport.REACTION_TOWER_ENTITY.get(), (be, side) -> be.fluidPort(side));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CETransport.REACTION_TOWER_ENTITY.get(), (be, side) -> be.itemPort(side));
    }

    /** Every recipe that runs on a catalyst, and the uncatalysed gas reactions that run over empty trays as packing. */
    public static List<ProcessRecipe> recipes() {
        if (recipes == null) recipes = ProcessRecipe.all().stream().filter(recipe -> recipe.catalyst() != Items.AIR || recipe.module() == ProcessModule.REACTION_VESSEL).toList();
        return recipes;
    }
    public static boolean tray(ItemStack stack) { return stack.is(CEReactor.TRAY.get()); }
    public static boolean catalyst(ItemStack stack) { return !stack.isEmpty() && recipes().stream().anyMatch(recipe -> stack.is(recipe.catalyst())); }
    private static boolean spent(ItemStack stack) { return stack.isDamageableItem() && stack.getDamageValue() >= stack.getMaxDamage(); }

    /** Create notifies the parts before their controller knows its size, so the controller marks the outer walls of every part. */
    @Override public void notifyMultiUpdated() {
        super.notifyMultiUpdated();
        if (level == null || level.isClientSide || !isController()) return;
        boolean wide = getWidth() == 3;
        for (int x = 0; x < getWidth(); x++) for (int y = 0; y < getHeight(); y++) for (int z = 0; z < getWidth(); z++) {
            BlockPos at = worldPosition.offset(x, y, z);
            BlockState state = level.getBlockState(at);
            if (!(state.getBlock() instanceof ReactionTowerBlock)) continue;
            BlockState next = state.setValue(ReactionTowerBlock.NORTH, !wide || z == 0).setValue(ReactionTowerBlock.SOUTH, !wide || z == 2)
                .setValue(ReactionTowerBlock.WEST, !wide || x == 0).setValue(ReactionTowerBlock.EAST, !wide || x == 2);
            if (next != state) level.setBlock(at, next, 6);
            level.invalidateCapabilities(at);
        }
    }

    private ReactionTowerBlockEntity part(BlockPos at) {
        return level != null && level.hasChunkAt(at) && level.getBlockEntity(at) instanceof ReactionTowerBlockEntity part ? part : null;
    }
    private BlockPos base() { return getController() == null ? null : getController().offset(1, 0, 1); }
    /** The tower's owner if this block belongs to a formed one: 3x3 and three to twelve layers high. */
    public ReactionTowerBlockEntity owner() {
        var controller = getControllerBE();
        if (controller == null || controller.getWidth() != 3 || controller.getHeight() < MIN_LAYERS || controller.getHeight() > MAX_LAYERS) return null;
        return part(controller.getBlockPos().offset(1, 0, 1));
    }
    /** The bottom centre draws the trays of every layer above it and the gauges on the shell round them. */
    @Override protected net.minecraft.world.phys.AABB createRenderBoundingBox() {
        return worldPosition.equals(base()) ? new net.minecraft.world.phys.AABB(worldPosition).expandTowards(0, layers() + .5, 0).inflate(1.5, 0, 1.5) : super.createRenderBoundingBox();
    }
    public int layers() { return getControllerBE() == null ? 0 : getControllerBE().getHeight(); }
    /** The centre block of a layer, which keeps its tray and what lies on it. Called on the owner. */
    public ReactionTowerBlockEntity layer(int y) { return part(worldPosition.above(y)); }
    public boolean hasTray() { return tray; }
    public ItemStack load() { return load; }
    public ItemStack made() { return made; }
    public Direction front() { return front; }
    public void face(Direction front) {
        if (this.front == front) return;
        this.front = front;
        setChanged();
        sendData();
    }
    public Direction ladderSide() { return front.getClockWise(); }
    public BlockPos ladderBase() { return worldPosition.relative(front.getOpposite()).relative(ladderSide()); }
    public ItemStack catalystStack() { return catalyst; }
    public String status() { return status; }
    public Grade.Heat heaterGrade() {
        var level = heat();
        return level.isAtLeast(BlazeBurnerBlock.HeatLevel.SEETHING) ? Grade.Heat.VERY_HOT
            : level.isAtLeast(BlazeBurnerBlock.HeatLevel.KINDLED) ? Grade.Heat.HOT : Grade.Heat.AMBIENT;
    }
    public FluidStack contents(int bank) { return tanks[bank].copy(); }
    public int contentsCapacity(int bank) { return capacity(tanks[bank]); }
    public int progress() { return progress; }
    public FluidStack tank(int slot) { return tanks[slot].copy(); }

    private void changed() { syncNeeded = true;setChanged(); }
    private void setLayer(boolean tray, ItemStack catalyst) {
        this.tray = tray;this.catalyst = catalyst;
        layerChanged();
    }
    private void layerChanged() {
        setChanged();sendData();
        var owner = owner();
        if (owner != null) owner.syncNeeded = true;
    }

    /** Whether a tray or a catalyst can go in now. Called on the owner. */
    public boolean canInsert(ItemStack stack) { return slotFor(stack) != null; }
    private ReactionTowerBlockEntity slotFor(ItemStack stack) {
        boolean asTray = tray(stack);
        // The first tray turns a separator into a reactor, so it waits until the separator is empty.
        if (asTray && !hasTrays() && holdsFluid()) return null;
        // A catalyst and a solid feed never share the tower.
        if (!asTray && (!catalyst(stack) || !stack.is(catalystType(stack.getItem())) || holdsSolids())) return null;
        for (int y = 0; y < layers(); y++) {
            var layer = layer(y);
            if (layer != null && (asTray ? !layer.tray : layer.tray && layer.catalyst.isEmpty())) return layer;
        }
        return null;
    }
    /** The catalyst the loaded trays share, or the offered one when there is none yet. Called on the owner. */
    private Item catalystType(Item offered) {
        for (int y = 0; y < layers(); y++) { var layer = layer(y); if (layer != null && !layer.catalyst.isEmpty()) return layer.catalyst.getItem(); }
        return offered;
    }
    public BlockPos insert(ItemStack stack) {
        var layer = slotFor(stack);
        if (layer == null) throw new IllegalStateException("No layer takes " + stack);
        if (tray(stack)) layer.setLayer(true, layer.catalyst); else layer.setLayer(true, stack);
        return layer.getBlockPos();
    }
    public boolean hasTrays() {
        for (int y = 0; y < layers(); y++) { var layer = layer(y); if (layer != null && layer.tray) return true; }
        return false;
    }
    /** Whether a tray holds solid feed or products. Called on the owner. */
    public boolean holdsSolids() {
        for (int y = 0; y < layers(); y++) { var layer = layer(y); if (layer != null && (!layer.load.isEmpty() || !layer.made.isEmpty())) return true; }
        return false;
    }
    /**
     * Takes out the highest catalyst; when none is left, what lies on the highest tray, products first, and then the tray itself. Called on the owner.
     */
    public ItemStack takeTop() {
        for (int y = layers() - 1; y >= 0; y--) { var layer = layer(y); if (layer != null && !layer.catalyst.isEmpty()) { var taken = layer.catalyst;layer.setLayer(true, ItemStack.EMPTY);return taken; } }
        for (int y = layers() - 1; y >= 0; y--) {
            var layer = layer(y);
            if (layer == null || !layer.tray) continue;
            if (!layer.made.isEmpty()) { var taken = layer.made;layer.made = ItemStack.EMPTY;layer.layerChanged();return taken; }
            if (!layer.load.isEmpty()) { var taken = layer.load;layer.load = ItemStack.EMPTY;layer.layerChanged();return taken; }
            // Likewise the last tray stays while the reactor still holds fluid.
            if (holdsFluid() && trays() == 1) return ItemStack.EMPTY;
            layer.setLayer(false, ItemStack.EMPTY);return trayStack();
        }
        return ItemStack.EMPTY;
    }
    private int trays() {
        int count = 0;
        for (int y = 0; y < layers(); y++) { var layer = layer(y); if (layer != null && layer.tray) count++; }
        return count;
    }
    private boolean holdsFluid() {
        for (var tank : tanks) if (!tank.isEmpty()) return true;
        return false;
    }
    private static ItemStack trayStack() { return new ItemStack(CEReactor.TRAY.get()); }

    /** Drops this block's tray and what lies on it and releases the gas the owner held. */
    public void removed() {
        double x = worldPosition.getX() + .5, y = worldPosition.getY() + .5, z = worldPosition.getZ() + .5;
        if (tray) Containers.dropItemStack(level, x, y, z, trayStack());
        for (var stack : List.of(catalyst, load, made)) if (!stack.isEmpty()) Containers.dropItemStack(level, x, y, z, stack);
        tray = false;catalyst = ItemStack.EMPTY;load = ItemStack.EMPTY;made = ItemStack.EMPTY;
        for (int i = 0; i < tanks.length; i++) if (!tanks[i].isEmpty()) {
            if (net.createplant.thermal.FluidState.gas(tanks[i])) net.createplant.safety.ToxicGas.release(level, worldPosition, tanks[i]);
            tanks[i] = FluidStack.EMPTY;
        }
    }

    private boolean outer(Direction side) {
        if (!side.getAxis().isHorizontal()) return false;
        var state = getBlockState();
        return state.getBlock() instanceof ReactionTowerBlock && state.getValue(switch (side) {
            case NORTH -> ReactionTowerBlock.NORTH; case SOUTH -> ReactionTowerBlock.SOUTH; case WEST -> ReactionTowerBlock.WEST; default -> ReactionTowerBlock.EAST; });
    }
    private IFluidHandler fluidPort(Direction side) {
        var owner = side == null ? null : owner();
        if (owner == null) return null;
        if (side == Direction.DOWN && owner == this) return bottomCentre;
        if (!outer(side)) return null;
        int y = worldPosition.getY() - owner.worldPosition.getY();
        return y == 0 ? net.createplant.mixture.GasMixturePort.inlet(owner.bottomSide, owner)
            : y == layers() - 1 ? net.createplant.mixture.GasMixturePort.outlet(owner.topSide, owner) : null;
    }
    /** Catalysts through the top; solid feed and products at the outer sides of the bottom layer. */
    private IItemHandler itemPort(Direction side) {
        var owner = side == null ? null : owner();
        if (owner == null) return null;
        int y = worldPosition.getY() - owner.worldPosition.getY();
        if (side == Direction.UP) return y == layers() - 1 ? owner.catalystPort : null;
        return y == 0 && outer(side) ? owner.solidPort : null;
    }

    public boolean hasServiceHatch() {
        var owner = owner();
        return owner != null && worldPosition.equals(owner.getBlockPos().above(owner.layers() - 1));
    }
    public ItemStack topCatalyst() {
        for (int layer = layers() - 1; layer >= 0; layer--) {
            var part = layer(layer);
            if (part != null && !part.catalyst.isEmpty()) return part.catalyst.copy();
        }
        return ItemStack.EMPTY;
    }

    @Override public void tick() {
        super.tick();
        if (level != null && level.isClientSide) { lid.chase(hatchOpen ? 1 : 0, .12, LerpedFloat.Chaser.LINEAR);lid.tickChaser(); }
        if (level == null || level.isClientSide || !worldPosition.equals(base())) return;
        if (++clock % 5 == 0) process(5);
        if (syncNeeded && clock % 10 == 0) { syncNeeded = false;sendData(); }
    }

    /** The hottest heater under the bottom layer. */
    private BlazeBurnerBlock.HeatLevel heat() {
        var heat = BlazeBurnerBlock.HeatLevel.NONE;
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            var below = BasinBlockEntity.getHeatLevelOf(level.getBlockState(worldPosition.offset(x, -1, z)));
            if (below.isAtLeast(heat)) heat = below;
        }
        return heat;
    }

    private int capacity(FluidStack fluid) { return VOLUME_PER_LAYER * Math.max(1, layers()) * (net.createplant.thermal.FluidState.gas(fluid) ? 64 : 1); }
    /** With trays, the recipes of the loaded catalyst and the uncatalysed ones; without, the gas-liquid separations. */
    private List<ProcessRecipe> candidates() {
        if (!hasTrays()) return AmmoniaSeparatorBlockEntity.recipes();
        Item type = catalystType(Items.AIR);
        return recipes().stream().filter(recipe -> recipe.catalyst() == type || recipe.catalyst() == Items.AIR).toList();
    }
    /** The recipe the trays and the feed in the buffers select, or null. */
    public ProcessRecipe recipe() {
        for (var recipe : candidates()) if (feeds(recipe)) return recipe;
        return null;
    }
    private boolean feeds(ProcessRecipe recipe) {
        boolean any = false;
        for (int i = 0; i < 2; i++) {
            if (tanks[i].isEmpty()) continue;
            if (i >= recipe.inputs().size() || !recipe.accepts(i,tanks[i])) return false;
            any = true;
        }
        return any;
    }
    /** Layers whose tray holds the shared catalyst with batches left, or every tray for an uncatalysed reaction, in the order the gas meets them: from the top down. */
    private List<ReactionTowerBlockEntity> working(Item type) {
        List<ReactionTowerBlockEntity> working = new ArrayList<>();
        for (int y = layers() - 1; y >= 0; y--) {
            var layer = layer(y);
            if (layer != null && layer.tray && (type == Items.AIR || layer.catalyst.is(type) && !spent(layer.catalyst))) working.add(layer);
        }
        return working;
    }
    private boolean mixed() {
        Item type = catalystType(Items.AIR);
        for (int y = 0; y < layers(); y++) { var layer = layer(y); if (layer != null && !layer.catalyst.isEmpty() && !layer.catalyst.is(type)) return true; }
        return false;
    }

    /** Advances the batch by the given ticks; feed and products are settled only when it completes. Public so tests can drive it. */
    public void process(int ticks) {
        boolean formed = owner() == this;
        glaze(formed);
        if (formed && holdsSolids()) { processSolids(ticks);return; }
        boolean separator = formed && !hasTrays();
        var recipe = formed ? recipe() : null;
        var working = recipe == null || separator ? List.<ReactionTowerBlockEntity>of() : working(recipe.catalyst());
        int batches = separator ? layers() : working.size();
        int feedBatches = batches;
        if (recipe != null) {
            ProductionMonitor.plan(this, ProductionMonitor.batchRate(ProductionMonitor.fluids(recipe.outputs()), layers(), ((recipe.ticks()+4)/5)*5, 1), 0, 0);
            for (int i = 0; i < recipe.inputs().size(); i++) feedBatches = Math.min(feedBatches, tanks[i].getAmount() / recipe.inputs().get(i).getAmount());
            batches = feedBatches;
            for (int i = 0; i < recipe.outputs().size(); i++) {
                FluidStack out = recipe.outputs().get(i), stored = tanks[2 + i];
                batches = !stored.isEmpty() && !FluidThermal.compatible(stored, out) ? 0 : Math.min(batches, (capacity(out) - stored.getAmount()) / out.getAmount());
            }
        }
        String next = !formed ? "unformed" : hatchOpen ? "hatch" : separator ? (recipe == null || feedShort(recipe) ? "no_feed" : batches > 0 ? "running" : "output")
            : mixed() ? "mixed" : recipe == null ? "no_feed" : working.isEmpty() ? "catalyst"
            : !recipe.heatAllowed(heat()) ? "heat" : batches > 0 ? "running" : feedShort(recipe) ? "no_feed" : "output";
        if (!next.equals(status)) { status = next;changed(); }
        ProductionMonitor.limitedBy(this, switch (next) {
            case "running" -> batches < feedBatches ? "output" : feedBatches < (separator ? layers() : working.size()) ? "feed"
                : !separator && working.size() < layers() ? "catalyst" : "rated";
            case "unformed" -> "structure";
            case "no_feed" -> "feed";
            case "mixed" -> "catalyst";
            default -> next;
        });
        instruments(ticks, !separator && status.equals("running") ? recipe : null);
        if (!status.equals("running")) return;
        cycle = recipe.ticks();
        progress += ticks;
        if (progress < recipe.ticks()) { syncNeeded = true;return; }
        progress = 0;
        for (int i = 0; i < recipe.inputs().size(); i++) tanks[i] = tanks[i].copyWithAmount(tanks[i].getAmount() - recipe.inputs().get(i).getAmount() * batches);
        for (int i = 0; i < recipe.outputs().size(); i++) {
            FluidStack made = recipe.outputs().get(i).copyWithAmount(recipe.outputs().get(i).getAmount() * batches);
            tanks[2 + i] = tanks[2 + i].isEmpty() ? made : tanks[2 + i].copyWithAmount(tanks[2 + i].getAmount() + made.getAmount());
        }
        ProductionMonitor.produced(this, ProductionMonitor.fluids(recipe.outputs()) * batches, 0, 0);
        // Each layer that ran wears its own catalyst by one batch.
        for (int i = 0; i < batches && !separator && recipe.catalyst() != Items.AIR; i++) { var layer = working.get(i);var worn = layer.catalyst.copy();worn.setDamageValue(worn.getDamageValue() + 1);layer.setLayer(true, worn); }
        changed();
    }
    /** The heated gas-solid reactions that run on trays without a catalyst. */
    private List<PressureReactionRecipe> solidRecipes() {
        if (level == null) return List.of();
        var recipes = new ArrayList<PressureReactionRecipe>();
        for (var holder : level.getRecipeManager().getAllRecipesFor(CERecipeTypes.PRESSURE_REACTION.<RecipeInput, PressureReactionRecipe>getType()))
            if (holder.value().module() == ReactorModule.ABSORBER) recipes.add(holder.value());
        return recipes;
    }
    /** How many of a solid a tray uses per batch of the recipe, or 0 when the recipe takes no batch of it. */
    private static int solidUse(PressureReactionRecipe recipe, ItemStack solid) {
        if (solid.isEmpty() || recipe.getIngredients().isEmpty()) return 0;
        for (var ingredient : recipe.getIngredients()) if (!ingredient.test(solid)) return 0;
        return recipe.getIngredients().size();
    }
    /** Whether a tray reaction takes the solid; a tower holding a catalyst takes none. Called on the owner. */
    private boolean solidFeed(ItemStack stack) {
        return catalystType(Items.AIR) == Items.AIR && solidRecipes().stream().anyMatch(recipe -> solidUse(recipe, stack) > 0);
    }
    /** Whether a tray reaction takes the fluid in the slot beside the other feed. */
    private boolean solidFluidFeed(int slot, FluidStack stack) {
        if (!hasTrays() || catalystType(Items.AIR) != Items.AIR) return false;
        FluidStack other = tanks[1 - slot];
        for (var recipe : solidRecipes()) {
            var fluids = recipe.getFluidIngredients();
            if (fluids.stream().noneMatch(ingredient -> ingredient.ingredient().test(stack))) continue;
            if (other.isEmpty() || fluids.stream().anyMatch(ingredient -> ingredient.ingredient().test(other))) return true;
        }
        return false;
    }
    /** The feed slot that holds a fluid ingredient, or -1. */
    private int feedSlot(net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient ingredient) {
        for (int slot = 0; slot < 2; slot++) if (!tanks[slot].isEmpty() && ingredient.ingredient().test(tanks[slot])) return slot;
        return -1;
    }
    /** Whether a tray has room for one batch's solid products. */
    private static boolean roomFor(ReactionTowerBlockEntity layer, PressureReactionRecipe recipe) {
        int count = layer.made.getCount();
        for (var result : recipe.getRollableResults()) {
            var stack = result.getStack();
            if (!layer.made.isEmpty() && !ItemStack.isSameItemSameComponents(layer.made, stack)) return false;
            count += stack.getCount();
        }
        return count <= TRAY_LOAD;
    }

    /**
     * Without a catalyst, every tray that holds a reaction's solid and has room for its products runs one batch of it with the fluid feed, from the
     * top down. The heat under the base must meet the recipe; solid products stay on the tray, fluid ones go to the product buffers.
     */
    private void processSolids(int ticks) {
        PressureReactionRecipe recipe = null;
        List<ReactionTowerBlockEntity> working = new ArrayList<>();
        for (var candidate : solidRecipes()) {
            working.clear();
            for (int y = layers() - 1; y >= 0; y--) {
                var layer = layer(y);
                int use = layer == null || !layer.tray ? 0 : solidUse(candidate, layer.load);
                if (use > 0 && layer.load.getCount() >= use) working.add(layer);
            }
            if (!working.isEmpty()) { recipe = candidate;break; }
        }
        int batches = 0, feedBatches = 0;
        if (recipe != null) {
            final var chosen = recipe;
            working.removeIf(layer -> !roomFor(layer, chosen));
            feedBatches = layers();
            for (var ingredient : recipe.getFluidIngredients()) {
                int slot = feedSlot(ingredient);
                feedBatches = slot < 0 ? 0 : Math.min(feedBatches, tanks[slot].getAmount() / ingredient.amount());
            }
            batches = Math.min(working.size(), feedBatches);
            for (int i = 0; i < recipe.getFluidResults().size(); i++) {
                FluidStack out = recipe.getFluidResults().get(i), stored = tanks[2 + i];
                batches = !stored.isEmpty() && !FluidThermal.compatible(stored, out) ? 0 : Math.min(batches, (capacity(out) - stored.getAmount()) / out.getAmount());
            }
        }
        String next = hatchOpen ? "hatch" : recipe == null ? "no_feed" : !recipe.getRequiredHeat().testBlazeBurner(heat()) ? "heat"
            : batches > 0 ? "running" : feedBatches == 0 ? "no_feed" : "output";
        if (!next.equals(status)) { status = next;changed(); }
        ProductionMonitor.limitedBy(this, switch (next) {
            case "running" -> batches < trays() ? "throughput" : "rated";
            case "no_feed" -> "feed";
            default -> next;
        });
        instruments(ticks, null);
        if (!status.equals("running")) { if (progress > 0) { progress = 0;changed(); } return; }
        cycle = Math.clamp(recipe.getProcessingDuration() > 0 ? recipe.getProcessingDuration() : 100, 1, 200);
        progress += ticks;
        if (progress < cycle) { syncNeeded = true;return; }
        progress = 0;
        for (var ingredient : recipe.getFluidIngredients()) {
            int slot = feedSlot(ingredient);
            tanks[slot] = tanks[slot].copyWithAmount(tanks[slot].getAmount() - ingredient.amount() * batches);
        }
        for (int i = 0; i < recipe.getFluidResults().size(); i++) {
            FluidStack made = recipe.getFluidResults().get(i).copyWithAmount(recipe.getFluidResults().get(i).getAmount() * batches);
            tanks[2 + i] = tanks[2 + i].isEmpty() ? made : FluidThermal.mix(tanks[2 + i], made, made.getAmount());
        }
        int items = 0;
        for (int i = 0; i < batches; i++) {
            var layer = working.get(i);
            layer.load = layer.load.copyWithCount(layer.load.getCount() - solidUse(recipe, layer.load));
            for (var product : recipe.rollResults(level.random)) {
                layer.made = layer.made.isEmpty() ? product.copy() : layer.made.copyWithCount(layer.made.getCount() + product.getCount());
                items += product.getCount();
            }
            layer.layerChanged();
        }
        ProductionMonitor.produced(this, ProductionMonitor.fluids(recipe.getFluidResults()) * batches, items, 0);
        changed();
    }

    /** Opens the sight glasses in the middle of every side while the tower is formed. */
    private void glaze(boolean open) {
        if (open == glazed) return;
        glazed = open;setChanged();
        BlockPos corner = worldPosition.offset(-1, 0, -1);
        for (int y = 0; y < layers(); y++) for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) {
            if ((x == 1) == (z == 1)) continue;
            BlockPos at = corner.offset(x, y, z);
            BlockState state = level.getBlockState(at);
            if (state.getBlock() instanceof ReactionTowerBlock && state.getValue(ReactionTowerBlock.WINDOW) != open) level.setBlock(at, state.setValue(ReactionTowerBlock.WINDOW, open), 2);
        }
    }
    public boolean glazed() { return glazed; }
    public boolean hatchOpen() { return hatchOpen; }
    public float lidOpening(float partialTicks) { return lid.getValue(partialTicks); }
    /** Opens or shuts the hatch in the middle of the top. Opening lets out the gas the tower holds, as a cloud. Called on the owner. */
    public void toggleHatch() {
        hatchOpen = !hatchOpen;
        if (hatchOpen) for (int i = 0; i < tanks.length; i++) if (!tanks[i].isEmpty() && net.createplant.thermal.FluidState.gas(tanks[i])) {
            net.createplant.safety.ToxicGas.release(level, worldPosition.above(layers()), tanks[i]);
            tanks[i] = FluidStack.EMPTY;
        }
        level.playSound(null, worldPosition.above(layers()), hatchOpen ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE,
            SoundSource.BLOCKS, 1, .8f);
        changed();sendData();
    }
    public float pressure() { return pressure; }
    /** The temperature of a layer, counted from the bottom. */
    public float layerTemperature(int layer) { return temperatures[layer]; }

    /**
     * Moves the gauge readings toward the present state. The loop pressure follows the gas the tower holds. While a reactor runs, the gas meets the beds
     * from the top: each layer converts the share of the remaining approach to equilibrium its catalyst's activity allows, and is warmed by the
     * conversion in it, scaled so a third of the tower's height rises as one bed does. A fresh tower converts and heats most in its upper layers; as the
     * catalysts wear, the upper layers cool and the conversion, and the heat, move down into the lower ones.
     */
    private void instruments(int ticks, ProcessRecipe running) {
        long gas = 0;
        for (var tank : tanks) if (!tank.isEmpty() && net.createplant.thermal.FluidState.gas(tank)) gas += tank.getAmount();
        float pressureTarget = Math.min(MAX_PRESSURE, FULL_PRESSURE * gas / (VOLUME_PER_LAYER * 64f * Math.max(1, layers())));
        var heat = heat();
        float inlet = heat.isAtLeast(BlazeBurnerBlock.HeatLevel.SEETHING) ? 450 : heat.isAtLeast(BlazeBurnerBlock.HeatLevel.KINDLED) ? 380 : AMBIENT;
        int layers = layers();
        float[] targets = new float[MAX_LAYERS];
        java.util.Arrays.fill(targets, inlet);
        if (running != null) {
            double remaining = 1, rate = FRESH_THIRD_RATE * 3.0 / layers;
            for (int y = layers - 1; y >= 0; y--) {
                var layer = layer(y);
                double activity = layer == null || !layer.tray ? 0 : running.catalyst() == Items.AIR ? 1 : !layer.catalyst.is(running.catalyst()) || spent(layer.catalyst) ? 0
                    : 1 - (double) layer.catalyst.getDamageValue() / layer.catalyst.getMaxDamage();
                double converted = remaining * (1 - Math.exp(-rate * activity));
                remaining -= converted;
                targets[y] = (float) Math.min(MAX_TEMPERATURE, inlet + BED_RISE * converted * layers / 3);
            }
        }
        float settle = 1 - (float) Math.exp(-ticks / 60.0), quick = 1 - (float) Math.exp(-ticks / 20.0);
        float before = pressure;
        pressure += (pressureTarget - pressure) * quick;
        boolean moved = Math.round(before * 10) != Math.round(pressure * 10);
        for (int y = 0; y < MAX_LAYERS; y++) {
            float old = temperatures[y];
            temperatures[y] += (targets[y] - temperatures[y]) * settle;
            moved |= Math.round(old) != Math.round(temperatures[y]);
        }
        if (moved) changed();
    }
    private boolean feedShort(ProcessRecipe recipe) {
        for (int i = 0; i < recipe.inputs().size(); i++) if (tanks[i].getAmount() < recipe.inputs().get(i).getAmount()) return true;
        return false;
    }

    private final class Port implements IFluidHandler {
        static final int BOTTOM_SIDE = 0, BOTTOM_CENTRE = 1, TOP_SIDE = 2;
        private final int place;
        Port(int place) { this.place = place; }
        private boolean live() { return !isRemoved() && level != null && !level.isClientSide && owner() == ReactionTowerBlockEntity.this; }
        /** Feed always enters the sides of the bottom layer. */
        private boolean feeds() { return live() && place == BOTTOM_SIDE; }
        /** A reactor's products leave the bottom layer, at its sides or under its centre; a separator's liquids do too, and its gases leave the top. */
        private boolean gives(int slot) {
            if (!live() || tanks[slot].isEmpty()) return false;
            boolean top = place == TOP_SIDE;
            return hasTrays() ? !top : top == net.createplant.thermal.FluidState.gas(tanks[slot]);
        }
        @Override public int getTanks() { return 4; }
        /** The feeds this port takes, then the products it gives. */
        @Override public FluidStack getFluidInTank(int slot) {
            if (slot < 0 || slot >= 4) return FluidStack.EMPTY;
            return slot < 2 ? feeds() ? tanks[slot].copy() : FluidStack.EMPTY : gives(slot) ? tanks[slot].copy() : FluidStack.EMPTY;
        }
        @Override public int getTankCapacity(int slot) { var stored = getFluidInTank(slot);return stored.isEmpty() ? VOLUME_PER_LAYER * Math.max(1, layers()) : capacity(stored); }
        @Override public boolean isFluidValid(int slot, FluidStack stack) { return slot >= 0 && slot < 2 && feeds() && accepts(slot, stack); }
        /** A feed joins the slot that holds it, or an empty slot when a recipe the tower can run takes it there beside the other feed. */
        private boolean accepts(int slot, FluidStack stack) {
            if (!tanks[slot].isEmpty()) return FluidThermal.mixable(tanks[slot], stack);
            if (solidFluidFeed(slot, stack)) return true;
            FluidStack other = tanks[1 - slot];
            for (var recipe : candidates()) {
                if (slot >= recipe.inputs().size() || !recipe.accepts(slot,stack)) continue;
                if (other.isEmpty() || 1 - slot < recipe.inputs().size() && recipe.accepts(1-slot,other)) return true;
            }
            return false;
        }
        @Override public int fill(FluidStack stack, FluidAction action) {
            if (stack.isEmpty() || !feeds()) return 0;
            for (int slot = 0; slot < 2; slot++) {
                if (!tanks[slot].isEmpty() && !FluidThermal.mixable(tanks[slot], stack)) continue;
                if (!accepts(slot, stack)) continue;
                int n = Math.max(0, Math.min(stack.getAmount(), capacity(stack) - tanks[slot].getAmount()));
                if (n > 0 && action.execute()) { tanks[slot] = tanks[slot].isEmpty() ? stack.copyWithAmount(n) : FluidThermal.mix(tanks[slot], stack, n);changed(); }
                return n;
            }
            return 0;
        }
        @Override public FluidStack drain(FluidStack request, FluidAction action) {
            for (int slot = 2; slot < 4; slot++) if (gives(slot) && FluidThermal.compatible(tanks[slot], request)) return drain(slot, request.getAmount(), action);
            return FluidStack.EMPTY;
        }
        @Override public FluidStack drain(int amount, FluidAction action) {
            for (int slot = 2; slot < 4; slot++) if (gives(slot)) return drain(slot, amount, action);
            return FluidStack.EMPTY;
        }
        private FluidStack drain(int slot, int amount, FluidAction action) {
            int n = Math.min(amount, tanks[slot].getAmount());
            if (n <= 0) return FluidStack.EMPTY;
            var out = tanks[slot].copyWithAmount(n);
            if (action.execute()) { tanks[slot] = n == tanks[slot].getAmount() ? FluidStack.EMPTY : tanks[slot].copyWithAmount(tanks[slot].getAmount() - n);changed(); }
            return out;
        }
    }

    /** One slot per layer: a catalyst goes into a tray that has none, and only a spent one comes out. */
    private final class Catalysts implements IItemHandler {
        private ReactionTowerBlockEntity layer(int slot) { return owner() == ReactionTowerBlockEntity.this && slot >= 0 && slot < layers() ? ReactionTowerBlockEntity.this.layer(slot) : null; }
        @Override public int getSlots() { return layers(); }
        @Override public ItemStack getStackInSlot(int slot) { var layer = layer(slot);return layer == null ? ItemStack.EMPTY : layer.catalyst; }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            var layer = layer(slot);
            if (layer == null || !layer.tray || !layer.catalyst.isEmpty() || !isItemValid(slot, stack)) return stack;
            if (!simulate) layer.setLayer(true, stack.copyWithCount(1));
            return stack.copyWithCount(stack.getCount() - 1);
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            var layer = layer(slot);
            if (layer == null || amount <= 0 || !spent(layer.catalyst)) return ItemStack.EMPTY;
            var taken = layer.catalyst;
            if (!simulate) layer.setLayer(true, ItemStack.EMPTY);
            return taken.copy();
        }
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return catalyst(stack) && stack.is(catalystType(stack.getItem())) && !spent(stack) && !holdsSolids(); }
    }

    /**
     * Two slots per layer, from the bottom: the solid feed on each tray, which takes a tray reaction's solid and gives nothing, then what each tray has
     * made, which only gives. A funnel or chute fills the lowest tray with room first.
     */
    private final class Solids implements IItemHandler {
        private ReactionTowerBlockEntity layer(int slot) {
            int y = slot % Math.max(1, layers());
            return owner() == ReactionTowerBlockEntity.this && slot >= 0 && slot < 2 * layers() ? ReactionTowerBlockEntity.this.layer(y) : null;
        }
        @Override public int getSlots() { return 2 * layers(); }
        @Override public ItemStack getStackInSlot(int slot) { var layer = layer(slot);return layer == null ? ItemStack.EMPTY : slot < layers() ? layer.load : layer.made; }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            var layer = layer(slot);
            if (layer == null || slot >= layers() || !layer.tray || stack.isEmpty() || !isItemValid(slot, stack)
                || !layer.load.isEmpty() && !ItemStack.isSameItemSameComponents(layer.load, stack)) return stack;
            int moved = Math.min(stack.getCount(), TRAY_LOAD - layer.load.getCount());
            if (moved <= 0) return stack;
            if (!simulate) { layer.load = layer.load.isEmpty() ? stack.copyWithCount(moved) : layer.load.copyWithCount(layer.load.getCount() + moved);layer.layerChanged(); }
            return stack.copyWithCount(stack.getCount() - moved);
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            var layer = layer(slot);
            if (layer == null || slot < layers() || amount <= 0 || layer.made.isEmpty()) return ItemStack.EMPTY;
            int taken = Math.min(amount, layer.made.getCount());
            var out = layer.made.copyWithCount(taken);
            if (!simulate) { layer.made = taken == layer.made.getCount() ? ItemStack.EMPTY : layer.made.copyWithCount(layer.made.getCount() - taken);layer.layerChanged(); }
            return out;
        }
        @Override public int getSlotLimit(int slot) { return TRAY_LOAD; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return slot >= 0 && slot < layers() && solidFeed(stack); }
    }

    @Override public boolean addToGoggleTooltip(List<Component> text, boolean sneaking) {
        var owner = owner();
        String key = "reaction_tower.create_refinery_n_reactors.";
        text.add(getBlockState().getBlock().getName());
        if (owner == null) { text.add(Component.translatable(key + "size", MIN_LAYERS, MAX_LAYERS));return true; }
        text.add(Component.translatable(key + "status." + owner.status));
        int trays = 0, working = 0, life = 0, solids = 0, made = 0;
        for (int y = 0; y < owner.layers(); y++) {
            var layer = owner.layer(y);
            if (layer == null || !layer.tray) continue;
            trays++;solids += layer.load.getCount();made += layer.made.getCount();
            if (!layer.catalyst.isEmpty() && !spent(layer.catalyst)) { working++;life = Math.max(life, layer.catalyst.getMaxDamage() - layer.catalyst.getDamageValue()); }
        }
        text.add(trays == 0 ? Component.translatable(key + "separator") : solids + made > 0 ? Component.translatable(key + "solids", trays, owner.layers(), solids, made)
            : Component.translatable(key + "catalyst", working, trays, owner.layers(), life));
        text.add(Component.translatable(key + "contents", owner.tanks[0].getAmount() + owner.tanks[1].getAmount(), owner.tanks[2].getAmount() + owner.tanks[3].getAmount(),
            owner.progress, owner.cycle));
        MachineReadings.compact(text);
        return true;
    }

    @Override public void write(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        super.write(tag, lookup, packet);
        tag.putBoolean("Basket", tray);
        if (!load.isEmpty()) tag.put("TrayLoad", load.save(lookup));
        if (!made.isEmpty()) tag.put("TrayMade", made.save(lookup));
        if (cycle > 0) tag.putInt("TowerCycle", cycle);
        tag.putString("Front", front.getSerializedName());
        if (!catalyst.isEmpty()) tag.put("Catalyst", catalyst.save(lookup));
        if (!status.equals("unformed") || progress > 0) { tag.putString("TowerStatus", status);tag.putInt("TowerProgress", progress); }
        if (glazed) tag.putBoolean("Glazed", true);
        if (hatchOpen) tag.putBoolean("HatchOpen", true);
        if (pressure > 0) tag.putFloat("Pressure", pressure);
        for (int y = 0; y < MAX_LAYERS; y++) if (temperatures[y] != AMBIENT) tag.putFloat("Temperature" + y, temperatures[y]);
        for (int i = 0; i < tanks.length; i++) if (!tanks[i].isEmpty()) tag.put("TowerTank" + i, tanks[i].save(lookup));
    }
    @Override protected void read(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        super.read(tag, lookup, packet);
        tray = tag.getBoolean("Basket");
        load = tag.contains("TrayLoad") ? ItemStack.parseOptional(lookup, tag.getCompound("TrayLoad")) : ItemStack.EMPTY;
        made = tag.contains("TrayMade") ? ItemStack.parseOptional(lookup, tag.getCompound("TrayMade")) : ItemStack.EMPTY;
        cycle = tag.getInt("TowerCycle");
        var facing = Direction.byName(tag.getString("Front"));
        front = facing != null && facing.getAxis().isHorizontal() ? facing : Direction.NORTH;
        catalyst = tag.contains("Catalyst") ? ItemStack.parseOptional(lookup, tag.getCompound("Catalyst")) : ItemStack.EMPTY;
        status = tag.contains("TowerStatus") ? tag.getString("TowerStatus") : "unformed";
        progress = Math.max(0, tag.getInt("TowerProgress"));
        glazed = tag.getBoolean("Glazed");
        hatchOpen = tag.getBoolean("HatchOpen");
        pressure = Math.clamp(tag.getFloat("Pressure"), 0, MAX_PRESSURE);
        for (int y = 0; y < MAX_LAYERS; y++) temperatures[y] = tag.contains("Temperature" + y) ? tag.getFloat("Temperature" + y) : AMBIENT;
        for (int i = 0; i < tanks.length; i++) tanks[i] = tag.contains("TowerTank" + i) ? FluidStack.parseOptional(lookup, tag.getCompound("TowerTank" + i)) : FluidStack.EMPTY;
    }
}
