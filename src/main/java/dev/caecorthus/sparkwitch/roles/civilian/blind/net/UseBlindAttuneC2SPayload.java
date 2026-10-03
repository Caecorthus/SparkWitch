package dev.caecorthus.sparkwitch.roles.civilian.blind.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Stable C2S contract {@code sparkwitch:use_blind_attune}: an empty Attune request; the server validates everything.
 * Classified as a role skill in the Control Expert stun, Seeker session and Grand Witch Fear deny-lists.
 * 稳定 C2S 契约 {@code sparkwitch:use_blind_attune}：空的凝神请求，全部校验在服务端完成。
 * 已作为职业技能归入控场专家眩晕、搜寻者会话与大魔女恐惧的拦截名单。
 */
public record UseBlindAttuneC2SPayload() implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("use_blind_attune");
    public static final Id<UseBlindAttuneC2SPayload> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, UseBlindAttuneC2SPayload> CODEC =
            PacketCodec.of(UseBlindAttuneC2SPayload::write, UseBlindAttuneC2SPayload::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
    }

    public static UseBlindAttuneC2SPayload read(PacketByteBuf buf) {
        return new UseBlindAttuneC2SPayload();
    }
}
