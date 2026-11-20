package dev.caecorthus.sparkwitch.net.props;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Client -> server: apply these blink settings to that column. / 客户端 -> 服务端：把这份闪烁设置应用到那根灯柱。 */
public record NeonSettingsC2SPayload(BlockPos pos, int onTicks, int offTicks, boolean enabled, boolean random) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("neon_settings_apply");
    public static final Id<NeonSettingsC2SPayload> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, NeonSettingsC2SPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                BlockPos.PACKET_CODEC.encode(buf, value.pos());
                buf.writeVarInt(value.onTicks());
                buf.writeVarInt(value.offTicks());
                buf.writeBoolean(value.enabled());
                buf.writeBoolean(value.random());
            },
            buf -> new NeonSettingsC2SPayload(BlockPos.PACKET_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean())
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
