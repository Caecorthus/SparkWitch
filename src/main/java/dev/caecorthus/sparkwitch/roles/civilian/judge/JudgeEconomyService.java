package dev.caecorthus.sparkwitch.roles.civilian.judge;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.compat.OptionalStaticMethod;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** Judge income has one owner; its police-network registration (which earns it a free SparkStrength tablet) must
 * never grant a second task reward.
 * 法官收入只有一个归属；其警察网络登记（因此获得 SparkStrength 免费平板）不得产生第二份任务收益。 */
public final class JudgeEconomyService {
    private static final Identifier IMPOSTOR = Identifier.of("sparktraits", "impostor");
    private static final OptionalStaticMethod HAS_ACTIVE_TRAIT = OptionalStaticMethod.of("sparktraits",
                    "dev.caecorthus.sparktraits.api.SparkTraitsApi", "hasActiveTrait",
                    PlayerEntity.class, Identifier.class)
            .initializingClass()
            .warnOnce(error -> SparkWitch.LOGGER.warn(
                    "Judge task income cannot verify the optional Impostor trait; skipping the reward", error));

    private JudgeEconomyService() {
    }

    public static void initialize(ServerPlayerEntity player) {
        PlayerShopComponent.KEY.get(player).setBalance(JudgeRules.INITIAL_MONEY);
    }

    public static void onTaskComplete(ServerPlayerEntity player) {
        if (JudgeRuntime.canJudge(player) && receivesOrdinaryTaskMoney(player)) {
            PlayerShopComponent.KEY.get(player).addToBalance(JudgeRules.TASK_MONEY_REWARD);
        }
    }

    /** Pays only on an exact FALSE: an absent SparkTraits counts as FALSE, an unknown answer (null) never pays.
     * 仅在确切为 FALSE 时发放：未安装 SparkTraits 视为 FALSE，未知答案（null）从不发放。 */
    private static boolean receivesOrdinaryTaskMoney(PlayerEntity player) {
        return Boolean.FALSE.equals(HAS_ACTIVE_TRAIT.callNullableBoolean(Boolean.FALSE, null, null, player, IMPOSTOR));
    }
}
