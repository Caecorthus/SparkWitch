package dev.caecorthus.sparkwitch.roles.civilian.sherry.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.UUID;

/**
 * The confirmed 推演 pick. Re-validated server-side (gates, corpse, cooldown, coins, a living target in range) before
 * anything is charged; the picked player is never told.
 * 推演的确认选择。服务端重新校验（门槛、尸体、冷却、金币、射程内的存活目标）后才扣费；被选玩家不会收到任何提示。
 */
public record ConfirmSherryDeductionC2SPacket(UUID target) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("confirm_sherry_deduction");
    public static final Id<ConfirmSherryDeductionC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, ConfirmSherryDeductionC2SPacket> CODEC = PacketCodec.tuple(
            Uuids.PACKET_CODEC,
            ConfirmSherryDeductionC2SPacket::target,
            ConfirmSherryDeductionC2SPacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
