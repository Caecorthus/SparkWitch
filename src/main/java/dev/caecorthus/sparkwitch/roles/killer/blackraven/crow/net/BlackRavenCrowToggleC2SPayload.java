package dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Stable C2S contract {@code sparkwitch:black_raven_crow_toggle}: an empty Raven Form request sent by the Black Raven's
 * secondary key (key 2 / N). Outside a session it asks to start one; during a session it always ends it. The server
 * validates everything. Classified as a role skill on the Control Expert stun, Seeker session, Riftwalker session and
 * Fear deny-lists.
 * 稳定 C2S 契约 {@code sparkwitch:black_raven_crow_toggle}：黑羽鸦按副技能键（技能键 2 / N）发送的空化鸦请求。会话外请求开始，
 * 会话中总是结束会话。全部校验在服务端完成。已作为职业技能归入控场专家眩晕、搜寻者会话、隙行者会话与恐惧拦截名单。
 */
public record BlackRavenCrowToggleC2SPayload() implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("black_raven_crow_toggle");
    public static final Id<BlackRavenCrowToggleC2SPayload> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, BlackRavenCrowToggleC2SPayload> CODEC =
            PacketCodec.of(BlackRavenCrowToggleC2SPayload::write, BlackRavenCrowToggleC2SPayload::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
    }

    public static BlackRavenCrowToggleC2SPayload read(PacketByteBuf buf) {
        return new BlackRavenCrowToggleC2SPayload();
    }
}
