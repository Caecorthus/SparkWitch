package dev.caecorthus.sparkwitch.roles.civilian.medium;

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
 * Opens the 招魂 target menu: the candidates (every round member who is online, alive or dead — the owner rule
 * 2026-11-10 is "everyone is selectable") and the re-checked price. Opening changes nothing; the confirm is the paid
 * act. The client grays out already-bound targets from its synced component; the server never charges for them.
 * 打开招魂目标菜单：候选（所有在线的本局成员，无论生死——用户规则 2026-11-10「所有人都能选」）与复核过的价格。打开不改变
 * 任何东西；确认才是付费动作。客户端根据同步组件把已绑定目标置灰；服务端绝不为重复绑定收费。
 */
public record OpenMediumHauntSelectS2CPacket(List<UUID> targets, List<String> names, int price)
        implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("open_medium_haunt_select");
    public static final Id<OpenMediumHauntSelectS2CPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, OpenMediumHauntSelectS2CPacket> CODEC =
            PacketCodec.tuple(
                    Uuids.PACKET_CODEC.collect(PacketCodecs.toList()), OpenMediumHauntSelectS2CPacket::targets,
                    PacketCodecs.STRING.collect(PacketCodecs.toList()), OpenMediumHauntSelectS2CPacket::names,
                    PacketCodecs.VAR_INT, OpenMediumHauntSelectS2CPacket::price,
                    OpenMediumHauntSelectS2CPacket::new
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
