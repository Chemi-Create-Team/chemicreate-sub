package net.createplant.atomics.nuclear;

import net.createplant.atomics.registry.CEFuelCycle;
import net.createplant.atomics.registry.CENuclear;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Fuel identities persist through channels, storage pools and the refuelling grapple. Worth is a game-scale reactivity contribution. */
public enum ReactorFuel {
    URANIUM(ReactorPhysics.FUEL_WORTH), MOX(ReactorPhysics.FUEL_WORTH), COMPACT_ENRICHED(0.165);

    private final double worth;

    ReactorFuel(double worth) { this.worth = worth; }

    public double worth() { return worth; }
    public boolean acceptsHeight(int height) { return this != COMPACT_ENRICHED || height == ReactorPhysics.COMPACT_CORE_HEIGHT; }
    public Item item() {
        return switch (this) {
            case URANIUM -> CENuclear.FUEL_ROD.get();
            case MOX -> CEFuelCycle.MOX_FUEL_ROD.get();
            case COMPACT_ENRICHED -> CENuclear.COMPACT_ENRICHED_FUEL_ROD.get();
        };
    }
    public static ReactorFuel of(ItemStack stack) {
        return stack.is(CENuclear.COMPACT_ENRICHED_FUEL_ROD.get()) ? COMPACT_ENRICHED
            : stack.is(CEFuelCycle.MOX_FUEL_ROD.get()) ? MOX : URANIUM;
    }
    public static ReactorFuel fromId(int id) { return id == 2 ? COMPACT_ENRICHED : id == 1 ? MOX : URANIUM; }
}
