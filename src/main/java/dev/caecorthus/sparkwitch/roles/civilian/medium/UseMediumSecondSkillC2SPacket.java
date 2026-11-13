package dev.caecorthus.sparkwitch.roles.civilian.medium;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Empty request for the Medium's second skill (Role Skill 2 key). / 通灵师二技能（职业技能 2 键）的空请求。 */
public record UseMediumSecondSkillC2SPacket() implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("use_medium_second_skill");
    public static final Id<UseMediumSecondSkillC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, UseMediumSecondSkillC2SPacket> CODEC =
            PacketCodec.unit(new UseMediumSecondSkillC2SPacket());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
