package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Requests one invisibility charge be spent for a 20 s concealment. / 请求消耗一次隐身充能，换取 20 秒隐蔽。 */
public record CriminalReasonerInvisibilityC2SPacket() implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("criminal_reasoner_invisibility");
    public static final Id<CriminalReasonerInvisibilityC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, CriminalReasonerInvisibilityC2SPacket> CODEC =
            PacketCodec.unit(new CriminalReasonerInvisibilityC2SPacket());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
