package dev.caecorthus.sparkwitch.roles.killer.witchmaiden;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** Owns Witch Maiden's final Voodoo cancellation. / 负责巫女的巫毒最终死亡取消。 */
public final class WitchMaidenFeatureService {
    private WitchMaidenFeatureService() {
    }

    /**
     * Asked by {@code mixin/witchmaiden/GameFunctionsWitchMaidenVoodooMixin} at the head of Wathe's
     * {@code killPlayer}, before {@code KillPlayer.BEFORE}: that event stops at the first non-null result, and
     * NoellesRoles answers {@code allowWithoutBody()} for a Taotie-swallowed victim before SparkWitch's listeners run.
     * 由 {@code GameFunctionsWitchMaidenVoodooMixin} 在 Wathe {@code killPlayer} 开头、{@code KillPlayer.BEFORE}
     * 之前调用：该事件以首个非空结果为准，NoellesRoles 会先对被饕餮吞下的受害者返回 {@code allowWithoutBody()}。
     */
    public static boolean blocksKill(ServerPlayerEntity victim, Identifier deathReason, boolean force) {
        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(victim.getServerWorld());
        return WitchMaidenRules.cancelsVoodooKill(gameComponent.getRole(victim), deathReason, force);
    }
}
