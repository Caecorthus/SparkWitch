package dev.caecorthus.sparkwitch.roles.civilian.seeker.console;

import dev.caecorthus.sparkwitch.compat.SparkStrengthTabletCompat;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerCarState;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerStatusComponent;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerCarItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Stable contract, side-neutral: which stacks act as the Seeker console. The SparkStrength tablet always does (matched
 * by registry id). Only when SparkStrength is absent (a forced Seeker) does the car item stand in, and only when this
 * use would not deploy a car and a device is out. The whole inventory counts (main, hotbar, offhand, cursor).
 * 稳定契约，两端通用：哪些物品可充当搜寻者控制台。SparkStrength 平板始终可以（按注册 id 识别）。
 * 仅当 SparkStrength 缺失（被强制指定的搜寻者）时，小车物品才会代替，且仅在本次使用不会部署小车、并已有设备在场时。
 * 整个背包都算（主背包、快捷栏、副手、光标）。
 */
public final class SeekerConsoleDevices {
    private SeekerConsoleDevices() {
    }

    public static boolean isConsoleDevice(@Nullable PlayerEntity player, @Nullable ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return false;
        }
        if (SparkStrengthTabletCompat.isTablet(stack)) {
            return true;
        }
        if (SparkStrengthTabletCompat.isAvailable() || !(stack.getItem() instanceof SeekerCarItem)) {
            return false;
        }
        SeekerStatusComponent status = SeekerStatusComponent.KEY.getNullable(player);
        if (status == null) {
            return false;
        }
        boolean carReady = status.carState() == SeekerCarState.READY
                && !player.getItemCooldownManager().isCoolingDown(stack.getItem());
        return carFallbackActsAsConsole(carReady, status.carState() == SeekerCarState.DEPLOYED,
                status.cameraEntityId() >= 0);
    }

    /**
     * Pure fallback rule for the car item without SparkStrength: never when the use would deploy (READY and off
     * cooldown); otherwise only while the car is deployed or a camera is placed.
     * 无 SparkStrength 时小车物品的纯兜底规则：若本次使用会部署（READY 且不在冷却）则永不充当；
     * 否则仅在小车已部署或摄像头已放置时充当。
     */
    public static boolean carFallbackActsAsConsole(boolean wouldDeploy, boolean carDeployed, boolean cameraPlaced) {
        return !wouldDeploy && (carDeployed || cameraPlaced);
    }

    /** Whole-inventory scan. / 全背包扫描。 */
    public static boolean hasConsoleDevice(@Nullable PlayerEntity player) {
        if (player == null) {
            return false;
        }
        if (SparkStrengthTabletCompat.hasTabletAnywhere(player)) {
            return true;
        }
        if (SparkStrengthTabletCompat.isAvailable()) {
            return false;
        }
        PlayerInventory inventory = player.getInventory();
        for (ItemStack stack : inventory.main) {
            if (stack.getItem() instanceof SeekerCarItem) {
                return true;
            }
        }
        for (ItemStack stack : inventory.offHand) {
            if (stack.getItem() instanceof SeekerCarItem) {
                return true;
            }
        }
        return player.currentScreenHandler != null
                && player.currentScreenHandler.getCursorStack().getItem() instanceof SeekerCarItem;
    }
}
