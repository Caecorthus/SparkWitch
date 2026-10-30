package dev.caecorthus.sparkwitch.roles.civilian.knight;

import dev.caecorthus.sparkwitch.SparkWitchDeathReasons;
import dev.caecorthus.sparkwitch.compat.KnightTraitsBridge;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.index.WatheDataComponentTypes;
import dev.doctor4t.wathe.index.WatheItems;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Knight kill economy and the derringer's wrong-target rule.
 * <ul>
 *   <li>Reward: +100 coins for killing a non-good player without Conscience, or any Impostor.</li>
 *   <li>Punishment: a derringer kill of an innocent (passenger or police) without Impostor keeps the derringer
 *   spent (undoing Wathe's kill reload) and costs the whole sanity bar (100 points).</li>
 * </ul>
 * 骑士击杀经济与德林加的误杀规则。
 * <ul>
 *   <li>奖励：击杀没有良知词条的非好人，或任意内鬼，+100 金币。</li>
 *   <li>惩罚：德林加击杀没有内鬼词条的乘客/警察类好人时，德林加保持已使用（撤销 Wathe 的击杀装填），并扣除整条理智（100 点）。</li>
 * </ul>
 */
public final class KnightKillService {
    private static boolean registered;

    private KnightKillService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        KillPlayer.AFTER.register(KnightKillService::afterKill);
    }

    static void afterKill(ServerPlayerEntity victim, @Nullable ServerPlayerEntity killer, Identifier deathReason) {
        if (killer == null) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(killer.getWorld());
        if (!KnightRules.isKnight(game.getRole(killer))) {
            return;
        }
        boolean impostor = Boolean.TRUE.equals(KnightTraitsBridge.isImpostorActive(victim));
        boolean conscience = Boolean.TRUE.equals(KnightTraitsBridge.isConscienceActive(victim));
        // Pushing a good player (innocent without Impostor, or any Conscience holder) off the train kills the Knight.
        // 把好人（不带内鬼的平民/警察，或任何带善良词条者）推下列车时，骑士死于“小脑”。
        if (GameConstants.DeathReasons.FELL_OUT_OF_TRAIN.equals(deathReason)
                && (conscience || (game.isInnocent(victim) && !impostor))) {
            GameFunctions.killPlayer(killer, true, null, SparkWitchDeathReasons.CEREBELLUM);
            return;
        }
        if (impostor || (!game.isInnocent(victim) && !conscience)) {
            PlayerShopComponent.KEY.get(killer).addToBalance(KnightRules.KILL_REWARD);
        }
    }

    /** Runs after Wathe's kill handler, i.e. after its derringer reload loop. / 在 Wathe 击杀处理（含德林加装填循环）之后执行。 */
    public static void afterDerringerKill(ServerPlayerEntity killer, ServerPlayerEntity victim) {
        GameWorldComponent game = GameWorldComponent.KEY.get(killer.getWorld());
        if (!KnightRules.isKnight(game.getRole(killer)) || !game.isPlayerDead(victim.getUuid())) {
            return;
        }
        if (!game.isInnocent(victim) || Boolean.TRUE.equals(KnightTraitsBridge.isImpostorActive(victim))) {
            return;
        }
        ItemStack derringer = killer.getMainHandStack();
        if (derringer.isOf(WatheItems.DERRINGER)) {
            derringer.set(WatheDataComponentTypes.USED, true);
        }
        PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(killer);
        mood.setMood(Math.max(0.0F, mood.getMood() - KnightRules.WRONG_KILL_MOOD_COST));
    }
}
