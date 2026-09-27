package dev.caecorthus.sparkwitch.roles.killer.timestealer;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Victim-side curse runtime: starts a theft, advances stages from the victim's own component tick, settles the lethal
 * stage once through {@code TimeStealerKillService}, and lifts curses (Timekeeper purge, death, reset).
 * 受害者侧诅咒运行时：开始窃取、由受害者自身组件 tick 推进阶段、经 {@code TimeStealerKillService} 对致死阶段结算一次，
 * 并解除诅咒（计时员清除、死亡、重置）。
 */
public final class TimeTheftRuntime {
    private TimeTheftRuntime() {
    }

    /** Starts a curse on {@code victim}; false when refused (already stolen, invalid). / 对受害者开始诅咒；被拒绝时返回 false。 */
    public static boolean steal(ServerPlayerEntity victim, ServerPlayerEntity stealer) {
        // TODO(WP-02): bind match, start the component; never throws.
        return false;
    }

    /** Called only while {@code state.isStolen()}. / 仅在 {@code state.isStolen()} 时调用。 */
    public static void tick(ServerPlayerEntity victim, TimeTheftPlayerComponent state) {
        // TODO(WP-02): mismatch/inactive purge, stage slowness + private chime, lethal settle.
    }

    /** Lifts every active curse in the world (grace included); returns how many were lifted. / 解除世界内所有诅咒（含静默期），返回解除数量。 */
    public static int purgeAll(ServerWorld world, PurgeCause cause) {
        // TODO(WP-02): idempotent per-player purge with N11 feedback.
        return 0;
    }

    /** Drops only the dead player's own curse; curses they cast keep running (Q7). / 只清除死者自身的诅咒；其施下的诅咒继续（Q7）。 */
    public static void onDeath(ServerPlayerEntity dead) {
        // TODO(WP-02): clear the dead victim's own curse and owned Slowness.
    }

    /** Clears this player's curse and the Slowness it owns. / 清除该玩家的诅咒及其拥有的缓慢。 */
    public static void reset(ServerPlayerEntity player) {
        // TODO(WP-02): clear the component and remove only the curse's own Slowness.
    }

    /** Why curses are being lifted. / 解除诅咒的原因。 */
    public enum PurgeCause {
        TIMEKEEPER,
        RESET
    }
}
