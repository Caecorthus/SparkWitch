package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Carries one purchaser-only faction-count snapshot; clients must not recalculate it live.
 * 仅向购买者下发一次阵营人数快照；客户端不得实时重算。
 */
public record TarotDivinationSnapshotS2CPacket(
        int civilianCount,
        int killerCount,
        int neutralCount,
        int witchCount
) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("tarot_divination_snapshot");
    public static final Id<TarotDivinationSnapshotS2CPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, TarotDivinationSnapshotS2CPacket> CODEC =
            PacketCodec.of(TarotDivinationSnapshotS2CPacket::write, TarotDivinationSnapshotS2CPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private void write(PacketByteBuf buf) {
        buf.writeVarInt(civilianCount);
        buf.writeVarInt(killerCount);
        buf.writeVarInt(neutralCount);
        buf.writeVarInt(witchCount);
    }

    private static TarotDivinationSnapshotS2CPacket read(PacketByteBuf buf) {
        return new TarotDivinationSnapshotS2CPacket(
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt()
        );
    }
}
