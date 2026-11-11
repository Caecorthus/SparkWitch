package dev.caecorthus.sparkwitch.roles.civilian.planner.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.UUID;

/**
 * C2S {@code sparkwitch:planner_recommend}: the marked player's UUID; the server validates role, cost and cooldown.
 * C2S 契约：被标记玩家的 UUID；角色、花费与冷却全部由服务端校验。
 */
public record PlannerRecommendC2SPayload(UUID target) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("planner_recommend");
    public static final Id<PlannerRecommendC2SPayload> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, PlannerRecommendC2SPayload> CODEC =
            PacketCodec.of((payload, buf) -> Uuids.PACKET_CODEC.encode(buf, payload.target()),
                    buf -> new PlannerRecommendC2SPayload(Uuids.PACKET_CODEC.decode(buf)));

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
