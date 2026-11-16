package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Asks the server to build the reasoning roster and open the menu. / 请求服务端生成推理名单并打开菜单。 */
public record RequestCriminalReasonerC2SPacket() implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("request_criminal_reasoner");
    public static final Id<RequestCriminalReasonerC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, RequestCriminalReasonerC2SPacket> CODEC =
            PacketCodec.unit(new RequestCriminalReasonerC2SPacket());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
