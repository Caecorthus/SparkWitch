package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import java.util.Objects;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Requests one identity change inside a mask session; {@code sparkwitch:black_raven} means revert.
 * The client never predicts the result; the server consumes the session and validates on its thread.
 * 在面具会话内请求一次身份切换；sparkwitch:black_raven 表示恢复。客户端从不预测结果，服务端在其线程消费会话并校验。
 */
public record SelectBlackRavenDisguiseC2SPacket(int session, Identifier target) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("select_black_raven_disguise");
    public static final Id<SelectBlackRavenDisguiseC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, SelectBlackRavenDisguiseC2SPacket> CODEC =
            PacketCodec.of(SelectBlackRavenDisguiseC2SPacket::write, SelectBlackRavenDisguiseC2SPacket::read);

    public SelectBlackRavenDisguiseC2SPacket {
        Objects.requireNonNull(target, "target");
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private void write(PacketByteBuf buf) {
        buf.writeVarInt(session);
        buf.writeIdentifier(target);
    }

    private static SelectBlackRavenDisguiseC2SPacket read(PacketByteBuf buf) {
        return new SelectBlackRavenDisguiseC2SPacket(buf.readVarInt(), buf.readIdentifier());
    }
}
