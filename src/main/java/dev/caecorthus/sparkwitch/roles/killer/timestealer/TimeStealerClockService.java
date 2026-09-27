package dev.caecorthus.sparkwitch.roles.killer.timestealer;

import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceHits;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;

/**
 * Server-side Clock use flow: gates, target, theft, authoritative cooldown, feedback and replay. Returns CONSUME on a
 * committed theft and FAIL otherwise, never SUCCESS (a success would broadcast an arm swing).
 * 服务端时钟使用流程：判定、目标、窃取、权威冷却、反馈与回放。成功窃取返回 CONSUME，其余返回 FAIL，
 * 绝不返回 SUCCESS（SUCCESS 会广播挥手动作）。
 *
 * <p>Only two owner-only outcomes exist, "stolen" and one generic "no target", so a free miss cannot be used to probe
 * hidden states (invisibility, Last Escape, faction vetoes, an existing theft). A refusal costs nothing; the cooldown is
 * written only after {@link TimeTheftRuntime#steal} committed, or when a nearer Seeker device absorbed the Clock
 * (owner decision Q9 default, Taser parity). The authoritative deadline is {@code ClockReadyAt}; the item cooldown is
 * display only, written exactly through SparkTraits with a vanilla fallback.
 * 只有两种仅所有者可见的结果：“已窃取”与一条通用的“无目标”，因此免费的未命中无法用来探测隐藏状态（隐身、最后逃脱、
 * 阵营否决、已被窃）。被拒绝不付出任何代价；只有在 {@link TimeTheftRuntime#steal} 提交之后，或更近的搜寻者设备吸收了
 * 时钟时（所有者决定 Q9 默认，与电击枪一致）才写入冷却。权威截止为 {@code ClockReadyAt}；物品冷却只作显示，
 * 经 SparkTraits 精确写入，缺失时回退到原版。
 */
public final class TimeStealerClockService {
    static final String STOLEN_KEY = "message.sparkwitch.time_stealer.stolen";
    static final String NO_TARGET_KEY = "message.sparkwitch.time_stealer.no_target";

    private TimeStealerClockService() {
    }

    public static TypedActionResult<ItemStack> use(ServerPlayerEntity user, ItemStack stack, Hand hand) {
        if (user == null || user.getWorld().isClient()) {
            // The item already returns consume on the client; this service is server-only.
            // 物品在客户端已直接返回 consume；本服务仅在服务端运行。
            return TypedActionResult.consume(stack);
        }
        Item clock = stack.getItem();
        ServerWorld world = user.getServerWorld();
        TimeStealerPlayerComponent state = TimeStealerPlayerComponent.KEY.get(user);
        if (!TimeStealerTargeting.canUse(user, stack)) {
            restoreDisplayedCooldown(user, stack, state);
            return refuse(user, stack);
        }

        // Players the Time Stealer may not affect are filtered before the nearest pick, so they are transparent.
        // 窃时者无法影响的玩家在选取最近者之前就被过滤，因此对射线透明。
        ServerPlayerEntity aimed = ClockGeometry.findTarget(user, TimeStealerRules.CLOCK_RANGE, world.getPlayers(),
                candidate -> TimeStealerTargeting.canAffect(user, candidate));
        if (aimed == null) {
            return refuse(user, stack);
        }
        // Seeker seam (Q9): a nearer Seeker device absorbs the Clock and breaks; nothing is stolen, cooldown applies.
        // 搜寻者接缝（Q9）：更近的搜寻者设备吸收时钟并被打坏；不窃取任何人，但照常进入冷却。
        ServerPlayerEntity target = SeekerDeviceHits.onClockFired(user, aimed, TimeStealerRules.CLOCK_RANGE);
        if (target == null) {
            startCooldown(user, clock, state);
            user.sendMessage(Text.translatable(NO_TARGET_KEY), true);
            return TypedActionResult.consume(stack);
        }
        if (!TimeStealerTargeting.canAffect(user, target) || !TimeTheftRuntime.steal(target, user)) {
            return refuse(user, stack);
        }

        // Committed: only now is the cooldown written. / 已提交：此时才写入冷却。
        startCooldown(user, clock, state);
        user.sendMessage(Text.translatable(STOLEN_KEY), true);
        GameRecordManager.recordItemUse(user, TimeStealerRules.CLOCK_ID, target, new NbtCompound());
        return TypedActionResult.consume(stack);
    }

    /** No cooldown, one generic owner-only line. / 不写冷却，只给一条仅所有者可见的通用提示。 */
    private static TypedActionResult<ItemStack> refuse(ServerPlayerEntity user, ItemStack stack) {
        user.sendMessage(Text.translatable(NO_TARGET_KEY), true);
        return TypedActionResult.fail(stack);
    }

    private static void startCooldown(ServerPlayerEntity user, Item clock, TimeStealerPlayerComponent state) {
        state.setClockReadyAt(user.getWorld().getTime() + TimeStealerRules.CLOCK_COOLDOWN_TICKS);
        showCooldown(user, clock, TimeStealerRules.CLOCK_COOLDOWN_TICKS);
    }

    /**
     * Vanilla refuses a cooling item before {@code Item#use}, so reaching here inside {@code ClockReadyAt} means the
     * display cooldown was shortened elsewhere (NoellesRoles Stimulation, Last Escape halving, commands). When the
     * refusal is due to the deadline alone, rewrite the display to the authoritative remaining ticks.
     * 原版会在 {@code Item#use} 之前拒绝冷却中的物品，因此在 {@code ClockReadyAt} 之前到达这里说明显示冷却已被其他机制
     * 缩短（NoellesRoles 兴奋剂、最后逃脱减半、命令）。当拒绝仅由截止 tick 造成时，把显示冷却重写为权威剩余值。
     */
    private static void restoreDisplayedCooldown(ServerPlayerEntity user, ItemStack stack,
                                                 TimeStealerPlayerComponent state) {
        Item clock = stack.getItem();
        long remaining = state.clockReadyAt() - user.getWorld().getTime();
        if (remaining <= 0L || user.getItemCooldownManager().isCoolingDown(clock)
                || !TimeStealerTargeting.canUseWhenReady(user, stack)) {
            return;
        }
        showCooldown(user, clock, (int) Math.min(Integer.MAX_VALUE, remaining));
    }

    /**
     * SparkTraits' exact write performs the vanilla {@code set} itself past cooldown modifiers, so it goes first and
     * the plain vanilla write is only the fallback when Traits is absent or older (never both: two cooldown packets).
     * SparkTraits 的精确写入会越过冷却倍率自行完成原版写入，因此优先调用；仅在 Traits 缺失或过旧时回退到原版写入
     * （二者不会同时执行，以免发送两次冷却数据包）。
     */
    private static void showCooldown(ServerPlayerEntity user, Item clock, int ticks) {
        if (!SparkTraitsKillerBridge.setExactItemCooldownRemaining(user, clock, ticks)) {
            user.getItemCooldownManager().set(clock, ticks);
        }
    }
}
