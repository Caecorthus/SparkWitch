package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Asks the server for the hypnosis target list. 请求催眠目标列表。 */
public record RequestNatsumeMenuC2SPacket() implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("request_natsume_menu");
    public static final Id<RequestNatsumeMenuC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, RequestNatsumeMenuC2SPacket> CODEC =
            PacketCodec.unit(new RequestNatsumeMenuC2SPacket());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
