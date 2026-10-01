package dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.effect;

import dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.PotionBlastContext;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.PotionBlastHit;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * GW-DK: Blindness + Slowness II scaled by falloff, attributed to the gunner (unattributed while offline).
 * GW-DK：按衰减施加失明与缓慢 II，来源记为药炮手（离线时无来源）。
 */
public final class DkShellEffect {
    private DkShellEffect() {
    }

    public static void apply(PotionBlastContext context) {
        for (PotionBlastHit hit : context.hits()) {
            ServerPlayerEntity target = hit.target();
            // The resolver already dropped allies and the gunner; both flags are re-checked fail-closed.
            // 判定器已剔除队友与药炮手本人；此处再按失败即关闭的原则复查两项标记。
            if (hit.ally() || PotionShellTargets.isSelf(context, hit) || !PotionShellTargets.isLiving(target)) {
                continue;
            }
            PotionShellDebuff.apply(target, DkShellEffectRules.ticks(hit.factor()), context.gunner());
        }
    }
}
