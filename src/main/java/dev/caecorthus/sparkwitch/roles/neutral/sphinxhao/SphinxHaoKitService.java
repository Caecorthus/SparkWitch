package dev.caecorthus.sparkwitch.roles.neutral.sphinxhao;

import dev.caecorthus.sparkwitch.roles.neutral.insider.InsiderDoorRules;
import dev.caecorthus.sparkwitch.roles.neutral.insider.InsiderRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.DoorInteraction;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.ShouldPunishGunShooter;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.agmas.noellesroles.ModItems;
import org.jetbrains.annotations.Nullable;

/**
 * Sphinx Hao's starting kit (owner Q3), the doors its key opens and its gun-punishment exemption, on the Insider /
 * 泽渡可可 model:
 * <ul>
 *   <li>In {@code ON_FINISH_INITIALIZE}, on final roles after every {@code RoleAssigned} listener, each living Sphinx
 *   Hao gets one NoellesRoles neutral master key and one EMPTY "手"枪 when it does not already hold one; a full
 *   inventory drops them at its feet. The gun belongs to the GUN work package and is looked up by registry id
 *   ({@link SphinxHaoRules#HAND_GUN_ID}); its default stack is the unloaded gun. Without it registered only the key is
 *   handed out.</li>
 *   <li>A SparkWitch-owned {@code DoorInteraction} listener opens train doors and key-locked room doors through
 *   {@link InsiderDoorRules} (answers that never conflict with NoellesRoles' or SparkStrength's key listeners) and
 *   writes the key's single nominal cooldown ({@link InsiderRules#MASTER_KEY_COOLDOWN_TICKS}, 10 s). It runs on both
 *   logical sides, so the client predicts the same answer and cooldown.</li>
 * </ul>
 * Kit items are never taken back when the role changes, like every other SparkWitch kit (the gun's own bound/delete
 * lifecycle is the GUN package's).
 * 狮身豪的开局物品（所有者 Q3）及其钥匙可开的门，均沿用内应 / 泽渡可可模式：在 {@code ON_FINISH_INITIALIZE} 按最终身份
 * （晚于所有 {@code RoleAssigned} 监听器）为每名存活且尚未持有的狮身豪发放一把 NoellesRoles 中立万能钥匙与一把空的“手”枪，
 * 背包已满时掉在脚下。手枪属于 GUN 工作包，按注册 id（{@link SphinxHaoRules#HAND_GUN_ID}）查找，其默认物品栈即未装填的手枪；
 * 未注册时只发钥匙。由 SparkWitch 自有的 {@code DoorInteraction} 监听经 {@link InsiderDoorRules} 打开列车门与上锁的房间门
 * （与 NoellesRoles、SparkStrength 的钥匙监听从不冲突），并写入该钥匙唯一的标准冷却
 * （{@link InsiderRules#MASTER_KEY_COOLDOWN_TICKS}，10 秒）；它在两个逻辑端运行，客户端会预测相同的结果与冷却。与其他
 * SparkWitch 开局物品一样，身份变化时从不收回（手枪自身的绑定/删除生命周期属于 GUN 包）。
 */
public final class SphinxHaoKitService {
    private static boolean registered;

    private SphinxHaoKitService() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                    grantKit(player, game);
                }
            }
        });
        DoorInteraction.EVENT.register(SphinxHaoKitService::doorInteraction);
        // Team Jiahao parity with the Insider (D5): a Sphinx Hao firing a Wathe gun it picked up is never punished for
        // an innocent victim. Its own hand gun is not a Wathe gun and never reaches this event.
        // 与内应保持嘉豪阵营一致（D5）：狮身豪用捡到的 Wathe 枪械击中无辜者时不受惩罚。它自己的手枪不是 Wathe 枪械，从不触发此事件。
        ShouldPunishGunShooter.EVENT.register((shooter, victim) -> SphinxHaoParticipation.isSphinxHao(shooter)
                ? ShouldPunishGunShooter.PunishResult.cancel() : null);
    }

    private static void grantKit(ServerPlayerEntity player, GameWorldComponent game) {
        // The game is still STARTING here, so liveness is read from Wathe's dead list, not the running-state helper.
        // 此时对局仍处于 STARTING，因此存活状态取自 Wathe 的死亡名单，而不是运行态判定。
        boolean hasRole = game.hasAnyRole(player);
        boolean dead = game.isPlayerDead(player.getUuid());
        Role role = game.getRole(player);
        PlayerInventory inventory = player.getInventory();
        boolean changed = false;
        if (receivesKitItem(hasRole, dead, role, inventory.contains(stack -> stack.isOf(ModItems.NEUTRAL_MASTER_KEY)))) {
            inventory.offerOrDrop(new ItemStack(ModItems.NEUTRAL_MASTER_KEY));
            changed = true;
        }
        Item gun = handGun();
        if (gun != null && receivesKitItem(hasRole, dead, role, inventory.contains(stack -> stack.isOf(gun)))) {
            inventory.offerOrDrop(new ItemStack(gun));
            changed = true;
        }
        if (changed) {
            inventory.markDirty();
            player.currentScreenHandler.sendContentUpdates();
        }
    }

    /** One of each kit item per living final Sphinx Hao. / 每名存活的最终狮身豪每种开局物品一件。 */
    static boolean receivesKitItem(boolean hasRole, boolean dead, @Nullable Role finalRole, boolean alreadyHolds) {
        return hasRole && !dead && SphinxHaoRules.isSphinxHao(finalRole) && !alreadyHolds;
    }

    /** The GUN package's hand gun, or null while it is not registered. / GUN 包的手枪；未注册时为 null。 */
    static @Nullable Item handGun() {
        Item item = Registries.ITEM.get(SphinxHaoRules.HAND_GUN_ID);
        return item == Items.AIR ? null : item;
    }

    private static DoorInteraction.DoorInteractionResult doorInteraction(
            DoorInteraction.DoorInteractionContext context) {
        PlayerEntity player = context.getPlayer();
        DoorInteraction.DoorInteractionResult result = InsiderDoorRules.masterKeyDoorResult(
                // handItem is Wathe's copy of the main-hand stack. / handItem 是 Wathe 复制的主手物品栈。
                context.getHandItem().isOf(ModItems.NEUTRAL_MASTER_KEY),
                SphinxHaoParticipation.isSphinxHao(player),
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
