package dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.net;

import dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.SphinxHaoRules;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * Stable C2S contract {@code sparkwitch:use_sphinx_hao_roar}: an empty 豪气冲天 request sent by the Sphinx Hao's
 * secondary skill key (N); the server validates everything (role, round, control states, cooldown). Classified as a
 * role skill in the shared payload policy table (SparkFactionAPI {@code config/payload-policy/actions.tsv}).
 * 稳定 C2S 契约 {@code sparkwitch:use_sphinx_hao_roar}：狮身豪按第二技能键（N）发送的空豪气冲天请求，全部校验（职业、回合、受控
 * 状态、冷却）在服务端完成。已在共享数据包策略表（SparkFactionAPI {@code config/payload-policy/actions.tsv}）中归类为职业技能。
 */
public record UseSphinxHaoRoarC2SPayload() implements CustomPayload {
    public static final UseSphinxHaoRoarC2SPayload INSTANCE = new UseSphinxHaoRoarC2SPayload();
    public static final Id<UseSphinxHaoRoarC2SPayload> ID = new Id<>(SphinxHaoRules.ROAR_PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, UseSphinxHaoRoarC2SPayload> CODEC = PacketCodec.unit(INSTANCE);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
