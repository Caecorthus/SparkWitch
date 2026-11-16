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

    /** Server only: marks {@code type} as a recommendation until {@code endTick}. / 仅服务端：标记推荐直到 {@code endTick}。 */
    public void setRecommendation(PlayerMoodComponent.Task type, long endTick) {
        recommendedType = type;
        recommendationEndTick = Math.max(0L, endTick);
        sync();
    }

    /** Server only: drops the recommendation marker; returns whether one was set. / 仅服务端：清除推荐标记。 */
    public boolean clearRecommendation() {
        if (recommendedType == null) {
            return false;
        }
        recommendedType = null;
        recommendationEndTick = 0L;
        sync();
        return true;
    }

    /** Server only: clears everything (role assignment, round reset). / 仅服务端：清空全部状态（身份分配、换局）。 */
    public void clear() {
        if (skillReadyTick == 0L && nextTaskSeconds == -1 && recommendedType == null && recommendationEndTick == 0L) {
            return;
        }
        skillReadyTick = 0L;
        nextTaskSeconds = -1;
        recommendedType = null;
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
        recommendationEndTick = Math.max(0L, buf.readVarLong());
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        skillReadyTick = Math.max(0L, tag.getLong(SKILL_READY_TICK));
        recommendedType = tag.contains(RECOMMENDED_TYPE, NbtElement.NUMBER_TYPE)
                ? taskOrNull(tag.getInt(RECOMMENDED_TYPE))
                : null;
        recommendationEndTick = recommendedType == null ? 0L : Math.max(0L, tag.getLong(RECOMMENDATION_END_TICK));
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
