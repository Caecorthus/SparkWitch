package dev.caecorthus.sparkwitch.roles.killer.blackraven.crow;

import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.index.WatheItems;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Which vanilla {@code damage()} calls on the crow count as killing the Black Raven (plan §2.5: only what would kill a
 * player). In Wathe a playing player never loses health to vanilla damage; players die only through
 * {@code GameFunctions.killPlayer}. So the crow ignores everything except:
 * <ul>
 *   <li>a player melee with the Wathe bat (the one melee that kills a player): {@code wathe:bat_hit};</li>
 *   <li>an explosion of at least {@link BlackRavenCrowRules#PLAYER_LETHAL_DAMAGE}: {@code wathe:grenade}, credited to
 *   the explosion's player attacker.</li>
 * </ul>
 * Wathe's own weapons (revolver, knife, grenade, ...) never call {@code damage()} on a non-player; crow-hits wires them
 * to {@link BlackRavenCrowSessions#killOwnerVia} directly.
 * 乌鸦上哪些原版 {@code damage()} 调用算作杀死黑羽鸦（计划 §2.5：只有会杀死玩家的）。在 Wathe 中，参与对局的玩家从不因原版伤害掉血，
 * 只会经 {@code GameFunctions.killPlayer} 死亡。因此乌鸦只认：手持 Wathe 球棒的玩家近战（唯一能杀死玩家的近战）→
 * {@code wathe:bat_hit}；不低于 {@link BlackRavenCrowRules#PLAYER_LETHAL_DAMAGE} 的爆炸 → {@code wathe:grenade}，记在爆炸的玩家
 * 攻击者名下。Wathe 自身武器（左轮、刀、手雷等）从不对非玩家调用 {@code damage()}；由 crow-hits 直接接到
 * {@link BlackRavenCrowSessions#killOwnerVia}。
 */
public final class BlackRavenCrowDamage {
    private BlackRavenCrowDamage() {
    }

    /** Killer credit and Wathe death reason of a lethal hit. / 致命命中的击杀归属与 Wathe 死因。 */
    public record LethalHit(@Nullable ServerPlayerEntity killer, Identifier deathReason) {
    }

    /** The lethal reading of a vanilla hit, or null when the crow ignores it. / 原版命中的致命解读；乌鸦忽略时为 null。 */
    public static @Nullable LethalHit lethalHit(DamageSource source, float amount) {
        if (source == null) {
            return null;
        }
        ServerPlayerEntity attacker = source.getAttacker() instanceof ServerPlayerEntity player ? player : null;
        boolean playerMelee = attacker != null && source.isOf(DamageTypes.PLAYER_ATTACK)
                && source.getSource() == attacker;
        Identifier reason = deathReason(playerMelee,
                playerMelee && attacker.getMainHandStack().isOf(WatheItems.BAT),
                source.isIn(DamageTypeTags.IS_EXPLOSION), amount);
        return reason == null ? null : new LethalHit(attacker, reason);
    }

    /** Pure core of {@link #lethalHit}. / {@link #lethalHit} 的纯逻辑核心。 */
    public static @Nullable Identifier deathReason(boolean playerMelee, boolean holdsBat, boolean explosion,
                                                   float amount) {
        if (playerMelee) {
            return holdsBat ? GameConstants.DeathReasons.BAT : null;
        }
        if (explosion && amount >= BlackRavenCrowRules.PLAYER_LETHAL_DAMAGE) {
            return GameConstants.DeathReasons.GRENADE;
        }
        return null;
    }
}
