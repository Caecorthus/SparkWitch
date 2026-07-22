package dev.caecorthus.sparkwitch.roles.witch.curser;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Empty client request; the server recomputes every Curser ability predicate. / 空客户端请求；服务端重新计算所有诅咒师技能条件。 */
public record UseCurserAbilityC2SPacket() implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("use_curser_ability");
    public static final CustomPayload.Id<UseCurserAbilityC2SPacket> ID = new CustomPayload.Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, UseCurserAbilityC2SPacket> CODEC =
            PacketCodec.of(UseCurserAbilityC2SPacket::write, UseCurserAbilityC2SPacket::read);

    private static void write(UseCurserAbilityC2SPacket payload, PacketByteBuf buf) {
    }

    private static UseCurserAbilityC2SPacket read(PacketByteBuf buf) {
        return new UseCurserAbilityC2SPacket();
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
