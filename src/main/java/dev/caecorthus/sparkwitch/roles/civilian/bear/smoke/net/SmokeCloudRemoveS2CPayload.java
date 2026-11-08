package dev.caecorthus.sparkwitch.roles.civilian.bear.smoke.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Stable packet contract {@code sparkwitch:bear_smoke_remove} (S2C, every player in the world): {@code cloudId:varint}.
 * Sent when the server drops a cloud (expired, or forced out with nothing left to fade). A malformed body decodes as
 * {@link #INVALID_ID} and is ignored.
 * 稳定数据包契约 {@code sparkwitch:bear_smoke_remove}（S2C，发给该世界所有玩家）：{@code cloudId:varint}。服务端丢弃烟团
 * （过期，或被强制淘汰且已无可消散的格）时发送。畸形正文解码为 {@link #INVALID_ID} 并被忽略。
 */
public record SmokeCloudRemoveS2CPayload(int cloudId) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("bear_smoke_remove");
    public static final Id<SmokeCloudRemoveS2CPayload> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, SmokeCloudRemoveS2CPayload> CODEC =
            PacketCodec.of(SmokeCloudRemoveS2CPayload::write, SmokeCloudRemoveS2CPayload::read);
    /** Server cloud ids start at 1. / 服务端烟团 id 从 1 开始。 */
    public static final int INVALID_ID = 0;

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeVarInt(cloudId);
    }

    public static SmokeCloudRemoveS2CPayload read(PacketByteBuf buf) {
        int id = INVALID_ID;
        try {
            id = buf.readVarInt();
        } catch (RuntimeException malformed) {
            id = INVALID_ID;
        }
        if (buf.readableBytes() != 0) {
            id = INVALID_ID;
            buf.skipBytes(buf.readableBytes());
        }
        return new SmokeCloudRemoveS2CPayload(id);
    }
}
