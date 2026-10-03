package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * One priced Prophecy guess. The session nonce binds it to the server's candidate set; the group id is re-validated
 * against {@code ProphetDeathCauseGroup} on the server.
 * 一次付费预言猜测。会话随机标识把它绑定到服务端的候选名单；死因分组 id 由服务端按 {@code ProphetDeathCauseGroup} 重新校验。
 */
public record ConfirmProphecyC2SPacket(UUID sessionId, UUID victim, String groupId) implements CustomPayload {
    public static final int MAX_GROUP_ID_LENGTH = 32;
    public static final Identifier PAYLOAD_ID = SparkWitch.id("confirm_prophecy");
    public static final Id<ConfirmProphecyC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, ConfirmProphecyC2SPacket> CODEC =
            PacketCodec.of(ConfirmProphecyC2SPacket::write, ConfirmProphecyC2SPacket::read);

    public ConfirmProphecyC2SPacket {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(victim, "victim");
        Objects.requireNonNull(groupId, "groupId");
        if (groupId.length() > MAX_GROUP_ID_LENGTH) {
            throw new IllegalArgumentException("Prophecy group id is too long");
        }
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    void write(PacketByteBuf buf) {
        buf.writeUuid(sessionId);
        buf.writeUuid(victim);
        buf.writeString(groupId, MAX_GROUP_ID_LENGTH);
    }

    static ConfirmProphecyC2SPacket read(PacketByteBuf buf) {
        return new ConfirmProphecyC2SPacket(buf.readUuid(), buf.readUuid(), buf.readString(MAX_GROUP_ID_LENGTH));
    }
}
