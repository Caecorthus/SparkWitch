package dev.caecorthus.sparkwitch.roles.civilian.hanna;

import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Names the player whose own action the server is resolving right now, so {@link HannaSilence} can tell a sourceless
 * world sound ({@code world.playSound(null, ...)}: Wathe doors, guns, lockpicks) made by a floating player from
 * everyone else's. Entered by the {@code mixin/hanna/*HannaSoundScopeMixin} seams around that player's interactions,
 * attacks, released items, own tick and custom payloads.
 * <p>Entered for every player without reading any state: whether the actor floats is read lazily, only when a
 * sourceless sound is played inside the scope, so a float that starts or is interrupted mid-action is judged at the
 * moment of the sound. One mutable cell per thread (the {@code RaycastShapeScope} pattern), so entering, leaving and
 * the per-sound read allocate nothing; scopes nest and always restore the caller's actor. Only meaningful on the
 * server thread, which is also the only thread that plays world sounds.
 * 标记服务端此刻正在结算其自身行动的玩家，使 {@link HannaSilence} 能把浮游玩家制造的无来源世界声音
 * （{@code world.playSound(null, ...)}：Wathe 的门、枪、开锁器）与其他人的区分开。由 {@code mixin/hanna/*HannaSoundScopeMixin}
 * 接缝在该玩家的交互、攻击、松开物品、自身 tick 与自定义数据包前后进入。
 * <p>对所有玩家都进入、且不读取任何状态：行动者是否在浮游只在作用域内播放无来源声音时才惰性读取，因此行动中途开始或被打断的浮游
 * 以声音发出的那一刻为准。每个线程只有一个可变单元（沿用 {@code RaycastShapeScope} 的做法），进入、退出与逐声音读取都不产生分配；
 * 作用域可嵌套，并始终恢复调用者的行动者。仅在服务端线程上有意义，世界声音也只在该线程播放。
 */
public final class HannaSoundScope {
    private static final ThreadLocal<ServerPlayerEntity[]> ACTOR =
            ThreadLocal.withInitial(() -> new ServerPlayerEntity[1]);

    private HannaSoundScope() {
    }

    /**
     * Opens the scope for {@code actor} and returns the caller's actor; pair it with {@link #exit} in a
     * {@code finally} on the same thread.
     * 为 {@code actor} 打开作用域并返回调用者的行动者；必须在同一线程的 {@code finally} 中以 {@link #exit} 配对。
     */
    @Nullable
    public static ServerPlayerEntity enter(@Nullable ServerPlayerEntity actor) {
        ServerPlayerEntity[] cell = ACTOR.get();
        ServerPlayerEntity previous = cell[0];
        cell[0] = actor;
        return previous;
    }

    /** Restores the actor {@link #enter} returned. / 恢复 {@link #enter} 返回的行动者。 */
    public static void exit(@Nullable ServerPlayerEntity previous) {
        ACTOR.get()[0] = previous;
    }

    /** Runs {@code action} as {@code actor}'s own action. / 以 {@code actor} 自身行动的身份执行 {@code action}。 */
    public static void run(@Nullable ServerPlayerEntity actor, Runnable action) {
        ServerPlayerEntity previous = enter(actor);
        try {
            action.run();
        } finally {
            exit(previous);
        }
    }

    /** The player acting on this thread right now, or null. / 本线程此刻正在行动的玩家，没有则为 null。 */
    @Nullable
    public static ServerPlayerEntity actor() {
        return ACTOR.get()[0];
    }
}
