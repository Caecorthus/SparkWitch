package dev.caecorthus.sparkwitch.roles.civilian.seeker.net;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * Stable packet contract {@code sparkwitch:seeker_car_move} (C2S, L3): {@code sessionId, seq:varint, x, y, z:double,
 * yaw:float}. The server replays, validates, clamps and corrects; values are untrusted (NaN is rejected there).
 * 稳定数据包契约 {@code sparkwitch:seeker_car_move}（C2S，L3）：服务端重放、校验、钳制并纠正；数值不可信（NaN 在服务端拒绝）。
 */
public record SeekerCarMoveC2SPacket(int sessionId, int seq, double x, double y, double z, float yaw)
        implements CustomPayload {
    public static final Id<SeekerCarMoveC2SPacket> ID = new Id<>(SeekerRules.CAR_MOVE_PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, SeekerCarMoveC2SPacket> CODEC =
            PacketCodec.of(SeekerCarMoveC2SPacket::write, SeekerCarMoveC2SPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeVarInt(sessionId);
        buf.writeVarInt(seq);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(yaw);
    }

    public static SeekerCarMoveC2SPacket read(PacketByteBuf buf) {
        return new SeekerCarMoveC2SPacket(buf.readVarInt(), buf.readVarInt(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readFloat());
    }
}
