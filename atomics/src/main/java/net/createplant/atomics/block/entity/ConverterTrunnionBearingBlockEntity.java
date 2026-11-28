package net.createplant.atomics.block.entity;
import net.createplant.block.entity.ProductionMonitor;
import net.createplant.atomics.registry.CEAtomicsRecipeTypes;

import net.createplant.thermal.FluidForm;
import java.util.List;
import java.util.Map;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.bearing.BearingBlock;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.foundation.fluid.SmartFluidTank;

import net.createplant.atomics.block.ConverterShellBlock;
import net.createplant.atomics.block.ConverterShellBlock.Part;
import net.createplant.atomics.block.ConverterStructure;
import net.createplant.compat.display.MachineReadings;
import net.createplant.atomics.recipe.ConverterRecipe;
import net.createplant.atomics.registry.CEConverter;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CERecipeTypes;
import net.createplant.safety.ToxicGas;
import net.createplant.thermal.FluidThermal;
import net.createplant.thermal.PhaseChange;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Stationary inventory and powered Create bearing for the exact glued converter vessel. */
public class ConverterTrunnionBearingBlockEntity extends MechanicalBearingBlockEntity implements IHaveGoggleInformation {
    public static final int INPUT_SLOTS = 6, OUTPUT_SLOTS = 4;
    public static final int GAS_INPUT_CAPACITY = 64_000, GAS_OUTPUT_CAPACITY = 128_000;
    public static final int METAL_CAPACITY = 16_000;
    public static final float MAX_ANGLE = 60, UPRIGHT_EPSILON = .5f, TAPPED_ANGLE = 59;
    public static final double ACTIVE_SURFACE_KELVIN = 1900;
    private static final String TOOLTIP = "converter.create_refinery_n_reactors.";
    private static final String ASSEMBLY = "gui.assembly.exception.create_refinery_n_reactors.";

    private final ItemStackHandler inventory = new ItemStackHandler(INPUT_SLOTS + OUTPUT_SLOTS) {
        @Override protected void onContentsChanged(int slot) { changed(); }
    };
    private final ItemStackHandler reservedItems = new ItemStackHandler(INPUT_SLOTS) {
        @Override protected void onContentsChanged(int slot) { changed(); }
    };
    private final SmartFluidTank gasInput = new SmartFluidTank(GAS_INPUT_CAPACITY, stack -> changed());
    private final SmartFluidTank gasOutput = new SmartFluidTank(GAS_OUTPUT_CAPACITY, stack -> changed());
    private final SmartFluidTank reservedGas = new SmartFluidTank(GAS_INPUT_CAPACITY, stack -> changed());
    private final SmartFluidTank metalInput = new SmartFluidTank(METAL_CAPACITY, stack -> changed());
    private final SmartFluidTank metalOutput = new SmartFluidTank(METAL_CAPACITY, stack -> changed());
    private final SmartFluidTank reservedMetal = new SmartFluidTank(METAL_CAPACITY, stack -> changed());
    private final IItemHandler rawPort = new RawPort();
    private final IItemHandler tapPort = new TapPort();
    private final IFluidHandler lancePort = new LancePort();
    private final IFluidHandler offgasPort = new OffgasPort();
    private final IFluidHandler feedMetalPort = new MetalPort(true);
    private final IFluidHandler tapMetalPort = new MetalPort(false);

    private BlockPos origin;
    private Direction structureFacing = Direction.NORTH;
    private boolean formed, assigned, revalidate = true, dismantled;
    private int clock, progress;
    private ResourceLocation activeRecipe;
    private String status = "unformed";

    public ConverterTrunnionBearingBlockEntity(BlockPos pos, BlockState state) {
        super(CEConverter.TRUNNION_ENTITY.get(), pos, state);
        gasInput.setValidator(this::acceptsGas);
        gasOutput.setValidator(stack -> stack.is(CEFluids.CONVERTER_GAS.get()));
        metalInput.setValidator(this::acceptsMetal);
        metalOutput.setValidator(TorpedoTankBlockEntity::accepts);
    }

    public boolean formed() { return formed; }
    public BlockPos origin() { return origin; }
    public Direction structureFacing() { return structureFacing; }
    public String status() { return status; }
    public int progress() { return progress; }
    public float angle() { return Math.clamp(angle, 0, MAX_ANGLE); }
    public boolean upright() { return !running && angle() <= UPRIGHT_EPSILON; }
    public boolean tilted() { return running && angle() >= TAPPED_ANGLE; }
    public boolean hot() { return activeRecipe != null && formed; }
    public ItemStackHandler inventory() { return inventory; }
    public SmartFluidTank gasInput() { return gasInput; }
    public SmartFluidTank gasOutput() { return gasOutput; }

    /** Client-only storyboard state; the Ponder scene rotates the real vessel section separately. */
    public void ponderVisual(BlockPos base, Direction facing, float tilt, boolean blowing) {
        origin = base;
        structureFacing = facing;
        formed = true;
        angle = Math.clamp(tilt, 0, MAX_ANGLE);
        activeRecipe = blowing ? net.createplant.CreatePlant.location("converter/refine_hot_metal") : null;
        status = blowing ? "blowing" : angle >= TAPPED_ANGLE ? "tapping" : "idle";
    }

    @Override public void initialize() {
        super.initialize();
        updateGeometry();
        ConverterStructure.track(this);
        revalidate = true;
    }

    @Override public void onChunkUnloaded() {
        ConverterStructure.untrack(this);
        super.onChunkUnloaded();
    }

    @Override public void remove() {
        ConverterStructure.untrack(this);
        if (level != null && !level.isClientSide && running) {
            angle = 0;
            applyRotation();
        }
        super.remove();
        if (level != null && !level.isClientSide) dismantle();
    }

    private void updateGeometry() {
        BlockState state = getBlockState();
        if (!state.hasProperty(BearingBlock.FACING)) return;
        Direction bearingFacing = state.getValue(BearingBlock.FACING);
        if (!bearingFacing.getAxis().isHorizontal()) return;
        structureFacing = ConverterStructure.structureFacing(bearingFacing);
        origin = ConverterStructure.originFromBearing(worldPosition, bearingFacing);
    }

    @Override public float calculateStressApplied() {
        lastStressApplied = formed ? 64 : 0;
        return lastStressApplied;
    }

    @Override public void onSpeedChanged(float previousSpeed) {
        super.onSpeedChanged(previousSpeed);
        if (running || getSpeed() <= 0) assembleNextTick = false;
    }

    @Override public float getInterpolatedAngle(float partialTicks) {
        return Math.clamp(super.getInterpolatedAngle(partialTicks), 0, MAX_ANGLE);
    }

    @Override public boolean isNearInitialAngle() { return angle() <= UPRIGHT_EPSILON; }

    @Override public void assemble() {
        if (level == null || level.isClientSide || running || getAngularSpeed() <= 0) return;
        updateGeometry();
        if (origin == null || !getBlockState().hasProperty(BearingBlock.FACING)
            || !getBlockState().getValue(BearingBlock.FACING).getAxis().isHorizontal()) {
            fail("converter_direction");
            return;
        }
        ConverterStructure.Validation fixed = ConverterStructure.validateFixed(level, origin, structureFacing);
        if (!fixed.valid()) {
            fail(fixed.unloaded() ? AssemblyException.unloadedChunk(fixed.fault())
                : new AssemblyException(Component.translatable(ASSEMBLY + "converter_frame")), fixed.unloaded());
            return;
        }
        ConverterStructure.Validation vessel = ConverterStructure.validateVessel(level, origin, structureFacing);
        if (!vessel.valid()) {
            if (vessel.unloaded()) fail(AssemblyException.unloadedChunk(vessel.fault()), true);
            else fail(level.getBlockState(vessel.fault()).isAir() ? "converter_missing" : "converter_material");
            return;
        }

        Direction direction = getBlockState().getValue(BearingBlock.FACING);
        BearingContraption probe = new BearingContraption(false, direction);
        try {
            probe.searchMovedStructure(level, worldPosition.relative(direction), null);
        } catch (AssemblyException exception) {
            fail(exception);
            return;
        }
        Map<BlockPos, BlockState> expected = ConverterStructure.expectedMovingBlocks(origin, structureFacing);
        Map<BlockPos, StructureBlockInfo> found = probe.getBlocks();
        if (!found.keySet().containsAll(expected.keySet())) {
            fail("converter_unglued");
            return;
        }
        if (!expected.keySet().containsAll(found.keySet())) {
            fail("converter_extra");
            return;
        }
        for (Map.Entry<BlockPos, BlockState> entry : expected.entrySet()) {
            StructureBlockInfo info = found.get(entry.getKey());
            if (info == null || !ConverterStructure.matchesMovingState(info.state(), entry.getValue())) {
                fail("converter_material");
                return;
            }
        }

        super.assemble();
        if (!running) return;
        formed = true;
        status = "tilting";
        assignPorts(true, false);
        revalidate = true;
        changed();
    }

    private void fail(String suffix) { fail(new AssemblyException(Component.translatable(ASSEMBLY + suffix)), false); }

    private void fail(AssemblyException exception) { fail(exception, false); }

    private void fail(AssemblyException exception, boolean unloaded) {
        lastException = exception;
        formed = false;
        status = unloaded ? "unloaded" : "unformed";
        sendData();
    }

    @Override public void attach(ControlledContraptionEntity contraption) {
        super.attach(contraption);
        if (movedContraption != contraption) return;
        angle = Math.clamp(angle, 0, MAX_ANGLE);
        applyRotation();
        updateGeometry();
        revalidate = true;
    }

    @Override public void tick() {
        boolean tiltRequested = getSpeed() > 0 || assembleNextTick;
        super.tick();
        if (level == null) return;
        if (running && angle > MAX_ANGLE) {
            angle = MAX_ANGLE;
            applyRotation();
        }
        if (level.isClientSide) return;
        clock++;
        if (running && angle < 0) {
            angle = 0;
            applyRotation();
            disassemble();
            revalidate = true;
        }
        if (revalidate || !formed && clock % 20 == Math.floorMod(worldPosition.hashCode(), 20)) validateStructure();
        if (!formed) { ProductionMonitor.limitedBy(this, "structure");return; }
        if (running || tiltRequested || getSpeed() > 0) {
            String next = tilted() && hasProduct() ? "tapping" : "tilting";
            ProductionMonitor.limitedBy(this, "upright");
            if (!status.equals(next)) { status = next;changed(); }
            if (running) invalidatePortCapabilities();
            return;
        }
        runProcessUnchecked(1);
        ProductionMonitor.limitedBy(this, switch (status) {
            case "blowing" -> "rated";
            case "no_feed" -> hasProduct() ? "tap" : "feed";
            case "gas_disconnected" -> "offgas";
            case "output_full" -> hasProduct() ? "tap" : "output";
            case "ready" -> "tap";
            case "tilting" -> "upright";
            default -> "recipe";
        });
    }

    public void validateStructure() {
        revalidate = false;
        updateGeometry();
        if (origin == null) return;
        ConverterStructure.Validation validation = ConverterStructure.validateFixed(level, origin, structureFacing);
        if (validation.valid() && !running) validation = ConverterStructure.validateVessel(level, origin, structureFacing);
        if (!validation.valid()) {
            formed = false;
            status = validation.unloaded() ? "unloaded" : "unformed";
            assignPorts(false, false);
            changed();
            return;
        }
        boolean wasFormed = formed;
        formed = true;
        assignPorts(true, false);
        if (!running) status = activeRecipe == null ? hasProduct() ? "ready" : "idle" : "blowing";
        if (!wasFormed && hasNetwork()) getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
        changed();
    }

    public void structureChanged() {
        if (level == null || level.isClientSide || dismantled) return;
        formed = false;
        revalidate = true;
        status = "unformed";
        invalidatePortCapabilities();
        setChanged();
    }

    public boolean containsStructurePosition(BlockPos pos) {
        if (origin == null) updateGeometry();
        if (origin == null) return false;
        BlockPos delta = pos.subtract(origin);
        Direction right = structureFacing.getClockWise();
        int x = delta.getX() * right.getStepX() + delta.getZ() * right.getStepZ();
        int z = delta.getX() * structureFacing.getStepX() + delta.getZ() * structureFacing.getStepZ();
        return Math.abs(x) <= 4 && delta.getY() >= 0 && delta.getY() <= 10 && Math.abs(z) <= 4;
    }

    private void assignPorts(boolean forming, boolean clearController) {
        if (origin == null || level == null) return;
        assigned = forming;
        for (ConverterStructure.Cell cell : ConverterStructure.fixedCells()) {
            if (!cell.shell()) continue;
            BlockPos pos = ConverterStructure.at(origin, structureFacing, cell.x(), cell.y(), cell.z());
            if (!level.hasChunkAt(pos) || !level.getBlockState(pos).is(CEConverter.SHELL.get())) continue;
            BlockState state = level.getBlockState(pos);
            ConverterShellBlock.setAppearance(level, pos, state
                .setValue(ConverterShellBlock.FACING, structureFacing)
                .setValue(ConverterShellBlock.PART, forming ? cell.part() : Part.LOOSE));
            if (level.getBlockEntity(pos) instanceof ConverterShellBlockEntity part)
                part.assign(forming || !clearController ? worldPosition : null);
            level.invalidateCapabilities(pos);
        }
        if (hasNetwork()) getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
    }

    private void invalidatePortCapabilities() {
        if (origin == null || level == null) return;
        level.invalidateCapabilities(ConverterStructure.rawPort(origin, structureFacing));
        level.invalidateCapabilities(ConverterStructure.lance(origin, structureFacing));
        level.invalidateCapabilities(ConverterStructure.gasPort(origin, structureFacing));
        level.invalidateCapabilities(ConverterStructure.tap(origin, structureFacing));
    }

    public void setAngleForTest(float value) {
        angle = Math.clamp(value, 0, MAX_ANGLE);
        if (movedContraption != null) applyRotation();
        setChanged();
    }

    public void setSpeedForTest(float value) {
        float previous = getSpeed();
        setSpeed(value);
        onSpeedChanged(previous);
    }

    /** Advances blowing by exact ticks. Public for deterministic GameTests. */
    public void runProcess(int ticks) {
        if (ticks <= 0) return;
        if (revalidate) validateStructure();
        if (!formed || running || getSpeed() > 0 || !upright()) return;
        runProcessUnchecked(ticks);
    }

    private void runProcessUnchecked(int ticks) {
        if (activeRecipe == null) {
            if (!upright()) return;
            startCharge();
            if (activeRecipe == null) return;
        }
        RecipeHolder<ConverterRecipe> holder = active();
        if (holder == null) {
            status = "recipe_missing";
            changed();
            return;
        }
        ConverterRecipe recipe = holder.value();
        ProductionMonitor.plan(this, ProductionMonitor.batchRate(recipe.byproduct().getAmount(), 1, recipe.duration(), 1),
            ProductionMonitor.batchRate(recipe.result().getCount(), 1, recipe.duration(), 1), 0);
        if (!upright()) { status = "tilting";return; }
        if (!recipe.byproduct().isEmpty() && !offgasConnected()) { status = "gas_disconnected";return; }
        if (!canStoreOutputs(recipe)) { status = "output_full";return; }
        progress = Math.min(recipe.duration(), progress + ticks);
        status = "blowing";
        setChanged();
        if (progress < recipe.duration()) {
            if (clock % 10 == 0) sendData();
            return;
        }
        insertResult(recipe.result(), false);
        int gas = recipe.byproduct().isEmpty() ? 0 : gasOutput.fill(recipe.byproduct(), IFluidHandler.FluidAction.EXECUTE);
        if (!recipe.metalResult().isEmpty()) metalOutput.fill(recipe.metalResult(), IFluidHandler.FluidAction.EXECUTE);
        ProductionMonitor.produced(this, gas, recipe.result().getCount(), 0);
        consumeReservation();
        activeRecipe = null;
        progress = 0;
        status = "ready";
        changed();
    }

    private void startCharge() {
        if (!reservationEmpty()) { status = "recipe_missing";return; }
        boolean sawInputs = false, sawOutputProblem = false, sawConnectionProblem = false;
        for (RecipeHolder<ConverterRecipe> holder : recipes()) {
            ConverterRecipe recipe = holder.value();
            if (recipe.legacy()) continue;
            int gasToDrain = gasDrainAmount(recipe);
            int metalToDrain = recipe.metalIngredient().map(ingredient -> ingredient.test(metalInput.getFluid()) ? ingredient.amount() : -1).orElse(0);
            if (gasToDrain <= 0 || metalToDrain < 0 || !hasItems(recipe.itemIngredients())) continue;
            sawInputs = true;
            ProductionMonitor.plan(this, ProductionMonitor.batchRate(recipe.byproduct().getAmount(), 1, recipe.duration(), 1),
                ProductionMonitor.batchRate(recipe.result().getCount(), 1, recipe.duration(), 1), 0);
            if (!canStoreOutputs(recipe)) { sawOutputProblem = true;continue; }
            if (!recipe.byproduct().isEmpty() && !offgasConnected()) { sawConnectionProblem = true;continue; }
            reserveItems(recipe.itemIngredients());
            reservedGas.setFluid(gasInput.drain(gasToDrain, IFluidHandler.FluidAction.EXECUTE));
            if (metalToDrain > 0) reservedMetal.setFluid(metalInput.drain(metalToDrain, IFluidHandler.FluidAction.EXECUTE));
            activeRecipe = holder.id();
            progress = 0;
            status = "blowing";
            changed();
            sendData();
            return;
        }
        status = sawOutputProblem ? "output_full" : sawConnectionProblem ? "gas_disconnected" : sawInputs ? "waiting" : "no_feed";
    }

    private List<RecipeHolder<ConverterRecipe>> recipes() {
        if (level == null) return List.of();
        return level.getRecipeManager().getAllRecipesFor(CEAtomicsRecipeTypes.CONVERTER.<RecipeInput, ConverterRecipe>getType());
    }

    private RecipeHolder<ConverterRecipe> active() {
        if (level == null || activeRecipe == null) return null;
        var holder = level.getRecipeManager().byKey(activeRecipe);
        return holder.isPresent() && holder.get().value() instanceof ConverterRecipe recipe
            ? new RecipeHolder<>(holder.get().id(), recipe) : null;
    }

    private int gasDrainAmount(ConverterRecipe recipe) {
        FluidStack held = gasInput.getFluid();
        if (held.isEmpty()) return 0;
        int required = recipe.gasIngredient().amount();
        // Liquid oxygen is oxygen too, so its liquid state is checked before the gas ingredient would take it mB for mB.
        PhaseChange.Pair pair = net.createplant.thermal.FluidState.gas(held) ? null : PhaseChange.ofLiquid(held);
        if (pair == null) return recipe.gasIngredient().ingredient().test(held) && held.getAmount() >= required ? required : 0;
        boolean wantsOxygen = recipe.gasIngredient().ingredient().test(new FluidStack(CEFluids.OXYGEN_GAS.get(), 1));
        if (!wantsOxygen || pair.vapour().get() != CEFluids.OXYGEN_GAS.get()) return 0;
        int liquid = Math.ceilDiv(required, pair.ratio());
        return held.getAmount() >= liquid ? liquid : 0;
    }

    private boolean acceptsGas(FluidStack stack) {
        if (stack.isEmpty() || !FluidThermal.valid(stack)) return false;
        if (FluidForm.LIQUID_OXYGEN.matches(stack)) return true;
        for (RecipeHolder<ConverterRecipe> holder : recipes())
            if (holder.value().gasIngredient().ingredient().test(stack)) return true;
        return false;
    }

    private boolean acceptsItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (RecipeHolder<ConverterRecipe> holder : recipes()) {
            if (holder.value().legacy()) continue;
            for (SizedIngredient ingredient : holder.value().itemIngredients())
                if (ingredient.ingredient().test(stack)) return true;
        }
        return false;
    }

    private boolean acceptsMetal(FluidStack stack) {
        if (!TorpedoTankBlockEntity.accepts(stack)) return false;
        for (var holder : recipes())
            if (!holder.value().legacy() && holder.value().metalIngredient()
                .filter(ingredient -> ingredient.ingredient().test(stack)).isPresent()) return true;
        return false;
    }

    private boolean hasItems(List<SizedIngredient> ingredients) { return itemPlan(ingredients) != null; }

    private int[][] itemPlan(List<SizedIngredient> ingredients) {
        int[] left = new int[INPUT_SLOTS];
        for (int slot = 0; slot < INPUT_SLOTS; slot++) left[slot] = inventory.getStackInSlot(slot).getCount();
        int[][] used = new int[ingredients.size()][INPUT_SLOTS];
        for (int index = 0; index < ingredients.size(); index++) {
            SizedIngredient ingredient = ingredients.get(index);
            int needed = ingredient.count();
            for (int slot = 0; slot < INPUT_SLOTS && needed > 0; slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (left[slot] <= 0 || !ingredient.ingredient().test(stack)) continue;
                int take = Math.min(needed, left[slot]);
                used[index][slot] = take;
                left[slot] -= take;
                needed -= take;
            }
            if (needed > 0) return null;
        }
        return used;
    }

    private void reserveItems(List<SizedIngredient> ingredients) {
        int[][] plan = itemPlan(ingredients);
        if (plan == null) return;
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            int amount = 0;
            for (int[] row : plan) amount += row[slot];
            if (amount > 0) reservedItems.setStackInSlot(slot, inventory.extractItem(slot, amount, false));
        }
    }

    private boolean reservationEmpty() {
        if (!reservedGas.isEmpty() || !reservedMetal.isEmpty()) return false;
        for (int slot = 0; slot < reservedItems.getSlots(); slot++)
            if (!reservedItems.getStackInSlot(slot).isEmpty()) return false;
        return true;
    }

    private void consumeReservation() {
        for (int slot = 0; slot < reservedItems.getSlots(); slot++) reservedItems.setStackInSlot(slot, ItemStack.EMPTY);
        reservedGas.setFluid(FluidStack.EMPTY);
        reservedMetal.setFluid(FluidStack.EMPTY);
    }

    private boolean canStoreResult(ItemStack stack) { return insertResult(stack, true).isEmpty(); }

    private boolean canStoreOutputs(ConverterRecipe recipe) {
        var metal = recipe.metalResult();
        return canStoreResult(recipe.result()) && canStoreGas(recipe.byproduct())
            && (metal.isEmpty() || metalOutput.fill(metal, IFluidHandler.FluidAction.SIMULATE) == metal.getAmount());
    }

    private ItemStack insertResult(ItemStack offered, boolean simulate) {
        ItemStack left = offered.copy();
        for (int slot = INPUT_SLOTS; slot < INPUT_SLOTS + OUTPUT_SLOTS && !left.isEmpty(); slot++) {
            ItemStack stored = inventory.getStackInSlot(slot);
            if (!stored.isEmpty() && !ItemStack.isSameItemSameComponents(stored, left)) continue;
            int space = left.getMaxStackSize() - stored.getCount();
            int move = Math.min(space, left.getCount());
            if (move <= 0) continue;
            if (!simulate) inventory.setStackInSlot(slot,
                stored.isEmpty() ? left.copyWithCount(move) : stored.copyWithCount(stored.getCount() + move));
            left.shrink(move);
        }
        return left;
    }

    private boolean canStoreGas(FluidStack stack) {
        return stack.isEmpty() || gasOutput.fill(stack, IFluidHandler.FluidAction.SIMULATE) == stack.getAmount();
    }

    private boolean hasProduct() {
        if (!metalOutput.isEmpty()) return true;
        for (int slot = INPUT_SLOTS; slot < INPUT_SLOTS + OUTPUT_SLOTS; slot++)
            if (!inventory.getStackInSlot(slot).isEmpty()) return true;
        return false;
    }

    public boolean offgasConnected() {
        if (level == null || origin == null) return false;
        BlockPos port = ConverterStructure.gasPort(origin, structureFacing);
        Direction outward = structureFacing.getOpposite();
        BlockPos neighbour = port.relative(outward);
        return level.hasChunkAt(neighbour)
            && level.getCapability(Capabilities.FluidHandler.BLOCK, neighbour, outward.getOpposite()) != null;
    }

    public IItemHandler itemPort(Part part, Direction side) {
        if (!formed) return null;
        if (part == Part.RAW_PORT && (side == null || side == Direction.UP)) return rawPort;
        if (part == Part.TAP && (side == null || side == structureFacing || side == Direction.DOWN)) return tapPort;
        return null;
    }

    public IFluidHandler fluidPort(Part part, Direction side) {
        if (!formed) return null;
        if (part == Part.LANCE && (side == null || side == Direction.UP)) return lancePort;
        if (part == Part.RAW_PORT && (side == null || side == structureFacing.getClockWise())) return feedMetalPort;
        if (part == Part.TAP && (side == null || side == structureFacing)) return tapMetalPort;
        Direction gasSide = structureFacing.getOpposite();
        if (part == Part.GAS_PORT && (side == null || side == gasSide)) return offgasPort;
        return null;
    }

    private final class RawPort implements IItemHandler {
        @Override public int getSlots() { return INPUT_SLOTS; }
        @Override public ItemStack getStackInSlot(int slot) { return slot >= 0 && slot < INPUT_SLOTS ? inventory.getStackInSlot(slot) : ItemStack.EMPTY; }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return formed && upright() && slot >= 0 && slot < INPUT_SLOTS && acceptsItem(stack)
                ? inventory.insertItem(slot, stack, simulate) : stack;
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
        @Override public int getSlotLimit(int slot) { return slot >= 0 && slot < INPUT_SLOTS ? inventory.getSlotLimit(slot) : 0; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return slot >= 0 && slot < INPUT_SLOTS && acceptsItem(stack); }
    }

    private final class TapPort implements IItemHandler {
        @Override public int getSlots() { return OUTPUT_SLOTS; }
        @Override public ItemStack getStackInSlot(int slot) { return slot >= 0 && slot < OUTPUT_SLOTS ? inventory.getStackInSlot(INPUT_SLOTS + slot) : ItemStack.EMPTY; }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return stack; }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return formed && tilted() && slot >= 0 && slot < OUTPUT_SLOTS
                ? inventory.extractItem(INPUT_SLOTS + slot, amount, simulate) : ItemStack.EMPTY;
        }
        @Override public int getSlotLimit(int slot) { return slot >= 0 && slot < OUTPUT_SLOTS ? inventory.getSlotLimit(INPUT_SLOTS + slot) : 0; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return false; }
    }

    private final class LancePort implements IFluidHandler {
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int tank) { return tank == 0 ? gasInput.getFluid().copy() : FluidStack.EMPTY; }
        @Override public int getTankCapacity(int tank) { return tank == 0 ? gasInput.getCapacity() : 0; }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return tank == 0 && acceptsGas(stack); }
        @Override public int fill(FluidStack stack, FluidAction action) { return formed && upright() && acceptsGas(stack) ? gasInput.fill(stack, action) : 0; }
        @Override public FluidStack drain(FluidStack stack, FluidAction action) { return FluidStack.EMPTY; }
        @Override public FluidStack drain(int amount, FluidAction action) { return FluidStack.EMPTY; }
    }

    private final class OffgasPort implements IFluidHandler {
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int tank) { return tank == 0 ? gasOutput.getFluid().copy() : FluidStack.EMPTY; }
        @Override public int getTankCapacity(int tank) { return tank == 0 ? gasOutput.getCapacity() : 0; }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return false; }
        @Override public int fill(FluidStack stack, FluidAction action) { return 0; }
        @Override public FluidStack drain(FluidStack stack, FluidAction action) {
            return formed && !stack.isEmpty() ? gasOutput.drain(stack, action) : FluidStack.EMPTY;
        }
        @Override public FluidStack drain(int amount, FluidAction action) {
            return formed && amount > 0 ? gasOutput.drain(amount, action) : FluidStack.EMPTY;
        }
    }

    private final class MetalPort implements IFluidHandler {
        private final boolean input;
        private final SmartFluidTank tank;

        private MetalPort(boolean input) {
            this.input = input;
            tank = input ? metalInput : metalOutput;
        }

        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int slot) { return slot == 0 ? tank.getFluid().copy() : FluidStack.EMPTY; }
        @Override public int getTankCapacity(int slot) { return slot == 0 ? tank.getCapacity() : 0; }
        @Override public boolean isFluidValid(int slot, FluidStack stack) { return input && slot == 0 && acceptsMetal(stack); }
        @Override public int fill(FluidStack stack, FluidAction action) {
            return input && formed && upright() ? tank.fill(stack, action) : 0;
        }
        @Override public FluidStack drain(FluidStack stack, FluidAction action) {
            return !input && formed && tilted() ? tank.drain(stack, action) : FluidStack.EMPTY;
        }
        @Override public FluidStack drain(int amount, FluidAction action) {
            return !input && formed && tilted() ? tank.drain(amount, action) : FluidStack.EMPTY;
        }
    }

    public boolean hasContents() {
        if (activeRecipe != null || !gasInput.isEmpty() || !gasOutput.isEmpty() || !metalInput.isEmpty()
            || !metalOutput.isEmpty() || !reservationEmpty()) return true;
        for (int slot = 0; slot < inventory.getSlots(); slot++)
            if (!inventory.getStackInSlot(slot).isEmpty()) return true;
        return false;
    }

    public void dismantle() {
        if (dismantled || level == null || level.isClientSide) return;
        dismantled = true;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.extractItem(slot, Integer.MAX_VALUE, false);
            if (!stack.isEmpty()) Containers.dropItemStack(level, worldPosition.getX() + .5, worldPosition.getY() + .5,
                worldPosition.getZ() + .5, stack);
        }
        for (int slot = 0; slot < reservedItems.getSlots(); slot++) {
            ItemStack stack = reservedItems.extractItem(slot, Integer.MAX_VALUE, false);
            if (!stack.isEmpty()) Containers.dropItemStack(level, worldPosition.getX() + .5, worldPosition.getY() + .5,
                worldPosition.getZ() + .5, stack);
        }
        vent(gasInput);vent(reservedGas);vent(gasOutput);
        metalInput.setFluid(FluidStack.EMPTY);
        metalOutput.setFluid(FluidStack.EMPTY);
        reservedMetal.setFluid(FluidStack.EMPTY);
        activeRecipe = null;
        progress = 0;
        formed = false;
        status = "unformed";
        assignPorts(false, true);
        setChanged();
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide && clock % 5 == 0) sendData();
    }

    @Override public boolean addToGoggleTooltip(List<Component> text, boolean sneaking) {
        text.add(Component.translatable(TOOLTIP + "state", Component.translatable(TOOLTIP + "status." + status)));
        text.add(Component.translatable(TOOLTIP + "angle", Math.round(angle()), Math.round(MAX_ANGLE)));
        RecipeHolder<ConverterRecipe> active = active();
        text.add(Component.translatable(TOOLTIP + "progress", progress, active == null ? 0 : active.value().duration()));
        int items = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) items += inventory.getStackInSlot(slot).getCount();
        for (int slot = 0; slot < reservedItems.getSlots(); slot++) items += reservedItems.getStackInSlot(slot).getCount();
        text.add(Component.translatable(TOOLTIP + "stores", items, gasInput.getFluidAmount(), GAS_INPUT_CAPACITY,
            gasOutput.getFluidAmount(), GAS_OUTPUT_CAPACITY));
        text.add(Component.translatable(TOOLTIP + "molten_stores", metalInput.getFluidAmount() + reservedMetal.getFluidAmount(),
            metalOutput.getFluidAmount()));
        MachineReadings.compact(text);
        return true;
    }

    @Override public void write(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        super.write(tag, lookup, packet);
        tag.putBoolean("ConverterFormed", formed);
        tag.putBoolean("ConverterAssigned", assigned);
        tag.putString("ConverterStatus", status);
        tag.putString("ConverterFacing", structureFacing.getName());
        if (origin != null) tag.putLong("ConverterOrigin", origin.asLong());
        tag.put("ConverterInventory", inventory.serializeNBT(lookup));
        tag.put("ConverterReservedItems", reservedItems.serializeNBT(lookup));
        tag.put("ConverterGasInput", gasInput.writeToNBT(lookup, new CompoundTag()));
        tag.put("ConverterGasOutput", gasOutput.writeToNBT(lookup, new CompoundTag()));
        tag.put("ConverterReservedGas", reservedGas.writeToNBT(lookup, new CompoundTag()));
        tag.put("ConverterMetalInput", metalInput.writeToNBT(lookup, new CompoundTag()));
        tag.put("ConverterMetalOutput", metalOutput.writeToNBT(lookup, new CompoundTag()));
        tag.put("ConverterReservedMetal", reservedMetal.writeToNBT(lookup, new CompoundTag()));
        tag.putInt("ConverterProgress", progress);
        if (activeRecipe != null) tag.putString("ConverterRecipe", activeRecipe.toString());
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        super.read(tag, lookup, packet);
        formed = tag.getBoolean("ConverterFormed");
        assigned = tag.getBoolean("ConverterAssigned");
        status = tag.getString("ConverterStatus").isEmpty() ? "unformed" : tag.getString("ConverterStatus");
        Direction savedFacing = Direction.byName(tag.getString("ConverterFacing"));
        structureFacing = savedFacing != null && savedFacing.getAxis().isHorizontal() ? savedFacing : Direction.NORTH;
        origin = tag.contains("ConverterOrigin") ? BlockPos.of(tag.getLong("ConverterOrigin")) : null;
        if (tag.contains("ConverterInventory")) inventory.deserializeNBT(lookup, tag.getCompound("ConverterInventory"));
        if (tag.contains("ConverterReservedItems")) reservedItems.deserializeNBT(lookup, tag.getCompound("ConverterReservedItems"));
        else for (int slot = 0; slot < reservedItems.getSlots(); slot++) reservedItems.setStackInSlot(slot, ItemStack.EMPTY);
        gasInput.readFromNBT(lookup, tag.getCompound("ConverterGasInput"));
        gasOutput.readFromNBT(lookup, tag.getCompound("ConverterGasOutput"));
        if (tag.contains("ConverterReservedGas")) reservedGas.readFromNBT(lookup, tag.getCompound("ConverterReservedGas"));
        else reservedGas.setFluid(FluidStack.EMPTY);
        metalInput.readFromNBT(lookup, tag.getCompound("ConverterMetalInput"));
        metalOutput.readFromNBT(lookup, tag.getCompound("ConverterMetalOutput"));
        reservedMetal.readFromNBT(lookup, tag.getCompound("ConverterReservedMetal"));
        angle = Float.isFinite(angle) ? Math.clamp(angle, 0, MAX_ANGLE) : 0;
        progress = Math.clamp(tag.getInt("ConverterProgress"), 0, 1_000_000);
        activeRecipe = ResourceLocation.tryParse(tag.getString("ConverterRecipe"));
        revalidate = true;
        dismantled = false;
    }

    private void vent(SmartFluidTank tank) {
        if (tank.isEmpty()) return;
        FluidStack released = tank.getFluid().copy();
        ToxicGas.release(level, worldPosition, released);
        if (level instanceof ServerLevel server) server.sendParticles(ParticleTypes.CLOUD, worldPosition.getX() + .5,
            worldPosition.getY() + .5, worldPosition.getZ() + .5, 8, .3, .3, .3, .02);
        tank.setFluid(FluidStack.EMPTY);
    }
}
