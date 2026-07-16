package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Empty server authorization to open the owner-private Perception ledger. */
public record OpenBlackRavenLedgerS2CPacket() implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("open_black_raven_ledger");
    public static final Id<OpenBlackRavenLedgerS2CPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, OpenBlackRavenLedgerS2CPacket> CODEC =
            PacketCodec.of(OpenBlackRavenLedgerS2CPacket::write, OpenBlackRavenLedgerS2CPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private void write(PacketByteBuf buf) {
    }

    private static OpenBlackRavenLedgerS2CPacket read(PacketByteBuf buf) {
        return new OpenBlackRavenLedgerS2CPacket();
    }
}
