package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Picks the deduction target. 选择推演目标。 */
public record ConfirmSherryDeductionC2SPacket(UUID targetId) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("confirm_sherry_deduction");
    public static final Id<ConfirmSherryDeductionC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, ConfirmSherryDeductionC2SPacket> CODEC =
            PacketCodec.of(ConfirmSherryDeductionC2SPacket::write, ConfirmSherryDeductionC2SPacket::read);

    public ConfirmSherryDeductionC2SPacket {
        Objects.requireNonNull(targetId, "targetId");
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    void write(PacketByteBuf buf) {
        buf.writeUuid(targetId);
    }

    static ConfirmSherryDeductionC2SPacket read(PacketByteBuf buf) {
        return new ConfirmSherryDeductionC2SPacket(buf.readUuid());
    }
}
