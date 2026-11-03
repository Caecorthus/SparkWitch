package dev.caecorthus.sparkwitch.roles.civilian.athlete.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Empty C2S contract {@code sparkwitch:athlete_dash}; every check happens on the server.
 * 空的 C2S 契约 {@code sparkwitch:athlete_dash}；全部校验在服务端完成。
 */
public record AthleteDashC2SPayload() implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("athlete_dash");
    public static final Id<AthleteDashC2SPayload> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, AthleteDashC2SPayload> CODEC =
            PacketCodec.of((payload, buf) -> {
            }, buf -> new AthleteDashC2SPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
