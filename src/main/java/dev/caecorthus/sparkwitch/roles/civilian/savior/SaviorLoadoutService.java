package dev.caecorthus.sparkwitch.roles.civilian.savior;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.util.Scheduler;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Savior round-start loadout: the pistol first, then the lightsaber, into hotbar slots 0 and 1 so the gun stays on the
 * left. A revolver is planted in main-inventory slot 9: Wathe's LimitedInventoryScreen locks slots 9–35, so the player
 * can see but never touch it; it is meant to be part of the death drop. Both visible weapons start on cooldown.
 * 救世主开局装备：先发手枪、再发光剑，放进快捷栏 0/1 槽，保证枪在左边。主背包 9 槽塞一把左轮，Wathe 的
 * LimitedInventoryScreen 锁住 9–35，玩家看得见但碰不到，留作死亡掉落。两把可见武器都带开局冷却。
 */
public final class SaviorLoadoutService {
    private static boolean registered;

    private SaviorLoadoutService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                grantRoundStartLoadouts(serverWorld, game);
            }
        });
    }

    static void grantRoundStartLoadouts(ServerWorld world, GameWorldComponent game) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (receivesLoadout(game.hasAnyRole(player), game.isPlayerDead(player.getUuid()), game.getRole(player))) {
                grant(player);
            }
        }
    }

    static boolean receivesLoadout(boolean hasRole, boolean dead, @Nullable Role finalRole) {
        return hasRole && !dead && finalRole == SparkWitchRoles.savior();
    }

    private static void grant(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        // The game already hands out the room key and letter with their room-number/text components. Reuse those
        // exact stacks (do not create blank items), then order key, letter, pistol, sword.
        // 房间钥匙和信件由游戏按义警同款流程发放，自带房间号/信件内容；复用原物品堆（不要新建白板物品），
        // 再按钥匙、信件、手枪、光剑排序。
        moveExistingToHotbar(player, 0, WatheItems.KEY);
        moveExistingToHotbar(player, 1, WatheItems.LETTER);
        // SparkStrength issues the police tablet; it may arrive after this listener, so retry briefly.
        // SparkStrength 发放警用平板，可能晚于本监听器，因此短暂重试。
        Item tablet = tabletItem();
        if (tablet != null && !moveExistingToHotbar(player, 2, tablet)) {
            scheduleTabletMove(player, 0);
        }
        putHotbarSlot(player, 3, new ItemStack(SparkWitchItems.saviorPistol()));
        putHotbarSlot(player, 4, new ItemStack(SparkWitchItems.saviorLightsaber()));
        // Top-left main-inventory slot (PlayerScreenHandler slot 9), unreachable in the limited inventory screen.
        // 主背包左上角（PlayerScreenHandler 槽位 9），有限背包界面里无法交互。
        putMainSlot(player, 9, new ItemStack(WatheItems.REVOLVER));

        // Creative players get the weapons without the round-start cooldowns. / 创造模式玩家拿到武器但不进入开局冷却。
        if (!player.isCreative()) {
            SaviorCooldowns.writeCooldown(player, SparkWitchItems.saviorPistol(), SaviorRules.PISTOL_START_COOLDOWN_TICKS);
            SaviorCooldowns.writeCooldown(player, SparkWitchItems.saviorLightsaber(), SaviorRules.LIGHTSABER_START_COOLDOWN_TICKS);
        }
        // The Savior may not pick guns back up for the whole round (Wathe tag); our mixin covers SparkWitch guns.
        // 整局禁止救世主捡枪（Wathe 标签）；SparkWitch 自有枪械由 mixin 拦截。
        GameWorldComponent.KEY.get(player.getServerWorld()).addToPreventGunPickup(player);
        inventory.markDirty();
        player.currentScreenHandler.sendContentUpdates();
    }

    /**
     * Moves the game-given key/letter stack (components preserved) into the requested hotbar slot by swapping with
     * whatever currently sits there. Does nothing when the game did not hand that item out.
     * 把游戏发放的钥匙/信件（保留组件）通过交换移到指定快捷栏槽；游戏没发该物品时不做任何事。
     */
    private static boolean moveExistingToHotbar(ServerPlayerEntity player, int slot, Item item) {
        PlayerInventory inventory = player.getInventory();
        int from = -1;
        for (int i = 0; i < 36; i++) {
            if (inventory.getStack(i).isOf(item)) {
                from = i;
                break;
            }
        }
        if (from < 0) {
            return false;
        }
        if (from != slot) {
            ItemStack target = inventory.getStack(slot);
            inventory.setStack(slot, inventory.getStack(from));
            inventory.setStack(from, target);
        }
        return true;
    }

    private static @Nullable Item tabletItem() {
        Item item = Registries.ITEM.get(Identifier.of("sparkstrength", "tablet"));
        return item == Items.AIR ? null : item;
    }

    /** SparkStrength may grant the tablet after this listener; retry for a short while. / 平板可能晚于本监听器发放，短暂重试。 */
    private static void scheduleTabletMove(ServerPlayerEntity player, int attempt) {
        if (attempt > 40) {
            return;
        }
        Scheduler.schedule(() -> {
            Item tablet = tabletItem();
            if (tablet == null) {
                return;
            }
            if (!moveExistingToHotbar(player, 2, tablet)) {
                scheduleTabletMove(player, attempt + 1);
            }
        }, 1);
    }

    private static void putHotbarSlot(ServerPlayerEntity player, int slot, ItemStack stack) {
        PlayerInventory inventory = player.getInventory();
        ItemStack existing = inventory.getStack(slot);
        if (!existing.isEmpty()) {
            boolean moved = false;
            for (int i = 9; i < 36; i++) {
                if (inventory.getStack(i).isEmpty()) {
                    inventory.setStack(i, existing);
                    moved = true;
                    break;
                }
            }
            if (!moved) {
                player.dropItem(existing, false);
            }
        }
        inventory.setStack(slot, stack);
    }

    private static void putMainSlot(ServerPlayerEntity player, int slot, ItemStack stack) {
        PlayerInventory inventory = player.getInventory();
        ItemStack existing = inventory.getStack(slot);
        if (!existing.isEmpty()) {
            boolean moved = false;
            for (int i = 10; i < 36; i++) {
                if (inventory.getStack(i).isEmpty()) {
                    inventory.setStack(i, existing);
                    moved = true;
                    break;
                }
            }
            if (!moved) {
                player.dropItem(existing, false);
            }
        }
        inventory.setStack(slot, stack);
    }
}
