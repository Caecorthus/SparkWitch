package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import java.util.Objects;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Submits one selector choice; the server consumes and validates its one-shot authorization.
 * 提交一次占卜选择；服务端会消费并校验对应的一次性授权。
 */
public record SubmitTarotDivinationSelectionC2SPacket(int mode, String target) implements CustomPayload {
    private static final int MAX_TARGET_LENGTH = 128;

    public static final Identifier PAYLOAD_ID = SparkWitch.id("submit_tarot_divination_selection");
    public static final Id<SubmitTarotDivinationSelectionC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, SubmitTarotDivinationSelectionC2SPacket> CODEC =
            PacketCodec.of(SubmitTarotDivinationSelectionC2SPacket::write, SubmitTarotDivinationSelectionC2SPacket::read);

    public SubmitTarotDivinationSelectionC2SPacket {
        Objects.requireNonNull(target, "target");
        if (target.length() > MAX_TARGET_LENGTH) {
            throw new IllegalArgumentException("Tarot selection target is too long");
        }
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private void write(PacketByteBuf buf) {
        buf.writeVarInt(mode);
        buf.writeString(target, MAX_TARGET_LENGTH);
    }

    private static SubmitTarotDivinationSelectionC2SPacket read(PacketByteBuf buf) {
        return new SubmitTarotDivinationSelectionC2SPacket(
                buf.readVarInt(),
                buf.readString(MAX_TARGET_LENGTH)
        );
    }
}
