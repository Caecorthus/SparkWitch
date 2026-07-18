package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Empty throw request; the server revalidates role, state, and carried corpse. / 空投掷请求；服务端重新校验身份、状态与携带尸体。 */
public record ThrowKidnapperBodyC2SPacket() implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("throw_kidnapper_body");
    public static final Id<ThrowKidnapperBodyC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, ThrowKidnapperBodyC2SPacket> CODEC =
            PacketCodec.of(ThrowKidnapperBodyC2SPacket::write, ThrowKidnapperBodyC2SPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
    }

    public static ThrowKidnapperBodyC2SPacket read(PacketByteBuf buf) {
        return new ThrowKidnapperBodyC2SPacket();
    }
}
