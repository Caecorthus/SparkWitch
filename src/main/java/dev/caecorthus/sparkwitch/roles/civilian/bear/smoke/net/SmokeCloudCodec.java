package dev.caecorthus.sparkwitch.roles.civilian.bear.smoke.net;

import dev.caecorthus.sparkwitch.roles.civilian.bear.smoke.SmokeCloudSnapshot;
import net.minecraft.network.PacketByteBuf;
import org.jetbrains.annotations.Nullable;

/**
 * Stable wire contract for one smoke cloud (sparse): {@code cloudId:varint, minCellX:int, minCellY:int, minCellZ:int,
 * sizeX-1:ubyte, sizeY-1:ubyte, sizeZ-1:ubyte, spawnTime:long, count:varint}, then {@code count} index gaps
 * ({@code varint}, gap = index - previousIndex - 1, the first from -1, so indices strictly increase), then
 * {@code count} cell bytes in the same order. A compact 1600-cell cloud is about 3.3 KB. Decoding runs on the Netty
 * thread and never throws: any malformed or oversized input (box over {@link #MAX_AXIS_CELLS} per axis, count over the
 * volume, an index past the box, an empty cell byte, a truncated or overlong buffer) consumes the rest of the buffer
 * and yields null. The cloud must be the last field of its payload, because trailing bytes count as malformed (vanilla
 * would otherwise disconnect on them).
 * 单团烟的稳定线格式（稀疏）：{@code cloudId:varint, minCellX:int, minCellY:int, minCellZ:int, sizeX-1:ubyte,
 * sizeY-1:ubyte, sizeZ-1:ubyte, spawnTime:long, count:varint}，随后是 {@code count} 个索引间隔（{@code varint}，
 * 间隔 = 索引 - 上一个索引 - 1，首个相对 -1，因此索引严格递增），再是同顺序的 {@code count} 个格字节。紧凑的 1600 格烟团约
 * 3.3 KB。解码在 Netty 线程执行且从不抛出：任何畸形或超限输入（盒子某轴超过 {@link #MAX_AXIS_CELLS}、数量超过体积、
 * 索引越出盒子、空格字节、缓冲区截断或多余）都会消耗剩余字节并返回 null。
 * 烟团必须是其数据包的最后一个字段，因为多余字节视为畸形（否则原版会因此断开连接）。
 */
public final class SmokeCloudCodec {
    /** Largest box edge in cells on the wire. / 线格式上盒子每轴的最大格数。 */
    public static final int MAX_AXIS_CELLS = 64;

    private SmokeCloudCodec() {
    }

    /** True when the snapshot fits the wire format. / 快照能用线格式表达时为 true。 */
    public static boolean encodable(SmokeCloudSnapshot cloud) {
        return cloud.sizeX() <= MAX_AXIS_CELLS && cloud.sizeY() <= MAX_AXIS_CELLS && cloud.sizeZ() <= MAX_AXIS_CELLS
                && cloud.presentCount() > 0;
    }

    public static void write(PacketByteBuf buf, SmokeCloudSnapshot cloud) {
        buf.writeVarInt(cloud.cloudId());
        buf.writeInt(cloud.minCellX());
        buf.writeInt(cloud.minCellY());
        buf.writeInt(cloud.minCellZ());
        buf.writeByte(cloud.sizeX() - 1);
        buf.writeByte(cloud.sizeY() - 1);
        buf.writeByte(cloud.sizeZ() - 1);
        buf.writeLong(cloud.spawnTime());
        byte[] cells = cloud.cells();
        buf.writeVarInt(cloud.presentCount());
        int previous = -1;
        for (int i = 0; i < cells.length; i++) {
            if (cloud.present(i)) {
                buf.writeVarInt(i - previous - 1);
                previous = i;
            }
        }
        for (int i = 0; i < cells.length; i++) {
            if (cloud.present(i)) {
                buf.writeByte(cells[i]);
            }
        }
    }

    @Nullable
    public static SmokeCloudSnapshot read(PacketByteBuf buf) {
        try {
            int cloudId = buf.readVarInt();
            int minCellX = buf.readInt();
            int minCellY = buf.readInt();
            int minCellZ = buf.readInt();
            int sizeX = buf.readUnsignedByte() + 1;
            int sizeY = buf.readUnsignedByte() + 1;
            int sizeZ = buf.readUnsignedByte() + 1;
            long spawnTime = buf.readLong();
            int count = buf.readVarInt();
            if (sizeX > MAX_AXIS_CELLS || sizeY > MAX_AXIS_CELLS || sizeZ > MAX_AXIS_CELLS) {
                return reject(buf);
            }
            int volume = sizeX * sizeY * sizeZ;
            // Every present cell costs at least two bytes, so a lying count fails before any allocation.
            // 每个有烟格至少占两个字节，因此谎报的数量会在分配前就被拒绝。
            if (count <= 0 || count > volume || volume > SmokeCloudSnapshot.MAX_BOX_CELLS
                    || buf.readableBytes() < 2L * count) {
                return reject(buf);
            }
            int[] indices = new int[count];
            long previous = -1L;
            for (int n = 0; n < count; n++) {
                int gap = buf.readVarInt();
                long index = previous + 1L + gap;
                if (gap < 0 || index >= volume) {
                    return reject(buf);
                }
                indices[n] = (int) index;
                previous = index;
            }
            if (buf.readableBytes() != count) {
                return reject(buf);
            }
            byte[] cells = new byte[volume];
            for (int n = 0; n < count; n++) {
                byte cell = buf.readByte();
                if ((cell & SmokeCloudSnapshot.ARRIVAL_MASK) == SmokeCloudSnapshot.EMPTY) {
                    return reject(buf);
                }
                cells[indices[n]] = cell;
            }
            return new SmokeCloudSnapshot(cloudId, minCellX, minCellY, minCellZ, sizeX, sizeY, sizeZ, cells,
                    spawnTime);
        } catch (RuntimeException malformed) {
            return reject(buf);
        }
    }

    @Nullable
    private static SmokeCloudSnapshot reject(PacketByteBuf buf) {
        buf.skipBytes(buf.readableBytes());
        return null;
    }
}
