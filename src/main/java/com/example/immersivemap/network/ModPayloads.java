package com.example.immersivemap.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class ModPayloads {
    private ModPayloads() {
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(ClientHelloC2S.ID, ClientHelloC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(HoldStateC2S.ID, HoldStateC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(SyncRequestC2S.ID, SyncRequestC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(MapChunksPayload.ID, MapChunksPayload.CODEC);

        PayloadTypeRegistry.playS2C().register(ServerHelloS2C.ID, ServerHelloS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(PlayerHoldStateS2C.ID, PlayerHoldStateS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(PlayerPositionsS2C.ID, PlayerPositionsS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(MapChunksPayload.ID, MapChunksPayload.CODEC);
    }
}
