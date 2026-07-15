package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Opens a paid selector; survival targets come from the authoritative server roster.
 * 打开已付费的选择界面；存活占卜目标名单由服务端权威提供。
 */
public record OpenTarotDivinationSelectorS2CPacket(
        int mode,
        List<UUID> playerIds,
        List<String> playerNames
) implements CustomPayload {
    public static final int MODE_IDENTITY = 1;
    public static final int MODE_SURVIVAL = 2;
    private static final int MAX_TARGETS = 256;
    private static final int MAX_PLAYER_NAME_LENGTH = 64;

    public static final Identifier PAYLOAD_ID = SparkWitch.id("open_tarot_divination_selector");
    public static final Id<OpenTarotDivinationSelectorS2CPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, OpenTarotDivinationSelectorS2CPacket> CODEC =
            PacketCodec.of(OpenTarotDivinationSelectorS2CPacket::write, OpenTarotDivinationSelectorS2CPacket::read);

    public OpenTarotDivinationSelectorS2CPacket {
        playerIds = List.copyOf(playerIds);
        playerNames = List.copyOf(playerNames);
        if (playerIds.size() != playerNames.size() || playerIds.size() > MAX_TARGETS) {
            throw new IllegalArgumentException("Tarot selector roster is invalid");
        }
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private void write(PacketByteBuf buf) {
        buf.writeVarInt(mode);
        buf.writeVarInt(playerIds.size());
        for (int index = 0; index < playerIds.size(); index++) {
            buf.writeUuid(playerIds.get(index));
            buf.writeString(playerNames.get(index), MAX_PLAYER_NAME_LENGTH);
        }
    }

    private static OpenTarotDivinationSelectorS2CPacket read(PacketByteBuf buf) {
        int mode = buf.readVarInt();
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_TARGETS) {
            throw new IllegalArgumentException("Tarot selector roster is too large");
        }
        java.util.ArrayList<UUID> playerIds = new java.util.ArrayList<>(size);
        java.util.ArrayList<String> playerNames = new java.util.ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            playerIds.add(buf.readUuid());
            playerNames.add(buf.readString(MAX_PLAYER_NAME_LENGTH));
        }
        return new OpenTarotDivinationSelectorS2CPacket(mode, playerIds, playerNames);
    }
}
