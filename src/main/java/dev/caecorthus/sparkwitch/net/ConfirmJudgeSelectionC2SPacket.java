package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** The nonce binds this one-shot choice to the server's roster. / 随机会话标识将单次选择绑定到服务端名单。 */
public record ConfirmJudgeSelectionC2SPacket(UUID sessionId, UUID targetId) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("confirm_judge_selection");
    public static final Id<ConfirmJudgeSelectionC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, ConfirmJudgeSelectionC2SPacket> CODEC =
            PacketCodec.of(ConfirmJudgeSelectionC2SPacket::write, ConfirmJudgeSelectionC2SPacket::read);

    public ConfirmJudgeSelectionC2SPacket {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(targetId, "targetId");
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    void write(PacketByteBuf buf) {
        buf.writeUuid(sessionId);
        buf.writeUuid(targetId);
    }

    static ConfirmJudgeSelectionC2SPacket read(PacketByteBuf buf) {
        return new ConfirmJudgeSelectionC2SPacket(buf.readUuid(), buf.readUuid());
    }
}
