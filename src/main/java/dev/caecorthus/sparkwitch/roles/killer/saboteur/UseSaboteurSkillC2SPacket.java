package dev.caecorthus.sparkwitch.roles.killer.saboteur;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Empty request; the server owns light capture and cooldown validation. / 空请求；灯具捕获与冷却校验均由服务端负责。 */
public record UseSaboteurSkillC2SPacket() implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("use_saboteur_skill");
    public static final Id<UseSaboteurSkillC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, UseSaboteurSkillC2SPacket> CODEC =
            PacketCodec.of(UseSaboteurSkillC2SPacket::write, UseSaboteurSkillC2SPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
    }

    public static UseSaboteurSkillC2SPacket read(PacketByteBuf buf) {
        return new UseSaboteurSkillC2SPacket();
    }
}
