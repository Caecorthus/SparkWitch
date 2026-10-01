package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Potion launcher fire intent carrying the crosshair at click time, like the Death Ray: the attack key is handled
 * before that tick's rotation packet. The server uses the aim only as the launch direction and validates everything
 * else. Decoding is tolerant: a payload without aim decodes to NaN and the server falls back to its own rotation.
 * 药炮筒发射意图，与死亡射线一样携带点击瞬间的准星朝向：攻击键先于该刻的朝向数据包处理。服务端只把朝向用作发射方向，
 * 其余全部复核。解码是宽容的：不带朝向的载荷解码为 NaN，服务端回退到自身朝向。
 */
public record FirePotionLauncherC2SPacket(float yaw, float pitch) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("fire_potion_launcher");
    public static final Id<FirePotionLauncherC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, FirePotionLauncherC2SPacket> CODEC =
            PacketCodec.of(FirePotionLauncherC2SPacket::write, FirePotionLauncherC2SPacket::read);

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

    public static FirePotionLauncherC2SPacket read(PacketByteBuf buf) {
        if (buf.readableBytes() >= 2 * Float.BYTES) {
            return new FirePotionLauncherC2SPacket(buf.readFloat(), buf.readFloat());
        }
        // Drain a malformed tail so the outer decoder does not reject leftover bytes.
        // 丢弃残缺尾部，避免外层解码器因剩余字节而断开连接。
        buf.skipBytes(buf.readableBytes());
        return new FirePotionLauncherC2SPacket(Float.NaN, Float.NaN);
    }
}
