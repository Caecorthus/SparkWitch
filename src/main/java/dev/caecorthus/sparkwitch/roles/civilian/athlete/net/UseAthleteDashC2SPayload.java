package dev.caecorthus.sparkwitch.roles.civilian.athlete.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Stable C2S contract {@code sparkwitch:use_athlete_dash}: an empty dash request sent by the Athlete's ability key;
 * the server validates everything. Classified as a role skill in the Control Expert stun, Knight duel, Seeker session,
 * Riftwalker session and Grand Witch Fear deny-lists; SparkStrength's Taotie daze list and SparkFactionAPI's opening
 * safe-time list classify it on their own side.
 * 稳定 C2S 契约 {@code sparkwitch:use_athlete_dash}：运动员按技能键发送的空冲刺请求，全部校验在服务端完成。
 * 已作为职业技能归入控场专家眩晕、骑士决斗、搜寻者会话、隙行者会话与大魔女恐惧的拦截名单；SparkStrength 饕餮眩晕名单与
 * SparkFactionAPI 开局安全时间名单在各自一侧归类。
 */
public record UseAthleteDashC2SPayload() implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("use_athlete_dash");
    public static final Id<UseAthleteDashC2SPayload> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, UseAthleteDashC2SPayload> CODEC =
            PacketCodec.unit(new UseAthleteDashC2SPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
