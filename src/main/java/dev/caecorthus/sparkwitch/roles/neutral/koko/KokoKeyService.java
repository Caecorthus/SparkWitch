package dev.caecorthus.sparkwitch.roles.neutral.koko;

import dev.caecorthus.sparkwitch.roles.neutral.insider.InsiderDoorRules;
import dev.caecorthus.sparkwitch.roles.neutral.insider.InsiderRules;
import dev.doctor4t.wathe.api.event.DoorInteraction;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.agmas.noellesroles.ModItems;

/**
 * Koko's only starting item: one NoellesRoles neutral master key ({@code noellesroles:neutral_master_key}), and the
 * doors it opens for her. Both follow the Insider:
 * <ul>
 *   <li>The key is handed out in {@code ON_FINISH_INITIALIZE}, on final roles after every {@code RoleAssigned}
 *   listener, one per living Koko that does not already hold one; a full inventory drops it at her feet.</li>
 *   <li>NoellesRoles' own key listener only serves its three key roles, and SparkStrength's any-neutral rule covers
 *   train doors only when that mod is installed. This SparkWitch-owned {@code DoorInteraction} listener gives her
 *   train doors and key-locked room doors through {@link InsiderDoorRules}, whose answers never conflict with the
 *   other two listeners, and writes the key's single nominal cooldown ({@code InsiderRules.MASTER_KEY_COOLDOWN_TICKS},
 *   10 s). It runs on both logical sides, so the client predicts the same answer and cooldown.</li>
 * </ul>
 * The key is never taken back when her role changes, like every other SparkWitch kit item.
 * 可可唯一的开局物品：一把 NoellesRoles 的中立万能钥匙（{@code noellesroles:neutral_master_key}），以及它为她打开的门。两者都
 * 沿用内应的做法：钥匙在 {@code ON_FINISH_INITIALIZE} 按最终身份发放（晚于所有 {@code RoleAssigned} 监听器），每名存活且尚未
 * 持有钥匙的可可一把，背包已满时掉在脚下。NoellesRoles 自己的钥匙监听只服务它的三个钥匙职业，SparkStrength 的“任意中立”规则
 * 只在装有该模组时覆盖列车门；这个由 SparkWitch 自有的 {@code DoorInteraction} 监听让她能开列车门与上锁的房间门，并带有通常的
 * 10 秒钥匙冷却（规则见 {@link InsiderDoorRules}，其结果与另外两个监听从不冲突；冷却写入该物品唯一的标准值
 * {@code InsiderRules.MASTER_KEY_COOLDOWN_TICKS}）。它在两个逻辑端运行，因此客户端会预测相同的结果与
 * 冷却。与其他 SparkWitch 开局物品一样，身份变化时从不收回钥匙。
 */
public final class KokoKeyService {
    private static boolean registered;

    private KokoKeyService() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                    grantMasterKey(player, game);
                }
            }
        });
        DoorInteraction.EVENT.register(KokoKeyService::doorInteraction);
    }

    private static void grantMasterKey(ServerPlayerEntity player, GameWorldComponent game) {
        PlayerInventory inventory = player.getInventory();
        // The game is still STARTING here, so liveness is read from Wathe's dead list, not the running-state helper.
        // 此时对局仍处于 STARTING，因此存活状态取自 Wathe 的死亡名单，而不是运行态判定。
        if (KokoRules.receivesMasterKey(game.hasAnyRole(player), game.isPlayerDead(player.getUuid()),
                game.getRole(player), inventory.contains(stack -> stack.isOf(ModItems.NEUTRAL_MASTER_KEY)))) {
            inventory.offerOrDrop(new ItemStack(ModItems.NEUTRAL_MASTER_KEY));
            inventory.markDirty();
            player.currentScreenHandler.sendContentUpdates();
        }
    }

    private static DoorInteraction.DoorInteractionResult doorInteraction(
            DoorInteraction.DoorInteractionContext context) {
        PlayerEntity player = context.getPlayer();
        DoorInteraction.DoorInteractionResult result = InsiderDoorRules.masterKeyDoorResult(
                // handItem is Wathe's copy of the main-hand stack. / handItem 是 Wathe 复制的主手物品栈。
                context.getHandItem().isOf(ModItems.NEUTRAL_MASTER_KEY),
                KokoParticipation.isKoko(player),
                context.getDoorType(),
                context.isBlasted(),
                context.isJammed(),
                context.isOpen(),
                context.requiresKey(),
                player.getItemCooldownManager().isCoolingDown(ModItems.NEUTRAL_MASTER_KEY)
        );
        if (result == DoorInteraction.DoorInteractionResult.ALLOW) {
            player.getItemCooldownManager().set(ModItems.NEUTRAL_MASTER_KEY, InsiderRules.MASTER_KEY_COOLDOWN_TICKS);
        }
        return result;
    }
}
