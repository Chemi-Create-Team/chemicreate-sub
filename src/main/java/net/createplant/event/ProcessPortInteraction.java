package net.createplant.event;

import com.simibubi.create.AllItems;
import com.simibubi.create.AllParticleTypes;
import com.simibubi.create.content.fluids.particle.FluidParticleData;
import net.createplant.CreatePlant;
import net.createplant.block.ProcessPorts;
import net.createplant.block.entity.GasifierBlockEntity;
import net.createplant.block.entity.TankCasingBlockEntity;
import net.createplant.block.entity.WetProcessPartBlockEntity;
import net.createplant.chemistry.ChemicalReadout;
import net.createplant.thermal.FluidMaterials;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.items.ItemHandlerHelper;

@EventBusSubscriber(modid = CreatePlant.MOD_ID)
public final class ProcessPortInteraction {
    private ProcessPortInteraction() { }

    @SubscribeEvent
    public static void use(PlayerInteractEvent.RightClickBlock event) {
        var level = event.getLevel();
        if (event.getHand() != InteractionHand.MAIN_HAND || event.getEntity().isSpectator()
            || !level.mayInteract(event.getEntity(), event.getPos())) return;
        var be = level.getBlockEntity(event.getPos());
        var face = event.getFace();
        if (be == null || face == null || !ProcessPorts.plant(be)) return;
        var player = event.getEntity();
        var held = event.getItemStack();
        boolean emptyHands = held.isEmpty() && player.getOffhandItem().isEmpty();
        var kind = ProcessPorts.at(be, face);
        if ((kind == ProcessPorts.Kind.DRAIN || kind == ProcessPorts.Kind.OUTLET)
            && (held.is(AllItems.WRENCH.get()) || player.isShiftKeyDown() && emptyHands) && exposed(be, face)) {
            FluidStack drain = purge(be, face, FluidAction.SIMULATE);
            if (drain.isEmpty()) return;
            if (!level.isClientSide && level instanceof ServerLevel server) {
                var removed = purge(be, face, FluidAction.EXECUTE);
                if (!removed.isEmpty()) {
                    var position = Vec3.atCenterOf(be.getBlockPos()).relative(face, .65);
                    var particles = new FluidParticleData(AllParticleTypes.FLUID_PARTICLE.get(), removed.copyWithAmount(1));
                    server.sendParticles(particles, position.x, position.y, position.z, 16, .14, .15, .14, .08);
                    server.playSound(null, be.getBlockPos(), SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, .8f, .85f);
                    player.displayClientMessage(Component.translatable(ChemicalReadout.KEY + "purged", ChemicalReadout.name(removed), removed.getAmount()), true);
                }
            }
            consume(event);
            return;
        }
        if (kind != ProcessPorts.Kind.HATCH) return;
        var handler = ProcessPorts.items(be, face);
        if (handler == null) return;
        if (emptyHands && player.isShiftKeyDown()) {
            var taken = extract(be, handler, face, true);
            if (taken.isEmpty()) return;
            if (!level.isClientSide) {
                taken = extract(be, handler, face, false);
                if (!taken.isEmpty() && !player.addItem(taken)) player.drop(taken, false);
            }
        } else {
            if (held.isEmpty() || player.isShiftKeyDown() || held.is(AllItems.WRENCH.get())) return;
            var offered = held.copyWithCount(1);
            if (!ItemHandlerHelper.insertItemStacked(handler, offered, true).isEmpty()) return;
            if (!level.isClientSide && ItemHandlerHelper.insertItemStacked(handler, offered, false).isEmpty() && !player.isCreative()) held.shrink(1);
        }
        if (!level.isClientSide) level.playSound(null, be.getBlockPos(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, .5f, 1.2f);
        consume(event);
    }

    private static ItemStack extract(BlockEntity be, net.neoforged.neoforge.items.IItemHandler handler, Direction face, boolean simulate) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            var taken = handler.extractItem(slot, 64, simulate);
            if (!taken.isEmpty()) return taken;
        }
        if (be instanceof GasifierBlockEntity gasifier) return gasifier.takeIdleFeed(face, simulate);
        if (be instanceof WetProcessPartBlockEntity part && part.owner() != null) return part.owner().takeFlocculant(simulate);
        if (be instanceof TankCasingBlockEntity casing && casing.clarifier() != null) return casing.clarifier().takeFlocculant(simulate);
        return ItemStack.EMPTY;
    }

    public static boolean exposed(BlockEntity be, Direction face) {
        var adjacent = be.getBlockPos().relative(face);
        var level = be.getLevel();
        if (!level.hasChunkAt(adjacent) || !level.getBlockState(adjacent).getCollisionShape(level, adjacent).isEmpty()) return false;
        return com.simibubi.create.content.fluids.FluidPropagator.getPipe(level, adjacent) == null;
    }

    public static FluidStack purge(BlockEntity be, Direction face, FluidAction action) {
        var handler = ProcessPorts.fluid(be, face);
        if (handler == null) return FluidStack.EMPTY;
        for (int slot = 0; slot < handler.getTanks(); slot++) {
            var stack = handler.getFluidInTank(slot);
            if (stack.isEmpty() || net.createplant.thermal.FluidState.gas(stack)) continue;
            var drained = handler.drain(stack.copy(), action);
            if (!drained.isEmpty()) return drained;
        }
        return FluidStack.EMPTY;
    }

    private static void consume(PlayerInteractEvent.RightClickBlock event) {
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        event.setCanceled(true);
    }
}
