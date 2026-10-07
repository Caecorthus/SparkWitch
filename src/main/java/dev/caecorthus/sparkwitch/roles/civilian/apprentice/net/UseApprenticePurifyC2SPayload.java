package dev.caecorthus.sparkwitch.roles.civilian.apprentice.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Stable C2S contract {@code sparkwitch:use_apprentice_purify}: an empty Purify request sent by the graduated
 * Apprentice's secondary key; the server validates everything. Classified as a role skill in the Control Expert stun,
 * Seeker session and Riftwalker session deny-lists. Fear does not block it (the Apprentice is exempt, owner D4).
 * 稳定 C2S 契约 {@code sparkwitch:use_apprentice_purify}：出师后的预备魔女按副技能键发送的空净化请求，全部校验在服务端完成。
 * 已作为职业技能归入控场专家眩晕、搜寻者会话与隙行者会话的拦截名单。恐惧不拦截它（预备魔女豁免，所有者 D4）。
 */
public record UseApprenticePurifyC2SPayload() implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("use_apprentice_purify");
    public static final Id<UseApprenticePurifyC2SPayload> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, UseApprenticePurifyC2SPayload> CODEC =
            PacketCodec.of(UseApprenticePurifyC2SPayload::write, UseApprenticePurifyC2SPayload::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
    }

    public static UseApprenticePurifyC2SPayload read(PacketByteBuf buf) {
        return new UseApprenticePurifyC2SPayload();
    }
}
