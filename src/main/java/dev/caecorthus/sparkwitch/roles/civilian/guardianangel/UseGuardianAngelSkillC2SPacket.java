package dev.caecorthus.sparkwitch.roles.civilian.guardianangel;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Empty request; the server resolves and validates the aimed player. / 空请求；准星目标由服务端解析并校验。 */
public record UseGuardianAngelSkillC2SPacket() implements CustomPayload {
    public static final Identifier PAYLOAD_ID = GuardianAngelRules.SKILL_ID;
    public static final Id<UseGuardianAngelSkillC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, UseGuardianAngelSkillC2SPacket> CODEC =
            PacketCodec.unit(new UseGuardianAngelSkillC2SPacket());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
