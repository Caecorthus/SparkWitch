package dev.caecorthus.sparkwitch.roles.civilian.usec.net;

import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecRules;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Stable packet contract {@code sparkwitch:fire_usec_rifle} (C2S): {@code yaw, pitch:float}, the crosshair at click
 * time (the attack key is handled before that tick's rotation packet). The server uses it only as the shot direction
 * and re-validates everything else. Decoding is tolerant, like {@code FireDeathRayC2SPacket}: a payload without aim
 * decodes to NaN and the server falls back to its own rotation instead of disconnecting.
 * 稳定数据包契约 {@code sparkwitch:fire_usec_rifle}（C2S）：点击瞬间的准星朝向（攻击键先于该刻的朝向数据包处理）。
 * 服务端只将其用作射击方向，其余全部复核。解码是宽容的，与 {@code FireDeathRayC2SPacket} 相同：不带朝向的载荷解码为
 * NaN，服务端回退到自身朝向而不断开连接。
 */
public record FireUsecRifleC2SPacket(float yaw, float pitch) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = UsecRules.FIRE_PAYLOAD_ID;
    public static final Id<FireUsecRifleC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, FireUsecRifleC2SPacket> CODEC =
            PacketCodec.of(FireUsecRifleC2SPacket::write, FireUsecRifleC2SPacket::read);

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

    public static FireUsecRifleC2SPacket read(PacketByteBuf buf) {
        if (buf.readableBytes() >= 2 * Float.BYTES) {
            return new FireUsecRifleC2SPacket(buf.readFloat(), buf.readFloat());
        }
        // Drain a malformed tail so the outer decoder does not reject leftover bytes.
        // 丢弃残缺尾部，避免外层解码器因剩余字节而断开连接。
        buf.skipBytes(buf.readableBytes());
        return new FireUsecRifleC2SPacket(Float.NaN, Float.NaN);
    }
}
