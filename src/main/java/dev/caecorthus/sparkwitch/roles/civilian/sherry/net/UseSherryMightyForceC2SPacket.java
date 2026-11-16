package dev.caecorthus.sparkwitch.roles.civilian.sherry.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Empty request for 橘雪莉's own 巨力魔法 (the Role Skill 2 key); the server owns every check.
 * 橘雪莉自己的巨力魔法（职业技能 2 键）的空请求；全部校验在服务端。
 */
public record UseSherryMightyForceC2SPacket() implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("use_sherry_mighty_force");
    public static final Id<UseSherryMightyForceC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, UseSherryMightyForceC2SPacket> CODEC =
            PacketCodec.unit(new UseSherryMightyForceC2SPacket());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
