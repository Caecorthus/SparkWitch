package dev.caecorthus.sparkwitch.roles.civilian.judge;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/** Judge income has one owner; tablet eligibility must never grant a second task reward.
 * 法官收入只有一个归属，平板资格不得产生第二份任务收益。 */
public final class JudgeEconomyService {
    private static final Identifier IMPOSTOR = Identifier.of("sparktraits", "impostor");
    private static final AtomicBoolean WARNING_LOGGED = new AtomicBoolean();
    private static volatile boolean traitMethodResolved;
    private static Method hasActiveTrait;

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

    private static boolean receivesOrdinaryTaskMoney(PlayerEntity player) {
        if (!FabricLoader.getInstance().isModLoaded("sparktraits")) {
            return true;
        }
        Method method = traitMethod();
        if (method == null) {
            return false;
        }
        try {
            return Boolean.FALSE.equals(method.invoke(null, player, IMPOSTOR));
        } catch (ReflectiveOperationException | LinkageError error) {
            warn(error);
            return false;
        }
    }

    private static Method traitMethod() {
        if (!traitMethodResolved) {
            synchronized (JudgeEconomyService.class) {
                if (!traitMethodResolved) {
                    try {
                        hasActiveTrait = Class.forName("dev.caecorthus.sparktraits.api.SparkTraitsApi")
                                .getMethod("hasActiveTrait", PlayerEntity.class, Identifier.class);
                    } catch (ReflectiveOperationException | LinkageError error) {
                        warn(error);
                    }
                    traitMethodResolved = true;
                }
            }
        }
        return hasActiveTrait;
    }

    private static void warn(Throwable error) {
        if (WARNING_LOGGED.compareAndSet(false, true)) {
            SparkWitch.LOGGER.warn("Judge task income cannot verify the optional Impostor trait; skipping the reward", error);
        }
    }
}
