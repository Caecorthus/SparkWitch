package dev.caecorthus.sparkwitch.roles.civilian.knight.duel;

import dev.caecorthus.sparkwitch.SparkWitchDeathReasons;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Duel interference rule: while the duel variable ({@link KnightDuelWorldComponent#active()} is 1), a player outside
 * the pair whose human-caused lethal death reason is in {@link #HUMAN_LETHAL_REASONS} kills a duelist is force-killed
 * with {@link SparkWitchDeathReasons#DUEL_INTERFERENCE}. Ordinary non-lethal hits and non-damage kills never trigger
 * it. The attacker's kill is forced and SparkTraits-terminal, so their traits and every shield are bypassed; the
 * victim's damage still resolves.
 * 决斗干扰规则：决斗变量（{@link KnightDuelWorldComponent#active()} 为 1）期间，局外玩家以
 * {@link #HUMAN_LETHAL_REASONS} 中的人为致死原因击杀决斗者时，会以
 * {@link SparkWitchDeathReasons#DUEL_INTERFERENCE} 强制击杀该玩家。普通非致命攻击与非伤害类死亡不会触发。攻击者的
 * 死亡为强制且是 SparkTraits 终结原因，绕过其词条与一切护盾；受害者的伤害仍正常结算。
 */
public final class KnightDuelInterference {
    /**
     * Human-caused lethal reasons from this pack: Wathe weapon damage, every player-caused SparkWitch kill, the
     * SparkStrength kills, and the NoellesRoles attacks reachable here (the SparkStrength bomber drone credits
     * {@code noellesroles:bomb}). Self-inflicted penalties, duel-rule deaths and environment reasons stay out.
     * 本整合包中的人为致死原因：Wathe 武器伤害、全部由玩家造成的 SparkWitch 击杀、SparkStrength 击杀，以及此处可触发的
     * NoellesRoles 攻击（SparkStrength 轰炸无人机记 {@code noellesroles:bomb}）。自杀式惩罚、决斗规则死亡与环境死因不在其中。
     */
    private static final Set<Identifier> HUMAN_LETHAL_REASONS = Set.of(
            // Wathe weapon damage. / Wathe 武器伤害。
            GameConstants.DeathReasons.GENERIC,
            GameConstants.DeathReasons.KNIFE,
            GameConstants.DeathReasons.GUN,
            GameConstants.DeathReasons.BAT,
            GameConstants.DeathReasons.GRENADE,
            GameConstants.DeathReasons.POISON,
            // SparkWitch player-caused kills. / SparkWitch 玩家造成的击杀。
            SparkWitchDeathReasons.CEREMONIAL_BLADE,
            SparkWitchDeathReasons.MIGHTY_FORCE,
            SparkWitchDeathReasons.PIERCED_BY_RAY,
            SparkWitchDeathReasons.NINJA_KNIFE_KILL,
            SparkWitchDeathReasons.NINJA_SHURIKEN_KILL,
            SparkWitchDeathReasons.BELL_TOLL,
            SparkWitchDeathReasons.TIME_STOLEN,
            SparkWitchDeathReasons.SWORDFISH_STAB,
            SparkWitchDeathReasons.POTION_SHELL,
            SparkWitchDeathReasons.POTION_BACKBLAST,
            SparkWitchDeathReasons.FIEND_DEVOURED,
            SparkWitchDeathReasons.CRIMSON_WITHER_SKULL,
            // SparkStrength player-caused kills. / SparkStrength 玩家造成的击杀。
            Identifier.of("sparkstrength", "tomahawk"),
            Identifier.of("sparkstrength", "partygoer_eaten"),
            Identifier.of("sparkstrength", "partygoer_kicked"),
            // NoellesRoles attacks used in this pack. / 本整合包使用的 NoellesRoles 攻击。
            Identifier.of("noellesroles", "bomb"),
            Identifier.of("noellesroles", "throwing_axe"),
            Identifier.of("noellesroles", "demon_hunter_shot"),
            Identifier.of("noellesroles", "assassinated")
    );

    private KnightDuelInterference() {
    }

    /**
     * Called from the {@code killPlayer} HEAD mixin: this method is the exact point where Wathe turns a would-be lethal
     * blow into a kill, before every protection listener. The kill itself is never cancelled here; only the attacker is
     * punished. The death reason must be one of the human-caused lethal reasons above.
     * 由 {@code killPlayer} 的 HEAD mixin 调用：这是 Wathe 把可能致死的伤害转为击杀的确切位置，早于所有保护监听。
     * 这里绝不取消击杀本身，只处死攻击者。死因必须属于上面的人为致死原因集合。
     */
    public static void onKillPlayer(ServerPlayerEntity victim, @Nullable ServerPlayerEntity attacker, Identifier reason) {
        if (reason == null || !HUMAN_LETHAL_REASONS.contains(reason)) {
            return;
        }
        if (attacker == null || attacker == victim || !isThirdParty(victim, attacker)) {
            return;
        }
        punish(attacker);
    }

    /**
     * Duel variable is 1, the victim is a duelist, and the attacker is a living player outside the pair.
     * 决斗变量为 1、受害者是决斗者，且攻击者是局外的存活玩家。
     */
    private static boolean isThirdParty(ServerPlayerEntity victim, ServerPlayerEntity attacker) {
        if (!(victim.getWorld() instanceof ServerWorld world)) {
            return false;
        }
        KnightDuelWorldComponent duel = KnightDuelWorldComponent.KEY.get(world);
        return duel.active() && duel.isDuelist(victim.getUuid()) && !duel.isDuelist(attacker.getUuid());
    }

    private static void punish(ServerPlayerEntity attacker) {
        if (!GameFunctions.isPlayerPlayingAndAlive(attacker)) {
            return;
        }
        // Forced + terminal: bypasses the attacker's traits and every shield. / 强制 + 终结：绕过攻击者的词条与一切护盾。
        GameFunctions.killPlayer(attacker, true, null, SparkWitchDeathReasons.DUEL_INTERFERENCE, true);
    }
}
