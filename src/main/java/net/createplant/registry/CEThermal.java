package net.createplant.registry;

import com.mojang.serialization.Codec;
import net.createplant.CreatePlant;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

public final class CEThermal {
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS=DeferredRegister.create(Registries.DATA_COMPONENT_TYPE,CreatePlant.MOD_ID);
    public static final Supplier<DataComponentType<Integer>> HEAT_GRADE=COMPONENTS.register("heat_grade",()->DataComponentType.<Integer>builder()
        .persistent(Codec.intRange(0,4)).networkSynchronized(ByteBufCodecs.VAR_INT).build());
    public static final Supplier<DataComponentType<Boolean>> EXPANDED_STEAM=COMPONENTS.register("expanded_steam",()->DataComponentType.<Boolean>builder()
        .persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());
    public static final Supplier<DataComponentType<Integer>> SUSPENDED_IMPURITY=COMPONENTS.register("suspended_impurity",()->DataComponentType.<Integer>builder()
        .persistent(Codec.intRange(0,1000)).networkSynchronized(ByteBufCodecs.VAR_INT).build());
    public static final Supplier<DataComponentType<Boolean>> CONDENSATE=COMPONENTS.register("condensate",()->DataComponentType.<Boolean>builder()
        .persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());
    private CEThermal() { }
    public static void register(IEventBus bus) { COMPONENTS.register(bus); }
}
