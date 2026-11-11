package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * S2C {@code sparkwitch:savior_gun_drop}: the innocent-shooting punishment dropped the shooter's pistol; the client
 * removes its local copy immediately so the drop animation does not replay. Mirrors Wathe's {@code gundrop} payload.
 * S2C {@code sparkwitch:savior_gun_drop}：误杀惩罚已打掉射手的手枪；客户端立刻移除本地副本，避免重播掉落动画。
 * 与 Wathe 的 {@code gundrop} 数据包一致。
 */
public record SaviorGunDropS2CPayload() implements CustomPayload {
    public static final Id<SaviorGunDropS2CPayload> ID = new Id<>(SparkWitch.id("savior_gun_drop"));
    public static final PacketCodec<RegistryByteBuf, SaviorGunDropS2CPayload> CODEC =
            PacketCodec.unit(new SaviorGunDropS2CPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
