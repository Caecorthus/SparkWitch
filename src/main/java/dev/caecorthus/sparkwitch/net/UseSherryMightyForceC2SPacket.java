package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Asks to open the detective's own Mighty Force window. 请求为橘雪莉开启她自己的巨力窗口。 */
public record UseSherryMightyForceC2SPacket() implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("sherry_mighty_force");
    public static final Id<UseSherryMightyForceC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, UseSherryMightyForceC2SPacket> CODEC =
            PacketCodec.unit(new UseSherryMightyForceC2SPacket());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
