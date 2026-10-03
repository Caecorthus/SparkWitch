package dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell;

import dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.effect.AcShellEffect;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.effect.DkShellEffect;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.effect.MrShellEffect;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.effect.TrShellEffect;

/**
 * Dispatches a resolved blast to its shell effect. Effects run on the server thread after targeting is final.
 * 把已判定的爆炸分派给对应炮弹效果。效果在目标确定后于服务端线程执行。
 */
public final class PotionShellEffects {
    private PotionShellEffects() {
    }

    public static void apply(PotionBlastContext context) {
        switch (context.type()) {
            case DK -> DkShellEffect.apply(context);
            case AC -> AcShellEffect.apply(context);
            case MR -> MrShellEffect.apply(context);
            case TR -> TrShellEffect.apply(context);
        }
    }
}
