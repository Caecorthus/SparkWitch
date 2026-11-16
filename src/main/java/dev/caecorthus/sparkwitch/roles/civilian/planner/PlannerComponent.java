package dev.caecorthus.sparkwitch.roles.civilian.planner;

import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

import java.util.Map;

/**
 * Stable CCA contract {@code sparkwitch:planner_master_player} (NEVER_COPY), on every player.
 * <ul>
 *   <li>Planner side: the absolute world tick at which the recommendation is ready again, and the whole seconds left
 *   until Wathe deals the planner's next task (-1 while unknown; published by the server, never saved).</li>
 *   <li>Target side (any player): the task type that currently is a planner recommendation, and the absolute world
 *   tick at which it expires. At most one recommendation per player.</li>
 * </ul>
 * Sync goes to the owner alone: no other client learns who holds a recommendation or when the planner is ready. The
 * client reads the ready tick against its own world clock (the Noa/Hanna precedent). NBT keeps the ready tick and the
 * recommendation across a mid-round relog; role assignment and Wathe's {@code ResetPlayer} clear everything. The
 * forced-cooldown store {@code sparkwitch:planner_recommend} writes the ready tick through
 * {@link #setSkillReadyTick(long)}.
 * 稳定 CCA 契约 {@code sparkwitch:planner_master_player}（NEVER_COPY），挂在每名玩家身上。
 * 规划大师一侧：推荐再次就绪的绝对世界刻，以及距 Wathe 发下一个任务的整秒数（未知为 -1；由服务端发布，不保存）。
 * 目标一侧（任何玩家）：当前作为规划大师推荐的任务类型及其过期的绝对世界刻。每名玩家最多一条推荐。
 * 只同步给本人：其他客户端不会得知谁持有推荐、规划大师何时就绪。客户端按自身世界时钟读取就绪刻（沿用诺亚/汉娜的先例）。
 * NBT 使就绪刻与推荐在局中重新登录后保留；身份分配与 Wathe 的 {@code ResetPlayer} 清空全部状态。强制冷却存储
 * {@code sparkwitch:planner_recommend} 通过 {@link #setSkillReadyTick(long)} 写入就绪刻。
 * <p>The server also keeps a transient reference to the exact Wathe task object it inserted (never saved or synced):
 * a feature that rewrites the task map with a fresh task of the same type (a Bell Ringer Echo, a task slip, another
 * mod) no longer matches it, so that task never counts as the recommendation. After a relog the reference is gone and
 * the type alone decides; the client always decides by type.
 * <p>服务端还保留一个指向其插入的那个 Wathe 任务对象的临时引用（从不保存、从不同步）：用同类型的新任务改写任务表的功能
 * （敲钟人回响、任务单、其他模组）不再与之匹配，因此那个任务永远不算推荐。重新登录后该引用丢失，只按类型判断；客户端始终
 * 按类型判断。
 */
public final class PlannerComponent implements AutoSyncedComponent {
    public static final ComponentKey<PlannerComponent> KEY = ComponentRegistry.getOrCreate(
            PlannerRules.ROLE_ID.withSuffixedPath("_player"), PlannerComponent.class);
    static final String SKILL_READY_TICK = "SkillReadyTick";
    static final String RECOMMENDED_TYPE = "RecommendedType";
    static final String RECOMMENDATION_END_TICK = "RecommendationEndTick";

    private final PlayerEntity player;
    private long skillReadyTick;
    /** Planner only, never saved: whole seconds until the next Wathe task, -1 unknown. / 仅规划大师、不保存。 */
    private int nextTaskSeconds = -1;
    private @Nullable PlayerMoodComponent.Task recommendedType;
    private long recommendationEndTick;
    /** Server only, never saved or synced: the exact task object inserted. / 仅服务端、不保存不同步：插入的那个任务对象。 */
    private @Nullable PlayerMoodComponent.TrainTask recommendedTask;
    /**
     * Planner only, server only, never saved or synced: the last {@code nextTaskTimer} reading (-1 none) and the
     * smoothed per-tick decrease. / 仅规划大师、仅服务端、不保存不同步：上一次 {@code nextTaskTimer} 读数（-1 表示没有）与
     * 平滑后的每刻减少量。
     */
    private int lastNextTaskTimer = -1;
    private float nextTaskTimerRate = 1.0F;

    public PlannerComponent(PlayerEntity player) {
        this.player = player;
    }

    public long skillReadyTick() {
        return skillReadyTick;
    }

    /** Ticks until the recommendation is ready at world time {@code now}; 0 when ready. / 距推荐就绪的刻数，就绪为 0。 */
    public int skillCooldownRemaining(long now) {
        return (int) Math.clamp(skillReadyTick - now, 0L, Integer.MAX_VALUE);
    }

    /** Server only (also the forced-cooldown store's setter). / 仅服务端（亦为强制冷却存储的写入口）。 */
    public void setSkillReadyTick(long tick) {
        long next = Math.max(0L, tick);
        if (next != skillReadyTick) {
            skillReadyTick = next;
            sync();
        }
    }

    public int nextTaskSeconds() {
        return nextTaskSeconds;
    }

    /**
     * Server only: records this tick's {@code nextTaskTimer} reading and returns the smoothed per-tick decrease
     * ({@link PlannerRules#nextTaskTimerRate}). / 仅服务端：记录本刻的 {@code nextTaskTimer} 读数并返回平滑后的每刻减少量。
     */
    public float observeNextTaskTimer(int timer) {
        nextTaskTimerRate = PlannerRules.nextTaskTimerRate(nextTaskTimerRate, lastNextTaskTimer, timer);
        lastNextTaskTimer = Math.max(0, timer);
        return nextTaskTimerRate;
    }

    /** Server only: a gap in the readings (dead, off-round) starts a fresh pair. / 仅服务端：读数中断后重新配对。 */
    public void forgetNextTaskTimer() {
        lastNextTaskTimer = -1;
    }

    /** Server only: syncs only when the shown second changes (1 Hz at most). / 仅服务端：显示的秒数变化时才同步。 */
    public void setNextTaskSeconds(int seconds) {
        int next = Math.max(-1, seconds);
        if (next != nextTaskSeconds) {
            nextTaskSeconds = next;
            sync();
        }
    }

    public @Nullable PlayerMoodComponent.Task recommendedType() {
        return recommendedType;
    }

    public long recommendationEndTick() {
        return recommendationEndTick;
    }

    public boolean hasRecommendation() {
        return recommendedType != null;
    }

    public boolean isRecommended(@Nullable PlayerMoodComponent.Task type) {
        return type != null && type == recommendedType;
    }

    /**
     * Whether {@code task} is the recommendation: its type is marked and, while the server still holds the inserted
     * object, it is that very object. 该任务是否就是推荐：类型已被标记，且在服务端仍持有插入对象时必须是同一个对象。
     */
    public boolean isRecommendedTask(@Nullable PlayerMoodComponent.TrainTask task) {
        return task != null && task.getType() == recommendedType && recommendedType != null
                && (recommendedTask == null || recommendedTask == task);
    }

    /** Whether {@code tasks} holds the recommendation ({@link #isRecommendedTask}). / 任务表中是否持有推荐。 */
    public boolean holdsRecommendation(Map<PlayerMoodComponent.Task, PlayerMoodComponent.TrainTask> tasks) {
        return recommendedType != null && isRecommendedTask(tasks.get(recommendedType));
    }

    /** Server only: marks {@code type} as a recommendation until {@code endTick}. / 仅服务端：标记推荐直到 {@code endTick}。 */
    public void setRecommendation(PlayerMoodComponent.Task type, long endTick) {
        recommendedType = type;
        recommendedTask = null;
        recommendationEndTick = Math.max(0L, endTick);
        sync();
    }

    /**
     * Server only: marks the task object just inserted as the recommendation until {@code endTick}.
     * 仅服务端：把刚插入的任务对象标记为推荐，直到 {@code endTick}。
     */
    public void setRecommendation(PlayerMoodComponent.TrainTask task, long endTick) {
        setRecommendation(task.getType(), endTick);
        recommendedTask = task;
    }

    /** Server only: drops the recommendation marker; returns whether one was set. / 仅服务端：清除推荐标记。 */
    public boolean clearRecommendation() {
        if (recommendedType == null) {
            return false;
        }
        recommendedType = null;
        recommendedTask = null;
        recommendationEndTick = 0L;
        sync();
        return true;
    }

    /** Server only: clears everything (role assignment, round reset). / 仅服务端：清空全部状态（身份分配、换局）。 */
    public void clear() {
        lastNextTaskTimer = -1;
        nextTaskTimerRate = 1.0F;
        if (skillReadyTick == 0L && nextTaskSeconds == -1 && recommendedType == null && recommendationEndTick == 0L) {
            return;
        }
        skillReadyTick = 0L;
        nextTaskSeconds = -1;
        recommendedType = null;
        recommendedTask = null;
        recommendationEndTick = 0L;
        sync();
    }

    private void sync() {
        if (player instanceof ServerPlayerEntity) {
            KEY.sync(player);
        }
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        // Owner only: nobody else may learn who holds a recommendation. / 仅本人：其他人不得得知谁持有推荐。
        return recipient == player;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeVarLong(skillReadyTick);
        buf.writeVarInt(nextTaskSeconds + 1);
        buf.writeVarInt(recommendedType == null ? -1 : recommendedType.ordinal());
        buf.writeVarLong(recommendationEndTick);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        skillReadyTick = Math.max(0L, buf.readVarLong());
        nextTaskSeconds = Math.max(-1, buf.readVarInt() - 1);
        recommendedType = taskOrNull(buf.readVarInt());
        recommendedTask = null;
        recommendationEndTick = Math.max(0L, buf.readVarLong());
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        skillReadyTick = Math.max(0L, tag.getLong(SKILL_READY_TICK));
        recommendedType = tag.contains(RECOMMENDED_TYPE, NbtElement.NUMBER_TYPE)
                ? taskOrNull(tag.getInt(RECOMMENDED_TYPE))
                : null;
        recommendationEndTick = recommendedType == null ? 0L : Math.max(0L, tag.getLong(RECOMMENDATION_END_TICK));
        // A relog rebuilds the task objects too: fall back to the type. / 重新登录也会重建任务对象：退回按类型判断。
        recommendedTask = null;
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        if (skillReadyTick > 0L) {
            tag.putLong(SKILL_READY_TICK, skillReadyTick);
        }
        if (recommendedType != null) {
            tag.putInt(RECOMMENDED_TYPE, recommendedType.ordinal());
            tag.putLong(RECOMMENDATION_END_TICK, recommendationEndTick);
        }
    }

    private static @Nullable PlayerMoodComponent.Task taskOrNull(int ordinal) {
        PlayerMoodComponent.Task[] values = PlayerMoodComponent.Task.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }
}
