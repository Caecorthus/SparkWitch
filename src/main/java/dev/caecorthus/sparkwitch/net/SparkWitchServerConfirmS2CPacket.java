package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SparkWitchServerConfirmS2CPacket(String serverVersion) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("server_confirm");
    public static final Id<SparkWitchServerConfirmS2CPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, SparkWitchServerConfirmS2CPacket> CODEC =
            PacketCodec.of(SparkWitchServerConfirmS2CPacket::write, SparkWitchServerConfirmS2CPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeString(serverVersion);
    }

    public static SparkWitchServerConfirmS2CPacket read(PacketByteBuf buf) {
        return new SparkWitchServerConfirmS2CPacket(buf.readString());
    }
}
