package dev.caecorthus.sparkwitch.roles.witch;

import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.BlackoutEffect;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Wathe protection hooks for Witch faction blackout immunity and Voodoo guard.
 * 魔女阵营停电免疫和巫毒保护的 wathe 保护钩子。
 */
public final class WitchFactionProtectionPolicy {
    private static boolean registered;

    private WitchFactionProtectionPolicy() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BlackoutEffect.BEFORE.register(WitchFactionProtectionPolicy::beforeBlackoutEffect);
        KillPlayer.BEFORE.register(WitchFactionProtectionPolicy::beforeKillPlayer);
    }

    static BlackoutEffect.BlackoutResult beforeBlackoutEffect(ServerPlayerEntity player, int durationTicks) {
        Role role = GameWorldComponent.KEY.get(player.getServerWorld()).getRole(player);
        return WitchFactionRules.isWitchFactionMember(role) ? BlackoutEffect.BlackoutResult.cancel() : null;
    }

    /**
     * Asked by {@code mixin/GameFunctionsGrandWitchVoodooMixin} at the head of Wathe's {@code killPlayer}, before
     * {@code KillPlayer.BEFORE}: that event stops at the first non-null result, and NoellesRoles answers
     * {@code allowWithoutBody()} for a Taotie-swallowed victim before {@link #beforeKillPlayer} is asked. The listener
     * below stays registered as the same rule on the event path.
     * 由 {@code mixin/GameFunctionsGrandWitchVoodooMixin} 在 Wathe {@code killPlayer} 开头、{@code KillPlayer.BEFORE} 之前
     * 调用：该事件以首个非空结果为准，NoellesRoles 会先对被饕餮吞下的受害者返回 {@code allowWithoutBody()}，轮不到
     * {@link #beforeKillPlayer}。下方的监听仍然注册，作为事件路径上的同一条规则。
     */
    public static boolean cancelsVoodooKill(ServerPlayerEntity victim, Identifier deathReason, boolean force) {
        Role role = GameWorldComponent.KEY.get(victim.getServerWorld()).getRole(victim);
        return GrandWitchRules.cancelsVoodooKill(role, deathReason, force);
    }

    static KillPlayer.KillResult beforeKillPlayer(
            ServerPlayerEntity victim,
            ServerPlayerEntity killer,
            Identifier deathReason
    ) {
        Role role = GameWorldComponent.KEY.get(victim.getServerWorld()).getRole(victim);
        return GrandWitchRules.shouldBlockVoodooCurse(role, deathReason) ? KillPlayer.KillResult.cancel() : null;
    }
}
