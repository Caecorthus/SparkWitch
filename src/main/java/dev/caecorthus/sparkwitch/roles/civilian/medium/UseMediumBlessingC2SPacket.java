package dev.caecorthus.sparkwitch.roles.civilian.medium;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Empty request for 幽灵的加护 (the SparkWitch skill key). Unlike 通灵 there is no input box: the server charges on
 * receipt once every gate passes.
 * 幽灵的加护（SparkWitch 技能键）的空请求。与通灵不同，它没有输入框：所有门槛通过后服务端在收到时即扣费。
 */
public record UseMediumBlessingC2SPacket() implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("use_medium_blessing");
    public static final Id<UseMediumBlessingC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, UseMediumBlessingC2SPacket> CODEC =
            PacketCodec.unit(new UseMediumBlessingC2SPacket());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
