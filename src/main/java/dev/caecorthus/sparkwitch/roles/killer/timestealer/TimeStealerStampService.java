package dev.caecorthus.sparkwitch.roles.killer.timestealer;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.roles.killer.timestealer.TimeStampLedger.Plan;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * Server-authoritative Time Stamp economy: who may hold stamps, grants, re-delivery, reserve-then-undo purchases, the
 * owner's per-tick upkeep, and clearing on role change or reset. Stamps exist only as items in the holder's inventory;
 * the component keeps just the server-only {@code UndeliveredStamps} retry counter, which purchases never spend.
 * 服务端权威的时光邮票经济：谁能持有邮票、发放、重新发放、先预留后退回的购买、持有者每 tick 维护，
 * 以及换职业或重置时的清空。邮票只以物品形式存在于持有者背包中；组件只保存仅服务端的 {@code UndeliveredStamps}
 * 重试计数，购买从不花费它。
 */
public final class TimeStealerStampService {
    private static final int RETRY_INTERVAL_TICKS = 20;

    private TimeStealerStampService() {
    }

    /**
     * Only a living, playing, exact Time Stealer may hold stamps (plan D10). Spectator mode is deliberately not a
     * condition: a Time Stealer swallowed by the NoellesRoles Taotie (or in any other alive-spectator state) is still
     * playing and alive, so the 20-tick sweep never destroys their stamps and a Clock kill that settles meanwhile still
     * pays. Clock use refuses spectators on its own.
     * 只有存活、参与中的精确窃时者能持有邮票（计划 D10）。刻意不以旁观模式为条件：被 NoellesRoles 饕餮吞下（或处于其他
     * 存活旁观状态）的窃时者仍在参与且存活，因此 20 tick 清理不会销毁其邮票，期间结算的时钟击杀照常发放。时钟使用自身会拒绝旁观者。
     */
    public static boolean mayHold(ServerPlayerEntity player) {
        if (player == null) {
            return false;
        }
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        return TimeStealerRules.isTimeStealer(role) && GameFunctions.isPlayerPlayingAndAlive(player);
    }

    /**
     * Self-gated grant. Requires a non-null holder ({@link #mayHold}), an ACTIVE round and the holder's component bound
     * to the current match; otherwise the stamps are discarded, so a Clock kill that lands after the stealer died,
     * changed role or disconnected (owner decision Q7) pays nothing. Delivery is hotbar-first; overflow goes to the
     * retry counter. The owner sees {@code stamp_gained} when a stamp reached the hotbar, else {@code stamp_stored}.
     * 自带门槛的发放。需要非空且满足 {@link #mayHold} 的持有者、ACTIVE 对局，且其组件绑定到当前对局；否则丢弃，
     * 因此窃时者死亡、换职业或掉线后才落地的时钟击杀（所有者决定 Q7）不发邮票。优先发到快捷栏，溢出计入重试计数。
     * 邮票进入快捷栏时提示 {@code stamp_gained}，否则提示 {@code stamp_stored}。
     */
    public static void grant(@Nullable ServerPlayerEntity stealer, int amount) {
        if (stealer == null || amount <= 0 || !mayHold(stealer) || !isActiveCurrentMatch(stealer)) {
            return;
        }
        Plan plan = deliver(stealer, amount);
        String key = TimeStampLedger.landsInHotbar(plan)
                ? "message.sparkwitch.time_stealer.stamp_gained"
                : "message.sparkwitch.time_stealer.stamp_stored";
        stealer.sendMessage(Text.translatable(key).withColor(TimeStealerRules.COLOR), true);
    }

    /**
     * {@code TaskComplete} listener: a Conscience Time Stealer earns {@link TimeStealerRules#TASK_STAMP_REWARD} per
     * task ({@link TimeStealerRules#earnsTaskStamp}); {@link #grant} keeps its own holder and match gates.
     * {@code TaskComplete} 监听：善良窃时者每完成一个任务获得 {@link TimeStealerRules#TASK_STAMP_REWARD} 枚邮票
     * （{@link TimeStealerRules#earnsTaskStamp}）；{@link #grant} 保留自身的持有者与对局门槛。
     */
    public static void onTaskComplete(ServerPlayerEntity player) {
        if (!mayHold(player)) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        if (TimeStealerRules.earnsTaskStamp(SparkFactionApi.resolveEffectiveFaction(player, game))) {
            grant(player, TimeStealerRules.TASK_STAMP_REWARD);
        }
    }

    /**
     * Silent re-delivery used by the drop guard after it emptied the caught stack (move semantics). A player who may
     * not hold stamps gets nothing: death and role loss clear stamps anyway.
     * 丢弃拦截在清空被拦下的物品堆后使用的静默重新发放（移动语义）。不能持有邮票的玩家什么也得不到：死亡与失去职业本就清空邮票。
     */
    public static void redeliver(ServerPlayerEntity player, int amount) {
        if (player == null || amount <= 0 || !mayHold(player)) {
            return;
        }
        deliver(player, amount);
    }

    /**
     * Stamp purchase, run inside the 0-coin entry's {@code onBuy} on the server thread (atomic with every other
     * purchase): gates, reserve (remove) the cost, run the native effect, and restore the exact slots in
     * {@code finally} unless the effect succeeded. Reserving first can free the hotbar slot a grenade or Psycho bat
     * needs. On success the purchase is recorded under {@link TimeStealerRules#STAMP_PURCHASE_RECORD_ID} with the
     * Wathe {@code ShopEntry.id()} String under {@link TimeStealerRules#REPLAY_ENTRY_KEY} and the int cost under
     * {@link TimeStealerRules#REPLAY_COST_KEY}.
     * 邮票购买，在 0 金币商品的 {@code onBuy} 中于服务器线程执行（与其他购买之间原子）：检查门槛、预留（扣除）费用、
     * 执行原生效果，除非效果成功，否则在 {@code finally} 中按原格位退回。先预留可能正好空出手雷或疯魔蝙蝠需要的快捷栏格。
     * 成功后以 {@link TimeStealerRules#STAMP_PURCHASE_RECORD_ID} 记录，载荷为 {@link TimeStealerRules#REPLAY_ENTRY_KEY}
     * 下的 Wathe {@code ShopEntry.id()} 字符串与 {@link TimeStealerRules#REPLAY_COST_KEY} 下的 int 花费。
     */
    public static boolean purchase(ServerPlayerEntity buyer, String entryId, int cost, Predicate<PlayerEntity> effect) {
        if (buyer == null || entryId == null || effect == null || cost <= 0
                || !mayHold(buyer) || !isActiveCurrentMatch(buyer)) {
            return false;
        }
        Optional<Plan> reserved = TimeStampLedger.reserve(TimeStampInventory.read(buyer), cost);
        if (reserved.isEmpty()) {
            return false;
        }
        TimeStampInventory.apply(buyer, reserved.get());
        boolean ok = false;
        try {
            ok = effect.test(buyer);
        } finally {
            if (!ok) {
                Plan undo = TimeStampLedger.undo(TimeStampInventory.read(buyer), reserved.get(),
                        TimeStealerRules.STAMP_MAX_STACK);
                int unplaced = TimeStampInventory.apply(buyer, undo);
                addUndelivered(buyer, undo.undelivered() + unplaced);
            }
        }
        if (ok) {
            NbtCompound extra = new NbtCompound();
            extra.putString(TimeStealerRules.REPLAY_ENTRY_KEY, entryId);
            extra.putInt(TimeStealerRules.REPLAY_COST_KEY, cost);
            GameRecordManager.recordItemUse(buyer, TimeStealerRules.STAMP_PURCHASE_RECORD_ID, null, extra);
        }
        return ok;
    }

    /**
     * Owner upkeep every tick: bind a holder who missed the round-start binding to the current match, merge stamps into
     * one stack when more than one exists (a no-write scan otherwise) and retry {@code UndeliveredStamps} every 20
     * ticks. Never moves stamps into a freed hotbar slot.
     * 持有者每 tick 维护：为错过开局绑定的持有者绑定当前对局；存在多个邮票堆时合并为一堆（否则只扫描、不写入），
     * 每 20 tick 重试 {@code UndeliveredStamps}。从不把邮票移入刚空出的快捷栏格。
     */
    public static void tickOwner(ServerPlayerEntity player) {
        if (!mayHold(player)) {
            return;
        }
        bindCurrentMatch(player);
        TimeStampLedger.Holdings holdings = TimeStampInventory.read(player);
        if (TimeStampLedger.stampStacks(holdings) > 1) {
            Plan merge = TimeStampLedger.consolidate(holdings, TimeStealerRules.STAMP_MAX_STACK);
            addUndelivered(player, TimeStampInventory.apply(player, merge));
        }
        TimeStealerPlayerComponent state = TimeStealerPlayerComponent.KEY.get(player);
        int pending = state.undeliveredStamps();
        if (pending > 0 && Math.floorMod(player.getWorld().getTime() + player.getId(), RETRY_INTERVAL_TICKS) == 0) {
            state.setUndeliveredStamps(0);
            deliver(player, pending);
        }
    }

    /**
     * Keeps stamps only when the Time Stealer role is re-assigned within the same, already-bound match (N1). At round
     * start the match id is not bound yet (Wathe starts the record after RoleAssigned), so every round-start
     * assignment strips; any other role strips and zeroes the retry counter. Wathe fires RoleAssigned after the role is
     * stored and the component binds every player's match, so the previous role is not observable here; a mid-match
     * first acquisition keeps only stamps that the non-holder sweep had not yet removed.
     * 仅当在同一个已绑定的对局内再次分配窃时者职业时保留邮票（N1）。开局时对局 id 尚未绑定（Wathe 在 RoleAssigned
     * 之后才开始记录），因此所有开局分配都会清空；其他任何职业都会清空邮票并将重试计数归零。Wathe 在写入职业之后才
     * 触发 RoleAssigned，且组件为所有玩家绑定对局，因此此处无法得知先前职业；对局中首次获得该职业时，只会保留非持有者
     * 清扫尚未移除的邮票。
     */
    public static void onRoleAssigned(ServerPlayerEntity player, @Nullable Role role) {
        if (player == null) {
            return;
        }
        UUID current = TimeStealerMatch.currentId();
        if (TimeStealerRules.isTimeStealer(role) && current != null
                && current.equals(TimeStealerPlayerComponent.KEY.get(player).matchId())) {
            return;
        }
        reset(player);
    }

    /** Strips every stamp and zeroes the retry counter. / 清除全部邮票并将重试计数归零。 */
    public static void reset(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        TimeStampInventory.stripAll(player);
        TimeStealerPlayerComponent.KEY.get(player).setUndeliveredStamps(0);
    }

    /**
     * Self-heal for the one-shot round-start binding (a player absent at initialization and assigned later, or a
     * changed listener order): a holder in an ACTIVE round with a current match is bound to it, as the Bell Ringer
     * re-binds in its tick. A no-op once bound; binding a new match drops only the stale retry counter.
     * 为一次性开局绑定兜底（初始化时不在场、之后才被分配的玩家，或监听器顺序变化）：ACTIVE 对局中有当前对局时，
     * 持有者会被绑定到该对局，与敲钟人在 tick 中重新绑定一致。已绑定时无操作；绑定新对局只丢弃过期的重试计数。
     */
    private static void bindCurrentMatch(ServerPlayerEntity player) {
        UUID current = TimeStealerMatch.currentId();
        if (current != null && GameWorldComponent.KEY.get(player.getWorld()).getGameStatus()
                == GameWorldComponent.GameStatus.ACTIVE) {
            TimeStealerPlayerComponent.KEY.get(player).bindMatch(current);
        }
    }

    private static boolean isActiveCurrentMatch(ServerPlayerEntity player) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        return game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                && Objects.equals(TimeStealerPlayerComponent.KEY.get(player).matchId(), TimeStealerMatch.currentId());
    }

    private static Plan deliver(ServerPlayerEntity player, int amount) {
        Plan plan = TimeStampLedger.deliver(TimeStampInventory.read(player), amount, TimeStealerRules.STAMP_MAX_STACK);
        int unplaced = TimeStampInventory.apply(player, plan);
        addUndelivered(player, plan.undelivered() + unplaced);
        return plan;
    }

    private static void addUndelivered(ServerPlayerEntity player, int amount) {
        if (amount <= 0) {
            return;
        }
        TimeStealerPlayerComponent state = TimeStealerPlayerComponent.KEY.get(player);
        state.setUndeliveredStamps((int) Math.min(Integer.MAX_VALUE, (long) state.undeliveredStamps() + amount));
    }
}
