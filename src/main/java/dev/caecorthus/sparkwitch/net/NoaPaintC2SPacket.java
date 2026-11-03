package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Asks to paint. 请求作画。 */
public record NoaPaintC2SPacket() implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("noa_paint");
    public static final Id<NoaPaintC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, NoaPaintC2SPacket> CODEC =
            PacketCodec.unit(new NoaPaintC2SPacket());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
