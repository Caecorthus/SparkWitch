package dev.caecorthus.sparkwitch.roles.civilian.seeker.net;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * Stable packet contract {@code sparkwitch:seeker_remote_close} (C2S): {@code sessionId:varint}. Idempotent; always
 * accepted, even while stunned or feared.
 * 稳定数据包契约 {@code sparkwitch:seeker_remote_close}（C2S）：{@code sessionId:varint}。幂等；即使眩晕或恐惧也始终接受。
 */
public record SeekerRemoteCloseC2SPacket(int sessionId) implements CustomPayload {
    public static final Id<SeekerRemoteCloseC2SPacket> ID = new Id<>(SeekerRules.REMOTE_CLOSE_PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, SeekerRemoteCloseC2SPacket> CODEC =
            PacketCodec.of(SeekerRemoteCloseC2SPacket::write, SeekerRemoteCloseC2SPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeVarInt(sessionId);
    }

    public static SeekerRemoteCloseC2SPacket read(PacketByteBuf buf) {
        return new SeekerRemoteCloseC2SPacket(buf.readVarInt());
    }
}
