package dev.caecorthus.sparkwitch.roles.killer.timestealer;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;

/**
 * Server-side Clock use flow: gates, target, theft, authoritative cooldown, feedback and replay. Returns CONSUME on a
 * committed theft and FAIL otherwise, never SUCCESS (a success would broadcast an arm swing).
 * 服务端时钟使用流程：判定、目标、窃取、权威冷却、反馈与回放。成功窃取返回 CONSUME，其余返回 FAIL，
 * 绝不返回 SUCCESS（SUCCESS 会广播挥手动作）。
 */
public final class TimeStealerClockService {
    private TimeStealerClockService() {
    }

    public static TypedActionResult<ItemStack> use(ServerPlayerEntity user, ItemStack stack, Hand hand) {
        // TODO(WP-03a): canUse -> ClockGeometry.findTarget -> canAffect -> TimeTheftRuntime.steal -> cooldown/replay.
        return TypedActionResult.fail(stack);
    }
}
