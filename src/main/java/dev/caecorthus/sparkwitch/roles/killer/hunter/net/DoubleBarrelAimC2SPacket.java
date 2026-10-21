package dev.caecorthus.sparkwitch.roles.killer.hunter.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.killer.hunter.HunterShotgunAim;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Stable packet contract {@code sparkwitch:double_barrel_aim} (C2S): {@code count:varint, ids:varint[count]}, the
 * players the shooter's client saw on its shotgun ray, nearest first (at most
 * {@link HunterShotgunAim#MAX_CLAIMED_TARGETS}). Sent at the head of the client's {@code interactItem} with a shotgun
 * in that hand, before vanilla's use packet, so the server reads it on the same shot. It fires nothing itself; the
 * server confirms it. A malformed payload decodes to an empty claim (a miss) instead of disconnecting.
 * 稳定数据包契约 {@code sparkwitch:double_barrel_aim}（C2S）：射手客户端在猎枪射线上看到的玩家，按由近到远排列
 * （最多 {@link HunterShotgunAim#MAX_CLAIMED_TARGETS} 个）。当该手持有猎枪时，在客户端 {@code interactItem} 开头、
 * 原版使用数据包之前发送，因此服务端在同一次射击中读取它。它本身不触发射击，由服务端确认。格式错误的载荷解码为
 * 空声明（未命中），不会断开连接。
 */
public record DoubleBarrelAimC2SPacket(List<Integer> targetIds) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("double_barrel_aim");
    public static final Id<DoubleBarrelAimC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, DoubleBarrelAimC2SPacket> CODEC =
            PacketCodec.of(DoubleBarrelAimC2SPacket::write, DoubleBarrelAimC2SPacket::read);

    public DoubleBarrelAimC2SPacket {
        targetIds = List.copyOf(targetIds.subList(0, Math.min(targetIds.size(), HunterShotgunAim.MAX_CLAIMED_TARGETS)));
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeVarInt(targetIds.size());
        for (int id : targetIds) {
            buf.writeVarInt(id);
        }
    }

    public static DoubleBarrelAimC2SPacket read(PacketByteBuf buf) {
        try {
            int count = buf.readVarInt();
            if (count >= 0 && count <= HunterShotgunAim.MAX_CLAIMED_TARGETS) {
                List<Integer> ids = new ArrayList<>(count);
                for (int index = 0; index < count; index++) {
                    ids.add(buf.readVarInt());
                }
                if (buf.readableBytes() == 0) {
                    return new DoubleBarrelAimC2SPacket(ids);
                }
            }
        } catch (RuntimeException malformed) {
            // Falls through to the empty claim. / 落到下方的空声明。
        }
        // Drain a malformed tail so the outer decoder does not reject leftover bytes.
        // 丢弃残缺尾部，避免外层解码器因剩余字节而断开连接。
        buf.skipBytes(buf.readableBytes());
        return new DoubleBarrelAimC2SPacket(List.of());
    }
}
