package net.createplant.registry;

import java.util.function.Supplier;

import net.createplant.CreatePlant;
import net.createplant.block.entity.TankCasingBlockEntity;
import net.createplant.block.entity.HighPressureTurbineEndBlockEntity;
import net.createplant.block.entity.HighPressureTurbinePartBlockEntity;
import net.createplant.block.entity.LargeFlywheelBlockEntity;
import net.createplant.block.entity.SteamTurbineBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public class CEBlockEntityTypes {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreatePlant.MOD_ID);


    public static final Supplier<BlockEntityType<TankCasingBlockEntity>> TANK_CASING =
        BLOCK_ENTITIES.register("tank_casing", () -> BlockEntityType.Builder.of(
            TankCasingBlockEntity::new,
            CEBlocks.TANK_CASING.get()
        ).build(null));

    public static final Supplier<BlockEntityType<SteamTurbineBlockEntity>> STEAM_TURBINE =
        BLOCK_ENTITIES.register("steam_turbine", () -> BlockEntityType.Builder.of(
            SteamTurbineBlockEntity::new,
            CEBlocks.STEAM_TURBINE.get(),
            CEBlocks.HIGH_PRESSURE_STEAM_TURBINE.get(),
            CEBlocks.BOOSTED_STEAM_TURBINE.get(),
            CEBlocks.BOOSTED_HIGH_PRESSURE_STEAM_TURBINE.get()
        ).build(null));

    public static final Supplier<BlockEntityType<LargeFlywheelBlockEntity>> LARGE_FLYWHEEL =
        BLOCK_ENTITIES.register("large_flywheel", () -> BlockEntityType.Builder.of(
            LargeFlywheelBlockEntity::new,
            CEBlocks.largeFlywheelBlocks()
        ).build(null));

    public static final Supplier<BlockEntityType<HighPressureTurbinePartBlockEntity>> HIGH_PRESSURE_TURBINE_PART =
        BLOCK_ENTITIES.register("high_pressure_turbine_part", () -> BlockEntityType.Builder.of(
            HighPressureTurbinePartBlockEntity::new,
            CEBlocks.HIGH_PRESSURE_TURBINE_PART.get()
        ).build(null));

    public static final Supplier<BlockEntityType<HighPressureTurbineEndBlockEntity>> HIGH_PRESSURE_TURBINE_END =
        BLOCK_ENTITIES.register("high_pressure_turbine_end", () -> BlockEntityType.Builder.of(
            HighPressureTurbineEndBlockEntity::new,
            CEBlocks.HIGH_PRESSURE_TURBINE_END.get(),
            CEBlocks.LOW_PRESSURE_TURBINE_END.get()
        ).build(null));

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
