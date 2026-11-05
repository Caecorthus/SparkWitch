package dev.caecorthus.sparkwitch.roles.civilian.bear.smoke;

/**
 * Frozen contract: the timeline of the realistic emitter model (owner 2026-11-05: smoke must behave like real smoke,
 * not a CS2-style volume; the canister keeps emitting for 25 s, then the smoke disperses over about 6 s). The canister
 * may stop early when a cap evicts its cloud; {@link SmokeSimGrid#emitTicks()} carries the actual emission length.
 * 冻结契约：真实喷口模型的时间线（所有者 2026-11-05：烟要像真的烟，而不是 CS2 式的体积；弹体持续喷 25 秒，之后约 6 秒
 * 散去）。上限淘汰时弹体可提前停喷；实际喷烟时长由 {@link SmokeSimGrid#emitTicks()} 给出。
 */
public final class SmokeEmission {
    /** Ticks the canister emits smoke. / 弹体喷烟的 tick 数。 */
    public static final int EMIT_TICKS = 500;
    /** Ticks after the emission stops until indoor smoke has cleared. / 停喷后室内烟散尽所需的 tick 数。 */
    public static final int DISPERSE_TICKS = 120;
    /** Extra margin so a slow tail never pops out. / 额外余量，避免拖尾被突然删掉。 */
    public static final int MARGIN_TICKS = 40;

    private SmokeEmission() {
    }

    /** Ticks after spawn when a cloud that emitted for {@code emitTicks} is certainly gone. / 喷了 {@code emitTicks} 的烟团必定消失的时刻（相对爆开）。 */
    public static int lifetimeTicks(int emitTicks) {
        return Math.max(0, emitTicks) + DISPERSE_TICKS + MARGIN_TICKS;
    }
}
