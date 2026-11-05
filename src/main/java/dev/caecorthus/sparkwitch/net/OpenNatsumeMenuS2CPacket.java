package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Opens the hypnosis target menu. 打开催眠目标菜单。 */
public record OpenNatsumeMenuS2CPacket(List<UUID> targets, List<String> names) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("open_natsume_menu");
    public static final Id<OpenNatsumeMenuS2CPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, OpenNatsumeMenuS2CPacket> CODEC =
            PacketCodec.tuple(
                    Uuids.PACKET_CODEC.collect(PacketCodecs.toList()), OpenNatsumeMenuS2CPacket::targets,
                    PacketCodecs.STRING.collect(PacketCodecs.toList()), OpenNatsumeMenuS2CPacket::names,
                    OpenNatsumeMenuS2CPacket::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
