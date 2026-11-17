package dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.net;

import dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.SphinxHaoRules;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * Stable C2S contract {@code sparkwitch:use_sphinx_hao_infect}: an empty 嘉豪病毒 request sent by the Sphinx Hao's
 * ability key (G); the server validates everything (role, round, control states, cooldown, target). Classified as a
 * role skill in the shared payload policy table (SparkFactionAPI {@code config/payload-policy/actions.tsv}).
 * 稳定 C2S 契约 {@code sparkwitch:use_sphinx_hao_infect}：狮身豪按技能键（G）发送的空嘉豪病毒请求，全部校验（职业、回合、受控
 * 状态、冷却、目标）在服务端完成。已在共享数据包策略表（SparkFactionAPI {@code config/payload-policy/actions.tsv}）中归类为
 * 职业技能。
 */
public record UseSphinxHaoInfectC2SPayload() implements CustomPayload {
    public static final UseSphinxHaoInfectC2SPayload INSTANCE = new UseSphinxHaoInfectC2SPayload();
    public static final Id<UseSphinxHaoInfectC2SPayload> ID = new Id<>(SphinxHaoRules.INFECT_PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, UseSphinxHaoInfectC2SPayload> CODEC = PacketCodec.unit(INSTANCE);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
