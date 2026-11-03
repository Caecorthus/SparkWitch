package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Asks the server to open the deduction panel. 请求服务端打开推演面板。 */
public record RequestSherryDeductionC2SPacket() implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("request_sherry_deduction");
    public static final Id<RequestSherryDeductionC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, RequestSherryDeductionC2SPacket> CODEC =
            PacketCodec.unit(new RequestSherryDeductionC2SPacket());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
