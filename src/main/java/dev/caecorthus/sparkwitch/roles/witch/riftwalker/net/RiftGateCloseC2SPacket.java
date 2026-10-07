package dev.caecorthus.sparkwitch.roles.witch.riftwalker.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Stable packet contract {@code sparkwitch:rift_gate_close} (C2S): {@code consoleSessionId:varint, gateNumber:varint}.
 * Sent after the console's two-step confirm; the server re-validates role, session, gate and throttle.
 * 稳定数据包契约 {@code sparkwitch:rift_gate_close}（C2S）：{@code consoleSessionId:varint, gateNumber:varint}。
 * 控制台二次确认后发送；服务端重新校验职业、会话、门与节流。
 */
public record RiftGateCloseC2SPacket(int consoleSessionId, int gateNumber) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("rift_gate_close");
    public static final Id<RiftGateCloseC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, RiftGateCloseC2SPacket> CODEC =
            PacketCodec.of(RiftGateCloseC2SPacket::write, RiftGateCloseC2SPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeVarInt(consoleSessionId);
        buf.writeVarInt(gateNumber);
    }

    public static RiftGateCloseC2SPacket read(PacketByteBuf buf) {
        return new RiftGateCloseC2SPacket(buf.readVarInt(), buf.readVarInt());
    }
}
