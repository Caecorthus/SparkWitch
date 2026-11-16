package dev.caecorthus.sparkwitch.roles.civilian.medium;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * The Medium's Enter press in the question box. Every gate is re-checked server-side before anything is charged;
 * an empty or over-length text is dropped here.
 * 通灵师在提问框里的回车提交。服务端在扣费前重新复核所有门槛；空文本或超长文本在此丢弃。
 */
public record SubmitMediumSeanceC2SPacket(String question) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("submit_medium_seance");
    public static final Id<SubmitMediumSeanceC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, SubmitMediumSeanceC2SPacket> CODEC = PacketCodec.tuple(
            PacketCodecs.string(MediumRules.QUESTION_MAX_CHARS * 4),
            SubmitMediumSeanceC2SPacket::question,
            SubmitMediumSeanceC2SPacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
