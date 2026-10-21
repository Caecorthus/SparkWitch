package dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Stable S2C contract {@code sparkwitch:black_raven_crow_correction} (owner only): {@code crowEntityId:varint, x, y,
 * z:double, yaw, pitch:float}, the authoritative crow pose after a rejected step and once at session start. The owner
 * ignores tracker updates for the crow it flies, so corrections arrive only here (crow-client applies them with
 * {@code BlackRavenCrowEntity.clientApplyCorrection}).
 * 稳定 S2C 契约 {@code sparkwitch:black_raven_crow_correction}（仅主人）：被拒位移之后以及会话开始时下发的权威乌鸦位姿。
 * 主人会忽略自己操控乌鸦的追踪更新，因此纠正只经此包到达（crow-client 用 {@code clientApplyCorrection} 应用）。
 */
public record BlackRavenCrowCorrectionS2CPayload(int crowEntityId, double x, double y, double z, float yaw,
                                                 float pitch) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("black_raven_crow_correction");
    public static final Id<BlackRavenCrowCorrectionS2CPayload> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, BlackRavenCrowCorrectionS2CPayload> CODEC =
            PacketCodec.of(BlackRavenCrowCorrectionS2CPayload::write, BlackRavenCrowCorrectionS2CPayload::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeVarInt(crowEntityId);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
    }

    public static BlackRavenCrowCorrectionS2CPayload read(PacketByteBuf buf) {
        return new BlackRavenCrowCorrectionS2CPayload(buf.readVarInt(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readFloat(), buf.readFloat());
    }
}
