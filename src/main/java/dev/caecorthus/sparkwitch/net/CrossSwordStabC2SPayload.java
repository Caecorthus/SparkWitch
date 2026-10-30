package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Stable C2S contract {@code sparkwitch:cross_sword_stab}: only the entity id the Knight's client aimed at when it
 * released the cross sword, like Wathe's {@code wathe:knifestab}. The server revalidates everything
 * ({@code CrossSwordStabService}).
 * 稳定的 C2S 契约 {@code sparkwitch:cross_sword_stab}：只携带骑士客户端松开十字剑时瞄准的实体 id，与 Wathe 的
 * {@code wathe:knifestab} 相同。服务端重新校验一切（{@code CrossSwordStabService}）。
 */
public record CrossSwordStabC2SPayload(int targetEntityId) implements CustomPayload {
    public static final Id<CrossSwordStabC2SPayload> ID = new Id<>(SparkWitch.id("cross_sword_stab"));
    public static final PacketCodec<RegistryByteBuf, CrossSwordStabC2SPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER,
            CrossSwordStabC2SPayload::targetEntityId,
            CrossSwordStabC2SPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
