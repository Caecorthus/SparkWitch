package dev.caecorthus.sparkwitch.roles.civilian.bear.ricochet;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * Stable packet contract {@code sparkwitch:bear_rys_t_ricochet} (S2C, the wearer only): {@code dx:float, dy:float,
 * dz:float, glass:boolean}. {@code (dx, dy, dz)} is the world-space unit direction from the wearer's eyes toward the
 * shot origin, or zero when the origin is unknown; {@code glass} is a visor-glass hit (D4). The client runs the D5 daze
 * (view jolt away from the shot, fading double vision, tinnitus, muffled world sounds). Presentation only: the server
 * has already cancelled the kill. Decoding never throws: a non-finite or overlong direction becomes zero, a truncated
 * or overlong body yields the default.
 * 稳定数据包契约 {@code sparkwitch:bear_rys_t_ricochet}（S2C，只发给佩戴者）：{@code dx:float, dy:float, dz:float,
 * glass:boolean}。{@code (dx, dy, dz)} 是从佩戴者眼睛指向射击来源的世界坐标单位方向，来源未知时为零；{@code glass} 表示命中
 * 面罩玻璃（D4）。客户端据此执行 D5 眩晕（背离来枪方向的视角震动、渐隐重影、耳鸣、世界声音发闷）。纯表现：服务端已取消这次
 * 击杀。解码从不抛出：非有限或过长的方向视为零，正文截断或多余时得到默认值。
 */
public record RysTRicochetS2CPayload(float dx, float dy, float dz, boolean glass) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("bear_rys_t_ricochet");
    public static final Id<RysTRicochetS2CPayload> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, RysTRicochetS2CPayload> CODEC =
            PacketCodec.of(RysTRicochetS2CPayload::write, RysTRicochetS2CPayload::read);

    public RysTRicochetS2CPayload {
        double length = Math.sqrt((double) dx * dx + (double) dy * dy + (double) dz * dz);
        if (!Double.isFinite(length) || length > 1.001 || length < 1.0E-4) {
            dx = 0.0F;
            dy = 0.0F;
            dz = 0.0F;
        }
    }

    /**
     * The payload for a shot from {@code origin} (null = unknown) hitting the wearer whose eyes are at {@code eye}.
     * 来自 {@code origin}（null 表示未知）、命中眼睛位于 {@code eye} 的佩戴者的一次射击所对应的数据包。
     */
    public static RysTRicochetS2CPayload of(Vec3d eye, Vec3d origin, boolean glass) {
        if (eye == null || origin == null) {
            return new RysTRicochetS2CPayload(0.0F, 0.0F, 0.0F, glass);
        }
        Vec3d toward = origin.subtract(eye);
        double length = toward.length();
        if (!(length > 1.0E-4) || !Double.isFinite(length)) {
            return new RysTRicochetS2CPayload(0.0F, 0.0F, 0.0F, glass);
        }
        return new RysTRicochetS2CPayload((float) (toward.x / length), (float) (toward.y / length),
                (float) (toward.z / length), glass);
    }

    /** Whether the shot origin is known. / 射击来源是否已知。 */
    public boolean hasDirection() {
        return dx != 0.0F || dy != 0.0F || dz != 0.0F;
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeFloat(dx);
        buf.writeFloat(dy);
        buf.writeFloat(dz);
        buf.writeBoolean(glass);
    }

    public static RysTRicochetS2CPayload read(PacketByteBuf buf) {
        RysTRicochetS2CPayload payload = new RysTRicochetS2CPayload(0.0F, 0.0F, 0.0F, false);
        try {
            float dx = buf.readFloat();
            float dy = buf.readFloat();
            float dz = buf.readFloat();
            boolean glass = buf.readBoolean();
            if (buf.readableBytes() == 0) {
                payload = new RysTRicochetS2CPayload(dx, dy, dz, glass);
            }
        } catch (RuntimeException malformed) {
            payload = new RysTRicochetS2CPayload(0.0F, 0.0F, 0.0F, false);
        }
        buf.skipBytes(buf.readableBytes());
        return payload;
    }
}
