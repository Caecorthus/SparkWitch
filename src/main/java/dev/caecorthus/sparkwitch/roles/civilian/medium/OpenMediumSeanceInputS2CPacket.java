package dev.caecorthus.sparkwitch.roles.civilian.medium;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Opens the 通灵 question box on the Medium's client, carrying the price the server just re-checked. Sending it
 * changes nothing yet: nothing is charged and no cooldown starts until the submit comes back.
 * 在通灵师客户端打开通灵提问框，携带服务端刚复核过的价格。发送它不改变任何东西：直到提交返回之前，不扣钱也不进冷却。
 */
public record OpenMediumSeanceInputS2CPacket(int price) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("open_medium_seance_input");
    public static final Id<OpenMediumSeanceInputS2CPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, OpenMediumSeanceInputS2CPacket> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT,
            OpenMediumSeanceInputS2CPacket::price,
            OpenMediumSeanceInputS2CPacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
