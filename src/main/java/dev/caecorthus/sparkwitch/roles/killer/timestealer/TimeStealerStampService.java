package dev.caecorthus.sparkwitch.roles.killer.timestealer;

import dev.doctor4t.wathe.api.Role;
import java.util.function.Predicate;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Server-authoritative Time Stamp economy: who may hold stamps, grants, re-delivery, reserve-then-undo purchases, the
 * owner's per-tick upkeep, and clearing on role change or reset.
 * 服务端权威的时光邮票经济：谁能持有邮票、发放、重新发放、先预留后退回的购买、持有者每 tick 维护，
 * 以及换职业或重置时的清空。
 */
public final class TimeStealerStampService {
    private TimeStealerStampService() {
    }

    /** Only a living, playing, exact Time Stealer may hold stamps. / 只有存活、参与中的精确窃时者能持有邮票。 */
    public static boolean mayHold(ServerPlayerEntity player) {
        // TODO(WP-05): exact role && isPlayerPlayingAndAlive && !spectator.
        return false;
    }

    /** Self-gated grant; a null, dead, re-roled or stale-match stealer gets nothing. / 自带门槛的发放。 */
    public static void grant(@Nullable ServerPlayerEntity stealer, int amount) {
        // TODO(WP-05): mayHold + ACTIVE + match gate, deliver, UndeliveredStamps, actionbar.
    }

    /** Silent re-delivery used by the drop guard (move semantics). / 丢弃拦截使用的静默重新发放（移动语义）。 */
    public static void redeliver(ServerPlayerEntity player, int amount) {
        // TODO(WP-05): deliver without feedback.
    }

    /**
     * Stamp purchase: reserve, run the native effect, undo on failure; true only on success. {@code entryId} is the
     * Wathe {@code ShopEntry.id()} String (for example {@link TimeStealerShopRules#GRENADE_ENTRY_ID}), recorded under
     * {@link TimeStealerRules#REPLAY_ENTRY_KEY} with the cost under {@link TimeStealerRules#REPLAY_COST_KEY}.
     * 邮票购买：预留、执行效果、失败退回，仅成功时返回 true。{@code entryId} 是 Wathe {@code ShopEntry.id()} 字符串，
     * 成功后以 {@link TimeStealerRules#REPLAY_ENTRY_KEY} / {@link TimeStealerRules#REPLAY_COST_KEY} 写入回放。
     */
    public static boolean purchase(ServerPlayerEntity buyer, String entryId, int cost, Predicate<PlayerEntity> effect) {
        // TODO(WP-05): reserve -> apply -> try effect finally undo; on success record STAMP_PURCHASE_RECORD_ID with
        // the REPLAY_ENTRY_KEY / REPLAY_COST_KEY payload.
        return false;
    }

    /** Owner upkeep every tick: consolidate, retry undelivered every 20 ticks. / 持有者每 tick 维护。 */
    public static void tickOwner(ServerPlayerEntity player) {
        // TODO(WP-05): consolidate + retry UndeliveredStamps.
    }

    public static void onRoleAssigned(ServerPlayerEntity player, @Nullable Role role) {
        // TODO(WP-05): keep stamps on a same-match Time Stealer re-assignment, otherwise strip and zero.
    }

    public static void reset(ServerPlayerEntity player) {
        // TODO(WP-05): stripAll + zero UndeliveredStamps.
    }
}
