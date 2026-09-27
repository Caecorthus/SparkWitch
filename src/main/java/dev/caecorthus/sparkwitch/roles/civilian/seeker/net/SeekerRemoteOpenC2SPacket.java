package dev.caecorthus.sparkwitch.roles.civilian.seeker.net;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerSessionMode;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * Stable packet contract {@code sparkwitch:seeker_remote_open} (C2S): {@code mode:byte} (1 = CAR, 2 = CAMERA). The
 * server validates everything; the client never predicts entry.
 * 稳定数据包契约 {@code sparkwitch:seeker_remote_open}（C2S）：{@code mode:byte}（1 = 小车，2 = 摄像头）。
 * 全部由服务端校验；客户端从不预测进入。
 */
public record SeekerRemoteOpenC2SPacket(byte mode) implements CustomPayload {
    public static final Id<SeekerRemoteOpenC2SPacket> ID = new Id<>(SeekerRules.REMOTE_OPEN_PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, SeekerRemoteOpenC2SPacket> CODEC =
            PacketCodec.of(SeekerRemoteOpenC2SPacket::write, SeekerRemoteOpenC2SPacket::read);

    public static SeekerRemoteOpenC2SPacket of(SeekerSessionMode mode) {
        return new SeekerRemoteOpenC2SPacket((byte) mode.id());
    }

    /** Unknown bytes decode to NONE, which the server rejects. / 未知字节解码为 NONE，服务端会拒绝。 */
    public SeekerSessionMode sessionMode() {
        return SeekerSessionMode.fromId(mode);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeByte(mode);
    }

    public static SeekerRemoteOpenC2SPacket read(PacketByteBuf buf) {
        return new SeekerRemoteOpenC2SPacket(buf.readByte());
    }
}
