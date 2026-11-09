package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Carries one purchaser-only faction-count snapshot; clients must not recalculate it live. Wire order: civilian,
 * killer, neutral, witch, jiahao, one VarInt each (Team Jiahao appended 2026-11-04, so client and server jars must
 * match).
 * 仅向购买者下发一次阵营人数快照；客户端不得实时重算。字段顺序：好人、杀手、中立、魔女、嘉豪，各一个 VarInt
 * （嘉豪于 2026-11-04 追加在末尾，客户端与服务端的 jar 必须一致）。
 */
public record TarotDivinationSnapshotS2CPacket(
        int civilianCount,
        int killerCount,
        int neutralCount,
        int witchCount,
        int jiahaoCount
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
        buf.writeVarInt(jiahaoCount);
    }

    private static TarotDivinationSnapshotS2CPacket read(PacketByteBuf buf) {
        return new TarotDivinationSnapshotS2CPacket(
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt()
        );
    }
}
