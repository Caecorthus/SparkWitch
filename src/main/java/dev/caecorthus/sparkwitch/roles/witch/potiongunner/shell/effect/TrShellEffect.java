package dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.effect;

import dev.caecorthus.sparkwitch.SparkWitchDeathReasons;
import dev.caecorthus.sparkwitch.roles.civilian.bear.ricochet.BearRicochetService;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionGunnerRules;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.PotionBlastContext;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.PotionBlastHit;
import dev.caecorthus.sparkwitch.util.hitscan.RangedOriginScope;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * TR: ordinary kill of everyone hit; survivors get the 3-second fallback.
 * The kill is Wathe's non-forced 4-argument {@code killPlayer}, so every protection runs normally (shields, psycho
 * armour, Tofana retaliation on the gunner, Last Stand, a dormant Fiend). Any target still alive and playing
 * afterwards gets Blindness + Slowness II and harmless burning (a BEAR Rys-T wearer may deflect that with its own roll,
 * owner Q3); a dormant Fiend is not one, since the blocked kill puts it into its spectator feign death. The blast
 * service runs this inside Judge attribution.
 * TR：对所有被波及者进行普通击杀；幸存者获得 3 秒的后备效果。
 * 击杀使用 Wathe 非强制的四参数 {@code killPlayer}，所有保护照常生效（护盾、疯魔护甲、托法娜反杀药炮手、
 * 最后的坚守、休眠魔人）。之后仍存活且在对局中的目标获得失明 + 缓慢 II 与无伤燃烧（BEAR 的 Rys-T 佩戴者可单独掷骰弹开，所有者 Q3）；休眠魔人不在此列，因为被挡下的击杀
 * 会让它进入旁观假死。爆炸服务在审判官归因内调用本效果。
 */
public final class TrShellEffect {
    private TrShellEffect() {
    }

    public static void apply(PotionBlastContext context) {
        ServerPlayerEntity gunner = context.gunner();
        for (PotionBlastHit hit : TrShellEffectRules.killOrder(context.hits(),
                candidate -> PotionShellTargets.isSelf(context, candidate))) {
            ServerPlayerEntity target = hit.target();
            boolean livingBefore = PotionShellTargets.isLiving(target);
            if (!livingBefore) {
                continue;
            }
            ServerPlayerEntity killer = TrShellEffectRules.killer(gunner, PotionShellTargets.isSelf(context, hit));
            RangedOriginScope.run(context.center(), () -> { // BEAR: blast-centre origin / 爆心来源
                GameFunctions.killPlayer(target, true, killer, SparkWitchDeathReasons.POTION_SHELL);
            });
            // BEAR (owner Q3): a worn Rys-T rolls for the fallback on its own; everyone else always gets it.
            // BEAR（所有者 Q3）：佩戴的 Rys-T 为后备效果单独掷骰；其他人照常获得。
            if (TrShellEffectRules.needsFallback(livingBefore, PotionShellTargets.isLiving(target))
                    && !BearRicochetService.deflectsAfterEffect(target, killer, SparkWitchDeathReasons.POTION_SHELL,
                    context.center())) {
                PotionShellDebuff.apply(target, PotionGunnerRules.TR_FALLBACK_TICKS, killer);
                PotionShellBurn.ignite(target, PotionGunnerRules.TR_FALLBACK_TICKS);
            }
        }
    }
}
