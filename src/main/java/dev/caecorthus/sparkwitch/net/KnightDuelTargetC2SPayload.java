package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

import java.util.UUID;

/** Client proposes a duel target; the server re-validates everything. / 客户端提议决斗目标；服务端重查全部规则。 */
public record KnightDuelTargetC2SPayload(UUID target) implements CustomPayload {
    public static final Id<KnightDuelTargetC2SPayload> ID = new Id<>(SparkWitch.id("knight_duel_target"));
    public static final PacketCodec<RegistryByteBuf, KnightDuelTargetC2SPayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeUuid(value.target),
            buf -> new KnightDuelTargetC2SPayload(buf.readUuid()));

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
