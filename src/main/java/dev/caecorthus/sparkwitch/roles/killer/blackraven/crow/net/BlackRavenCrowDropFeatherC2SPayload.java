package dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Stable C2S contract {@code sparkwitch:black_raven_crow_drop_feather}: empty; the owner pressed the drop key while
 * flying. The server (crow-world) drops the one-use Homing Feather marker at the crow, at most once per session.
 * 稳定 C2S 契约 {@code sparkwitch:black_raven_crow_drop_feather}：空包；主人飞行期间按下丢弃键。服务端（crow-world）在乌鸦位置
 * 丢下一次性的归羽标记，每次会话至多一次。
 */
public record BlackRavenCrowDropFeatherC2SPayload() implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("black_raven_crow_drop_feather");
    public static final Id<BlackRavenCrowDropFeatherC2SPayload> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, BlackRavenCrowDropFeatherC2SPayload> CODEC =
            PacketCodec.of(BlackRavenCrowDropFeatherC2SPayload::write, BlackRavenCrowDropFeatherC2SPayload::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
    }

    public static BlackRavenCrowDropFeatherC2SPayload read(PacketByteBuf buf) {
        return new BlackRavenCrowDropFeatherC2SPayload();
    }
}
