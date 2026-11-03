package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Asks to audit the wallet leaderboard. 请求查账。 */
public record AccountantAuditC2SPacket() implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("accountant_audit");
    public static final Id<AccountantAuditC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, AccountantAuditC2SPacket> CODEC =
            PacketCodec.unit(new AccountantAuditC2SPacket());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
