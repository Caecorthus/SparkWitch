package dev.caecorthus.sparkwitch.roles.civilian.orthopedist;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Empty request; the server resolves and validates the aimed player. / 空请求；准星目标由服务端解析并校验。 */
public record UseOrthopedistSkillC2SPacket() implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("use_orthopedist_skill");
    public static final Id<UseOrthopedistSkillC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, UseOrthopedistSkillC2SPacket> CODEC =
            PacketCodec.unit(new UseOrthopedistSkillC2SPacket());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
