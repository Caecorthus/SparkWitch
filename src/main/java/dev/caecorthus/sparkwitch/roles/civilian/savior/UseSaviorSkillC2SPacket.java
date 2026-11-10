package dev.caecorthus.sparkwitch.roles.civilian.savior;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Empty request; the server validates the role, cooldown and round state. / 空请求；职业、冷却与回合状态由服务端校验。 */
public record UseSaviorSkillC2SPacket() implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("use_savior_skill");
    public static final Id<UseSaviorSkillC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, UseSaviorSkillC2SPacket> CODEC =
            PacketCodec.of(UseSaviorSkillC2SPacket::write, UseSaviorSkillC2SPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
    }

    public static UseSaviorSkillC2SPacket read(PacketByteBuf buf) {
        return new UseSaviorSkillC2SPacket();
    }
}
