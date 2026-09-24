package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeRules;
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

/** Public roster only: never include guilt, kills, role, or faction. / 仅公开名单，不携带罪责、击杀、身份或阵营。 */
public record OpenJudgeSelectionS2CPacket(UUID sessionId, List<UUID> playerUuids, List<String> playerNames)
        implements CustomPayload {
    public static final int MAX_PLAYER_NAME_LENGTH = 128;
    public static final Identifier PAYLOAD_ID = SparkWitch.id("open_judge_selection_s2c");
    public static final Id<OpenJudgeSelectionS2CPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, OpenJudgeSelectionS2CPacket> CODEC =
            PacketCodec.of(OpenJudgeSelectionS2CPacket::write, OpenJudgeSelectionS2CPacket::read);

    public OpenJudgeSelectionS2CPacket {
        Objects.requireNonNull(sessionId, "sessionId");
        playerUuids = List.copyOf(playerUuids);
        playerNames = List.copyOf(playerNames);
        if (playerUuids.size() != playerNames.size() || playerUuids.size() > JudgeRules.MAX_SELECTION_TARGETS
                || new HashSet<>(playerUuids).size() != playerUuids.size()) {
            throw new IllegalArgumentException("Invalid Judge selector roster");
        }
        for (String name : playerNames) {
            if (name.length() > MAX_PLAYER_NAME_LENGTH) {
                throw new IllegalArgumentException("Judge selector name is too long");
            }
        }
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    void write(PacketByteBuf buf) {
        buf.writeUuid(sessionId);
        buf.writeVarInt(playerUuids.size());
        for (int index = 0; index < playerUuids.size(); index++) {
            buf.writeUuid(playerUuids.get(index));
            buf.writeString(playerNames.get(index), MAX_PLAYER_NAME_LENGTH);
        }
    }

    static OpenJudgeSelectionS2CPacket read(PacketByteBuf buf) {
        UUID sessionId = buf.readUuid();
        int count = buf.readVarInt();
        // Bound before allocating; each entry is an inseparable UUID/name pair. / 分配前限制数量，每项固定为 UUID 与名字一对。
        if (count < 0 || count > JudgeRules.MAX_SELECTION_TARGETS) {
            throw new IllegalArgumentException("Judge selector roster is too large");
        }
        List<UUID> uuids = new ArrayList<>(count);
        List<String> names = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            uuids.add(buf.readUuid());
            names.add(buf.readString(MAX_PLAYER_NAME_LENGTH));
        }
        return new OpenJudgeSelectionS2CPacket(sessionId, uuids, names);
    }
}
