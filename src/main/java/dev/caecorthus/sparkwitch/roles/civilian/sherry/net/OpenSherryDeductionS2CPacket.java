package dev.caecorthus.sparkwitch.roles.civilian.sherry.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.List;
import java.util.UUID;

/**
 * Opens the 推演 candidate panel: the living players inside reach and the price. Opening changes nothing; the confirm
 * is the paid act. Sent to the detective only.
 * 打开推演候选面板：射程内的存活玩家与价格。打开不改变任何东西；确认才是付费动作。只发给侦探本人。
 */
public record OpenSherryDeductionS2CPacket(List<UUID> targets, List<String> names, int price)
        implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("open_sherry_deduction");
    public static final Id<OpenSherryDeductionS2CPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, OpenSherryDeductionS2CPacket> CODEC =
            PacketCodec.tuple(
                    Uuids.PACKET_CODEC.collect(PacketCodecs.toList()), OpenSherryDeductionS2CPacket::targets,
                    PacketCodecs.STRING.collect(PacketCodecs.toList()), OpenSherryDeductionS2CPacket::names,
                    PacketCodecs.VAR_INT, OpenSherryDeductionS2CPacket::price,
                    OpenSherryDeductionS2CPacket::new
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
