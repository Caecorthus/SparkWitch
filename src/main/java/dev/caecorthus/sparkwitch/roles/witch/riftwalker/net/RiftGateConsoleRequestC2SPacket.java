package dev.caecorthus.sparkwitch.roles.witch.riftwalker.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Stable packet contract {@code sparkwitch:rift_gate_console_request} (C2S): {@code consoleSessionId:varint}.
 * {@link #OPEN} (0) asks the server to open a new console (the client tablet opener sends it); any other value polls a
 * fresh snapshot for that open console. UI-only: the reply is a {@code sparkwitch:rift_gate_console} snapshot.
 * 稳定数据包契约 {@code sparkwitch:rift_gate_console_request}（C2S）：{@code consoleSessionId:varint}。{@link #OPEN}（0）
 * 请求服务端打开新控制台（由客户端平板拦截器发送）；其他值为该已打开控制台轮询最新快照。仅界面用途：回复为
 * {@code sparkwitch:rift_gate_console} 快照。
 */
public record RiftGateConsoleRequestC2SPacket(int consoleSessionId) implements CustomPayload {
    public static final int OPEN = 0;
    public static final Identifier PAYLOAD_ID = SparkWitch.id("rift_gate_console_request");
    public static final Id<RiftGateConsoleRequestC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, RiftGateConsoleRequestC2SPacket> CODEC =
            PacketCodec.of(RiftGateConsoleRequestC2SPacket::write, RiftGateConsoleRequestC2SPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeVarInt(consoleSessionId);
    }

    public static RiftGateConsoleRequestC2SPacket read(PacketByteBuf buf) {
        return new RiftGateConsoleRequestC2SPacket(buf.readVarInt());
    }
}
