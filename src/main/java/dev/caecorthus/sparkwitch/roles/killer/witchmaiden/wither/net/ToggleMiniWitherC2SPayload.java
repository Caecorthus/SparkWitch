package dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Stable C2S contract {@code sparkwitch:mini_wither_toggle}: an empty summon/dismiss request sent by the Witch Maiden's
 * secondary key; the server validates everything. Classified as a role skill in the Control Expert stun, Seeker session,
 * Riftwalker session and Fear deny-lists.
 * 稳定 C2S 契约 {@code sparkwitch:mini_wither_toggle}：巫女按副技能键发送的空召唤/收回请求，全部校验在服务端完成。
 * 已作为职业技能归入控场专家眩晕、搜寻者会话、隙行者会话与恐惧的拦截名单。
 */
public record ToggleMiniWitherC2SPayload() implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("mini_wither_toggle");
    public static final Id<ToggleMiniWitherC2SPayload> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, ToggleMiniWitherC2SPayload> CODEC =
            PacketCodec.of(ToggleMiniWitherC2SPayload::write, ToggleMiniWitherC2SPayload::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
    }

    public static ToggleMiniWitherC2SPayload read(PacketByteBuf buf) {
        return new ToggleMiniWitherC2SPayload();
    }
}
