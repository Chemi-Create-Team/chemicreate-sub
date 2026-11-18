package net.createplant.registry;

import java.util.function.Supplier;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.api.registry.CreateRegistries;
import net.createplant.CreatePlant;
import net.createplant.compat.display.MachineDisplaySource;
import net.createplant.compat.display.ReactorConsoleDisplayTarget;
import net.createplant.compat.display.FusionConsoleDisplayTarget;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CEDisplays {
    private static final DeferredRegister<DisplaySource> SOURCES=DeferredRegister.create(CreateRegistries.DISPLAY_SOURCE,CreatePlant.MOD_ID);
    private static final DeferredRegister<DisplayTarget> TARGETS=DeferredRegister.create(CreateRegistries.DISPLAY_TARGET,CreatePlant.MOD_ID);
    public static final Supplier<DisplaySource> MACHINE=SOURCES.register("machine",MachineDisplaySource::new);
    public static final Supplier<DisplayTarget> REACTOR_CONSOLE=TARGETS.register("reactor_console",ReactorConsoleDisplayTarget::new);
    public static final Supplier<DisplayTarget> FUSION_CONSOLE=TARGETS.register("fusion_console",FusionConsoleDisplayTarget::new);
    public static final Supplier<DisplayTarget> PID=TARGETS.register("pid_controller",net.createplant.compat.display.PidDisplayTarget::new);
    public static final Supplier<DisplaySource> MIXTURE=SOURCES.register("mixture",net.createplant.compat.display.MixtureDisplaySource::new);
    private CEDisplays() { }

    public static void register(IEventBus bus) {
        SOURCES.register(bus);
        TARGETS.register(bus);
        bus.addListener((FMLCommonSetupEvent event)->event.enqueueWork(()->{
            for (var block:BuiltInRegistries.BLOCK) {
                if (BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals(CreatePlant.MOD_ID)
                    && block.defaultBlockState().hasBlockEntity()) DisplaySource.BY_BLOCK.add(block,MACHINE.get());
            }
            DisplaySource.BY_BLOCK.add(CETransport.LIDDED_VESSEL.get(),MIXTURE.get());
            DisplaySource.BY_BLOCK.add(com.simibubi.create.AllBlocks.FLUID_TANK.get(),MIXTURE.get());
            for(var block:java.util.List.of(CETransport.STAINLESS_STEEL_TANK.get(),CETransport.PRESSURE_TANK.get(),CETransport.CRYOGENIC_TANK.get()))DisplaySource.BY_BLOCK.add(block,MIXTURE.get());
            DisplaySource.BY_BLOCK.add(com.simibubi.create.AllBlocks.FLUID_TANK.get(),MACHINE.get());
            DisplaySource.BY_BLOCK.add(com.simibubi.create.AllBlocks.BASIN.get(),MACHINE.get());
            DisplaySource.BY_BLOCK_ENTITY.add(com.simibubi.create.AllBlockEntityTypes.SMART_FLUID_PIPE.get(),MACHINE.get());
            DisplaySource.BY_BLOCK_ENTITY.add(com.simibubi.create.AllBlockEntityTypes.FLUID_VALVE.get(),MACHINE.get());
            DisplaySource.BY_BLOCK_ENTITY.add(com.simibubi.create.AllBlockEntityTypes.FLUID_PIPE.get(),MACHINE.get());
            DisplaySource.BY_BLOCK_ENTITY.add(com.simibubi.create.AllBlockEntityTypes.ENCASED_FLUID_PIPE.get(),MACHINE.get());
            DisplaySource.BY_BLOCK_ENTITY.add(com.simibubi.create.AllBlockEntityTypes.GLASS_FLUID_PIPE.get(),MACHINE.get());
            DisplayTarget.BY_BLOCK.register(CENuclear.REACTOR_CONTROL_CONSOLE.get(),REACTOR_CONSOLE.get());
            DisplayTarget.BY_BLOCK.register(CENuclear.REACTOR_CONSOLE_DISPLAY.get(),REACTOR_CONSOLE.get());
            DisplayTarget.BY_BLOCK.register(CEFusion.CONSOLE.get(),FUSION_CONSOLE.get());
            DisplayTarget.BY_BLOCK.register(CEFusion.CONSOLE_PANEL.get(),FUSION_CONSOLE.get());
            DisplayTarget.BY_BLOCK.register(CEPlantSystems.PID_CONTROLLER.get(),PID.get());
            DisplayTarget.BY_BLOCK.register(CEPlantSystems.PID_RACK.get(),PID.get());
        }));
    }
}
