package dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.effect;

import dev.caecorthus.sparkwitch.SparkWitchDeathReasons;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionGunnerRules;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.PotionBlastContext;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.PotionBlastHit;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * TR: ordinary kill of everyone hit; survivors get the 3-second fallback.
 * The kill is Wathe's non-forced 4-argument {@code killPlayer}, so every protection runs normally (shields, psycho
 * armour, Tofana retaliation on the gunner, Last Stand, a dormant Fiend). Any target still alive and playing
 * afterwards gets Blindness + Slowness II and harmless burning. The blast service runs this inside Judge attribution.
 * TR：对所有被波及者进行普通击杀；幸存者获得 3 秒的后备效果。
 * 击杀使用 Wathe 非强制的四参数 {@code killPlayer}，所有保护照常生效（护盾、疯魔护甲、托法娜反杀药炮手、
 * 最后的坚守、休眠魔人）。之后仍存活且在对局中的目标获得失明 + 缓慢 II 与无伤燃烧。爆炸服务在审判官归因内调用本效果。
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
            GameFunctions.killPlayer(target, true, killer, SparkWitchDeathReasons.POTION_SHELL);
            if (TrShellEffectRules.needsFallback(livingBefore, PotionShellTargets.isLiving(target))) {
                PotionShellDebuff.apply(target, PotionGunnerRules.TR_FALLBACK_TICKS, killer);
                PotionShellBurn.ignite(target, PotionGunnerRules.TR_FALLBACK_TICKS);
            }
        }
    }
}
