package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Owner-only Focused Footsteps cast acknowledgement. / 仅发送给巫女本人的聚焦步伐施放确认。 */
public record FocusedFootstepsUseResultS2CPacket(boolean accepted, int cooldownTicks) implements CustomPayload {
    public static final Id<FocusedFootstepsUseResultS2CPacket> ID =
            new Id<>(SparkWitch.id("focused_footsteps_use_result"));
    public static final PacketCodec<RegistryByteBuf, FocusedFootstepsUseResultS2CPacket> CODEC =
            PacketCodec.of(FocusedFootstepsUseResultS2CPacket::write, FocusedFootstepsUseResultS2CPacket::read);

    public FocusedFootstepsUseResultS2CPacket {
        cooldownTicks = Math.max(0, cooldownTicks);
    }

    private void write(PacketByteBuf buf) {
        buf.writeBoolean(accepted);
        buf.writeVarInt(cooldownTicks);
    }

    private static FocusedFootstepsUseResultS2CPacket read(PacketByteBuf buf) {
        return new FocusedFootstepsUseResultS2CPacket(buf.readBoolean(), buf.readVarInt());
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
