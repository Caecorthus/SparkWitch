package dev.caecorthus.sparkwitch.roles.civilian.sherry.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Empty request for 推演 (the shared ability key): asks the server to open the candidate panel. Nothing is charged
 * here; the server owns every check.
 * 推演（共享技能键）的空请求：请服务端打开候选面板。此处不扣任何费用；全部校验在服务端。
 */
public record RequestSherryDeductionC2SPacket() implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("request_sherry_deduction");
    public static final Id<RequestSherryDeductionC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, RequestSherryDeductionC2SPacket> CODEC =
            PacketCodec.unit(new RequestSherryDeductionC2SPacket());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
