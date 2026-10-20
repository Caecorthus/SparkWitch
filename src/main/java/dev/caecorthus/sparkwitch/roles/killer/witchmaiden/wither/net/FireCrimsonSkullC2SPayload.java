package dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Stable C2S contract {@code sparkwitch:fire_crimson_skull}: a Wither Gohei left-click with the click-time aim; the
 * server re-checks the held Gohei, the Wither, the cooldown and the mana. A lethal shot: classified wherever the other
 * weapon fire payloads are.
 * 稳定 C2S 契约 {@code sparkwitch:fire_crimson_skull}：手持凋灵御币左键，携带点击瞬间的朝向；服务端重新校验手持御币、
 * 小凋灵、冷却与魔力。属于致命射击：凡拦截其他武器开火数据包处均拦截。
 */
public record FireCrimsonSkullC2SPayload(float yaw, float pitch) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("fire_crimson_skull");
    public static final Id<FireCrimsonSkullC2SPayload> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, FireCrimsonSkullC2SPayload> CODEC =
            PacketCodec.of(FireCrimsonSkullC2SPayload::write, FireCrimsonSkullC2SPayload::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public boolean hasAim() {
        return Float.isFinite(yaw) && Float.isFinite(pitch);
    }

    public void write(PacketByteBuf buf) {
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
    }

    public static FireCrimsonSkullC2SPayload read(PacketByteBuf buf) {
        if (buf.readableBytes() >= 2 * Float.BYTES) {
            return new FireCrimsonSkullC2SPayload(buf.readFloat(), buf.readFloat());
        }
        // Drain a malformed tail so the outer decoder does not reject leftover bytes.
        // 丢弃残缺尾部，避免外层解码器因剩余字节而断开连接。
        buf.skipBytes(buf.readableBytes());
        return new FireCrimsonSkullC2SPayload(Float.NaN, Float.NaN);
    }
}
