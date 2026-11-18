package net.createplant.block.entity;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.createplant.block.ElectrolysisFurnacePartBlock;
import net.createplant.registry.CEFurnace;
import net.createplant.registry.CEItems;
import net.createplant.registry.CEMetals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/** A formed electric bath consumes fixed electrical work per ingot. */
public class ElectrolysisFurnaceBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    public static final double OHMS = 2, MINIMUM_WATTS = 5000;
    public static final int OXIDE = 0, CARBON = 1, PRODUCT = 2;
    /** Oxide to the metal it gives and the electrical work of one ingot. */
    public record Reduction(Item metal, double joules) { }
    private static Map<Item, Reduction> reductions;
    public static Map<Item, Reduction> reductions() {
        if (reductions == null) reductions = Map.of(CEMetals.ALUMINA.get(), new Reduction(CEItems.ALUMINUM_INGOT.get(), 200000),
            CEMetals.TITANIUM_DIOXIDE.get(), new Reduction(CEItems.TITANIUM_INGOT.get(), 400000), CEMetals.ZIRCONIA.get(), new Reduction(CEItems.ZIRCONIUM_INGOT.get(), 400000));
        return reductions;
    }
    private final ItemStackHandler inventory = new ItemStackHandler(3) {
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return slot == OXIDE ? reductions().containsKey(stack.getItem()) : slot == CARBON ? stack.is(ItemTags.COALS) : reductions().values().stream().anyMatch(r -> stack.is(r.metal()));
        }
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    private double progress, watts;
    private volatile double voltsSquared;
    private boolean formed, lit, shapeChanged;
    private String status = "structure";
    private int clock;

    public ElectrolysisFurnaceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) { }

    public Direction facing() { return getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING); }
    /** The centre of the top layer, where the anode hangs; the furnace is 5 × 5 × 3 below and around it. */
    public BlockPos anode() { return worldPosition.relative(facing(), 3); }
    public boolean formed() { return formed; }
    public double watts() { return watts; }
    public double volts() { return Math.sqrt(voltsSquared); }
    public String status() { return status; }
    public IItemHandler inventory() { return inventory; }
    /** Square of the voltage across the terminals from the last circuit solution; 0 when the furnace is not a load. */
    public void setVoltageSquared(double value) { voltsSquared = formed && Double.isFinite(value) ? Math.max(0, value) : 0; }

    /** An owned shell or anode disappeared; stop before another processing tick and verify the whole vessel later. */
    public void structureChanged() {
        if (!formed || level == null || level.isClientSide) return;
        formed = false;
        voltsSquared = 0;
        watts = 0;
        status = "structure";
        shapeChanged = true;
        setChanged();
        sendData();
    }

    /** Every block of the furnace present and loaded; parts take this controller as their owner. */
    public boolean validate() {
        var centre = anode();
        for (int y = -2; y <= 0; y++) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            var pos = centre.offset(dx, y, dz);
            if (level == null || !level.isLoaded(pos)) return false;
            var expected = y == 0 && dx == 0 && dz == 0 ? CEFurnace.ANODE.get() : CEFurnace.SHELL.get();
            if (!level.getBlockState(pos).is(expected)) return false;
            if (level.getBlockEntity(pos) instanceof ElectrolysisFurnacePartBlockEntity part && part.owner() != null && !part.owner().equals(worldPosition)
                && level.getBlockEntity(part.owner()) instanceof ElectrolysisFurnaceBlockEntity other && other != this && other.formed) return false;
        }
        for (int y = -2; y <= 0; y++) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
            if (level.getBlockEntity(centre.offset(dx, y, dz)) instanceof ElectrolysisFurnacePartBlockEntity part) part.setOwner(worldPosition);
        return true;
    }
    /** Gives each part it owns the shape of its place and the glow of the bath, or loosens them once the furnace is broken. */
    private void shapeParts(boolean molten) {
        var centre = anode();
        for (int y = -2; y <= 0; y++) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            var pos = centre.offset(dx, y, dz);
            if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof ElectrolysisFurnacePartBlockEntity part) || !worldPosition.equals(part.owner())) continue;
            var state = level.getBlockState(pos);
            int cell = formed && state.is(CEFurnace.SHELL.get()) ? (y + 2) * 25 + (dz + 2) * 5 + dx + 2 : ElectrolysisFurnacePartBlock.LOOSE;
            var shaped = state.setValue(ElectrolysisFurnacePartBlock.CELL, cell).setValue(ElectrolysisFurnacePartBlock.LIT, formed && molten);
            if (shaped != state) level.setBlock(pos, shaped, 2);
        }
    }

    @Override public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        if (clock++ % 20 == 0) {
            boolean was = formed;formed = validate();
            if (was != formed) { if (!formed) voltsSquared = 0;shapeChanged = true;sendData(); }
        }
        watts = formed ? voltsSquared / OHMS : 0;
        var oxide = inventory.getStackInSlot(OXIDE);var reduction = reductions().get(oxide.getItem());
        boolean fed = reduction != null && !inventory.getStackInSlot(CARBON).isEmpty()
            && inventory.insertItem(PRODUCT, new ItemStack(reduction.metal()), true).isEmpty();
        String next;
        if (!formed) next = "structure";
        else if (watts < MINIMUM_WATTS) next = "power";
        else if (!fed) next = reduction == null || inventory.getStackInSlot(CARBON).isEmpty() ? "materials" : "output";
        else next = "running";
        if (next.equals("running")) {
            progress += watts / 20;
            if (progress >= reduction.joules()) {
                progress -= reduction.joules();
                inventory.extractItem(OXIDE, 1, false);inventory.extractItem(CARBON, 1, false);inventory.insertItem(PRODUCT, new ItemStack(reduction.metal()), false);
            }
        } else progress = Math.min(progress, reduction == null ? 0 : reduction.joules());
        boolean molten = formed && watts >= MINIMUM_WATTS, statusChanged = !next.equals(status);
        status = next;
        if (molten != lit || shapeChanged) { lit = molten;shapeChanged = false;shapeParts(molten); }
        setChanged();
        if (statusChanged || clock % 20 == 0) sendData();
    }
    /** Contents go back to the world when the busbar goes; the bath and the progress of the batch are lost with it. */
    public void dropContents() {
        for (int i = 0; i < inventory.getSlots(); i++) net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5, inventory.getStackInSlot(i).copy());
        for (int i = 0; i < inventory.getSlots(); i++) inventory.setStackInSlot(i, ItemStack.EMPTY);
    }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("Inventory", inventory.serializeNBT(registries));tag.putDouble("Progress", progress);
        tag.putBoolean("Formed", formed);tag.putString("Status", status);
        if (clientPacket) { tag.putDouble("Watts", watts);tag.putDouble("VoltsSquared", voltsSquared); }
    }
    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        double savedProgress=tag.getDouble("Progress");
        double maximumProgress=reductions().values().stream().mapToDouble(Reduction::joules).max().orElse(0);
        progress=Double.isFinite(savedProgress) ? Math.clamp(savedProgress,0,maximumProgress) : 0;
        formed = tag.getBoolean("Formed");status = tag.getString("Status");
        if (clientPacket) {
            double savedWatts=tag.getDouble("Watts"),savedVolts=tag.getDouble("VoltsSquared");
            watts=Double.isFinite(savedWatts) ? Math.max(0,savedWatts) : 0;
            voltsSquared=Double.isFinite(savedVolts) ? Math.max(0,savedVolts) : 0;
        }
    }

    @Override public boolean addToGoggleTooltip(List<Component> text, boolean sneaking) {
        String key = "electrolysis_furnace.create_refinery_n_reactors.";
        text.add(Component.translatable(key + "status." + status));
        text.add(Component.translatable(key+"bath",(formed && watts>=MINIMUM_WATTS ? net.createplant.thermal.Grade.Heat.VERY_HOT : net.createplant.thermal.Grade.Heat.AMBIENT).label()));
        text.add(Component.translatable(key + "power", String.format(Locale.ROOT, "%.1f", watts / 1000), String.format(Locale.ROOT, "%.0f", volts())));
        var reduction = reductions().get(inventory.getStackInSlot(OXIDE).getItem());
        if (reduction != null) text.add(Component.translatable(key + "progress", reduction.metal().getDescription(), String.format(Locale.ROOT, "%.0f", Math.min(100, progress * 100 / reduction.joules()))));
        return true;
    }
}
