package dev.caecorthus.sparkwitch.roles.neutral.insider;

import dev.doctor4t.wathe.api.event.DoorInteraction;
import net.minecraft.entity.player.PlayerEntity;
import org.agmas.noellesroles.ModItems;

/**
 * SparkWitch-owned neutral master key doors for the Insider, so they work without SparkStrength (D6). NoellesRoles'
 * own listener returns PASS for the Insider (its key role set is a private immutable set), except DENY while the key
 * cools down, which agrees with this rule.
 * Runs on both logical sides like NoellesRoles' and SparkStrength's listeners: Wathe raises DoorInteraction from
 * {@code SmallDoorBlock#onUse} on the client too, so the client predicts the same ALLOW/DENY and key cooldown, and the
 * server's cooldown write is synced back to the owner.
 * 由 SparkWitch 自有的内应中立万能钥匙开门逻辑，不装 SparkStrength 也能用（D6）。NoellesRoles 自己的监听对内应返回
 * PASS（其钥匙身份集合是私有的不可变集合），仅在钥匙冷却时返回 DENY，与本规则一致。
 * 与 NoellesRoles、SparkStrength 的监听一样在两个逻辑端运行：Wathe 在客户端的 {@code SmallDoorBlock#onUse} 中同样触发
 * DoorInteraction，因此客户端会预测相同的 ALLOW/DENY 与钥匙冷却，服务端写入的冷却再同步给持有者。
 */
public final class InsiderDoorService {
    private static boolean registered;

    private InsiderDoorService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        DoorInteraction.EVENT.register(InsiderDoorService::doorInteraction);
    }

    private static DoorInteraction.DoorInteractionResult doorInteraction(DoorInteraction.DoorInteractionContext context) {
        PlayerEntity player = context.getPlayer();
        DoorInteraction.DoorInteractionResult result = InsiderDoorRules.masterKeyDoorResult(
                // handItem is Wathe's copy of the main-hand stack. / handItem 是 Wathe 复制的主手物品栈。
                context.getHandItem().isOf(ModItems.NEUTRAL_MASTER_KEY),
                InsiderParticipation.isInsider(player),
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
