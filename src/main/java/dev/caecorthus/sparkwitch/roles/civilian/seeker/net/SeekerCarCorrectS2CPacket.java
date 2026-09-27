package dev.caecorthus.sparkwitch.roles.civilian.seeker.net;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * Stable packet contract {@code sparkwitch:seeker_car_correct} (S2C, owner only): {@code sessionId:varint, x, y,
 * z:double, yaw:float}. The owner ignores tracker updates for the car it drives, so corrections arrive here.
 * 稳定数据包契约 {@code sparkwitch:seeker_car_correct}（S2C，仅拥有者）：拥有者会忽略自己驾驶小车的追踪包，纠正经由此包下发。
 */
public record SeekerCarCorrectS2CPacket(int sessionId, double x, double y, double z, float yaw)
        implements CustomPayload {
    public static final Id<SeekerCarCorrectS2CPacket> ID = new Id<>(SeekerRules.CAR_CORRECT_PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, SeekerCarCorrectS2CPacket> CODEC =
            PacketCodec.of(SeekerCarCorrectS2CPacket::write, SeekerCarCorrectS2CPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeVarInt(sessionId);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(yaw);
    }

    public static SeekerCarCorrectS2CPacket read(PacketByteBuf buf) {
        return new SeekerCarCorrectS2CPacket(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readFloat());
    }
}
