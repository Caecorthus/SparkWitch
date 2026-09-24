package dev.caecorthus.sparkwitch.roles.killer.bellringer;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Grants, restores, and removes the bound bell. Server-authoritative: a living Bell Ringer always holds
 * exactly one bell, and every other player is stripped of stray copies.
 * 发放、恢复与移除绑定之钟。由服务端裁定：存活敲钟人始终恰好持有一口钟，其余玩家的钟会被清除。
 */
public final class BellRingerLoadoutService {
    /** Cadence of the stray-bell sweep for non-ringers. / 非敲钟人残留钟清理的间隔。 */
    private static final int STRAY_SWEEP_INTERVAL_TICKS = 20;

    private BellRingerLoadoutService() {
    }

    /** Called on every RoleAssigned: gives one bell to a Bell Ringer, removes bells from anyone else. / 每次职业分配时调用：给敲钟人一口钟，并移除其他玩家的钟。 */
    public static void assignForRole(ServerPlayerEntity player, @Nullable Role role) {
        removeBells(player);
        if (!BellRingerRules.isBellRinger(role)) {
            return;
        }
        player.giveItemStack(new ItemStack(SparkWitchItems.tollBell()));
        player.getInventory().markDirty();
        player.currentScreenHandler.sendContentUpdates();
    }

    /**
     * Called every server tick from the component; must early-out cheaply. A living ringer is checked every
     * tick (as the Black Raven ledger is) so a bell removed before the drop guard, such as the server hotbar
     * drop path, returns on the next tick; everyone else is swept on a staggered 20-tick cadence.
     * 由组件每个服务端 tick 调用，必须廉价地提前返回。存活敲钟人每 tick 检查（与黑羽鸦感知册相同），
     * 使在丢弃拦截之前已被移出的钟（如服务端快捷栏丢弃路径）在下一 tick 恢复；其他玩家按错开的 20 tick 周期清理。
     */
    public static void tick(ServerPlayerEntity player) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getServerWorld());
        if (BellRingerRules.isBellRinger(game.getRole(player)) && GameFunctions.isPlayerPlayingAndAlive(player)) {
            restoreIfNeeded(player);
            return;
        }
        if (Math.floorMod(player.getServerWorld().getTime() + player.getId(), STRAY_SWEEP_INTERVAL_TICKS) == 0) {
            removeBells(player);
        }
    }

    public static void removeBells(ServerPlayerEntity player) {
        boolean changed = false;
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (isTollBell(inventory.getStack(slot))) {
                inventory.setStack(slot, ItemStack.EMPTY);
                changed = true;
            }
        }
        ScreenHandler handler = player.currentScreenHandler;
        if (isTollBell(handler.getCursorStack())) {
            handler.setCursorStack(ItemStack.EMPTY);
            changed = true;
        }
        // Also covers the crafting grid and any open foreign container. / 同时覆盖合成格与已打开的外部容器。
        for (Slot slot : handler.slots) {
            if (isTollBell(slot.getStack())) {
                slot.setStack(ItemStack.EMPTY);
                changed = true;
            }
        }
        if (changed) {
            inventory.markDirty();
            handler.sendContentUpdates();
        }
    }

    public static boolean isTollBell(ItemStack stack) {
        return BellRingerInventoryRules.isTollBell(stack);
    }

    private static void restoreIfNeeded(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        ScreenHandler handler = player.currentScreenHandler;
        boolean found = false;
        boolean changed = false;
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (!isTollBell(inventory.getStack(slot))) {
                continue;
            }
            if (!found) {
                found = true;
            } else {
                inventory.setStack(slot, ItemStack.EMPTY);
                changed = true;
            }
        }
        if (isTollBell(handler.getCursorStack())) {
            if (found) {
                handler.setCursorStack(ItemStack.EMPTY);
                changed = true;
            } else {
                found = true;
            }
        }
        if (!found) {
            ItemStack bell = new ItemStack(SparkWitchItems.tollBell());
            // Prefer the emptied selected slot so a blocked hotbar drop puts the bell back where it was.
            // 优先放回已空的当前选中栏位，使被拦截的快捷栏丢弃把钟放回原位。
            if (inventory.getStack(inventory.selectedSlot).isEmpty()) {
                inventory.setStack(inventory.selectedSlot, bell);
                changed = true;
            } else if (player.giveItemStack(bell)) {
                changed = true;
            }
        }
        if (changed) {
            inventory.markDirty();
            handler.sendContentUpdates();
        }
    }
}
