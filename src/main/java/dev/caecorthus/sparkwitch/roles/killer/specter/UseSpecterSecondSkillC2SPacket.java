package dev.caecorthus.sparkwitch.roles.killer.specter;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Empty request for the Specter's second skill (Role Skill 2 key). / 幽魂二技能（职业技能 2 键）的空请求。 */
public record UseSpecterSecondSkillC2SPacket() implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("use_specter_second_skill");
    public static final Id<UseSpecterSecondSkillC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, UseSpecterSecondSkillC2SPacket> CODEC =
            PacketCodec.of(UseSpecterSecondSkillC2SPacket::write, UseSpecterSecondSkillC2SPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
    }

    public static UseSpecterSecondSkillC2SPacket read(PacketByteBuf buf) {
        return new UseSpecterSecondSkillC2SPacket();
    }
}
