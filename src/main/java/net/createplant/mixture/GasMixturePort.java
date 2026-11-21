package net.createplant.mixture;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * A machine's fluid port that takes and gives gas mixtures by their composition, so no machine handles mixtures itself.
 * An inlet hands each component of a mixed feed to the machine; the components the machine does not use wait in its
 * {@link GasPassThrough} and leave through its outlets. When an outlet gives two different gases at once, or gas waits in the buffer,
 * it shows them first as one mixture in proportion to their volumes; otherwise it is the machine's port unchanged.
 */
public final class GasMixturePort implements IFluidHandler {
    private final IFluidHandler port;
    private final GasPassThrough through;
    private final net.createplant.block.entity.ReactorMachineBlockEntity reactor;
    private final boolean inlet, outlet;

    private GasMixturePort(IFluidHandler port, BlockEntity machine, boolean inlet, boolean outlet) {
        this.port = port;
        this.through = GasPassThrough.of(machine);
        this.reactor = machine instanceof net.createplant.block.entity.ReactorMachineBlockEntity reactor ? reactor : null;
        this.inlet = inlet;
        this.outlet = outlet;
    }

    public static IFluidHandler inlet(IFluidHandler port, BlockEntity machine) { return wrap(port, machine, true, false); }
    public static IFluidHandler outlet(IFluidHandler port, BlockEntity machine) { return wrap(port, machine, false, true); }
    public static IFluidHandler both(IFluidHandler port, BlockEntity machine) { return wrap(port, machine, true, true); }

    private static IFluidHandler wrap(IFluidHandler port, BlockEntity machine, boolean inlet, boolean outlet) {
        return port == null ? null : new GasMixturePort(port, machine, inlet, outlet);
    }

    /** One stock the outlet draws on: a tank of the port, or the pass-through gas when {@code tank} is negative. */
    private record Source(int tank, FluidStack stock) { }

    private List<Source> gasSources() {
        var sources = new ArrayList<Source>();
        for (int tank = 0; tank < port.getTanks(); tank++) {
            var stock = port.getFluidInTank(tank);
            if (MixtureHelper.gas(stock) && !port.drain(stock.copyWithAmount(1), FluidAction.SIMULATE).isEmpty()) sources.add(new Source(tank, stock));
        }
        if (through != null && !through.gas().isEmpty()) sources.add(new Source(-1, through.gas()));
        return sources;
    }

    private static FluidStack combined(List<Source> sources) {
        var view = FluidStack.EMPTY;
        for (var source : sources) view = view.isEmpty() ? source.stock().copy() : MixtureHelper.merge(view, source.stock(), true);
        return view;
    }

    /** The port shows its own tanks unchanged unless it gives two different gases at once or gas waits in the pass-through buffer. */
    private static boolean merging(List<Source> sources) {
        if (sources.size() < 2) return !sources.isEmpty() && sources.getFirst().tank() < 0;
        var first = sources.getFirst().stock();
        return sources.stream().anyMatch(source -> source.tank() < 0 || !FluidStack.isSameFluidSameComponents(source.stock(), first));
    }

    /** With merging, the combined gas comes first so Create's pipes take it before any single product. */
    private int offset() { return outlet && merging(gasSources()) ? 1 : 0; }

    @Override public int getTanks() { return port.getTanks() + offset(); }

    @Override public FluidStack getFluidInTank(int tank) {
        int offset = offset();
        if (offset == 1 && tank == 0) return combined(gasSources());
        return port.getFluidInTank(tank - offset);
    }

    @Override public int getTankCapacity(int tank) {
        int offset = offset();
        if (offset == 1 && tank == 0) return Math.max(getFluidInTank(0).getAmount(), through == null ? 0 : GasPassThrough.CAPACITY);
        return port.getTankCapacity(tank - offset);
    }

    @Override public boolean isFluidValid(int tank, FluidStack stack) {
        if (inlet && MixtureHelper.composite(stack) && MixtureHelper.gas(stack)) return fill(stack, FluidAction.SIMULATE) > 0;
        int offset = offset();
        return (offset == 0 || tank > 0) && port.isFluidValid(tank - offset, stack);
    }

    @Override public int fill(FluidStack resource, FluidAction action) {
        if (!inlet) return outlet ? port.fill(resource, action) : 0;
        if (!MixtureHelper.composite(resource) || !MixtureHelper.isMixableGas(resource)) return port.fill(resource, action);
        var components = MixtureHelper.components(resource);
        if (reactor == null) return fillComponents(resource, components, action);
        reactor.offer(components);
        try { return fillComponents(resource, components, action); }
        finally { reactor.offer(List.of()); }
    }

    private int fillComponents(FluidStack resource, List<FluidStack> components, FluidAction action) {
        double share = 1;
        int unused = 0;
        boolean anyUsed = false;
        for (var component : components) {
            int taken = port.fill(component, FluidAction.SIMULATE);
            if (taken > 0) { anyUsed = true;share = Math.min(share, taken / (double) component.getAmount()); }
            else unused += component.getAmount();
        }
        // Air goes in by its gases wherever the machine uses one of them; a machine that uses none, such as a compressor, takes it as air.
        if ((!anyUsed || unused > 0 && through == null) && !MixtureHelper.isMixture(resource)) return port.fill(resource, action);
        if (!anyUsed || unused > 0 && through == null) return 0;
        if (unused > 0) share = Math.min(share, through.room() / (double) unused);
        int accepted = (int) Math.floor(resource.getAmount() * share);
        if (accepted <= 0 || action.simulate()) return Math.max(0, accepted);
        int lost = 0;
        for (var component : MixtureHelper.components(MixtureHelper.portion(resource, accepted))) {
            int taken = port.fill(component, FluidAction.SIMULATE) > 0 ? port.fill(component, FluidAction.EXECUTE) : 0;
            var left = component.copyWithAmount(component.getAmount() - taken);
            if (left.isEmpty()) continue;
            if (through != null) through.add(left);
            else lost += left.getAmount();
        }
        return accepted - lost;
    }

    @Override public FluidStack drain(FluidStack request, FluidAction action) {
        if (!outlet || !MixtureHelper.gas(request)) return port.drain(request, action);
        var sources = gasSources();
        if (!merging(sources)) return port.drain(request, action);
        var view = combined(sources);
        if (!MixtureTransfer.matches(view, request) && !FluidStack.isSameFluidSameComponents(view, request)) return port.drain(request, action);
        return drainGas(sources, Math.min(request.getAmount(), view.getAmount()), action);
    }

    @Override public FluidStack drain(int maxDrain, FluidAction action) {
        if (!outlet) return port.drain(maxDrain, action);
        var sources = gasSources();
        if (!merging(sources)) return port.drain(maxDrain, action);
        var direct = port.drain(maxDrain, FluidAction.SIMULATE);
        if (!direct.isEmpty() && !MixtureHelper.gas(direct)) return port.drain(maxDrain, action);
        return drainGas(sources, Math.min(maxDrain, combined(sources).getAmount()), action);
    }

    /** Takes {@code amount} from the sources in proportion to their stocks, the largest remainders rounding up. */
    private FluidStack drainGas(List<Source> sources, int amount, FluidAction action) {
        if (amount <= 0) return FluidStack.EMPTY;
        long total = 0;
        for (var source : sources) total += source.stock().getAmount();
        int[] shares = new int[sources.size()];
        long[] remainders = new long[sources.size()];
        int assigned = 0;
        for (int i = 0; i < shares.length; i++) {
            long product = (long) sources.get(i).stock().getAmount() * amount;
            shares[i] = (int) (product / total);
            remainders[i] = product % total;
            assigned += shares[i];
        }
        while (assigned < amount) {
            int largest = 0;
            for (int i = 1; i < shares.length; i++) if (remainders[i] > remainders[largest]) largest = i;
            shares[largest]++;remainders[largest] = -1;assigned++;
        }
        var result = FluidStack.EMPTY;
        for (int i = 0; i < shares.length; i++) {
            if (shares[i] == 0) continue;
            var source = sources.get(i);
            var part = source.tank() < 0 ? through.take(shares[i], action) : port.drain(source.stock().copyWithAmount(shares[i]), action);
            if (!part.isEmpty()) result = result.isEmpty() ? part : MixtureHelper.merge(result, part, true);
        }
        return result;
    }
}
