package dev.caecorthus.sparkwitch.client.potiongunner;

import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionShellType;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.launcher.PotionLauncherItem;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.launcher.PotionLauncherLoad;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

import java.util.Optional;

/**
 * Reads the local player's launcher state for the scope, HUD and fire input. Client-only presentation state: the
 * server never trusts it. The scope is offered while the use key keeps the launcher in use (WP1: {@code use} starts
 * using it, main hand only), and it is "on" only while the shared {@code client/scope} module actually renders the
 * launcher's {@link PotionScopeProfile}, so no scope flag is synced or stored anywhere.
 * 读取本地玩家的炮筒状态，供瞄准镜、HUD 与开火输入使用。仅为客户端展示状态，服务端从不信任它。按住使用键使炮筒处于
 * 使用中时提供瞄准镜（WP1：{@code use} 开始使用炮筒，仅主手），且只有共享的 {@code client/scope} 模块正在渲染炮筒的
 * {@link PotionScopeProfile} 时才算开镜，因此任何地方都不同步或保存开镜标记。
 */
public final class PotionScopeClient {
    private PotionScopeClient() {
    }

    public static boolean isLauncher(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof PotionLauncherItem;
    }

    public static boolean holdsLauncherInMainHand(PlayerEntity player) {
        return isLauncher(player.getMainHandStack());
    }

    /** The player holds use with the launcher (the scope's only weapon-side input). / 玩家正按住使用键使用炮筒。 */
    public static boolean isUsingLauncher(PlayerEntity player) {
        return player.isUsingItem() && isLauncher(player.getActiveItem());
    }

    /**
     * The local player looks through the launcher scope this frame: exactly when {@code client/scope} renders the
     * launcher profile, so the hidden HUD line and the lens never disagree.
     * 本地玩家本帧正通过炮筒瞄准镜观看：恰好在 {@code client/scope} 渲染炮筒配置时为 true，使隐藏的 HUD 行与镜片始终一致。
     */
    public static boolean isScoped() {
        return PotionScopeProfile.isActive();
    }

    public static Optional<PotionShellType> loaded(ItemStack launcher) {
        return isLauncher(launcher) ? PotionLauncherLoad.loaded(launcher) : Optional.empty();
    }
}
