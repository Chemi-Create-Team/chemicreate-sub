package net.createplant.item;

import net.createplant.CreatePlant;
import net.createplant.registry.CEPortableTools;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record SteamGunAction(boolean bayonet) implements CustomPacketPayload {
    public static final Type<SteamGunAction> TYPE = new Type<>(CreatePlant.location("steam_gun_action"));
    private static final StreamCodec<RegistryFriendlyByteBuf, SteamGunAction> CODEC =
        StreamCodec.composite(ByteBufCodecs.BOOL, SteamGunAction::bayonet, SteamGunAction::new);

    @Override public Type<SteamGunAction> type() { return TYPE; }

    public void handle(ServerPlayer player) { CEPortableTools.STEAM_GUN.get().act(player, bayonet); }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, CODEC, (action, context) -> {
            if (context.player() instanceof ServerPlayer player) action.handle(player);
        });
    }
}
