package dev.caecorthus.sparkwitch.client.bear.launcher;

import dev.caecorthus.sparkwitch.roles.civilian.bear.launcher.M32A1Item;
import dev.caecorthus.sparkwitch.roles.civilian.bear.launcher.M32Cylinder;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

/**
 * Client only: reads the local player's M32A1 for the sight, HUD and fire input. Presentation state; the server never
 * trusts it. The sight is offered while use keeps the launcher in use ({@code M32A1Item.use} starts it, main hand
 * only), exactly like the anti-tank launcher, so no aim flag is synced or stored anywhere.
 * 仅客户端：读取本地玩家的 M32A1，供瞄具、HUD 与开火输入使用。仅为表现状态，服务端从不信任它。按住使用键使发射器处于使用中
 * 时提供瞄具（{@code M32A1Item.use} 开始使用，仅主手），与反坦克炮筒完全相同，因此任何地方都不同步或保存瞄准标记。
 */
public final class M32ClientState {
    private M32ClientState() {
    }

    public static boolean isLauncher(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof M32A1Item;
    }

    public static boolean holdsLauncherInMainHand(PlayerEntity player) {
        return player != null && isLauncher(player.getMainHandStack());
    }

    /** The player holds use with the launcher in the main hand. / 玩家主手持发射器并按住使用键。 */
    public static boolean isAiming(PlayerEntity player) {
        return player != null && player.isUsingItem() && isLauncher(player.getActiveItem())
                && holdsLauncherInMainHand(player);
    }

    /** The main-hand launcher's synced cylinder, or empty when not holding one. / 主手发射器已同步的转轮；未持有时为空。 */
    public static M32Cylinder cylinder(PlayerEntity player) {
        return holdsLauncherInMainHand(player) ? M32A1Item.cylinder(player.getMainHandStack()) : M32Cylinder.EMPTY;
    }
}
