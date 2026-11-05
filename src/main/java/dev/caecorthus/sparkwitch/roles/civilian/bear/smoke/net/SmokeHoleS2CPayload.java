package dev.caecorthus.sparkwitch.roles.civilian.bear.smoke.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.civilian.bear.smoke.SmokeHole;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Stable packet contract {@code sparkwitch:bear_smoke_hole} (S2C, every player in the world): {@code x:double,
 * y:double, z:double, radius:float, startTime:long, durationTicks:varint}. Decoding never throws: non-finite values, a
 * radius outside {@code (0, MAX_RADIUS]}, a duration outside {@code [1, MAX_DURATION_TICKS]} or a truncated or
 * overlong body yield a null {@code hole}, which the receiver ignores.
 * 稳定数据包契约 {@code sparkwitch:bear_smoke_hole}（S2C，发给该世界所有玩家）：{@code x:double, y:double, z:double,
 * radius:float, startTime:long, durationTicks:varint}。解码从不抛出：非有限值、半径不在 {@code (0, MAX_RADIUS]}、
 * 时长不在 {@code [1, MAX_DURATION_TICKS]}，或正文截断、多余，都得到 null {@code hole}，接收端会忽略。
 */
public record SmokeHoleS2CPayload(@Nullable SmokeHole hole) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("bear_smoke_hole");
    public static final Id<SmokeHoleS2CPayload> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, SmokeHoleS2CPayload> CODEC =
            PacketCodec.of(SmokeHoleS2CPayload::write, SmokeHoleS2CPayload::read);
    /** Largest hole radius in blocks, both sides. / 双端允许的最大空洞半径（格）。 */
    public static final float MAX_RADIUS = 8.0F;
    /** Longest hole on the wire, in ticks (10 s). / 线格式允许的最长空洞时长（tick，10 秒）。 */
    public static final int MAX_DURATION_TICKS = 200;

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        SmokeHole value = hole;
        if (value == null) {
            return;
        }
        buf.writeDouble(value.x());
        buf.writeDouble(value.y());
        buf.writeDouble(value.z());
        buf.writeFloat(value.radius());
        buf.writeLong(value.startTime());
        buf.writeVarInt(value.durationTicks());
    }

    public static SmokeHoleS2CPayload read(PacketByteBuf buf) {
        SmokeHole hole = null;
        try {
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            float radius = buf.readFloat();
            long startTime = buf.readLong();
            int duration = buf.readVarInt();
            if (buf.readableBytes() == 0 && Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                    && radius > 0.0F && radius <= MAX_RADIUS && duration >= 1 && duration <= MAX_DURATION_TICKS) {
                hole = new SmokeHole(x, y, z, radius, startTime, duration);
            }
        } catch (RuntimeException malformed) {
            hole = null;
        }
        buf.skipBytes(buf.readableBytes());
        return new SmokeHoleS2CPayload(hole);
    }
}
