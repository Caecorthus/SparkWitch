package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server authorization to open the ledger on the Absent Good Roles tab with one mask session.
 * The session only authorizes one later selection; the server re-validates everything on select.
 * 服务端授权以一次面具会话打开“未登场的善良职业”页；会话只授权一次后续选择，服务端在选择时重新校验全部条件。
 */
public record OpenBlackRavenDisguiseS2CPacket(int session) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("open_black_raven_disguise");
    public static final Id<OpenBlackRavenDisguiseS2CPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, OpenBlackRavenDisguiseS2CPacket> CODEC =
            PacketCodec.of(OpenBlackRavenDisguiseS2CPacket::write, OpenBlackRavenDisguiseS2CPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private void write(PacketByteBuf buf) {
        buf.writeVarInt(session);
    }

    private static OpenBlackRavenDisguiseS2CPacket read(PacketByteBuf buf) {
        return new OpenBlackRavenDisguiseS2CPacket(buf.readVarInt());
    }
}
