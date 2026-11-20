package net.createplant.block.entity;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.List;
import net.createplant.CreatePlant;
import net.createplant.agriculture.AgriculturalSpraying;
import net.createplant.block.AgriculturalSprayerBlock;
import net.createplant.recipe.SprayingRecipe;
import net.createplant.registry.CEAgriculture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public final class AgriculturalSprayerBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    private static final String KEY = "sprayer." + CreatePlant.MOD_ID + ".";
    private static final int CAPACITY = 250;

    private boolean supplyChanged;
    private final FluidTank tank = new FluidTank(CAPACITY) {
        @Override protected void onContentsChanged() {
            setChanged();
            supplyChanged = true;
        }
    };

    private final class Inlet implements IFluidHandler {
        private final Direction side;
        Inlet(Direction side) { this.side = side; }
        private boolean exposed() {
            return !isRemoved() && side == getBlockState().getValue(AgriculturalSprayerBlock.FACING).getOpposite();
        }

        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int slot) { return exposed() ? tank.getFluidInTank(slot).copy() : FluidStack.EMPTY; }
        @Override public int getTankCapacity(int slot) { return tank.getTankCapacity(slot); }
        @Override public boolean isFluidValid(int slot, FluidStack stack) {
            return slot == 0 && exposed() && level != null && SprayingRecipe.available(level).stream()
                .anyMatch(recipe -> recipe.value().liquid().ingredient().test(stack));
        }
        @Override public int fill(FluidStack stack, FluidAction action) {
            return isFluidValid(0, stack) ? tank.fill(stack, action) : 0;
        }
        @Override public FluidStack drain(FluidStack stack, FluidAction action) {
            return exposed() ? tank.drain(stack, action) : FluidStack.EMPTY;
        }
        @Override public FluidStack drain(int amount, FluidAction action) {
            return exposed() ? tank.drain(amount, action) : FluidStack.EMPTY;
        }
    }

    public AgriculturalSprayerBlockEntity(BlockPos pos, BlockState state) {
        super(CEAgriculture.SPRAYER_ENTITY.get(), pos, state);
    }

    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) { }

    @Override public void tick() {
        super.tick();
        if (!(level instanceof ServerLevel server) || isVirtual()
                || Math.floorMod(level.getGameTime() + worldPosition.asLong(), 10) != 0) return;
        if (level.hasNeighborSignal(worldPosition) && !tank.isEmpty()) spray(server);
        if (supplyChanged) {
            supplyChanged = false;
            sendData();
        }
    }

    private void spray(ServerLevel server) {
        var direction = Vec3.atLowerCornerOf(getBlockState().getValue(AgriculturalSprayerBlock.FACING).getNormal());
        var nozzle = Vec3.atCenterOf(worldPosition).add(direction.scale(AgriculturalSpraying.NOZZLE_OFFSET));
        AgriculturalSpraying.spray(server, tank, nozzle, direction);
    }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean packet) {
        super.write(tag, registries, packet);
        tag.put("Supply", tank.writeToNBT(registries, new CompoundTag()));
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean packet) {
        super.read(tag, registries, packet);
        tank.readFromNBT(registries, tag.getCompound("Supply"));
    }

    @Override protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (level == null || tank.isEmpty()) return;
        CompoundTag data = new CompoundTag();
        data.put("Supply", tank.writeToNBT(level.registryAccess(), new CompoundTag()));
        BlockEntity.addEntityType(data, getType());
        components.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(data));
    }

    @Override public boolean addToGoggleTooltip(List<Component> text, boolean sneaking) {
        text.add(Component.translatable(KEY + "supply", tank.getFluidAmount(), CAPACITY));
        if (!tank.isEmpty()) text.add(tank.getFluid().getHoverName());
        text.add(Component.translatable(KEY + (level != null && level.hasNeighborSignal(worldPosition) ? "powered" : "idle")));
        return true;
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CEAgriculture.SPRAYER_ENTITY.get(),
            (entity, side) -> side == entity.getBlockState().getValue(AgriculturalSprayerBlock.FACING).getOpposite()
                ? entity.new Inlet(side) : null);
    }
}
