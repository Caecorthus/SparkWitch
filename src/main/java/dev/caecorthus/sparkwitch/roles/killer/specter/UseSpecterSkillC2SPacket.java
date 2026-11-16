package dev.caecorthus.sparkwitch.roles.killer.specter;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Empty request; the server resolves the crosshair mark target. / 空请求；标记目标由服务端解析。 */
public record UseSpecterSkillC2SPacket() implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("use_specter_skill");
    public static final Id<UseSpecterSkillC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, UseSpecterSkillC2SPacket> CODEC =
            PacketCodec.of(UseSpecterSkillC2SPacket::write, UseSpecterSkillC2SPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
    }

    public static UseSpecterSkillC2SPacket read(PacketByteBuf buf) {
        return new UseSpecterSkillC2SPacket();
    }
}
