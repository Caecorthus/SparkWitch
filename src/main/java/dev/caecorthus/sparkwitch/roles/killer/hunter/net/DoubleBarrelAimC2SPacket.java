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
 * Stable packet contract {@code sparkwitch:double_barrel_aim} (C2S): {@code rayCount:varint, per ray:
 * count:varint, ids:varint[count]} — one claim per spread ray (at most {@link HunterShotgunAim#RAY_COUNT} rays, at most
 * {@link HunterShotgunAim#MAX_CLAIMED_PER_RAY} players each, nearest first), the players the shooter's client saw on
 * each spread ray. Sent at the head of the client's {@code interactItem} with a shotgun in that hand, before vanilla's
 * use packet, so the server reads it on the same shot. It fires nothing itself; the server confirms it. The client
 * builds one list per ray of the shot its top shell will fire (1 for a slug, {@link HunterShotgunAim#RAY_COUNT} for
 * buckshot); a claim with another ray count is ignored as if none were sent ({@link HunterShotgunAim#claimForRay}). A
 * malformed payload decodes to an empty claim, on which every ray misses, instead of disconnecting.
 * 稳定数据包契约 {@code sparkwitch:double_barrel_aim}（C2S）：每条散射射线一组声明（最多 {@link HunterShotgunAim#RAY_COUNT} 条
 * 射线，每条最多 {@link HunterShotgunAim#MAX_CLAIMED_PER_RAY} 名玩家，按由近到远排列），即射手客户端在每条散射射线上看到的玩家。
 * 当该手持有猎枪时，在客户端 {@code interactItem} 开头、原版使用数据包之前发送，因此服务端在同一次射击中读取它。它本身不触发
 * 射击，由服务端确认。客户端按栈顶弹药将射出的形状为每条射线生成一组（独头弹 1 组、霰弹 {@link HunterShotgunAim#RAY_COUNT} 组）；
 * 射线数不符的声明按未发送处理（{@link HunterShotgunAim#claimForRay}）。格式错误的载荷解码为空声明，每条射线都未命中，
 * 不会断开连接。
 */
public record DoubleBarrelAimC2SPacket(List<List<Integer>> rays) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("double_barrel_aim");
    public static final Id<DoubleBarrelAimC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, DoubleBarrelAimC2SPacket> CODEC =
            PacketCodec.of(DoubleBarrelAimC2SPacket::write, DoubleBarrelAimC2SPacket::read);

    public DoubleBarrelAimC2SPacket {
        List<List<Integer>> copy = new ArrayList<>(Math.min(rays.size(), HunterShotgunAim.RAY_COUNT));
        for (List<Integer> ray : rays) {
            copy.add(List.copyOf(ray.subList(0, Math.min(ray.size(), HunterShotgunAim.MAX_CLAIMED_PER_RAY))));
        }
        rays = List.copyOf(copy);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeVarInt(rays.size());
        for (List<Integer> ray : rays) {
            buf.writeVarInt(ray.size());
            for (int id : ray) {
                buf.writeVarInt(id);
            }
        }
    }

    public static DoubleBarrelAimC2SPacket read(PacketByteBuf buf) {
        try {
            int rayCount = buf.readVarInt();
            if (rayCount >= 0 && rayCount <= HunterShotgunAim.RAY_COUNT) {
                List<List<Integer>> rays = new ArrayList<>(rayCount);
                for (int rayIndex = 0; rayIndex < rayCount; rayIndex++) {
                    int count = buf.readVarInt();
                    if (count < 0 || count > HunterShotgunAim.MAX_CLAIMED_PER_RAY) {
                        throw new RuntimeException("bad ray claim count");
                    }
                    List<Integer> ids = new ArrayList<>(count);
                    for (int index = 0; index < count; index++) {
                        ids.add(buf.readVarInt());
                    }
                    rays.add(ids);
                }
                if (buf.readableBytes() == 0) {
                    return new DoubleBarrelAimC2SPacket(rays);
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
