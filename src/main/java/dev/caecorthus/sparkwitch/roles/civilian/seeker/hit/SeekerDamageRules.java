package dev.caecorthus.sparkwitch.roles.civilian.seeker.hit;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Frozen contract: who may break a Seeker device (not the owner, active participant, not in a Seeker session, not
 * stunned, not Last-Escape blocked, SparkFactionAPI {@code canAffectPlayer} with the owner as proxy target, Vendetta
 * isolation), plus pure blast/LOS helpers.
 * TODO(WP-04): implement. / 待 WP-04 实现。
 * 冻结契约：谁可以损坏搜寻者设备（非拥有者、可行动参与者、不在搜寻者会话中、未眩晕、未被最后逃脱阻止、
 * 以拥有者为代理目标通过 SparkFactionAPI {@code canAffectPlayer}、复仇者隔离），以及纯爆炸/视线辅助方法。
 */
public final class SeekerDamageRules {
    private SeekerDamageRules() {
    }

    public static boolean mayBreak(@Nullable ServerPlayerEntity attacker, @Nullable ServerPlayerEntity owner,
                                   GameWorldComponent game) {
        return false;
    }
}
