package net.createplant.block.entity;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.List;
import net.createplant.chemistry.ElectrolysisProcess;
import net.createplant.registry.CEElectrolysis;
import net.createplant.registry.CEItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Carries one top cell's electrode plates, the asbestos diaphragm and the metal won on them. The rack travels by itself between raised (0)
 * and lowered (1); the stack runs only with every rack lowered, and plates and metal change hands only with the rack raised.
 */
public class ElectrodeHeadBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    /** Travel per tick: a full stroke takes 16 ticks. */
    private static final float STROKE = 1 / 16f;
    private BlockPos controller;
    private boolean lower;
    private float position, previousPosition;

    public final ItemStackHandler electrodes = new ItemStackHandler(3) {
        @Override public int getSlotLimit(int slot) { return slot == 2 ? 64 : ElectrolysisProcess.PLATES_PER_HEAD; }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is((slot == 0 ? CEElectrolysis.ELECTRODE_PLATE : slot == 1 ? CEElectrolysis.NICKEL_ELECTRODE_PLATE : CEItems.ASBESTOS).get());
        }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot < 2 && plates() + stack.getCount() > ElectrolysisProcess.PLATES_PER_HEAD) {
                int room = Math.max(0, ElectrolysisProcess.PLATES_PER_HEAD - plates());
                var remainder = super.insertItem(slot, stack.copyWithCount(room), simulate);
                return stack.copyWithCount(stack.getCount() - room + remainder.getCount());
            }
            return super.insertItem(slot, stack, simulate);
        }
        @Override protected void onContentsChanged(int slot) {
            changed();
            var owner = controller();if (slot < 2 && owner != null) owner.electrodeInventoryChanged();
        }
    };
    public final ItemStackHandler products = new ItemStackHandler(1) {
        @Override protected void onContentsChanged(int slot) { changed(); }
    };

    public ElectrodeHeadBlockEntity(BlockPos pos, BlockState state) { super(CEElectrolysis.HEAD_ENTITY.get(), pos, state); }
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) { }

    public int plates() { return electrodes.getStackInSlot(0).getCount() + electrodes.getStackInSlot(1).getCount(); }
    public int nickel() { return electrodes.getStackInSlot(1).getCount(); }
    public boolean lowered() { return position >= 1; }
    public boolean raised() { return position <= 0; }
    /** On its way down after START. */
    public boolean lowering() { return lower && !lowered(); }
    public float position(float partialTicks) { return previousPosition + (position - previousPosition) * partialTicks; }

    private void changed() { setChanged();if (level != null && !level.isClientSide) sendData(); }

    public void bind(BlockPos owner, boolean running) {
        if (!owner.equals(controller)) { controller = owner;if (level != null) level.invalidateCapabilities(worldPosition); }
        lower(running);
    }
    public ElectrolysisControllerBlockEntity controller() {
        return controller != null && level != null && level.hasChunkAt(controller)
            && level.getBlockEntity(controller) instanceof ElectrolysisControllerBlockEntity owner && owner.formed() ? owner : null;
    }
    public void invalidateMachine() { var owner = controller();if (owner != null) owner.invalidateMachine(); }

    /** START sends the rack down and STOP brings it up. */
    public void lower(boolean down) {
        if (lower == down) return;
        lower = down;changed();
    }

    @Override public void tick() { super.tick();move(); }

    /** One tick of travel towards the asked-for end of the stroke. */
    public void move() {
        previousPosition = position;
        float target = lower ? 1 : 0;
        if (position == target) return;
        position = position < target ? Math.min(target, position + STROKE) : Math.max(target, position - STROKE);
        if (level == null || level.isClientSide || position != target) return;
        var owner = controller();if (owner != null) owner.electrodeInventoryChanged();
        level.playSound(null, worldPosition, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, .3f, lower ? .7f : .9f);
        changed();
    }

    public ItemInteractionResult use(ItemStack stack, Player player) {
        int slot = stack.is(CEElectrolysis.ELECTRODE_PLATE.get()) ? 0 : stack.is(CEElectrolysis.NICKEL_ELECTRODE_PLATE.get()) ? 1 : stack.is(CEItems.ASBESTOS.get()) ? 2 : -1;
        if (slot < 0 && !stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!raised()) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("electrolysis.head.lowered"), true);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (slot >= 0) {
            if (!electrodes.insertItem(slot, stack.copyWithCount(1), true).isEmpty()) return ItemInteractionResult.FAIL;
            if (!level.isClientSide) { electrodes.insertItem(slot, stack.copyWithCount(1), false);if (!player.getAbilities().instabuild) stack.shrink(1); }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            var taken = player.isShiftKeyDown() ? ItemStack.EMPTY : products.extractItem(0, 64, false);
            for (int index = 2; taken.isEmpty() && index >= 0; index--) if (player.isShiftKeyDown()) taken = electrodes.extractItem(index, 1, false);
            if (taken.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            player.getInventory().placeItemBackInInventory(taken);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Funnels fit plates and diaphragms and take the won metal, only while the rack is raised; the plates stay until taken by hand. */
    public IItemHandler rack() {
        return LiveMachinePorts.items(this, new IItemHandler() {
            @Override public int getSlots() { return 4; }
            @Override public ItemStack getStackInSlot(int slot) { return slot < 3 ? electrodes.getStackInSlot(slot) : products.getStackInSlot(0); }
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return slot < 3 && raised() ? electrodes.insertItem(slot, stack, simulate) : stack; }
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return slot == 3 && raised() ? products.extractItem(0, amount, simulate) : ItemStack.EMPTY; }
            @Override public int getSlotLimit(int slot) { return slot < 3 ? electrodes.getSlotLimit(slot) : 64; }
            @Override public boolean isItemValid(int slot, ItemStack stack) { return slot < 3 && electrodes.isItemValid(slot, stack); }
        });
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CEElectrolysis.HEAD_ENTITY.get(), (head, side) -> side == Direction.DOWN ? null : head.rack());
    }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider lookup, boolean clientPacket) {
        super.write(tag, lookup, clientPacket);
        tag.put("Electrodes", electrodes.serializeNBT(lookup));tag.put("Products", products.serializeNBT(lookup));
        tag.putBoolean("Lower", lower);tag.putFloat("Position", position);
        if (controller != null) tag.putLong("Controller", controller.asLong());
    }
    @Override protected void read(CompoundTag tag, HolderLookup.Provider lookup, boolean clientPacket) {
        super.read(tag, lookup, clientPacket);
        electrodes.deserializeNBT(lookup, tag.getCompound("Electrodes"));products.deserializeNBT(lookup, tag.getCompound("Products"));
        int room = ElectrolysisProcess.PLATES_PER_HEAD;
        for (int slot = 0; slot < 2; slot++) {
            var stack = electrodes.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            if (!electrodes.isItemValid(slot, stack)) { electrodes.setStackInSlot(slot, ItemStack.EMPTY);continue; }
            electrodes.setStackInSlot(slot, stack.copyWithCount(Math.min(stack.getCount(), room)));room -= electrodes.getStackInSlot(slot).getCount();
        }
        lower = tag.getBoolean("Lower");
        float saved = tag.getFloat("Position");position = Float.isFinite(saved) ? Math.clamp(saved, 0, 1) : 0;
        if (!clientPacket) previousPosition = position;
        controller = tag.contains("Controller") ? BlockPos.of(tag.getLong("Controller")) : null;
    }

    @Override public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        var owner = controller();
        if (owner != null && !sneaking) return owner.addToGoggleTooltip(tooltip, false);
        tooltip.add(Component.translatable("electrolysis.plates", plates(), ElectrolysisProcess.PLATES_PER_HEAD));
        tooltip.add(Component.translatable("electrolysis.head", Component.translatable(raised() ? "electrolysis.head.up" : lowered() ? "electrolysis.head.down" : "electrolysis.head.moving")));
        return true;
    }
}
