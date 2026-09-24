package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Opening is free; only the server authorizes a selection session. / 打开免费，选择会话仅由服务端授权。 */
public record OpenJudgeSelectionC2SPacket() implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("open_judge_selection");
    public static final Id<OpenJudgeSelectionC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, OpenJudgeSelectionC2SPacket> CODEC =
            PacketCodec.unit(new OpenJudgeSelectionC2SPacket());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
