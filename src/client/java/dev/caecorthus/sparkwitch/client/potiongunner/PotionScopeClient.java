package dev.caecorthus.sparkwitch.client.potiongunner;

import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionShellType;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.launcher.PotionLauncherItem;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.launcher.PotionLauncherLoad;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

import java.util.Optional;

/**
 * Reads the local player's launcher state for the scope, HUD and fire input. Client-only presentation state: the
 * server never trusts it. The scope is "on" while the use key keeps the launcher in use (WP1: {@code use} starts
 * using it), so no scope flag is synced or stored anywhere.
 * 读取本地玩家的炮筒状态，供瞄准镜、HUD 与开火输入使用。仅为客户端展示状态，服务端从不信任它。按住使用键使炮筒处于
 * 使用中时即为开镜（WP1：{@code use} 开始使用炮筒），因此任何地方都不同步或保存开镜标记。
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

    /** The local player looks through the scope this frame. / 本地玩家本帧正通过瞄准镜观看。 */
    public static boolean isScoped(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        return player != null && PotionScopeRules.isScoped(
                player.isUsingItem() && isLauncher(player.getActiveItem()),
                client.options.getPerspective().isFirstPerson(),
                client.getCameraEntity() == player,
                client.currentScreen != null,
                player.isSpectator());
    }

    /** True only for the local player while scoped (other players' FOV is never touched). / 仅对开镜中的本地玩家为 true。 */
    public static boolean isScopedLocalPlayer(PlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        return player == client.player && isScoped(client);
    }

    public static Optional<PotionShellType> loaded(ItemStack launcher) {
        return isLauncher(launcher) ? PotionLauncherLoad.loaded(launcher) : Optional.empty();
    }
}
