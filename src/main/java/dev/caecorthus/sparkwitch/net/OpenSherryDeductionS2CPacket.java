package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Candidates for a deduction: only the players inside the 60-block reach. 推演候选：只有 60 格内的玩家。 */
public record OpenSherryDeductionS2CPacket(List<UUID> targetUuids, List<String> targetNames)
        implements CustomPayload {
    public static final int MAX_ENTRIES = 64;
    public static final int MAX_NAME = 128;
    public static final Identifier PAYLOAD_ID = SparkWitch.id("open_sherry_deduction_s2c");
    public static final Id<OpenSherryDeductionS2CPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, OpenSherryDeductionS2CPacket> CODEC =
            PacketCodec.of(OpenSherryDeductionS2CPacket::write, OpenSherryDeductionS2CPacket::read);

    public OpenSherryDeductionS2CPacket {
        Objects.requireNonNull(targetUuids, "targetUuids");
        targetUuids = List.copyOf(targetUuids);
        targetNames = List.copyOf(targetNames);
        if (targetUuids.size() != targetNames.size() || targetUuids.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Invalid deduction roster");
        }
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    void write(PacketByteBuf buf) {
        buf.writeVarInt(targetUuids.size());
        for (int index = 0; index < targetUuids.size(); index++) {
            buf.writeUuid(targetUuids.get(index));
            buf.writeString(targetNames.get(index), MAX_NAME);
        }
    }

    static OpenSherryDeductionS2CPacket read(PacketByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) {
            throw new IllegalArgumentException("Deduction roster is too large");
        }
        List<UUID> uuids = new ArrayList<>(count);
        List<String> names = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            uuids.add(buf.readUuid());
            names.add(buf.readString(MAX_NAME));
        }
        return new OpenSherryDeductionS2CPacket(uuids, names);
    }
}
