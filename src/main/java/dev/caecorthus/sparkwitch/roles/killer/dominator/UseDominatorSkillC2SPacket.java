package dev.caecorthus.sparkwitch.roles.killer.dominator;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Empty G request; the server resolves and validates the aimed or marked player. Classified as a role skill in the
 * Fear, Control Expert stun (and thus Black Raven crow body), Seeker session, Rift occupant and Knight duel deny lists.
 * 空的 G 键请求；准星目标或被标记者由服务端解析并校验。在恐惧、控场专家眩晕（因而也包括黑羽鸦化鸦本体）、搜寻者会话、裂隙门内与
 * 骑士决斗拦截名单中按职业技能归类。
 */
public record UseDominatorSkillC2SPacket() implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("use_dominator_skill");
    public static final Id<UseDominatorSkillC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, UseDominatorSkillC2SPacket> CODEC =
            PacketCodec.of(UseDominatorSkillC2SPacket::write, UseDominatorSkillC2SPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
    }

    public static UseDominatorSkillC2SPacket read(PacketByteBuf buf) {
        return new UseDominatorSkillC2SPacket();
    }
}
