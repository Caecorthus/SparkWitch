package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** SparkWitch-owned replay of Wathe's role-opening presentation. */
public record WraithRoleAnnouncementS2CPacket(
        String roleId,
        int killers,
        int targets
) implements CustomPayload {
    public static final Id<WraithRoleAnnouncementS2CPacket> ID =
            new Id<>(SparkWitch.id("wraith_role_announcement"));
    public static final PacketCodec<PacketByteBuf, WraithRoleAnnouncementS2CPacket> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING,
            WraithRoleAnnouncementS2CPacket::roleId,
            PacketCodecs.INTEGER,
            WraithRoleAnnouncementS2CPacket::killers,
            PacketCodecs.INTEGER,
            WraithRoleAnnouncementS2CPacket::targets,
            WraithRoleAnnouncementS2CPacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
