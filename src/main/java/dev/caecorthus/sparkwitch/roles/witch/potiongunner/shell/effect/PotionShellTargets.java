package dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.effect;

import dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.PotionBlastContext;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.PotionBlastHit;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Server-side re-checks shared by the shell effects. Targeting is final when an effect runs, but an earlier kill or
 * effect in the same blast may already have removed a target.
 * 各炮弹效果共用的服务端复查。效果执行时目标已经确定，但同一次爆炸中更早的击杀或效果可能已让某个目标失效。
 */
final class PotionShellTargets {
    private PotionShellTargets() {
    }

    /** Still connected, alive, and playing in the running round. / 仍在线、存活且在进行中的对局里。 */
    static boolean isLiving(@Nullable ServerPlayerEntity target) {
        return target != null && !target.isRemoved()
                && GameFunctions.isPlayerPlayingAndAlive(target)
                && GameFunctions.isPlayerAliveAndSurvival(target);
    }

    /** The resolver's flag, or the gunner's own UUID as a fail-closed backstop. / 判定器标记，或以药炮手 UUID 兜底。 */
    static boolean isSelf(PotionBlastContext context, PotionBlastHit hit) {
        return hit.self() || context.gunnerUuid() != null && context.gunnerUuid().equals(hit.target().getUuid());
    }
}
