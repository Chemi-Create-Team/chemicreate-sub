package net.createplant.atomics.block.entity;

import java.util.List;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.createplant.atomics.block.ConverterShellBlock;
import net.createplant.atomics.registry.CEConverter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/** A stationary port which delegates storage and processing to the trunnion bearing. */
public class ConverterShellBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    private BlockPos controllerPos;

    public ConverterShellBlockEntity(BlockPos pos, BlockState state) {
        super(CEConverter.SHELL_ENTITY.get(), pos, state);
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CEConverter.SHELL_ENTITY.get(),
            (part, side) -> part.itemPort(side));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CEConverter.SHELL_ENTITY.get(),
            (part, side) -> net.createplant.mixture.GasMixturePort.both(part.fluidPort(side), part.controller()));
    }

    public void assign(BlockPos controller) {
        controllerPos = controller == null ? null : controller.immutable();
        setChanged();
        sendData();
    }

    public ConverterTrunnionBearingBlockEntity controller() {
        if (controllerPos == null || level == null || !level.hasChunkAt(controllerPos)) return null;
        return level.getBlockEntity(controllerPos) instanceof ConverterTrunnionBearingBlockEntity controller
            ? controller : null;
    }

    private IItemHandler itemPort(Direction side) {
        ConverterTrunnionBearingBlockEntity controller = controller();
        if (controller == null || !getBlockState().hasProperty(ConverterShellBlock.PART)) return null;
        return controller.itemPort(getBlockState().getValue(ConverterShellBlock.PART), side);
    }

    private IFluidHandler fluidPort(Direction side) {
        ConverterTrunnionBearingBlockEntity controller = controller();
        if (controller == null || !getBlockState().hasProperty(ConverterShellBlock.PART)) return null;
        return controller.fluidPort(getBlockState().getValue(ConverterShellBlock.PART), side);
    }

    public double surfaceKelvin() {
        ConverterTrunnionBearingBlockEntity controller = controller();
        return controller != null && controller.hot() ? ConverterTrunnionBearingBlockEntity.ACTIVE_SURFACE_KELVIN : 0;
    }

    @Override public boolean addToGoggleTooltip(List<Component> text, boolean sneaking) {
        ConverterTrunnionBearingBlockEntity controller = controller();
        if (controller != null) return controller.addToGoggleTooltip(text, sneaking);
        text.add(getBlockState().getBlock().getName());
        text.add(Component.translatable("converter.create_refinery_n_reactors.status.unformed"));
        return true;
    }

    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) { }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        super.write(tag, lookup, packet);
        if (controllerPos != null) tag.putLong("ConverterController", controllerPos.asLong());
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        super.read(tag, lookup, packet);
        controllerPos = tag.contains("ConverterController") ? BlockPos.of(tag.getLong("ConverterController")) : null;
    }
}
