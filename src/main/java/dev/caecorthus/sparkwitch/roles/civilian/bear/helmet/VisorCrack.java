package dev.caecorthus.sparkwitch.roles.civilian.bear.helmet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

/**
 * Frozen contract (WP0): one bullet hole with its cracks on the Rys-T visor glass (D4). {@code u}/{@code v} locate it
 * on the glass, each in [0, 1] (0,0 = top-left as the wearer looks out); {@code angle} rotates the decal in radians;
 * {@code style} picks one of the crack sprites (WP2 owns the sprite count and wraps any value). The compact
 * constructor clamps u/v and replaces a non-finite value, so decoded data is always drawable. Visual only.
 * 冻结契约（WP0）：Rys-T 面罩玻璃上的一个弹孔及其裂纹（D4）。{@code u}/{@code v} 表示它在玻璃上的位置，各在 [0, 1] 内
 * （0,0 为佩戴者向外看时的左上角）；{@code angle} 为贴花旋转角（弧度）；{@code style} 选择裂纹贴图之一（贴图数量归 WP2，
 * 任意值都会回绕）。紧凑构造器夹紧 u/v 并替换非有限值，因此解码数据始终可绘制。纯视觉。
 */
public record VisorCrack(float u, float v, float angle, int style) {
    public static final Codec<VisorCrack> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("u", 0.5F).forGetter(VisorCrack::u),
            Codec.FLOAT.optionalFieldOf("v", 0.5F).forGetter(VisorCrack::v),
            Codec.FLOAT.optionalFieldOf("angle", 0.0F).forGetter(VisorCrack::angle),
            Codec.INT.optionalFieldOf("style", 0).forGetter(VisorCrack::style)
    ).apply(instance, VisorCrack::new));

    public static final PacketCodec<ByteBuf, VisorCrack> PACKET_CODEC = PacketCodec.tuple(
            PacketCodecs.FLOAT, VisorCrack::u,
            PacketCodecs.FLOAT, VisorCrack::v,
            PacketCodecs.FLOAT, VisorCrack::angle,
            PacketCodecs.VAR_INT, VisorCrack::style,
            VisorCrack::new
    );

    public VisorCrack {
        u = unit(u);
        v = unit(v);
        angle = Float.isFinite(angle) ? angle : 0.0F;
    }

    private static float unit(float value) {
        return Float.isFinite(value) ? Math.max(0.0F, Math.min(1.0F, value)) : 0.5F;
    }
}
