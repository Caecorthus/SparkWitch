package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Asks the server to open a Prophecy session. Opening is free; the server alone decides eligibility.
 * 请求服务端开启一次预言会话。打开免费，资格完全由服务端判定。
 */
public record RequestProphecyC2SPacket() implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("request_prophecy");
    public static final Id<RequestProphecyC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, RequestProphecyC2SPacket> CODEC =
            PacketCodec.unit(new RequestProphecyC2SPacket());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
