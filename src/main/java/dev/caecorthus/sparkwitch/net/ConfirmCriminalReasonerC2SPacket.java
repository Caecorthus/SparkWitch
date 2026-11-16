package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** One reasoning attempt: victim + suspected killer, bound to the server's roster by {@code sessionId}. */
/** 一次推理尝试：死者 + 怀疑的凶手，用 {@code sessionId} 绑定到服务端下发的名单。 */
public record ConfirmCriminalReasonerC2SPacket(UUID sessionId, UUID victimId, UUID suspectId)
        implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("confirm_criminal_reasoner");
    public static final Id<ConfirmCriminalReasonerC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, ConfirmCriminalReasonerC2SPacket> CODEC =
            PacketCodec.of(ConfirmCriminalReasonerC2SPacket::write, ConfirmCriminalReasonerC2SPacket::read);

    public ConfirmCriminalReasonerC2SPacket {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(victimId, "victimId");
        Objects.requireNonNull(suspectId, "suspectId");
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    void write(PacketByteBuf buf) {
        buf.writeUuid(sessionId);
        buf.writeUuid(victimId);
        buf.writeUuid(suspectId);
    }

    static ConfirmCriminalReasonerC2SPacket read(PacketByteBuf buf) {
        return new ConfirmCriminalReasonerC2SPacket(buf.readUuid(), buf.readUuid(), buf.readUuid());
    }
}
