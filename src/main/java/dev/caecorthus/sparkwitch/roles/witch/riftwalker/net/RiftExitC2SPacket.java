package dev.caecorthus.sparkwitch.roles.witch.riftwalker.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Stable packet contract {@code sparkwitch:rift_exit} (C2S): {@code sessionId:varint}. The occupant's own Shift (a fresh
 * press after entry); the server always honours it for the current session.
 * 稳定数据包契约 {@code sparkwitch:rift_exit}（C2S）：{@code sessionId:varint}。门内玩家自己的 Shift（进门后的新按键）；
 * 服务端对当前会话总是接受。
 */
public record RiftExitC2SPacket(int sessionId) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("rift_exit");
    public static final Id<RiftExitC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, RiftExitC2SPacket> CODEC =
            PacketCodec.of(RiftExitC2SPacket::write, RiftExitC2SPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeVarInt(sessionId);
    }

    public static RiftExitC2SPacket read(PacketByteBuf buf) {
        return new RiftExitC2SPacket(buf.readVarInt());
    }
}
