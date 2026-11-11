package dev.caecorthus.sparkwitch.roles.civilian.planner.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C {@code sparkwitch:planner_task_assigned}: the task ordinal that was just recommended, so the client can wrap it
 * immediately instead of waiting for the component sync.
 * S2C 契约：刚被推荐的任务类型序号，让客户端立刻包装，而不必等组件同步。
 */
public record PlannerTaskAssignedS2CPayload(int ordinal) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("planner_task_assigned");
    public static final Id<PlannerTaskAssignedS2CPayload> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, PlannerTaskAssignedS2CPayload> CODEC =
            PacketCodec.of((payload, buf) -> buf.writeVarInt(payload.ordinal()),
                    buf -> new PlannerTaskAssignedS2CPayload(buf.readVarInt()));

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
