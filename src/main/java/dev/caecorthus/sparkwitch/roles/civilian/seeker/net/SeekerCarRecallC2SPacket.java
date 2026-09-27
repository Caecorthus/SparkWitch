package dev.caecorthus.sparkwitch.roles.civilian.seeker.net;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * Stable packet contract {@code sparkwitch:seeker_car_recall} (C2S, empty): the console's remote recall (replaces the
 * plan's abandon). Server: shared gate + console device + DEPLOYED → 180 s, no mark.
 * 稳定数据包契约 {@code sparkwitch:seeker_car_recall}（C2S，无字段）：控制台远程回收（取代原计划的放弃）。
 * 服务端：公共门槛 + 控制台设备 + 已部署 → 180 秒，不标记。
 */
public record SeekerCarRecallC2SPacket() implements CustomPayload {
    public static final Id<SeekerCarRecallC2SPacket> ID = new Id<>(SeekerRules.CAR_RECALL_PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, SeekerCarRecallC2SPacket> CODEC =
            PacketCodec.of(SeekerCarRecallC2SPacket::write, SeekerCarRecallC2SPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
    }

    public static SeekerCarRecallC2SPacket read(PacketByteBuf buf) {
        return new SeekerCarRecallC2SPacket();
    }
}
