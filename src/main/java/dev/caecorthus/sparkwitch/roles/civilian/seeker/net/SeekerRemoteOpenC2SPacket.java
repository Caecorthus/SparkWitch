package dev.caecorthus.sparkwitch.roles.civilian.seeker.net;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerSessionMode;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * Stable packet contract {@code sparkwitch:seeker_remote_open} (C2S): {@code mode:byte} (1 = CAR, 2 = CAMERA), then
 * {@code targetEntityId:varint} ({@link #ANY_TARGET} = the server picks: for CAMERA the last-viewed usable camera,
 * else the lowest-label usable one; an explicit id must be one of the owner's cameras; ignored for CAR). The server
 * validates everything; the client never predicts entry.
 * 稳定数据包契约 {@code sparkwitch:seeker_remote_open}（C2S）：{@code mode:byte}（1 = 小车，2 = 摄像头），随后是
 * {@code targetEntityId:varint}（{@link #ANY_TARGET} 表示由服务端选择：摄像头取最近观看且可用的那台，否则取编号最小的可用那台；
 * 明确的 id 必须是拥有者的摄像头之一；小车忽略此字段）。全部由服务端校验；客户端从不预测进入。
 */
public record SeekerRemoteOpenC2SPacket(byte mode, int targetEntityId) implements CustomPayload {
    /** No explicit target: the server chooses. / 未指定目标：由服务端选择。 */
    public static final int ANY_TARGET = -1;
    public static final Id<SeekerRemoteOpenC2SPacket> ID = new Id<>(SeekerRules.REMOTE_OPEN_PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, SeekerRemoteOpenC2SPacket> CODEC =
            PacketCodec.of(SeekerRemoteOpenC2SPacket::write, SeekerRemoteOpenC2SPacket::read);

    public static SeekerRemoteOpenC2SPacket of(SeekerSessionMode mode) {
        return of(mode, ANY_TARGET);
    }

    /** A specific camera (A/D cycling). / 指定的摄像头（A/D 切换）。 */
    public static SeekerRemoteOpenC2SPacket of(SeekerSessionMode mode, int targetEntityId) {
        return new SeekerRemoteOpenC2SPacket((byte) mode.id(), Math.max(ANY_TARGET, targetEntityId));
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
        buf.writeVarInt(targetEntityId);
    }

    public static SeekerRemoteOpenC2SPacket read(PacketByteBuf buf) {
        return new SeekerRemoteOpenC2SPacket(buf.readByte(), buf.readVarInt());
    }
}
