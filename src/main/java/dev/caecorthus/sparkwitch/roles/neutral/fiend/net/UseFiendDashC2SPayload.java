package dev.caecorthus.sparkwitch.roles.neutral.fiend.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Stable C2S contract {@code sparkwitch:use_fiend_dash}: an empty Dash request sent by the moment Fiend's ability key;
 * the server validates everything. Classified as a role skill in the Control Expert stun, Seeker session, Riftwalker
 * session and Grand Witch Fear deny-lists.
 * 稳定 C2S 契约 {@code sparkwitch:use_fiend_dash}：时刻中的魔人按技能键发送的空疾驰请求，全部校验在服务端完成。
 * 已作为职业技能归入控场专家眩晕、搜寻者会话、隙行者会话与大魔女恐惧的拦截名单。
 */
public record UseFiendDashC2SPayload() implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("use_fiend_dash");
    public static final Id<UseFiendDashC2SPayload> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, UseFiendDashC2SPayload> CODEC =
            PacketCodec.of(UseFiendDashC2SPayload::write, UseFiendDashC2SPayload::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
    }

    public static UseFiendDashC2SPayload read(PacketByteBuf buf) {
        return new UseFiendDashC2SPayload();
    }
}
