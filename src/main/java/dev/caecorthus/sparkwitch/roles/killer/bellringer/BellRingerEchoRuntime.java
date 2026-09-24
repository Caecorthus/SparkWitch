package dev.caecorthus.sparkwitch.roles.killer.bellringer;

import dev.doctor4t.wathe.api.event.TaskComplete;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * Per-tick Echo resolution (stale match, marker validity, deadline penalty, hint expiry, toll flag), plus
 * the narrow decisions behind {@code PlayerMoodComponentBellEchoMixin}.
 * 每 tick 的回响结算（过期对局、标记有效性、截止惩罚、提示过期、敲钟标记），以及
 * {@code PlayerMoodComponentBellEchoMixin} 背后的窄决策。
 */
public final class BellRingerEchoRuntime {
    /**
     * Stand-in for {@code TaskComplete.EVENT.invoker()} when the Echo task completes: no listener from any
     * mod runs, so it pays no coins, counts no task, grants no mana and writes no replay record.
     * 回响任务完成时替代 {@code TaskComplete.EVENT.invoker()}：任何模组的监听器都不会执行，
     * 因此不发金币、不计任务数、不给法力，也不写回放记录。
     */
    public static final TaskComplete SUPPRESSED_TASK_COMPLETE = (player, taskType) -> {
    };

    private BellRingerEchoRuntime() {
    }

    /** Called from {@link BellEchoPlayerComponent#serverTick()} for every player; must early-out cheaply. / 由每名玩家的组件服务端 tick 调用，必须廉价地提前返回。 */
    public static void tick(ServerPlayerEntity player, BellEchoPlayerComponent component) {
        boolean echo = component.hasEchoTask();
        boolean heard = component.heardUntilTick() != 0L;
        boolean tollReady = component.tollReady();
        long now = player.getServerWorld().getTime();
        boolean scan = now % BellRingerRules.TOLL_SCAN_INTERVAL_TICKS == 0L;
        // Hot path: idle players do nothing on 9 of every 10 ticks.
        // 热路径：无状态玩家每 10 tick 中有 9 tick 直接返回。
        if (!echo && !heard && !tollReady && !scan) {
            return;
        }
        if (echo || heard || tollReady) {
            UUID currentMatch = BellRingerMatch.currentId();
            if (!Objects.equals(component.matchId(), currentMatch)) {
                // Cross-round leftovers are dropped silently and the component is rebound, so a live flag
                // recomputed below cannot flap against a stale id.
                // 跨局残留静默丢弃并重新绑定当前对局，避免随后重算的标记与过期 id 反复冲突。
                component.clear();
                component.bindMatch(currentMatch);
                return;
            }
        }
        if (echo) {
            resolveEcho(player, component, now);
        }
        if (heard && now >= component.heardUntilTick()) {
            component.clearHeard();
        }
        if (scan) {
            refreshTollReady(player, component);
        }
    }

    /** Silently drops the player's Echo (marker and task), hint and toll flag (no penalty). / 静默清除该玩家的回响（标记与任务）、提示与敲钟标记（无惩罚）。 */
    public static void clearPlayer(ServerPlayerEntity player) {
        BellEchoPlayerComponent component = BellEchoPlayerComponent.KEY.getNullable(player);
        if (component == null) {
            return;
        }
        dropEchoSilently(player, component);
        component.clearHeard();
        component.setTollReady(false);
    }

    /**
     * Silent drop (owner decision 14): clears the marker AND removes the exact Wathe task it marked, with no
     * penalty or message. A marker-less leftover would be an ordinary task whose completion fires
     * {@code TaskComplete.EVENT} and pays out (owner decision 5), e.g. after a Taotie release, a recruitment
     * or a revive. While the marker exists that task is the player's only task and its own completion is
     * suppressed, so no {@code TaskComplete} listener can be iterating this map when this runs.
     * 静默清除（所有者决定 14）：清除标记并移除其所标记的那项 Wathe 任务，不施加惩罚也不提示。
     * 若只清标记，残留任务会变成普通任务，完成时触发 {@code TaskComplete.EVENT} 并发放奖励（违反所有者决定 5），
     * 例如被饕餮释放、被招募或复活之后。标记存在期间该任务是玩家唯一的任务且其完成事件已被屏蔽，
     * 因此执行时不会有 {@code TaskComplete} 监听器正在遍历该任务表。
     */
    static void dropEchoSilently(ServerPlayerEntity player, BellEchoPlayerComponent component) {
        PlayerMoodComponent.Task type = component.echoTask();
        component.clearEcho();
        if (type == null) {
            return;
        }
        PlayerMoodComponent mood = PlayerMoodComponent.KEY.getNullable(player);
        if (mood != null && mood.tasks.remove(type) != null) {
            mood.sync();
        }
    }

    /**
     * Mixin seam (server): true while Wathe's normal task generation must hold for {@code player}.
     * Mixin 接口（服务端）：为 true 时暂停该玩家的 Wathe 常规任务生成。
     */
    public static boolean holdsNormalTasks(PlayerEntity player) {
        BellEchoPlayerComponent component = BellEchoPlayerComponent.KEY.getNullable(player);
        return component != null && component.hasEchoTask();
    }

    /**
     * Mixin seam (both sides): scales Wathe's per-task drain while an Echo task is active. The client
     * reads the owner-synced marker, so its local drain prediction matches the server.
     * Mixin 接口（双端）：回响任务期间放大 Wathe 的单任务消耗。客户端读取仅同步给拥有者的标记，
     * 因此本地消耗预测与服务端一致。
     */
    public static float scaleDrain(PlayerEntity player, float drain) {
        return holdsNormalTasks(player) ? drain * BellRingerRules.ECHO_DRAIN_MULTIPLIER : drain;
    }

    /**
     * Mixin seam (server): when {@code completed} is this player's Echo task, clears the marker and returns
     * true so the caller swaps in {@link #SUPPRESSED_TASK_COMPLETE}. Wathe's +0.5 mood and the
     * task-complete arrow have already happened by then and are kept.
     * Mixin 接口（服务端）：若完成的任务是该玩家的回响任务，则清除标记并返回 true，由调用方换用
     * {@link #SUPPRESSED_TASK_COMPLETE}。Wathe 的 +0.5 理智与任务完成箭头此前已发生并予以保留。
     */
    public static boolean consumeEchoCompletion(PlayerEntity player, @Nullable PlayerMoodComponent.TrainTask completed) {
        if (completed == null || player.getWorld().isClient) {
            return false;
        }
        BellEchoPlayerComponent component = BellEchoPlayerComponent.KEY.getNullable(player);
        if (component == null || !component.isEchoTask(completed.getType())) {
            return false;
        }
        component.clearEcho();
        return true;
    }

    /**
     * Keeps the marker only while it still describes a live Echo task, then applies the flat failure
     * penalty exactly once: the marker is dropped before the mood write so no later tick can repeat it.
     * 仅当标记仍对应一个有效回响任务时保留它；超时则只施加一次固定惩罚：先清除标记再写入理智，
     * 保证之后任何 tick 都不会重复惩罚。
     */
    private static void resolveEcho(ServerPlayerEntity player, BellEchoPlayerComponent component, long now) {
        PlayerMoodComponent.Task type = component.echoTask();
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getServerWorld());
        // Role change, death, reset, Wraith activation, spectating (Taotie swallow) or losing real sanity:
        // drop the marker and its task silently (owner decision 14).
        // 职业变更、死亡、重置、冤魂激活、旁观（被饕餮吞下）或失去真实理智：静默清除标记及其任务（所有者决定 14）。
        if (type == null
                || !BellRingerEchoTargeting.isParticipant(player)
                || !BellRingerEchoTargeting.hasRealSanity(player, game)) {
            dropEchoSilently(player, component);
            return;
        }
        PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(player);
        // Finished or removed elsewhere (admin command, another mod): no penalty.
        // 已在别处完成或被移除（管理命令、其他模组）：不施加惩罚。
        if (!mood.tasks.containsKey(type)) {
            component.clearEcho();
            return;
        }
        if (!BellRingerRules.isDeadlinePassed(now, component.echoDeadlineTick())) {
            return;
        }
        // Owner decisions 4 and 6: flat -0.5 (may reach Wathe's breakdown), then free the slot for normal
        // generation; tasks cleared at cast are not restored.
        // 所有者决定 4 与 6：固定 -0.5（可能触发 Wathe 精神崩溃），随后让出常规任务生成；施放时清除的任务不恢复。
        component.clearEcho();
        mood.setMood(mood.getMood() - BellRingerRules.ECHO_FAILURE_MOOD_LOSS);
        mood.tasks.remove(type);
        mood.sync();
        player.sendMessage(Text.translatable("message.sparkwitch.bell_echo.failed"), true);
    }

    /** Ringer-only glint flag, recomputed on the scan cadence; everyone else holds false. / 仅敲钟人的发光标记，按扫描周期重算；其他玩家恒为 false。 */
    private static void refreshTollReady(ServerPlayerEntity player, BellEchoPlayerComponent component) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getServerWorld());
        boolean ready = BellRingerRules.isBellRinger(game.getRole(player))
                && BellRingerEchoTargeting.isParticipant(player)
                && BellTollService.hasAnyTarget(player);
        component.setTollReady(ready);
    }
}
