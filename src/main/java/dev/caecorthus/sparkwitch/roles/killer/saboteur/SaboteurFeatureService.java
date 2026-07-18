package dev.caecorthus.sparkwitch.roles.killer.saboteur;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.TaskComplete;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Registers Saboteur-owned shop and post-promotion task income.
 * 注册破坏者自有商店与晋升后的任务收入。
 */
public final class SaboteurFeatureService {
    private static boolean registered;

    private SaboteurFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        SaboteurLightOutageService.register();
        SaboteurShopService.register();
        TaskComplete.EVENT.register((player, taskType) -> rewardTask(player));
    }

    /**
     * Initializes role-owned cooldown and stock after the promotion transition that bypasses RoleAssigned.
     * 在绕过 RoleAssigned 的晋升切换后初始化职业自有冷却与库存。
     */
    public static void initializePromotion(ServerPlayerEntity player) {
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (!SaboteurRules.isSaboteur(role) || !SaboteurRules.isActivePromotedSaboteur(player)) {
            return;
        }
        SaboteurPlayerComponent.KEY.get(player).setCooldownTicks(SaboteurRules.INITIAL_COOLDOWN_TICKS);
        SaboteurShopService.initializePromotionStock(player);
    }

    private static void rewardTask(ServerPlayerEntity player) {
        // Promotion is deferred until the task event finishes, so the third task cannot be paid retroactively.
        // 晋升会延迟到任务事件结束后，因此触发晋升的第三个任务不会被追溯发钱。
        boolean activePromotedSaboteur = SaboteurRules.isActivePromotedSaboteur(player);
        if (SaboteurRules.shouldRewardTask(activePromotedSaboteur)) {
            PlayerShopComponent.KEY.get(player).addToBalance(SaboteurRules.TASK_REWARD);
        }
    }
}
