package dev.caecorthus.sparkwitch.roles.neutral.insider;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.ShouldPunishGunShooter;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.agmas.noellesroles.ModItems;
import org.jetbrains.annotations.Nullable;

/**
 * Insider equipment: the neutral master key granted at round start (C6), its key doors (D6, {@link InsiderDoorService})
 * and the Corrupt Cop's revolver punishment exemption (D5). The key and any bought items are never removed when the
 * role changes, like every other SparkWitch role.
 * 内应装备：开局发放的中立万能钥匙（C6）、钥匙开门（D6，{@link InsiderDoorService}），以及与黑警相同的开枪惩罚豁免（D5）。
 * 与其他 SparkWitch 职业一致，身份变化时从不收回钥匙和已购买的物品。
 */
public final class InsiderEquipmentService {
    private static boolean registered;

    private InsiderEquipmentService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        // Final roles only: fires after every RoleAssigned (including SparkTraits Conscience compensation), while the
        // round is still STARTING. NoellesRoles' RoleAssigned kit never covers the Insider.
        // 仅按最终身份发放：在所有 RoleAssigned（含 SparkTraits 良心补偿）之后、对局仍处于 STARTING 时触发。
        // NoellesRoles 在 RoleAssigned 中发放的装备不包括内应。
        GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                grantMasterKeys(serverWorld, game);
            }
        });
        InsiderDoorService.register();
        ShouldPunishGunShooter.EVENT.register(InsiderEquipmentService::gunPunishment);
    }

    static void grantMasterKeys(ServerWorld world, GameWorldComponent game) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            // The game is still STARTING here, so the running-state helper would reject every player.
            // 此时对局仍处于 STARTING，运行态判定会拒绝所有玩家。
            PlayerInventory inventory = player.getInventory();
            if (receivesMasterKey(game.hasAnyRole(player), game.isPlayerDead(player.getUuid()), game.getRole(player),
                    inventory.contains(stack -> stack.isOf(ModItems.NEUTRAL_MASTER_KEY)))) {
                // Drops at the player's feet when the inventory is full, so the key is never lost.
                // 背包已满时掉落在玩家脚下，钥匙绝不会丢失。
                inventory.offerOrDrop(new ItemStack(ModItems.NEUTRAL_MASTER_KEY));
                inventory.markDirty();
                player.currentScreenHandler.sendContentUpdates();
            }
        }
    }

    /** One key per living final Insider. / 每名存活的最终内应只发一把钥匙。 */
    static boolean receivesMasterKey(boolean hasRole, boolean dead, @Nullable Role finalRole, boolean alreadyHoldsKey) {
        return hasRole && !dead && InsiderParticipation.isInsiderRole(finalRole) && !alreadyHoldsKey;
    }

    /**
     * Same shape as NoellesRoles' Corrupt Cop listener ({@code Noellesroles.java:1335-1341}): an Insider who shoots an
     * innocent keeps the revolver and may pick guns up again. Wathe's event is first-non-null-wins.
     * 与 NoellesRoles 黑警监听（{@code Noellesroles.java:1335-1341}）形状相同：内应射中无辜者不会被没收左轮，
     * 也不会被禁止再拾取枪械。Wathe 该事件取第一个非 null 结果。
     */
    private static @Nullable ShouldPunishGunShooter.PunishResult gunPunishment(PlayerEntity shooter, PlayerEntity victim) {
        return InsiderParticipation.isInsider(shooter) ? ShouldPunishGunShooter.PunishResult.cancel() : null;
    }
}
