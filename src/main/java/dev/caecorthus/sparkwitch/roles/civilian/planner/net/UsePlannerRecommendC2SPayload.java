package dev.caecorthus.sparkwitch.roles.civilian.planner.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.UUID;

/**
 * Stable C2S contract {@code sparkwitch:use_planner_recommend}: the planner's ability key, carrying the UUID of the
 * player under the client's crosshair (the planner's own UUID when nothing is aimed at). The UUID is only a hint: the
 * server re-checks role, round, control state, cooldown, coins, and the target's distance and line of sight. Its row
 * in SparkFactionAPI's payload policy table classifies it as a role skill.
 * 稳定 C2S 契约 {@code sparkwitch:use_planner_recommend}：规划大师的技能键，携带客户端准星下玩家的 UUID（无目标时为自己的
 * UUID）。该 UUID 只是提示：服务端会重新校验身份、回合、受控状态、冷却、金币，以及目标的距离与视线。它在 SparkFactionAPI
 * 数据包策略表中的行把它归为职业技能。
 */
public record UsePlannerRecommendC2SPayload(UUID target) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("use_planner_recommend");
    public static final Id<UsePlannerRecommendC2SPayload> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, UsePlannerRecommendC2SPayload> CODEC = PacketCodec.tuple(
            Uuids.PACKET_CODEC, UsePlannerRecommendC2SPayload::target,
            UsePlannerRecommendC2SPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
