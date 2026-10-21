package dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Stable C2S contract {@code sparkwitch:black_raven_crow_move} (owner, every client tick while flying):
 * {@code crowEntityId:varint, x, y, z:double, yaw, pitch:float}, the client-predicted absolute crow pose. Untrusted: the
 * server drops moves for another crow or beyond two per tick, rejects non-finite values, and replays the rest through
 * the step caps, move budget, bounds clamp and block collision, answering a rejected step with
 * {@code sparkwitch:black_raven_crow_correction}.
 * 稳定 C2S 契约 {@code sparkwitch:black_raven_crow_move}（主人飞行期间每个客户端刻发送）：客户端预测的乌鸦绝对位姿。
 * 不可信：服务端丢弃指向其他乌鸦或每刻超过两个的移动包，拒绝非有限值，其余经步长上限、移动预算、边界限制与方块碰撞重放，
 * 被拒的位移以 {@code sparkwitch:black_raven_crow_correction} 回复。
 */
public record BlackRavenCrowMoveC2SPayload(int crowEntityId, double x, double y, double z, float yaw, float pitch)
        implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("black_raven_crow_move");
    public static final Id<BlackRavenCrowMoveC2SPayload> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, BlackRavenCrowMoveC2SPayload> CODEC =
            PacketCodec.of(BlackRavenCrowMoveC2SPayload::write, BlackRavenCrowMoveC2SPayload::read);

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

    public static BlackRavenCrowMoveC2SPayload read(PacketByteBuf buf) {
        return new BlackRavenCrowMoveC2SPayload(buf.readVarInt(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readFloat(), buf.readFloat());
    }
}
