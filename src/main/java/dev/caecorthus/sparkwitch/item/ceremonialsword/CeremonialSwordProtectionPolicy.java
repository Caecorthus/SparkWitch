package dev.caecorthus.sparkwitch.item.ceremonialsword;

import dev.doctor4t.wathe.api.event.KillPlayer;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Only the accepted sword death bypasses target protection; this is not an attacker eligibility gate.
 * 仅已获准的仪礼剑死因穿透目标保护；此策略不替代攻击者资格判定。
 */
public final class CeremonialSwordProtectionPolicy {
    private static final Identifier DEATH_REASON = Identifier.of("sparkwitch", "ceremonial_blade");

    private CeremonialSwordProtectionPolicy() {
    }

    public static boolean pierces(@Nullable Identifier deathReason) {
        return DEATH_REASON.equals(deathReason);
    }

    /** Invoke AFTER consumption/retaliation. Null continues BEFORE listeners; allow() would stop them.
     * 消耗与反击执行后调用。null 继续 BEFORE 监听；allow() 会使后续监听短路。 */
    public static @Nullable KillPlayer.KillResult afterProtection(@Nullable Identifier deathReason) {
        return pierces(deathReason) ? null : KillPlayer.KillResult.cancel();
    }

    public static boolean cancelsDeath(boolean protectionTriggered, @Nullable Identifier deathReason) {
        return protectionTriggered && !pierces(deathReason);
    }
}
