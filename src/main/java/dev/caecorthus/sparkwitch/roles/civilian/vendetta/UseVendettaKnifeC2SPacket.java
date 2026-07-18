package dev.caecorthus.sparkwitch.roles.civilian.vendetta;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Carries only an aimed entity id; the server revalidates the complete bound-pair contract. */
public record UseVendettaKnifeC2SPacket(int targetEntityId) implements CustomPayload {
    public static final Id<UseVendettaKnifeC2SPacket> ID = new Id<>(SparkWitch.id("vendetta_knife_stab"));
    public static final PacketCodec<RegistryByteBuf, UseVendettaKnifeC2SPacket> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER,
            UseVendettaKnifeC2SPacket::targetEntityId,
            UseVendettaKnifeC2SPacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
