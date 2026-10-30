package dev.caecorthus.sparkwitch.roles.civilian.knight;

import dev.caecorthus.sparkwitch.SparkWitchDeathReasons;
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
 * Knight kill economy, push-off rule and derringer wrong-target rule; "good" is always
 * {@link KnightRules#isGoodForKnight}.
 * <ul>
 *   <li>Reward: +100 coins for killing a non-good player (Impostor included).</li>
 *   <li>Push-off: a train-fall kill credited to the Knight whose victim is good kills the Knight (Cerebellum).</li>
 *   <li>Reload: killing a good player, with any weapon, never refills a derringer.</li>
 *   <li>Derringer: shooting a good player with it costs the whole sanity bar (100 points); a revolver misfire keeps
 *   only Wathe's own punishment.</li>
 * </ul>
 * 骑士击杀经济、推下车规则与德林加误杀规则；“好人”一律以 {@link KnightRules#isGoodForKnight} 判断。
 * <ul>
 *   <li>奖励：击杀非好人（含内鬼）+100 金币。</li>
 *   <li>推下车：记在骑士名下的坠车击杀若受害者是好人，骑士死于“小脑”。</li>
 *   <li>装填：以任何武器击杀好人都不会补充德林加。</li>
 *   <li>德林加：用它击中好人扣除整条理智（100 点）；左轮误杀只保留 Wathe 自身的惩罚。</li>
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
        // AFTER runs once the victim is dead; SparkTraits' death snapshot keeps Impostor/Conscience readable.
        // AFTER 在受害者死亡后执行；SparkTraits 的死亡快照使内鬼/善良词条仍可读取。
        boolean good = KnightRules.isGoodForKnight(victim, game);
        if (good && GameConstants.DeathReasons.FELL_OUT_OF_TRAIN.equals(deathReason)) {
            // Non-forced, like Wathe's own SHOT_INNOCENT kill. / 非强制击杀，与 Wathe 自身的 SHOT_INNOCENT 一致。
            GameFunctions.killPlayer(killer, true, null, SparkWitchDeathReasons.CEREBELLUM);
            return;
        }
        if (!good) {
            PlayerShopComponent.KEY.get(killer).addToBalance(KnightRules.KILL_REWARD);
        }
    }

    /**
     * Seam for {@code KnightDerringerReloadMixin}: whether Wathe's kill reload skips every derringer of
     * {@code killer}, i.e. a Knight killed a good player with any weapon. Read inside {@code killPlayer} after
     * {@code markPlayerDead}, which keeps the raw role, and before KillPlayer.AFTER clears SparkTraits traits.
     * Self-kills keep Wathe's reload.
     * {@code KnightDerringerReloadMixin} 的接缝：Wathe 的击杀装填是否跳过 {@code killer} 的所有德林加，即骑士以任何武器击杀了
     * 好人。在 {@code killPlayer} 内 {@code markPlayerDead} 之后读取（原始职业不变），且早于 KillPlayer.AFTER 清除
     * SparkTraits 词条。自杀保留 Wathe 的装填。
     */
    public static boolean blocksKillReload(ServerPlayerEntity victim, @Nullable ServerPlayerEntity killer) {
        if (killer == null || killer == victim) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(killer.getWorld());
        return KnightRules.isKnight(game.getRole(killer)) && KnightRules.isGoodForKnight(victim, game);
    }

    /**
     * Whether a gun shot is a Knight's derringer shot at a good player. {@code weapon} is Wathe's {@code mainHandStack}
     * of the shot; call it before the target kill, while the victim is alive.
     * 该枪击是否为骑士用德林加射击好人。{@code weapon} 为 Wathe 本次射击的 {@code mainHandStack}；须在目标击杀之前、
     * 受害者仍存活时调用。
     */
    public static boolean isWrongDerringerShot(ServerPlayerEntity shooter, ServerPlayerEntity victim, ItemStack weapon) {
        if (!weapon.isOf(WatheItems.DERRINGER)) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(shooter.getWorld());
        return KnightRules.isKnight(game.getRole(shooter)) && KnightRules.isGoodForKnight(victim, game);
    }

    /**
     * After the target kill of a wrong derringer shot. A derringer the shot spent stays spent even if something inside
     * the kill call refilled it (SparkTraits' fake-death replenish for Depression / Last Stand victims), so a fake
     * death keeps no bullet and looks like a real good kill. The sanity cost applies only when the victim really died.
     * 德林加误射的目标击杀之后执行。本次射击已用掉的德林加保持已使用，即使击杀调用内部将其补充（SparkTraits 对抑郁/背水一战
     * 受害者的假死补弹），使假死不返还子弹、与真实误杀无异。理智代价仅在受害者确实死亡时扣除。
     */
    public static void afterWrongDerringerShot(ServerPlayerEntity shooter, ServerPlayerEntity victim,
                                               ItemStack derringer, boolean spentBeforeKill) {
        if (spentBeforeKill && !Boolean.TRUE.equals(derringer.get(WatheDataComponentTypes.USED))) {
            derringer.set(WatheDataComponentTypes.USED, true);
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(shooter.getWorld());
        if (!game.isPlayerDead(victim.getUuid())) {
            return;
        }
        PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(shooter);
        // Wathe clamps mood to -1..1 itself; never floor at 0, or a low-sanity Knight would gain sanity.
        // Wathe 自行把理智限制在 -1..1；不得以 0 为下限，否则低理智骑士反而会回理智。
        mood.setMood(mood.getMood() - KnightRules.WRONG_KILL_MOOD_COST);
    }
}
