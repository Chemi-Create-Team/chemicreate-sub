package net.createplant.compat;

import com.mojang.serialization.Codec;
import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import java.util.function.Supplier;
import net.createplant.CreatePlant;
import net.createplant.registry.CEProcessFluids;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class WaterGasBurner {
    private static final int CHARGE = FluidType.BUCKET_VOLUME;
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, CreatePlant.MOD_ID);
    private static final Supplier<AttachmentType<Integer>> PENDING = ATTACHMENTS.register("burner_water_gas",
        () -> AttachmentType.builder(() -> 0).serialize(Codec.intRange(0, CHARGE - 1)).build());

    private WaterGasBurner() { }

    public static void register(IEventBus bus) {
        ATTACHMENTS.register(bus);
        bus.addListener(WaterGasBurner::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, AllBlockEntityTypes.HEATER.get(),
            (burner, side) -> side != null && side.getAxis().isHorizontal() && available(burner) ? new Inlet(burner) : null);
    }

    private static boolean available(BlazeBurnerBlockEntity burner) {
        return !burner.isRemoved() && burner.getLevel() != null && !burner.stockKeeper
            && burner.getHeatLevelFromBlock() != HeatLevel.NONE;
    }

    private record Inlet(BlazeBurnerBlockEntity burner) implements IFluidHandler {
        private int pending() {
            return burner.getExistingData(PENDING).orElse(0);
        }

        @Override public int getTanks() { return 1; }
        @Override public int getTankCapacity(int tank) { return tank == 0 && available(burner) ? CHARGE : 0; }
        @Override public FluidStack getFluidInTank(int tank) {
            return getTankCapacity(tank) == 0 ? FluidStack.EMPTY : new FluidStack(CEProcessFluids.WATER_GAS.get(), pending());
        }
        @Override public boolean isFluidValid(int tank, FluidStack stack) {
            return getTankCapacity(tank) != 0 && stack.is(CEProcessFluids.WATER_GAS.get());
        }

        @Override public int fill(FluidStack stack, FluidAction action) {
            if (stack.isEmpty() || !isFluidValid(0, stack) || burner.getLevel().isClientSide) return 0;
            int held = pending();
            int accepted = Math.min(stack.getAmount(), CHARGE - held);
            boolean complete = held + accepted == CHARGE;

            // Route each full charge through the same fuel rules and burn time as a right-clicked bucket.
            var fuel = new ItemStack(CEProcessFluids.WATER_GAS.get().getBucket());
            if (!BlazeBurnerBlock.tryInsert(burner.getBlockState(), burner.getLevel(), burner.getBlockPos(),
                fuel, true, false, action.simulate() || !complete).getResult().consumesAction()) return 0;
            if (action.execute()) {
                if (complete) burner.removeData(PENDING);
                else burner.setData(PENDING, held + accepted);
                burner.setChanged();
            }
            return accepted;
        }

        @Override public FluidStack drain(FluidStack stack, FluidAction action) { return FluidStack.EMPTY; }
        @Override public FluidStack drain(int amount, FluidAction action) { return FluidStack.EMPTY; }
    }
}
