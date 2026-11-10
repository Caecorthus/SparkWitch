package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * C2S intent {@code sparkwitch:savior_pistol_shoot}: only the entity id the client aimed at, exactly like Wathe's
 * {@code wathe:gunshoot}. The server revalidates the held pistol, cooldown, distance and every consequence.
 * C2S 意图 {@code sparkwitch:savior_pistol_shoot}：只携带客户端瞄准的实体 id，与 Wathe 的 {@code wathe:gunshoot} 相同。
 * 服务端重新校验手持手枪、冷却、距离与一切后果。
 */
public record SaviorPistolShootC2SPayload(int targetEntityId) implements CustomPayload {
    public static final Id<SaviorPistolShootC2SPayload> ID = new Id<>(SparkWitch.id("savior_pistol_shoot"));
    public static final PacketCodec<RegistryByteBuf, SaviorPistolShootC2SPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER,
            SaviorPistolShootC2SPayload::targetEntityId,
            SaviorPistolShootC2SPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
