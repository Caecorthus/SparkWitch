package dev.caecorthus.sparkwitch.item.rope;

/**
 * Server-authoritative Rope strangle. SCAFFOLD: nothing is registered yet; the feature is in progress. Numbers live
 * in {@link RopeRules}; the kill uses {@code SparkWitchDeathReasons.STRANGLED}; lang keys start at
 * {@code message.sparkwitch.rope.no_target} and {@code replay.item_use.sparkwitch.rope}.
 * 服务端权威的绳索勒杀。骨架阶段：尚未注册任何监听，功能仍在实现中。数值位于 {@link RopeRules}；击杀使用
 * {@code SparkWitchDeathReasons.STRANGLED}；语言键从 {@code message.sparkwitch.rope.no_target} 与
 * {@code replay.item_use.sparkwitch.rope} 开始。
 */
public final class RopeStrangleService {
    private static boolean registered;

    private RopeStrangleService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        // TODO(绳索): target pick (range, back arc), the 3 s channel freezing both players, the cosmetic hits, the
        // interrupt (attacked or controlled user -> RopeRules.INTERRUPT_COOLDOWN_TICKS), the shield-bypassing kill and
        // the consumption on success.
        // TODO（绳索）：选取目标（距离、背后弧）、定身双方的 3 秒引导、表现用受击、打断（使用者被攻击或被控制 →
        // RopeRules.INTERRUPT_COOLDOWN_TICKS）、无视护盾的击杀，以及成功后的消耗。
    }
}
