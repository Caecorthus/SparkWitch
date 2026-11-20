package dev.caecorthus.sparkwitch.net.props;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Server -> client: open the neon blink screen for this column. / 服务端 -> 客户端：为这根灯柱打开闪烁设置界面。 */
public record NeonSettingsS2CPayload(BlockPos pos, int onTicks, int offTicks, boolean enabled, boolean random) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("neon_settings_open");
    public static final Id<NeonSettingsS2CPayload> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, NeonSettingsS2CPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                BlockPos.PACKET_CODEC.encode(buf, value.pos());
                buf.writeVarInt(value.onTicks());
                buf.writeVarInt(value.offTicks());
                buf.writeBoolean(value.enabled());
                buf.writeBoolean(value.random());
            },
            buf -> new NeonSettingsS2CPayload(BlockPos.PACKET_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean())
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
