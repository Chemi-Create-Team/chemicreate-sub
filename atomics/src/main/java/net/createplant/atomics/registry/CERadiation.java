package net.createplant.atomics.registry;

import net.createplant.CreatePlant;
import net.createplant.atomics.item.RadiationMeterItem;
import net.createplant.atomics.nuclear.Radionuclides;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class CERadiation {
    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, CreatePlant.MOD_ID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(
                    net.neoforged.neoforge.registries.NeoForgeRegistries.ATTACHMENT_TYPES,
                    CreatePlant.MOD_ID);
    public static final Supplier<DataComponentType<Radionuclides>> MATERIAL =
            COMPONENTS.register(
                    "radionuclides",
                    () ->
                            DataComponentType.<Radionuclides>builder()
                                    .persistent(Radionuclides.CODEC)
                                    .build());
    public static final Supplier<DataComponentType<Radionuclides>> SURFACE =
            COMPONENTS.register(
                    "surface_activity",
                    () ->
                            DataComponentType.<Radionuclides>builder()
                                    .persistent(Radionuclides.CODEC)
                                    .build());
    public static final Supplier<DataComponentType<Boolean>> BETA_WINDOW =
            COMPONENTS.register(
                    "survey_beta_window",
                    () ->
                            DataComponentType.<Boolean>builder()
                                    .persistent(com.mojang.serialization.Codec.BOOL)
                                    .networkSynchronized(ByteBufCodecs.BOOL)
                                    .build());
    public static final Supplier<AttachmentType<CompoundTag>> EXPOSURE =
            ATTACHMENTS.register(
                    "radiation_exposure",
                    () ->
                            AttachmentType.builder(() -> new CompoundTag())
                                    .serialize(CompoundTag.CODEC)
                                    .copyOnDeath()
                                    .sync(
                                            (holder, player) -> holder == player,
                                            ByteBufCodecs.COMPOUND_TAG)
                                    .build());
    public static final DeferredItem<RadiationMeterItem> DOSIMETER =
            ITEMS.register(
                    "personal_dosimeter",
                    () -> new RadiationMeterItem(false, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<RadiationMeterItem> SURVEY_METER =
            ITEMS.register(
                    "survey_meter",
                    () -> new RadiationMeterItem(true, new Item.Properties().stacksTo(1)));

    private CERadiation() {}

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        COMPONENTS.register(bus);
        ATTACHMENTS.register(bus);
        net.createplant.atomics.nuclear.RadiationField.register();
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(RadiationMeterItem::clickTick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(RadiationMeterItem::tooltip);
    }
}
