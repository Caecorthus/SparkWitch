package dev.caecorthus.sparkwitch.roles.civilian.hanna.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Stable C2S contract {@code sparkwitch:use_hanna_float}: an empty float request sent by Hanna's ability key; the
 * server validates everything. Classified as a role skill in the Control Expert stun, Knight duel, Seeker session,
 * Riftwalker session and Grand Witch Fear deny-lists; SparkStrength's Taotie daze list and SparkFactionAPI's opening
 * safe-time list classify it on their own side.
 * 稳定 C2S 契约 {@code sparkwitch:use_hanna_float}：汉娜按技能键发送的空浮游请求，全部校验在服务端完成。已作为职业技能
 * 归入控场专家眩晕、骑士决斗、搜寻者会话、隙行者会话与大魔女恐惧的拦截名单；SparkStrength 饕餮眩晕名单与 SparkFactionAPI
 * 开局安全时间名单在各自一侧归类。
 */
public record UseHannaFloatC2SPayload() implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("use_hanna_float");
    public static final Id<UseHannaFloatC2SPayload> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, UseHannaFloatC2SPayload> CODEC =
            PacketCodec.unit(new UseHannaFloatC2SPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
