package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Empty server authorization to open the owner-private Necrology. It carries no names: the book reads only the
 * owner-synced {@code sparkwitch:prophet_player} component, so the packet can never leak deaths to anyone else.
 * 打开仅所有者可见的亡者名录的空授权包。它不携带任何名字：书本只读取仅同步给所有者的 {@code sparkwitch:prophet_player}
 * 组件，因此该包永远不会向他人泄露死亡信息。
 */
public record OpenProphetNecrologyS2CPacket() implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("open_prophet_necrology");
    public static final Id<OpenProphetNecrologyS2CPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, OpenProphetNecrologyS2CPacket> CODEC =
            PacketCodec.of(OpenProphetNecrologyS2CPacket::write, OpenProphetNecrologyS2CPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private void write(PacketByteBuf buf) {
    }

    private static OpenProphetNecrologyS2CPacket read(PacketByteBuf buf) {
        return new OpenProphetNecrologyS2CPacket();
    }
}
