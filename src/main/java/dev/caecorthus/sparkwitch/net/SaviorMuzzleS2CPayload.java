package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * S2C {@code sparkwitch:savior_muzzle}: tells clients a pistol fired, so everyone tracked sees the muzzle flash at the
 * recorded hand position. Mirrors Wathe's {@code shoot_muzzle_s2c} payload.
 * S2C {@code sparkwitch:savior_muzzle}：通知客户端有人开枪，让所有追踪者看到记录的手部位置的枪口火焰。
 * 与 Wathe 的 {@code shoot_muzzle_s2c} 数据包一致。
 */
public record SaviorMuzzleS2CPayload(String shooterId) implements CustomPayload {
    public static final Id<SaviorMuzzleS2CPayload> ID = new Id<>(SparkWitch.id("savior_muzzle"));
    public static final PacketCodec<RegistryByteBuf, SaviorMuzzleS2CPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING,
            SaviorMuzzleS2CPayload::shooterId,
            SaviorMuzzleS2CPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
