package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

import java.util.UUID;

/** Explicit duel state for clients, independent of component sync. / 独立的决斗状态同步，不依赖组件同步。 */
public record KnightDuelStateS2CPayload(boolean active, UUID knight, UUID target, int remainingTicks)
        implements CustomPayload {
    public static final Id<KnightDuelStateS2CPayload> ID = new Id<>(SparkWitch.id("knight_duel_state"));
    public static final PacketCodec<RegistryByteBuf, KnightDuelStateS2CPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeBoolean(value.active);
                if (value.active) {
                    buf.writeUuid(value.knight);
                    buf.writeUuid(value.target);
                    buf.writeVarInt(value.remainingTicks);
                }
            },
            buf -> {
                if (!buf.readBoolean()) {
                    return new KnightDuelStateS2CPayload(false, null, null, 0);
                }
                UUID knight = buf.readUuid();
                UUID target = buf.readUuid();
                int remaining = Math.max(0, buf.readVarInt());
                return new KnightDuelStateS2CPayload(true, knight, target, remaining);
            });

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public static KnightDuelStateS2CPayload inactive() {
        return new KnightDuelStateS2CPayload(false, null, null, 0);
    }
}
