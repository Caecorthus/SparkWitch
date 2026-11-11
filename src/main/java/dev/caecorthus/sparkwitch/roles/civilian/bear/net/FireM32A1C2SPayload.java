package dev.caecorthus.sparkwitch.roles.civilian.bear.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Stable packet contract: the M32A1 fire intent {@code sparkwitch:fire_m32a1}, carrying the launch direction at click
 * time like the anti-tank launcher's (the attack key is handled before that tick's rotation packet): the look for hip
 * fire, and the look raised by the dialled range's bore elevation for a shot aimed through the M2A1 (owner 2026-11-11:
 * the sight stays centred, the launcher noses up). The server uses the aim only as the launch direction from its own
 * eye position and validates everything else ({@code M32FireService}). Decoding
 * is tolerant: a payload without aim decodes to NaN and the server falls back to its own rotation. The id is on the
 * Control Expert stun, Seeker session, Rift occupant and Knight duel deny-lists here, and cross-repo on SparkStrength
 * {@code TaotieHeadRules} and SparkFactionAPI {@code AntiFastRoundRules}.
 * 稳定数据包契约：M32A1 开火意图 {@code sparkwitch:fire_m32a1}，与反坦克炮筒一样携带点击瞬间的发射方向（攻击键先于该刻的朝向
 * 数据包处理）：腰射为视线方向；通过 M2A1 瞄准射击时为视线再抬高当前刻度对应的枪膛抬高角（所有者 2026-11-11：镜子居中，
 * 发射器抬头）。服务端只把朝向用作从其自身眼部位置出发的发射方向，其余全部复核（{@code M32FireService}）。解码是宽容的：不带
 * 朝向的载荷解码为 NaN，服务端回退到自身朝向。该 id 在本模组的控场专家眩晕、搜寻者会话、隙行者门内与骑士决斗拒绝列表中，
 * 跨仓库则在 SparkStrength {@code TaotieHeadRules} 与 SparkFactionAPI {@code AntiFastRoundRules} 中。
 */
public record FireM32A1C2SPayload(float yaw, float pitch) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("fire_m32a1");
    public static final Id<FireM32A1C2SPayload> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, FireM32A1C2SPayload> CODEC =
            PacketCodec.of(FireM32A1C2SPayload::write, FireM32A1C2SPayload::read);

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

    public static FireM32A1C2SPayload read(PacketByteBuf buf) {
        if (buf.readableBytes() >= 2 * Float.BYTES) {
            return new FireM32A1C2SPayload(buf.readFloat(), buf.readFloat());
        }
        // Drain a malformed tail so the outer decoder does not reject leftover bytes.
        // 丢弃残缺尾部，避免外层解码器因剩余字节而断开连接。
        buf.skipBytes(buf.readableBytes());
        return new FireM32A1C2SPayload(Float.NaN, Float.NaN);
    }
}
