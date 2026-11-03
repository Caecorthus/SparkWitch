package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.UUID;

/** Confirms one hypnosis target picked in the menu. 确认菜单里选中的催眠目标。 */
public record ConfirmNatsumeTargetC2SPacket(UUID target) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("confirm_natsume_target");
    public static final Id<ConfirmNatsumeTargetC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, ConfirmNatsumeTargetC2SPacket> CODEC =
            PacketCodec.tuple(Uuids.PACKET_CODEC, ConfirmNatsumeTargetC2SPacket::target,
                    ConfirmNatsumeTargetC2SPacket::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
