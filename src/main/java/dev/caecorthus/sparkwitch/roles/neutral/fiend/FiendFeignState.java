package dev.caecorthus.sparkwitch.roles.neutral.fiend;

import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * One Fiend's feign-death cycle, server only and never persisted: the match it belongs to, the next periodic due tick,
 * and, while feigning, the end tick, the fake corpse, the anchor pose and what the revival restores. Pure data: the
 * world is read and written only by {@link FiendFeignService}.
 * 单个魔人的假死周期，仅服务端、从不持久化：所属对局、下一次定时假死的 tick，以及假死期间的结束 tick、假尸体、锚点姿态
 * 与复活时需要恢复的内容。纯数据：只有 {@link FiendFeignService} 读写世界。
 */
final class FiendFeignState {
    private @Nullable UUID matchId;
    private long dueTick = -1L;
    private boolean feigning;
    private long endTick;
    private @Nullable UUID bodyId;
    private double anchorX;
    private double anchorY;
    private double anchorZ;
    private float anchorYaw;
    private float anchorPitch;
    private boolean wasInvulnerable;
    private GameMode previousMode = GameMode.ADVENTURE;
    private boolean pendingSpeed;
    private boolean launched;
    private long launchedAt;
    private @Nullable Identifier launchReason;

    /** Whether the cycle is bound to this match. / 周期是否绑定到该对局。 */
    boolean isFor(@Nullable UUID match) {
        return match != null && dueTick >= 0L && Objects.equals(matchId, match);
    }

    void schedule(UUID match, long due) {
        matchId = match;
        dueTick = due;
    }

    boolean isScheduled() {
        return dueTick >= 0L;
    }

    long dueTick() {
        return dueTick;
    }

    void start(long end, @Nullable UUID body, double x, double y, double z, float yaw, float pitch,
               boolean invulnerable, GameMode mode) {
        feigning = true;
        endTick = end;
        bodyId = body;
        anchorX = x;
        anchorY = y;
        anchorZ = z;
        anchorYaw = yaw;
        anchorPitch = pitch;
        wasInvulnerable = invulnerable;
        previousMode = mode == null || mode == GameMode.SPECTATOR ? GameMode.ADVENTURE : mode;
        clearLaunch();
    }

    /** Ends the feign and schedules the next one. / 结束假死并排定下一次。 */
    void finish(long nextDue) {
        feigning = false;
        endTick = 0L;
        bodyId = null;
        pendingSpeed = false;
        clearLaunch();
        dueTick = nextDue;
    }

    void clear() {
        matchId = null;
        dueTick = -1L;
        feigning = false;
        endTick = 0L;
        bodyId = null;
        pendingSpeed = false;
        clearLaunch();
    }

    /**
     * A lethal hit that launched the Fiend: its 20 s feign waits for the landing (the first launch wins).
     * 击飞魔人的致命命中：其 20 秒假死等待落地后开始（以第一次击飞为准）。
     */
    void launch(long tick, @Nullable Identifier reason) {
        if (!launched) {
            launched = true;
            launchedAt = tick;
            launchReason = reason;
        }
    }

    boolean isLaunched() {
        return launched;
    }

    long launchedAt() {
        return launchedAt;
    }

    @Nullable Identifier launchReason() {
        return launchReason;
    }

    void clearLaunch() {
        launched = false;
        launchedAt = 0L;
        launchReason = null;
    }

    boolean isFeigning() {
        return feigning;
    }

    long endTick() {
        return endTick;
    }

    @Nullable UUID bodyId() {
        return bodyId;
    }

    double anchorX() {
        return anchorX;
    }

    double anchorY() {
        return anchorY;
    }

    double anchorZ() {
        return anchorZ;
    }

    float anchorYaw() {
        return anchorYaw;
    }

    float anchorPitch() {
        return anchorPitch;
    }

    boolean wasInvulnerable() {
        return wasInvulnerable;
    }

    GameMode previousMode() {
        return previousMode;
    }

    /** Gun-hit Speed III waits for the revival. / 枪击的速度 III 等到复活时再给。 */
    void deferSpeed() {
        pendingSpeed = true;
    }

    boolean hasPendingSpeed() {
        return pendingSpeed;
    }

    /** Returns and clears the deferred gun Speed III. / 返回并清除延后的枪击速度 III。 */
    boolean takePendingSpeed() {
        boolean pending = pendingSpeed;
        pendingSpeed = false;
        return pending;
    }
}
