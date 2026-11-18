package net.createplant.client;

import net.createplant.CreatePlant;
import net.createplant.assembly.AssemblyPlan;
import net.createplant.block.FacilityConstructorBlock;
import net.createplant.block.entity.FacilityConstructorBlockEntity;
import net.createplant.registry.CEAssembly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in integrated-world fixture for the actual menu and textured previews. */
@EventBusSubscriber(modid = CreatePlant.MOD_ID, value = Dist.CLIENT)
public final class FacilityConstructorClientCheck {
    private static final BlockPos ORIGIN = new BlockPos(0, 80, 0);
    private static int phase, ticks, selectionStep;
    private FacilityConstructorClientCheck() { }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("createplant.facilityConstructorClientCheck")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getOverlay() != null) return;
        if (phase == 0 && mc.screen instanceof TitleScreen && ++ticks > 60) {
            phase = 1;mc.options.pauseOnLostFocus = false;
            mc.createWorldOpenFlows().createFreshLevel("facility-constructor-" + System.currentTimeMillis(),
                new net.minecraft.world.level.LevelSettings("Facility constructor check", net.minecraft.world.level.GameType.CREATIVE, false,
                    net.minecraft.world.Difficulty.PEACEFUL, true, new net.minecraft.world.level.GameRules(), net.minecraft.world.level.WorldDataConfiguration.DEFAULT),
                new net.minecraft.world.level.levelgen.WorldOptions(42L, false, false),
                access -> access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                    .getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
        }
        if (phase == 1 && mc.player != null && mc.level != null) {
            phase = 2;ticks = 0;mc.setScreen(null);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);mc.getToasts().clear();
            mc.getSingleplayerServer().execute(() -> {
                var level = mc.getSingleplayerServer().overworld();
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                level.setDayTime(6000);
                for (int x = -4; x <= 4; x++) for (int z = -4; z <= 2; z++) level.setBlockAndUpdate(ORIGIN.offset(x, -1, z), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(ORIGIN, CEAssembly.CONSTRUCTOR.get().defaultBlockState().setValue(FacilityConstructorBlock.FACING, Direction.SOUTH));
                level.setBlockAndUpdate(ORIGIN.west(), Blocks.CHEST.defaultBlockState());
                var be = (FacilityConstructorBlockEntity) level.getBlockEntity(ORIGIN);
                be.configure(AssemblyPlan.Kind.FUSION.ordinal(), 0);
                be.inventory.setStackInSlot(4, new ItemStack(Items.GUNPOWDER, 32));
                player.getAbilities().flying = true;player.onUpdateAbilities();player.getInventory().clearContent();
                player.connection.teleport(.5, 80, -3, 0, 17);
            });
        }
        if (phase == 2 && ++ticks > 80) {
            phase = 3;ticks = 0;
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(ORIGIN), Direction.NORTH, ORIGIN, false));
        }
        if (phase == 3 && mc.screen instanceof FacilityConstructorScreen screen) {
            var be = (FacilityConstructorBlockEntity) mc.level.getBlockEntity(ORIGIN);
            if (++ticks > 600) throw new IllegalStateException("Facility selection timed out at step " + selectionStep + ": " + be.kind() + "/" + be.coreSize() + "/" + be.state + "/" + be.statusMsg);
            if (ticks < 10) return;
            boolean advance = switch (selectionStep) {
                case 0 -> true;
                case 1 -> be.kind() == AssemblyPlan.Kind.RBMK;
                case 2 -> be.coreSize() == 13;
                case 3 -> be.coreHeight() == 3;
                case 4 -> be.kind() == AssemblyPlan.Kind.FUSION && be.selectedSize() == 11;
                case 5, 9 -> be.kind() == AssemblyPlan.Kind.FUSION && be.selectedSize() == 19;
                case 6 -> be.kind() == AssemblyPlan.Kind.COOLING_TOWER && be.selectedSize() == 25;
                case 7, 10 -> be.kind() == AssemblyPlan.Kind.COOLING_TOWER && be.selectedSize() == 33;
                case 8, 11 -> be.kind() == AssemblyPlan.Kind.RBMK && be.coreSize() == 13 && be.coreHeight() == 3;
                case 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35 -> be.kind() == AssemblyPlan.Kind.CLARIFIER && be.selectedSize() == selectionStep - 5;
                case 36, 38, 40 -> be.kind() == AssemblyPlan.Kind.CLARIFIER && be.selectedSize() == 30 && be.totalHeight() == 4;
                case 37 -> be.kind() == AssemblyPlan.Kind.RBMK && be.coreSize() == 13 && be.coreHeight() == 3;
                case 39 -> be.kind() == AssemblyPlan.Kind.CLARIFIER && be.selectedSize() == 29 && be.totalHeight() == 4;
                default -> false;
            };
            if (!advance) return;
            org.slf4j.LoggerFactory.getLogger(FacilityConstructorClientCheck.class).info("FACILITY CLIENT STEP {}: {} / {} / height {} / {}", selectionStep, be.kind(), be.selectedSize(), be.coreHeight(), be.state);
            switch (selectionStep++) {
                case 0, 7, 10 -> press(screen, Component.translatable("facility.create_refinery_n_reactors.rbmk"));
                case 1 -> press(screen, Component.literal("13"));
                case 2 -> press(screen, Component.literal("3"));
                case 3, 8 -> press(screen, Component.translatable("facility.create_refinery_n_reactors.fusion"));
                case 4 -> press(screen, Component.literal("19"));
                case 5, 9 -> press(screen, Component.translatable("facility.create_refinery_n_reactors.cooling_tower"));
                case 6 -> press(screen, Component.literal("33"));
                case 11, 37 -> press(screen, Component.translatable("facility.create_refinery_n_reactors.clarifier"));
                case 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 39 -> press(screen, Component.literal("+"));
                case 35 -> press(screen, Component.literal("4"));
                case 36 -> press(screen, Component.translatable("facility.create_refinery_n_reactors.rbmk"));
                case 38 -> press(screen, Component.literal("−"));
                case 40 -> {
                    try {
                        java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("facility-constructor-client-check.json"),
                            new com.google.gson.Gson().toJson(java.util.Map.of("passed", true, "coreDiameter", 13, "coreHeight", 3, "fusionDiameter", 19, "towerDiameter", 33, "rememberedDimensions", true, "clarifierWidth", 30, "clarifierHeight", 4, "everyDiameter", true)) + System.lineSeparator());
                    } catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
                    org.slf4j.LoggerFactory.getLogger(FacilityConstructorClientCheck.class).info("FACILITY CONSTRUCTOR CLIENT CHECK PASSED");
                    phase = 4;
                }
            }
            ticks = 0;
        }
    }

    private static void press(FacilityConstructorScreen screen, Component label) {
        for (var child : screen.children()) if (child instanceof Button button && button.active && button.visible && button.getMessage().getString().equals(label.getString())) {
            button.onPress();return;
        }
        throw new IllegalStateException("Missing active facility button: " + label.getString());
    }

}
