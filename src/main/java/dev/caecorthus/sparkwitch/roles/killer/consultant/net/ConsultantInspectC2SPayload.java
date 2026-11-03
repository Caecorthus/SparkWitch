package dev.caecorthus.sparkwitch.roles.killer.consultant.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.UUID;

/** C2S {@code sparkwitch:consultant_inspect}: the target's UUID; all checks happen server-side. */
public record ConsultantInspectC2SPayload(UUID target) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("consultant_inspect");
    public static final Id<ConsultantInspectC2SPayload> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, ConsultantInspectC2SPayload> CODEC =
            PacketCodec.of((payload, buf) -> Uuids.PACKET_CODEC.encode(buf, payload.target()),
                    buf -> new ConsultantInspectC2SPayload(Uuids.PACKET_CODEC.decode(buf)));

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
