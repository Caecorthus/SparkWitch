package dev.caecorthus.sparkwitch.roles.witch.riftwalker.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Stable packet contract {@code sparkwitch:rift_hop} (C2S): {@code sessionId:varint, direction:byte} where direction is
 * {@link #PREVIOUS} (-1) or {@link #NEXT} (+1) in the hop ring. Values are untrusted; the server re-validates everything.
 * 稳定数据包契约 {@code sparkwitch:rift_hop}（C2S）：{@code sessionId:varint, direction:byte}，direction 为环中的
 * {@link #PREVIOUS}（-1）或 {@link #NEXT}（+1）。数值不可信，服务端全部重新校验。
 */
public record RiftHopC2SPacket(int sessionId, byte direction) implements CustomPayload {
    public static final byte PREVIOUS = -1;
    public static final byte NEXT = 1;
    public static final Identifier PAYLOAD_ID = SparkWitch.id("rift_hop");
    public static final Id<RiftHopC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, RiftHopC2SPacket> CODEC =
            PacketCodec.of(RiftHopC2SPacket::write, RiftHopC2SPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeVarInt(sessionId);
        buf.writeByte(direction);
    }

    public static RiftHopC2SPacket read(PacketByteBuf buf) {
        return new RiftHopC2SPacket(buf.readVarInt(), buf.readByte());
    }
}
