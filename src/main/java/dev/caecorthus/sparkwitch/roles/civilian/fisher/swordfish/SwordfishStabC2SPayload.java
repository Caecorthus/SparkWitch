package dev.caecorthus.sparkwitch.roles.civilian.fisher.swordfish;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Stable C2S contract {@code sparkwitch:swordfish_stab}: only the aimed entity id; the server revalidates everything.
 * 稳定的 C2S 契约 {@code sparkwitch:swordfish_stab}：只携带瞄准的实体 id，服务端重新校验一切。
 */
public record SwordfishStabC2SPayload(int targetEntityId) implements CustomPayload {
    public static final Id<SwordfishStabC2SPayload> ID = new Id<>(SparkWitch.id("swordfish_stab"));
    public static final PacketCodec<RegistryByteBuf, SwordfishStabC2SPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER,
            SwordfishStabC2SPayload::targetEntityId,
            SwordfishStabC2SPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
