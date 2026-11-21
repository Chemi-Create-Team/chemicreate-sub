package net.createplant.registry;

import java.util.List;

import com.simibubi.create.content.decoration.encasing.CasingBlock;
import net.createplant.CreatePlant;
import net.createplant.block.HighPressureTurbineEndBlock;
import net.createplant.block.HighPressureTurbinePartBlock;
import net.createplant.block.LargeFlywheelBlock;
import net.createplant.block.TankCasingBlock;
import net.createplant.block.SteamTurbineBlock;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class CEBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreatePlant.MOD_ID);

    public static final DeferredBlock<CasingBlock> DIORITE_CASING = BLOCKS.registerBlock(
        "diorite_casing",
        CasingBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.DIORITE).sound(SoundType.METAL)
    );


    public static final DeferredBlock<net.minecraft.world.level.block.TransparentBlock> INDUSTRIAL_GLASS =
        BLOCKS.registerBlock("industrial_glass", net.minecraft.world.level.block.TransparentBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS).strength(2.0F).noOcclusion());

    // Behaves like gravel, with the dust colour of its dark grey texture.
    public static final DeferredBlock<net.minecraft.world.level.block.ColoredFallingBlock> TRIIRON_TETROXIDE_PILE = BLOCKS.registerBlock(
        "triiron_tetroxide_pile",
        p -> new net.minecraft.world.level.block.ColoredFallingBlock(new net.minecraft.util.ColorRGBA(0x1D1B1B), p),
        BlockBehaviour.Properties.ofFullCopy(Blocks.GRAVEL)
    );
    public static final DeferredBlock<TankCasingBlock> TANK_CASING = BLOCKS.registerBlock(
        "tank_casing",
        TankCasingBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).sound(SoundType.NETHERITE_BLOCK)
    );

    public static final DeferredBlock<LargeFlywheelBlock> LARGE_FLYWHEEL = registerLargeFlywheel("large_flywheel");
    public static final DeferredBlock<LargeFlywheelBlock> WHITE_LARGE_FLYWHEEL =
        registerLargeFlywheel("white_large_flywheel");
    public static final DeferredBlock<LargeFlywheelBlock> ORANGE_LARGE_FLYWHEEL =
        registerLargeFlywheel("orange_large_flywheel");
    public static final DeferredBlock<LargeFlywheelBlock> MAGENTA_LARGE_FLYWHEEL =
        registerLargeFlywheel("magenta_large_flywheel");
    public static final DeferredBlock<LargeFlywheelBlock> LIGHT_BLUE_LARGE_FLYWHEEL =
        registerLargeFlywheel("light_blue_large_flywheel");
    public static final DeferredBlock<LargeFlywheelBlock> LIME_LARGE_FLYWHEEL =
        registerLargeFlywheel("lime_large_flywheel");
    public static final DeferredBlock<LargeFlywheelBlock> PINK_LARGE_FLYWHEEL =
        registerLargeFlywheel("pink_large_flywheel");
    public static final DeferredBlock<LargeFlywheelBlock> GRAY_LARGE_FLYWHEEL =
        registerLargeFlywheel("gray_large_flywheel");
    public static final DeferredBlock<LargeFlywheelBlock> LIGHT_GRAY_LARGE_FLYWHEEL =
        registerLargeFlywheel("light_gray_large_flywheel");
    public static final DeferredBlock<LargeFlywheelBlock> CYAN_LARGE_FLYWHEEL =
        registerLargeFlywheel("cyan_large_flywheel");
    public static final DeferredBlock<LargeFlywheelBlock> PURPLE_LARGE_FLYWHEEL =
        registerLargeFlywheel("purple_large_flywheel");
    public static final DeferredBlock<LargeFlywheelBlock> BLUE_LARGE_FLYWHEEL =
        registerLargeFlywheel("blue_large_flywheel");
    public static final DeferredBlock<LargeFlywheelBlock> BROWN_LARGE_FLYWHEEL =
        registerLargeFlywheel("brown_large_flywheel");
    public static final DeferredBlock<LargeFlywheelBlock> GREEN_LARGE_FLYWHEEL =
        registerLargeFlywheel("green_large_flywheel");
    public static final DeferredBlock<LargeFlywheelBlock> RED_LARGE_FLYWHEEL =
        registerLargeFlywheel("red_large_flywheel");
    public static final DeferredBlock<LargeFlywheelBlock> BLACK_LARGE_FLYWHEEL =
        registerLargeFlywheel("black_large_flywheel");

    public static final List<DeferredBlock<LargeFlywheelBlock>> LARGE_FLYWHEELS = List.of(
        LARGE_FLYWHEEL,
        WHITE_LARGE_FLYWHEEL,
        ORANGE_LARGE_FLYWHEEL,
        MAGENTA_LARGE_FLYWHEEL,
        LIGHT_BLUE_LARGE_FLYWHEEL,
        LIME_LARGE_FLYWHEEL,
        PINK_LARGE_FLYWHEEL,
        GRAY_LARGE_FLYWHEEL,
        LIGHT_GRAY_LARGE_FLYWHEEL,
        CYAN_LARGE_FLYWHEEL,
        PURPLE_LARGE_FLYWHEEL,
        BLUE_LARGE_FLYWHEEL,
        BROWN_LARGE_FLYWHEEL,
        GREEN_LARGE_FLYWHEEL,
        RED_LARGE_FLYWHEEL,
        BLACK_LARGE_FLYWHEEL
    );

    public static final DeferredBlock<SteamTurbineBlock> STEAM_TURBINE = BLOCKS.registerBlock(
        "steam_turbine",
        properties -> new SteamTurbineBlock(properties, CEItems.LOW_PRESSURE_TURBINE_BLADE::get, true, 4, 2),
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()
    );

    public static final DeferredBlock<SteamTurbineBlock> HIGH_PRESSURE_STEAM_TURBINE = BLOCKS.registerBlock(
        "high_pressure_steam_turbine",
        properties -> new SteamTurbineBlock(properties, CEItems.HIGH_PRESSURE_TURBINE_BLADE::get, false, 4, 6),
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()
    );

    public static final DeferredBlock<SteamTurbineBlock> BOOSTED_STEAM_TURBINE = BLOCKS.registerBlock(
        "boosted_steam_turbine",
        properties -> new SteamTurbineBlock(properties, CEItems.LOW_PRESSURE_TURBINE_BLADE::get, true, 4, 2, true),
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()
    );

    public static final DeferredBlock<SteamTurbineBlock> BOOSTED_HIGH_PRESSURE_STEAM_TURBINE = BLOCKS.registerBlock(
        "boosted_high_pressure_steam_turbine",
        properties -> new SteamTurbineBlock(properties, CEItems.HIGH_PRESSURE_TURBINE_BLADE::get, false, 4, 6, true),
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()
    );

    public static final DeferredBlock<HighPressureTurbinePartBlock> HIGH_PRESSURE_TURBINE_PART = BLOCKS.registerBlock(
        "high_pressure_turbine_part",
        HighPressureTurbinePartBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()
    );

    public static final DeferredBlock<HighPressureTurbineEndBlock> HIGH_PRESSURE_TURBINE_END = BLOCKS.registerBlock(
        "high_pressure_turbine_end",
        HighPressureTurbineEndBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()
    );

    public static final DeferredBlock<HighPressureTurbineEndBlock> LOW_PRESSURE_TURBINE_END = BLOCKS.registerBlock(
        "low_pressure_turbine_end",
        properties -> new HighPressureTurbineEndBlock(properties, true),
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()
    );

    public static boolean isBoostedSteamTurbine(BlockState state) {
        return state.is(BOOSTED_STEAM_TURBINE.get())
            || state.is(BOOSTED_HIGH_PRESSURE_STEAM_TURBINE.get());
    }

    public static boolean isLowPressureSteamTurbine(BlockState state) {
        return state.is(STEAM_TURBINE.get()) || state.is(BOOSTED_STEAM_TURBINE.get());
    }

    public static boolean isHighPressureSteamTurbine(BlockState state) {
        return state.is(HIGH_PRESSURE_STEAM_TURBINE.get()) || state.is(BOOSTED_HIGH_PRESSURE_STEAM_TURBINE.get());
    }

    public static boolean isCasedSteamTurbine(BlockState state) {
        return isHighPressureSteamTurbine(state) || isLowPressureSteamTurbine(state);
    }

    public static Block[] largeFlywheelBlocks() {
        return LARGE_FLYWHEELS.stream()
            .map(DeferredBlock::get)
            .toArray(Block[]::new);
    }

    public static LargeFlywheelBlock largeFlywheelForColor(DyeColor color) {
        return switch (color) {
            case WHITE -> WHITE_LARGE_FLYWHEEL.get();
            case ORANGE -> ORANGE_LARGE_FLYWHEEL.get();
            case MAGENTA -> MAGENTA_LARGE_FLYWHEEL.get();
            case LIGHT_BLUE -> LIGHT_BLUE_LARGE_FLYWHEEL.get();
            case YELLOW -> LARGE_FLYWHEEL.get();
            case LIME -> LIME_LARGE_FLYWHEEL.get();
            case PINK -> PINK_LARGE_FLYWHEEL.get();
            case GRAY -> GRAY_LARGE_FLYWHEEL.get();
            case LIGHT_GRAY -> LIGHT_GRAY_LARGE_FLYWHEEL.get();
            case CYAN -> CYAN_LARGE_FLYWHEEL.get();
            case PURPLE -> PURPLE_LARGE_FLYWHEEL.get();
            case BLUE -> BLUE_LARGE_FLYWHEEL.get();
            case BROWN -> BROWN_LARGE_FLYWHEEL.get();
            case GREEN -> GREEN_LARGE_FLYWHEEL.get();
            case RED -> RED_LARGE_FLYWHEEL.get();
            case BLACK -> BLACK_LARGE_FLYWHEEL.get();
        };
    }

    private static DeferredBlock<LargeFlywheelBlock> registerLargeFlywheel(String name) {
        return BLOCKS.registerBlock(
            name,
            LargeFlywheelBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()
        );
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
