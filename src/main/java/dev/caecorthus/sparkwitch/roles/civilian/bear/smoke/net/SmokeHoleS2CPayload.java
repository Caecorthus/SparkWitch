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
 * y:double, z:double, radius:float, startTime:long, durationTicks:varint}, then for a tunnel (BEAR WP5, a capsule) its
 * end point {@code endX:double, endY:double, endZ:double}. A blast hole is written exactly as before the tunnel
 * extension (no end point), and a body without the end point still decodes as a sphere, so the old wire format is a
 * valid subset. Decoding never throws: non-finite values, a radius outside {@code (0, MAX_RADIUS]}, a duration outside
 * {@code [1, MAX_DURATION_TICKS]}, a tunnel longer than {@link #MAX_TUNNEL_LENGTH} or a truncated or overlong body
 * yield a null {@code hole}, which the receiver ignores.
 * 稳定数据包契约 {@code sparkwitch:bear_smoke_hole}（S2C，发给该世界所有玩家）：{@code x:double, y:double, z:double,
 * radius:float, startTime:long, durationTicks:varint}，通道（BEAR WP5，胶囊）随后再加终点 {@code endX:double,
 * endY:double, endZ:double}。爆炸空洞的写法与扩展前完全相同（没有终点），没有终点的正文仍解码为球形，因此旧线格式是有效子集。
 * 解码从不抛出：非有限值、半径不在 {@code (0, MAX_RADIUS]}、时长不在 {@code [1, MAX_DURATION_TICKS]}、通道长于
 * {@link #MAX_TUNNEL_LENGTH}，或正文截断、多余，都得到 null {@code hole}，接收端会忽略。
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
    /**
     * Longest tunnel axis on the wire, in blocks: the server sends one capsule per cloud crossed (a box at most 32 blocks
     * a side, grown by the radius and {@code SmokeTunnels.CLIP_MARGIN}) and splits anything longer.
     * 线格式允许的最长通道轴线（格）：服务端每穿过一团烟发一条胶囊（盒子每边至多 32 格，再按半径与
     * {@code SmokeTunnels.CLIP_MARGIN} 外扩），更长的会被拆开。
     */
    public static final double MAX_TUNNEL_LENGTH = 96.0;
    /** Bytes of the optional end point. / 可选终点的字节数。 */
    private static final int END_POINT_BYTES = 3 * Double.BYTES;

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
        if (value.isTunnel()) {
            buf.writeDouble(value.endX());
            buf.writeDouble(value.endY());
            buf.writeDouble(value.endZ());
        }
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
            double endX = x;
            double endY = y;
            double endZ = z;
            if (buf.readableBytes() == END_POINT_BYTES) {
                endX = buf.readDouble();
                endY = buf.readDouble();
                endZ = buf.readDouble();
            }
            if (buf.readableBytes() == 0 && Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                    && radius > 0.0F && radius <= MAX_RADIUS && duration >= 1 && duration <= MAX_DURATION_TICKS
                    && validEnd(x, y, z, endX, endY, endZ)) {
                hole = new SmokeHole(x, y, z, radius, startTime, duration, endX, endY, endZ);
            }
        } catch (RuntimeException malformed) {
            hole = null;
        }
        buf.skipBytes(buf.readableBytes());
        return new SmokeHoleS2CPayload(hole);
    }

    private static boolean validEnd(double x, double y, double z, double endX, double endY, double endZ) {
        if (!Double.isFinite(endX) || !Double.isFinite(endY) || !Double.isFinite(endZ)) {
            return false;
        }
        double dx = endX - x;
        double dy = endY - y;
        double dz = endZ - z;
        return dx * dx + dy * dy + dz * dz <= MAX_TUNNEL_LENGTH * MAX_TUNNEL_LENGTH;
    }
}
