package dev.caecorthus.sparkwitch.roles.civilian.bear.smoke.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.civilian.bear.smoke.SmokeCloudSnapshot;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Stable packet contract {@code sparkwitch:bear_smoke_cloud} (S2C, every player in the cloud's world): adds a cloud
 * or replaces the one with the same id (a forced fade resends it with an earlier {@code spawnTime}). Body:
 * {@link SmokeCloudCodec}. {@code cloud} is null only when the client could not decode the body; the receiver
 * ignores it.
 * 稳定数据包契约 {@code sparkwitch:bear_smoke_cloud}（S2C，发给烟团所在世界的所有玩家）：新增烟团，或替换同 id 的烟团
 * （强制消散会以更早的 {@code spawnTime} 重发）。正文见 {@link SmokeCloudCodec}。仅当客户端无法解码正文时
 * {@code cloud} 为 null，接收端会忽略它。
 */
public record SmokeCloudS2CPayload(@Nullable SmokeCloudSnapshot cloud) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("bear_smoke_cloud");
    public static final Id<SmokeCloudS2CPayload> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, SmokeCloudS2CPayload> CODEC =
            PacketCodec.of(SmokeCloudS2CPayload::write, SmokeCloudS2CPayload::read);

    /**
     * Server-side factory: rejects snapshots the wire cannot carry here, on the server thread, instead of failing in
     * the Netty encoder. / 服务端工厂：在服务端线程就拒绝线格式无法承载的快照，而不是在 Netty 编码时失败。
     */
    public static SmokeCloudS2CPayload of(SmokeCloudSnapshot cloud) {
        if (!SmokeCloudCodec.encodable(cloud)) {
            throw new IllegalArgumentException("smoke cloud " + cloud.cloudId() + " does not fit the wire format");
        }
        return new SmokeCloudS2CPayload(cloud);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        if (cloud != null) {
            SmokeCloudCodec.write(buf, cloud);
        }
    }

    public static SmokeCloudS2CPayload read(PacketByteBuf buf) {
        return new SmokeCloudS2CPayload(SmokeCloudCodec.read(buf));
    }
}
