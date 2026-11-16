package dev.caecorthus.sparkwitch.roles.civilian.medium;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Empty request for 通灵 (the shared ability key); the server owns every check. / 通灵（共享技能键）的空请求；全部校验在服务端。 */
public record UseMediumSkillC2SPacket() implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("use_medium_skill");
    public static final Id<UseMediumSkillC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, UseMediumSkillC2SPacket> CODEC =
            PacketCodec.unit(new UseMediumSkillC2SPacket());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
