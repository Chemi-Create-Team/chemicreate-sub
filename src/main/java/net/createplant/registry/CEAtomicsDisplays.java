package net.createplant.registry;

import java.util.function.Supplier;
import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.api.registry.CreateRegistries;
import net.createplant.CreatePlant;
import net.createplant.compat.display.FusionConsoleDisplayTarget;
import net.createplant.compat.display.ReactorConsoleDisplayTarget;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Atomics display targets (reactor / fusion consoles); ids stay in the core namespace. */
public final class CEAtomicsDisplays {
    private static final DeferredRegister<DisplayTarget> TARGETS = DeferredRegister.create(CreateRegistries.DISPLAY_TARGET, CreatePlant.MOD_ID);
    public static final Supplier<DisplayTarget> REACTOR_CONSOLE = TARGETS.register("reactor_console", ReactorConsoleDisplayTarget::new);
    public static final Supplier<DisplayTarget> FUSION_CONSOLE = TARGETS.register("fusion_console", FusionConsoleDisplayTarget::new);

    private CEAtomicsDisplays() { }

    public static void register(IEventBus bus) {
        TARGETS.register(bus);
        bus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            DisplayTarget.BY_BLOCK.register(CENuclear.REACTOR_CONTROL_CONSOLE.get(), REACTOR_CONSOLE.get());
            DisplayTarget.BY_BLOCK.register(CENuclear.REACTOR_CONSOLE_DISPLAY.get(), REACTOR_CONSOLE.get());
            DisplayTarget.BY_BLOCK.register(CEFusion.CONSOLE.get(), FUSION_CONSOLE.get());
            DisplayTarget.BY_BLOCK.register(CEFusion.CONSOLE_PANEL.get(), FUSION_CONSOLE.get());
        }));
    }
}
