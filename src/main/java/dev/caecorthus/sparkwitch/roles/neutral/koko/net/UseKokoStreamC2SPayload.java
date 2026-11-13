package dev.caecorthus.sparkwitch.roles.neutral.koko.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Stable C2S contract {@code sparkwitch:use_koko_stream}: an empty go-live request sent by Koko's ability key; the
 * server validates everything. Classified as a role skill in the shared payload policy table (SparkFactionAPI
 * {@code config/payload-policy/actions.tsv}): denied under the Control Expert stun, the Knight duel, a Seeker session,
 * a Rift Gate, Grand Witch Fear, SparkStrength's Taotie daze and the opening safe time. Her corpse stream has no
 * payload: it is a vanilla right-click on the body.
 * 稳定 C2S 契约 {@code sparkwitch:use_koko_stream}：可可按技能键发送的空开播请求，全部校验在服务端完成。已在共享数据包策略表
 * （SparkFactionAPI {@code config/payload-policy/actions.tsv}）中归类为职业技能：控场专家眩晕、骑士决斗、搜寻者会话、裂隙门、
 * 大魔女恐惧、SparkStrength 饕餮眩晕与开局安全时间期间一律拦截。尸体直播没有数据包：它就是对尸体的一次原版右键。
 */
public record UseKokoStreamC2SPayload() implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("use_koko_stream");
    public static final Id<UseKokoStreamC2SPayload> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, UseKokoStreamC2SPayload> CODEC =
            PacketCodec.unit(new UseKokoStreamC2SPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
