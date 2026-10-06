package dev.caecorthus.sparkwitch.roles.killer.timestealer;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.mixin.accessor.ItemCooldownEntryAccessor;
import dev.caecorthus.sparkwitch.mixin.accessor.ItemCooldownManagerAccessor;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.TypedActionResult;

/**
 * Server-side Gift Watch use flow (owner decision 2026-10-05), the Clock's flow without its Seeker seam: gates, the
 * same 7-block ray, the gift, its own authoritative {@code GiftReadyAt} cooldown, feedback and replay. CONSUME on a
 * committed gift, FAIL otherwise, never SUCCESS (a success would broadcast an arm swing). A refusal costs nothing and
 * shows one generic owner-only line; the cooldown is written only after {@link TimeGiftRuntime#give} committed.
 * 服务端赠时怀表使用流程（所有者决定 2026-10-05），即去掉搜寻者接缝的时钟流程：判定、同样的 7 格射线、赠时、独立的权威
 * {@code GiftReadyAt} 冷却、反馈与回放。成功赠时返回 CONSUME，其余返回 FAIL，绝不返回 SUCCESS（SUCCESS 会广播挥手动作）。
 * 被拒绝不付出任何代价，只显示一条仅所有者可见的通用提示；只有在 {@link TimeGiftRuntime#give} 提交之后才写入冷却。
 */
public final class TimeGiftWatchService {
    static final String GIFTED_KEY = "message.sparkwitch.time_stealer.gifted";
    static final String NO_TARGET_KEY = "message.sparkwitch.time_stealer.gift_no_target";

    private TimeGiftWatchService() {
    }

    public static TypedActionResult<ItemStack> use(ServerPlayerEntity user, ItemStack stack) {
        if (user == null || user.getWorld().isClient()) {
            return TypedActionResult.consume(stack);
        }
        TimeStealerPlayerComponent state = TimeStealerPlayerComponent.KEY.get(user);
        if (!TimeStealerTargeting.canUseGift(user, stack)) {
            restoreDisplayedCooldown(user, stack, state);
            return refuse(user, stack);
        }
        // Players the giver may not affect are filtered before the nearest pick, so they are transparent.
        // 赠予者无法影响的玩家在选取最近者之前就被过滤，因此对射线透明。
        ServerPlayerEntity target = ClockGeometry.findTarget(user, TimeStealerRules.CLOCK_RANGE,
                user.getServerWorld().getPlayers(), candidate -> TimeStealerTargeting.canGift(user, candidate));
        if (target == null || !TimeGiftRuntime.give(target, user)) {
            return refuse(user, stack);
        }

        // Committed: only now is the cooldown written. / 已提交：此时才写入冷却。
        state.setGiftReadyAt(user.getWorld().getTime() + TimeStealerRules.GIFT_COOLDOWN_TICKS);
        showCooldown(user, stack.getItem(), TimeStealerRules.GIFT_COOLDOWN_TICKS);
        user.sendMessage(Text.translatable(GIFTED_KEY), true);
        GameRecordManager.recordItemUse(user, TimeStealerRules.GIFT_WATCH_ID, target, new NbtCompound());
        return TypedActionResult.consume(stack);
    }

    /** No cooldown, one generic owner-only line. / 不写冷却，只给一条仅所有者可见的通用提示。 */
    private static TypedActionResult<ItemStack> refuse(ServerPlayerEntity user, ItemStack stack) {
        user.sendMessage(Text.translatable(NO_TARGET_KEY), true);
        return TypedActionResult.fail(stack);
    }

    /**
     * Same repair as the Clock's: reaching here inside {@code GiftReadyAt} means the display was shortened elsewhere,
     * so a refusal due to the deadline alone rewrites the display to the authoritative remaining ticks.
     * 与时钟相同的修复：在 {@code GiftReadyAt} 之前到达这里说明显示冷却已被其他机制缩短，因此仅由截止 tick 造成的拒绝
     * 会把显示冷却重写为权威剩余值。
     */
    private static void restoreDisplayedCooldown(ServerPlayerEntity user, ItemStack stack,
                                                 TimeStealerPlayerComponent state) {
        Item watch = stack.getItem();
        long remaining = state.giftReadyAt() - user.getWorld().getTime();
        if (remaining <= 0L || user.getItemCooldownManager().isCoolingDown(watch)
                || !TimeStealerTargeting.canUseGiftWhenReady(user, stack)) {
            return;
        }
        showCooldown(user, watch, (int) Math.min(Integer.MAX_VALUE, remaining));
    }

    /**
     * Holder's per-tick upkeep, as for the Clock: the slot never shows less than {@code GiftReadyAt}, exact writes only.
     * 持有者每 tick 维护，与时钟相同：栏位显示绝不少于 {@code GiftReadyAt}，仅用精确写入。
     */
    static void keepDisplayedCooldown(ServerPlayerEntity user) {
        Item watch = SparkWitchItems.timeStealerGiftWatch();
        long remaining = TimeStealerPlayerComponent.KEY.get(user).giftReadyAt() - user.getWorld().getTime();
        if (TimeStealerRules.clockDisplayLags(remaining, displayedTicks(user, watch))) {
            SparkTraitsKillerBridge.setExactItemCooldownRemaining(user, watch, (int) Math.min(Integer.MAX_VALUE, remaining));
        }
    }

    /** SparkTraits' exact write first; vanilla only as the fallback (never both). / 优先 SparkTraits 精确写入；原版仅作回退（二者不会同时执行）。 */
    static void showCooldown(ServerPlayerEntity user, Item watch, int ticks) {
        if (!SparkTraitsKillerBridge.setExactItemCooldownRemaining(user, watch, ticks)) {
            user.getItemCooldownManager().set(watch, ticks);
        }
    }

    private static int displayedTicks(ServerPlayerEntity user, Item watch) {
        ItemCooldownManagerAccessor manager = (ItemCooldownManagerAccessor) user.getItemCooldownManager();
        return manager.sparkwitch$getEntries().get(watch) instanceof ItemCooldownEntryAccessor entry
                ? Math.max(0, entry.sparkwitch$getEndTick() - manager.sparkwitch$getTick()) : 0;
    }
}
