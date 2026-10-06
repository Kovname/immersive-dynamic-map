package com.example.immersivemap.network;

import com.example.immersivemap.ImmersiveMapMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Opts in or out of shared map sync; {@code since} is the last server watermark this client fully received. */
public record SyncRequestC2S(boolean enabled, long since) implements CustomPayload {
    public static final Id<SyncRequestC2S> ID = new Id<>(ImmersiveMapMod.id("sync_request"));
    public static final PacketCodec<RegistryByteBuf, SyncRequestC2S> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOL, SyncRequestC2S::enabled,
            PacketCodecs.VAR_LONG, SyncRequestC2S::since,
            SyncRequestC2S::new);

    @Override
    public Id<SyncRequestC2S> getId() {
        return ID;
    }
}
