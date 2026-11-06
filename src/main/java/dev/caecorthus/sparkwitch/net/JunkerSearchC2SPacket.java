package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Starts (or retries) the five-second corpse search on one body entity. / 对某具尸体发起（或重试）五秒搜刮。 */
public record JunkerSearchC2SPacket(UUID bodyUuid) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("ragpicker_search");
    public static final Id<JunkerSearchC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, JunkerSearchC2SPacket> CODEC =
            PacketCodec.of(JunkerSearchC2SPacket::write, JunkerSearchC2SPacket::read);

    public JunkerSearchC2SPacket {
        Objects.requireNonNull(bodyUuid, "bodyUuid");
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    void write(PacketByteBuf buf) {
        buf.writeUuid(bodyUuid);
    }

    static JunkerSearchC2SPacket read(PacketByteBuf buf) {
        return new JunkerSearchC2SPacket(buf.readUuid());
    }
}
