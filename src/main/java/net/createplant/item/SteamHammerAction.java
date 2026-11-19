package net.createplant.item;

import net.createplant.CreatePlant;
import net.createplant.registry.CEPortableTools;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record SteamHammerAction() implements CustomPacketPayload {
    public static final SteamHammerAction SWING = new SteamHammerAction();
    private static final Type<SteamHammerAction> TYPE = new Type<>(CreatePlant.location("steam_hammer_swing"));
    private static final StreamCodec<RegistryFriendlyByteBuf, SteamHammerAction> CODEC = StreamCodec.unit(SWING);

    @Override public Type<SteamHammerAction> type() { return TYPE; }

    public void handle(ServerPlayer player) { CEPortableTools.STEAM_HAMMER.get().startSwing(player); }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, CODEC, (action, context) -> {
            if (context.player() instanceof ServerPlayer player) action.handle(player);
        });
    }
}
