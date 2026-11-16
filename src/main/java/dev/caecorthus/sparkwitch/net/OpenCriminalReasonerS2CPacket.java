package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Opens the reasoning menu: the victims this reasoner may still solve, and the living players he may name.
 * <p>Carries public facts only — no role, faction or the recorded answer.
 * 打开推理菜单：他还能推理的死者，以及可以被指认的存活玩家。
 * <p>只携带公开事实，不含身份、阵营或已记录的答案。
 */
public record OpenCriminalReasonerS2CPacket(
        UUID sessionId,
        List<UUID> victimUuids,
        List<String> victimNames,
        List<UUID> suspectUuids,
        List<String> suspectNames
) implements CustomPayload {
    public static final int MAX_ENTRIES = 64;
    public static final int MAX_PLAYER_NAME_LENGTH = 128;
    public static final Identifier PAYLOAD_ID = SparkWitch.id("open_criminal_reasoner_s2c");
    public static final Id<OpenCriminalReasonerS2CPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, OpenCriminalReasonerS2CPacket> CODEC =
            PacketCodec.of(OpenCriminalReasonerS2CPacket::write, OpenCriminalReasonerS2CPacket::read);

    public OpenCriminalReasonerS2CPacket {
        Objects.requireNonNull(sessionId, "sessionId");
        victimUuids = List.copyOf(victimUuids);
        victimNames = List.copyOf(victimNames);
        suspectUuids = List.copyOf(suspectUuids);
        suspectNames = List.copyOf(suspectNames);
        checkRoster("victims", victimUuids, victimNames);
        checkRoster("suspects", suspectUuids, suspectNames);
    }

    private static void checkRoster(String label, List<UUID> uuids, List<String> names) {
        if (uuids.size() != names.size() || uuids.size() > MAX_ENTRIES
                || new HashSet<>(uuids).size() != uuids.size()) {
            throw new IllegalArgumentException("Invalid Criminal Reasoner " + label + " roster");
        }
        for (String name : names) {
            if (name.length() > MAX_PLAYER_NAME_LENGTH) {
                throw new IllegalArgumentException("Criminal Reasoner name is too long");
            }
        }
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    void write(PacketByteBuf buf) {
        buf.writeUuid(sessionId);
        writeRoster(buf, victimUuids, victimNames);
        writeRoster(buf, suspectUuids, suspectNames);
    }

    private static void writeRoster(PacketByteBuf buf, List<UUID> uuids, List<String> names) {
        buf.writeVarInt(uuids.size());
        for (int index = 0; index < uuids.size(); index++) {
            buf.writeUuid(uuids.get(index));
            buf.writeString(names.get(index), MAX_PLAYER_NAME_LENGTH);
        }
    }

    static OpenCriminalReasonerS2CPacket read(PacketByteBuf buf) {
        UUID sessionId = buf.readUuid();
        List<UUID> victimUuids = new ArrayList<>();
        List<String> victimNames = new ArrayList<>();
        readRoster(buf, victimUuids, victimNames);
        List<UUID> suspectUuids = new ArrayList<>();
        List<String> suspectNames = new ArrayList<>();
        readRoster(buf, suspectUuids, suspectNames);
        return new OpenCriminalReasonerS2CPacket(
                sessionId, victimUuids, victimNames, suspectUuids, suspectNames);
    }

    private static void readRoster(PacketByteBuf buf, List<UUID> uuids, List<String> names) {
        int count = buf.readVarInt();
        // Bound before allocating; each entry is an inseparable UUID/name pair.
        // 分配前限制数量；每项是 UUID 与名字不可分割的一对。
        if (count < 0 || count > MAX_ENTRIES) {
            throw new IllegalArgumentException("Criminal Reasoner roster is too large");
        }
        for (int index = 0; index < count; index++) {
            uuids.add(buf.readUuid());
            names.add(buf.readString(MAX_PLAYER_NAME_LENGTH));
        }
    }
}
