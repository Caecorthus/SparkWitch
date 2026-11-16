package dev.caecorthus.sparkwitch.roles.civilian.medium;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.UUID;

/**
 * The confirmed 招魂 pick. Re-validated server-side (round member, online, not yet bound, gates and price) before
 * anything is charged; the picked player is never told.
 * 招魂的确认选择。服务端重新校验（本局成员、在线、尚未绑定、门槛与价格）后才扣费；被选玩家不会收到任何提示。
 */
public record SubmitMediumHauntTargetC2SPacket(UUID target) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("submit_medium_haunt_target");
    public static final Id<SubmitMediumHauntTargetC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, SubmitMediumHauntTargetC2SPacket> CODEC = PacketCodec.tuple(
            Uuids.PACKET_CODEC,
            SubmitMediumHauntTargetC2SPacket::target,
            SubmitMediumHauntTargetC2SPacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
