package dev.caecorthus.sparkwitch.roles.witch.riftwalker.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Stable packet contract {@code sparkwitch:rift_gate_console} (S2C, the requesting Riftwalker only): {@code
 * consoleSessionId:varint, count:varint, count × entry}; entry = {@code number:varint, x:double, y:double, z:double,
 * facing:enum(Direction), occupantCount:varint, occupantCount × name:string(16)}. {@code consoleSessionId == }
 * {@link #CLOSED} (0) tells the client to close the console (denied or invalidated) and carries no entries. At most
 * {@link #MAX_ENTRIES} rows (lowest numbers first) and {@link #MAX_OCCUPANTS} names per row; the server never sends
 * placer identities.
 * 稳定数据包契约 {@code sparkwitch:rift_gate_console}（S2C，仅发给请求的隙行者）：{@code consoleSessionId:varint,
 * count:varint, count × entry}；entry = {@code number:varint, x/y/z:double, facing:enum(Direction), occupantCount:varint,
 * occupantCount × name:string(16)}。{@code consoleSessionId ==} {@link #CLOSED}（0）表示客户端应关闭控制台（被拒绝或已失效），
 * 且不带条目。最多 {@link #MAX_ENTRIES} 行（编号从小到大）、每行最多 {@link #MAX_OCCUPANTS} 个名字；服务端从不发送放置者身份。
 */
public record RiftGateConsoleS2CPacket(int consoleSessionId, List<Entry> entries) implements CustomPayload {
    public static final int CLOSED = 0;
    public static final int MAX_ENTRIES = 256;
    public static final int MAX_OCCUPANTS = 64;
    public static final int MAX_NAME_LENGTH = 16;
    public static final Identifier PAYLOAD_ID = SparkWitch.id("rift_gate_console");
    public static final Id<RiftGateConsoleS2CPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, RiftGateConsoleS2CPacket> CODEC =
            PacketCodec.of(RiftGateConsoleS2CPacket::write, RiftGateConsoleS2CPacket::read);

    public RiftGateConsoleS2CPacket {
        List<Entry> rows = entries == null ? List.of() : entries;
        entries = List.copyOf(rows.size() > MAX_ENTRIES ? rows.subList(0, MAX_ENTRIES) : rows);
    }

    /** The "close the console" reply. / 「关闭控制台」回复。 */
    public static RiftGateConsoleS2CPacket closed() {
        return new RiftGateConsoleS2CPacket(CLOSED, List.of());
    }

    /**
     * One gate row; {@code (x, y, z)} is the gate position (bottom centre) so the client can show distance and direction
     * without the entity being tracked.
     * 一行门数据；{@code (x, y, z)} 为门的位置（底部中心），客户端无需追踪实体即可显示距离与方向。
     */
    public record Entry(int number, double x, double y, double z, Direction facing, List<String> occupantNames) {
        public Entry {
            facing = Objects.requireNonNullElse(facing, Direction.NORTH);
            List<String> names = occupantNames == null ? List.of() : occupantNames;
            // Profile names are at most 16 characters; clamp anyway so encoding can never throw.
            // 玩家名最多 16 个字符；仍做截断，确保编码永不抛出异常。
            occupantNames = (names.size() > MAX_OCCUPANTS ? names.subList(0, MAX_OCCUPANTS) : names).stream()
                    .map(name -> name == null ? "" : name.length() > MAX_NAME_LENGTH
                            ? name.substring(0, MAX_NAME_LENGTH) : name)
                    .toList();
        }
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeVarInt(consoleSessionId);
        buf.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buf.writeVarInt(entry.number());
            buf.writeDouble(entry.x());
            buf.writeDouble(entry.y());
            buf.writeDouble(entry.z());
            buf.writeEnumConstant(entry.facing());
            buf.writeVarInt(entry.occupantNames().size());
            for (String name : entry.occupantNames()) {
                buf.writeString(name, MAX_NAME_LENGTH);
            }
        }
    }

    public static RiftGateConsoleS2CPacket read(PacketByteBuf buf) {
        int session = buf.readVarInt();
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) {
            throw new DecoderException("Rift gate console row count out of range: " + count);
        }
        List<Entry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int number = buf.readVarInt();
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            Direction facing = buf.readEnumConstant(Direction.class);
            int occupants = buf.readVarInt();
            if (occupants < 0 || occupants > MAX_OCCUPANTS) {
                throw new DecoderException("Rift gate occupant count out of range: " + occupants);
            }
            List<String> names = new ArrayList<>(occupants);
            for (int name = 0; name < occupants; name++) {
                names.add(buf.readString(MAX_NAME_LENGTH));
            }
            entries.add(new Entry(number, x, y, z, facing, names));
        }
        return new RiftGateConsoleS2CPacket(session, entries);
    }
}
