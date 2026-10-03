package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Opens the Prophecy screen for one server session. Carries only who is dead (owner decision Q3) — never a death
 * reason, cause group or killer; those reach the client solely through a correct guess's owner-synced record.
 * 为一次服务端会话打开预言界面。只携带谁已死亡（所有者决定 Q3），绝不携带死因、死因分组或凶手；
 * 这些信息只会在猜中后通过仅同步给拥有者的记录到达客户端。
 */
public record OpenProphecyS2CPacket(UUID sessionId, List<Candidate> candidates) implements CustomPayload {
    public static final int MAX_CANDIDATES = 256;
    public static final int MAX_NAME_LENGTH = 64;
    public static final Identifier PAYLOAD_ID = SparkWitch.id("open_prophecy");
    public static final Id<OpenProphecyS2CPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, OpenProphecyS2CPacket> CODEC =
            PacketCodec.of(OpenProphecyS2CPacket::write, OpenProphecyS2CPacket::read);

    /** One guessable dead player. / 一名可预言的死者。 */
    public record Candidate(UUID player, String name) {
        public Candidate {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(name, "name");
            if (name.length() > MAX_NAME_LENGTH) {
                throw new IllegalArgumentException("Prophecy candidate name is too long");
            }
        }
    }

    public OpenProphecyS2CPacket {
        Objects.requireNonNull(sessionId, "sessionId");
        candidates = List.copyOf(candidates);
        if (candidates.size() > MAX_CANDIDATES) {
            throw new IllegalArgumentException("Prophecy candidate list is too large");
        }
        Set<UUID> seen = new HashSet<>();
        for (Candidate candidate : candidates) {
            if (!seen.add(candidate.player())) {
                throw new IllegalArgumentException("Duplicate Prophecy candidate");
            }
        }
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    void write(PacketByteBuf buf) {
        buf.writeUuid(sessionId);
        buf.writeVarInt(candidates.size());
        for (Candidate candidate : candidates) {
            buf.writeUuid(candidate.player());
            buf.writeString(candidate.name(), MAX_NAME_LENGTH);
        }
    }

    static OpenProphecyS2CPacket read(PacketByteBuf buf) {
        UUID sessionId = buf.readUuid();
        int count = buf.readVarInt();
        // Bound before allocating. / 分配前先限制数量。
        if (count < 0 || count > MAX_CANDIDATES) {
            throw new IllegalArgumentException("Prophecy candidate list is too large");
        }
        List<Candidate> candidates = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            candidates.add(new Candidate(buf.readUuid(), buf.readString(MAX_NAME_LENGTH)));
        }
        return new OpenProphecyS2CPacket(sessionId, candidates);
    }
}
