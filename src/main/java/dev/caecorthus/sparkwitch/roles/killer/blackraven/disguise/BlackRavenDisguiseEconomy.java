package dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise;

import dev.caecorthus.sparkwitch.compat.SparkTraitsBlackRavenBridge;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenRules;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.adapter.BlackRavenDisguiseAdapter;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.adapter.BlackRavenDisguiseAdapters;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.adapter.DisguiseTaskReward;
import dev.doctor4t.wathe.api.event.TaskComplete;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Disguise task income (paid into the live disguise wallet, mirroring the real payer) and the money
 * visibility answer for the client CanSeeMoney listener. Also the single routing point for killer income
 * earned while disguised (amendment W): the wallet mixins credit it to the stashed Black Raven wallet here,
 * never to the live disguise wallet.
 * 伪装任务收入（按真实付款方规则发放到当前伪装钱包）以及客户端 CanSeeMoney 监听器使用的金钱可见性。
 * 同时是伪装期间杀手收入的唯一转入点（修订 W）：钱包 mixin 在此将其记入存档中的黑羽鸦钱包，
 * 从不进入当前伪装钱包。
 */
public final class BlackRavenDisguiseEconomy {
    static final String SPARKSTRENGTH_MOD_ID = "sparkstrength";
    /** SparkStrength killer-team purse service; reflected only for recordIncome(PlayerEntity, long). / 杀手团队资金服务。 */
    static final String SPARKSTRENGTH_PURSE_CLASS = "annina.sparkstrength.role.economy.KillerTeamEconomyService";

    /** Server thread only: set while a routed killer receipt is reported to the purse. / 仅服务端线程：转入收入上报团队资金时置位。 */
    private static final ThreadLocal<Boolean> ROUTED_PURSE_RECEIPT = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static volatile @Nullable Method purseRecordIncome;
    private static volatile boolean purseLookupFailed;
    private static boolean registered;

    private BlackRavenDisguiseEconomy() {
    }

    /**
     * Owner view of a Wathe shop component, added by PlayerShopComponentBlackRavenDisguiseMixin so wallet
     * mixins can tell whose balance a Wathe call credits without capturing method locals.
     * Wathe 商店组件的持有者视图，由 PlayerShopComponentBlackRavenDisguiseMixin 注入，使钱包 mixin
     * 无需捕获局部变量即可判断 Wathe 调用入账的是谁的余额。
     */
    public interface ShopOwnerAccess {
        @Nullable PlayerEntity sparkwitch$blackRavenShopOwner();
    }

    /** Registers the single TaskComplete listener. / 注册唯一的 TaskComplete 监听器。 */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        TaskComplete.EVENT.register((player, task) -> onTaskComplete(player));
    }

    /** TRUE/FALSE for a disguised player; null means "no opinion" (not disguised). / 伪装玩家返回 TRUE/FALSE；未伪装返回 null。 */
    public static @Nullable Boolean moneyVisibleFor(PlayerEntity player) {
        Identifier acting = BlackRavenActingRole.actingRoleId(player);
        if (acting == null) {
            return null;
        }
        // Missing adapter fails closed: the disguise shows no money. / 缺少适配器时失败关闭：伪装不显示金钱。
        BlackRavenDisguiseAdapter adapter = BlackRavenDisguiseAdapters.get(acting);
        return adapter != null && adapter.moneyVisible();
    }

    /**
     * Task money the disguise earns on top of what other listeners already pay the raw Black Raven.
     * SparkTraits pays +50 to a Conscience/Impostor holder whose raw role has no native task money, and
     * black_raven has none, so the NoellesRoles and SparkStrength mirrors skip it to avoid double pay.
     * An unknown trait answer (null) pays nothing for those two policies.
     * 伪装在其他监听器已向真实黑羽鸦支付之外可获得的任务金钱。SparkTraits 会向真实职业无原生任务金钱的
     * 良知/内鬼持有者支付 +50，而 black_raven 没有原生任务金钱，因此诺艾尔与 SparkStrength 镜像跳过以免重复。
     * 词条结果未知（null）时这两种策略不发放。
     */
    static int taskRewardAmount(
            @Nullable DisguiseTaskReward policy,
            @Nullable Boolean conscience,
            @Nullable Boolean impostor,
            boolean sparkStrengthLoaded
    ) {
        if (policy == null) {
            return 0;
        }
        boolean traitsPayNothing = Boolean.FALSE.equals(conscience) && Boolean.FALSE.equals(impostor);
        return switch (policy) {
            case NOELLES_NATIVE -> traitsPayNothing ? BlackRavenDisguiseRules.TASK_REWARD : 0;
            case SPARKSTRENGTH_GOOD_ROLE -> sparkStrengthLoaded && traitsPayNothing
                    ? BlackRavenDisguiseRules.TASK_REWARD : 0;
            case SPARKWITCH -> BlackRavenDisguiseRules.TASK_REWARD;
            case NONE -> 0;
        };
    }

    // Killer income routing (amendment W). / 杀手收入转入（修订 W）。

    /** Owner of the shop when its killer income must go to the stashed Raven wallet, else null. / 需转入黑羽鸦钱包时返回持有者。 */
    public static @Nullable PlayerEntity killerIncomeOwner(@Nullable PlayerShopComponent shop) {
        PlayerEntity owner = shopOwner(shop);
        return owner != null && BlackRavenDisguiseService.routesKillerIncome(owner) ? owner : null;
    }

    /**
     * Credits a Wathe killer receipt to the stashed Raven wallet; false leaves the caller's original credit
     * in place. {@code purseReceipt} mirrors SparkStrength's rule that only action rewards (not Wathe's timed
     * passive income) feed the killer-team purse.
     * 将 Wathe 杀手收入记入存档黑羽鸦钱包；返回 false 时调用方保留原始入账。purseReceipt 对应 SparkStrength
     * 规则：只有行动奖励（非 Wathe 定时被动收入）计入杀手团队资金。
     */
    public static boolean creditKillerIncome(@Nullable PlayerShopComponent shop, int amount, boolean purseReceipt) {
        PlayerEntity owner = killerIncomeOwner(shop);
        if (owner == null) {
            return false;
        }
        int before = BlackRavenDisguiseService.ravenWalletBalance(owner);
        BlackRavenDisguiseService.creditRavenWallet(owner, amount);
        long received = (long) BlackRavenDisguiseService.ravenWalletBalance(owner) - before;
        if (purseReceipt && received > 0) {
            recordPurseReceipt(owner, received);
        }
        return true;
    }

    /**
     * SparkStrength purse gate: income landing in a disguise wallet is not killer income, so only a routed
     * Raven-wallet receipt may contribute while disguised.
     * SparkStrength 团队资金门禁：进入伪装钱包的收入不是杀手收入，伪装期间只有转入黑羽鸦钱包的收入可计入。
     */
    public static boolean acceptsKillerTeamContribution(@Nullable PlayerEntity player) {
        return player == null
                || ROUTED_PURSE_RECEIPT.get()
                || !BlackRavenDisguiseService.routesKillerIncome(player);
    }

    private static void onTaskComplete(ServerPlayerEntity player) {
        if (!GameFunctions.isPlayerPlayingAndAlive(player)
                || !BlackRavenRules.isBlackRaven(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))) {
            return;
        }
        Identifier acting = BlackRavenActingRole.actingRoleId(player);
        BlackRavenDisguiseAdapter adapter = BlackRavenDisguiseAdapters.get(acting);
        if (adapter == null) {
            return;
        }
        DisguiseTaskReward policy = adapter.taskReward();
        int amount = switch (policy) {
            case NOELLES_NATIVE, SPARKSTRENGTH_GOOD_ROLE -> taskRewardAmount(
                    policy,
                    SparkTraitsBlackRavenBridge.isConscienceActive(player),
                    SparkTraitsBlackRavenBridge.isImpostor(player),
                    FabricLoader.getInstance().isModLoaded(SPARKSTRENGTH_MOD_ID)
            );
            case SPARKWITCH, NONE -> taskRewardAmount(policy, Boolean.FALSE, Boolean.FALSE, false);
        };
        if (amount > 0) {
            // Live wallet = the disguise identity's own wallet (amendment W). / 当前钱包即伪装身份自己的钱包。
            PlayerShopComponent.KEY.get(player).addToBalance(amount);
        }
    }

    private static @Nullable PlayerEntity shopOwner(@Nullable PlayerShopComponent shop) {
        return (Object) shop instanceof ShopOwnerAccess access ? access.sparkwitch$blackRavenShopOwner() : null;
    }

    private static void recordPurseReceipt(PlayerEntity owner, long received) {
        if (!FabricLoader.getInstance().isModLoaded(SPARKSTRENGTH_MOD_ID)) {
            return;
        }
        Method method = purseRecordIncome();
        if (method == null) {
            return;
        }
        boolean outer = !ROUTED_PURSE_RECEIPT.get();
        ROUTED_PURSE_RECEIPT.set(Boolean.TRUE);
        try {
            method.invoke(null, owner, received);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            // Optional seam: a failed purse report never affects the Raven wallet. / 可选接缝：上报失败不影响黑羽鸦钱包。
        } finally {
            if (outer) {
                ROUTED_PURSE_RECEIPT.remove();
            }
        }
    }

    private static @Nullable Method purseRecordIncome() {
        Method cached = purseRecordIncome;
        if (cached != null || purseLookupFailed) {
            return cached;
        }
        synchronized (BlackRavenDisguiseEconomy.class) {
            if (purseRecordIncome == null && !purseLookupFailed) {
                try {
                    purseRecordIncome = Class.forName(SPARKSTRENGTH_PURSE_CLASS, false,
                                    BlackRavenDisguiseEconomy.class.getClassLoader())
                            .getMethod("recordIncome", PlayerEntity.class, long.class);
                } catch (ReflectiveOperationException | LinkageError | SecurityException ignored) {
                    purseLookupFailed = true;
                }
            }
            return purseRecordIncome;
        }
    }
}
