package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * S2C {@code sparkwitch:savior_domain}: the Savior opened the 15-block domain. Clients use the caster entity id and
 * duration for the keybind-free white/role-color outlines; the server remains authoritative for every effect.
 * S2C {@code sparkwitch:savior_domain}：救世主展开了 15 格领域。客户端用施法者实体 id 与持续时间绘制无需本能键的
 * 白色/职业色描边；所有效果仍由服务端决定。
 */
public record SaviorDomainS2CPayload(int saviorEntityId, int durationTicks) implements CustomPayload {
    public static final Id<SaviorDomainS2CPayload> ID = new Id<>(SparkWitch.id("savior_domain"));
    public static final PacketCodec<RegistryByteBuf, SaviorDomainS2CPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT,
            SaviorDomainS2CPayload::saviorEntityId,
            PacketCodecs.VAR_INT,
            SaviorDomainS2CPayload::durationTicks,
            SaviorDomainS2CPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
