package dev.caecorthus.sparkwitch.roles.civilian.seeker.net;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * Stable packet contract {@code sparkwitch:seeker_car_swallow} (C2S, Taotie only): {@code carEntityId:varint}.
 * 稳定数据包契约 {@code sparkwitch:seeker_car_swallow}（C2S，仅饕餮）：{@code carEntityId:varint}。
 */
public record SeekerCarSwallowC2SPacket(int carEntityId) implements CustomPayload {
    public static final Id<SeekerCarSwallowC2SPacket> ID = new Id<>(SeekerRules.CAR_SWALLOW_PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, SeekerCarSwallowC2SPacket> CODEC =
            PacketCodec.of(SeekerCarSwallowC2SPacket::write, SeekerCarSwallowC2SPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeVarInt(carEntityId);
    }

    public static SeekerCarSwallowC2SPacket read(PacketByteBuf buf) {
        return new SeekerCarSwallowC2SPacket(buf.readVarInt());
    }
}
