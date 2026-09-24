package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** A single intent packet; the server rechecks geometry, role restrictions and costs. / 单一意图包；服务端复核几何、职业限制及消耗。 */
public record EmmaFactorC2SPacket(@Nullable UUID targetId) implements CustomPayload {
    public static final Id<EmmaFactorC2SPacket> ID = new Id<>(SparkWitch.id("emma_factor"));
    public static final PacketCodec<RegistryByteBuf, EmmaFactorC2SPacket> CODEC =
            PacketCodec.of(EmmaFactorC2SPacket::write, EmmaFactorC2SPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private void write(PacketByteBuf buf) {
        buf.writeBoolean(targetId != null);
        if (targetId != null) {
            buf.writeUuid(targetId);
        }
    }

    private static EmmaFactorC2SPacket read(PacketByteBuf buf) {
        return new EmmaFactorC2SPacket(buf.readBoolean() ? buf.readUuid() : null);
    }
}
