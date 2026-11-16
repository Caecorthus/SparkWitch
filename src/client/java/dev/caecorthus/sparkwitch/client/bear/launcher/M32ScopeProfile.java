package dev.caecorthus.sparkwitch.client.bear.launcher;

import dev.caecorthus.sparkwitch.client.scope.ScopeClient;
import dev.caecorthus.sparkwitch.client.scope.ScopeFrame;
import dev.caecorthus.sparkwitch.client.scope.ScopeProfile;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * The M32A1's M2A1 reflex sight for the shared {@code client/scope} module (registered once through
 * {@link ScopeClient#registerProvider} by {@code M32LauncherClient}). It is offered while the local player aims
 * ({@link M32ClientState#isAiming}: use held with the launcher in the main hand) on a confirmed SparkWitch server;
 * {@code ScopeClient}'s view gate adds first person, the camera being the player and no screen. The sight is lensless
 * ({@link #drawsLens} false: no lens, rim, mask or periphery blur) and keeps the held launcher
 * ({@link #hidesHeldItem} false): the aimed 3-D launcher with its screen-centred sight, the centre mark and the fixed
 * range scale under it are drawn in the hand pass by {@link M32SightRenderer}, so {@link #drawReticle} draws nothing
 * and the module only hides the crosshair. The world
 * zooms 1.25x ({@link M32SightPose#WORLD_FOV_MULTIPLIER}) with look scale {@link M32SightPose#LOOK_MULTIPLIER}. The
 * wheel is left to vanilla (owner 2026-11-11: there is no range dial). The launcher's item use never
 * coincides with the USEC rifle's or the anti-tank launcher's (one active item), so no other provider answers at the
 * same time. Client only and presentation only: an aimed shot leaves along the look exactly like a hip shot
 * ({@link M32FireInput}), and the scale shows how far below the centre the round is at each range.
 * M32A1 的 M2A1 反射式瞄具，供共享 {@code client/scope} 模块使用（由 {@code M32LauncherClient} 经
 * {@link ScopeClient#registerProvider} 注册一次）。在已确认的 SparkWitch 服务器上、本地玩家瞄准时提供
 * （{@link M32ClientState#isAiming}：主手持发射器并按住使用键）；{@code ScopeClient} 的视角条件再加上第一人称、相机即玩家与
 * 未打开界面。该瞄具无镜片（{@link #drawsLens} 为 false：没有镜片、镜框、遮罩或镜外模糊），并保留手中的发射器
 * （{@link #hidesHeldItem} 为 false）：瞄准中的三维发射器（瞄具居于屏幕正中）、中心分划及其下方的固定射程刻度尺由 {@link M32SightRenderer} 在手部渲染中绘制，
 * 因此 {@link #drawReticle} 不绘制任何内容，模块只隐藏准星。世界放大 1.25 倍（{@link M32SightPose#WORLD_FOV_MULTIPLIER}），视角缩放
 * {@link M32SightPose#LOOK_MULTIPLIER}。滚轮交还原版（所有者 2026-11-11：不设射程调节）。发射器的物品使用与 USEC 步枪或
 * 反坦克炮筒的从不同时发生（只有一个使用中物品），因此不会有其他提供者同时作答。仅客户端、仅为表现：开镜射击与腰射一样沿视线射出
 * （{@link M32FireInput}），刻度尺标出各射程下榴弹位于中心下方多远。
 */
public final class M32ScopeProfile implements ScopeProfile {
    public static final M32ScopeProfile INSTANCE = new M32ScopeProfile();

    private M32ScopeProfile() {
    }

    /** {@code ScopeClient} provider: this profile while aiming, else null. / 瞄准时返回本配置，否则为 null。 */
    public static @Nullable ScopeProfile provide() {
        if (!SparkWitchServerConnection.isConfirmedServer()) {
            return null;
        }
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || player.isSpectator() || !M32ClientState.isAiming(player)) {
            return null;
        }
        return INSTANCE;
    }

    /** True when the shared module is scoped through this profile this frame. / 本帧共享模块正通过本配置开镜。 */
    public static boolean isActive() {
        return ScopeClient.activeProfile() == INSTANCE;
    }

    /** The red-dot world zoom, 1.25x. / 红点瞄具的世界放大，1.25 倍。 */
    @Override
    public float fovMultiplier() {
        return M32SightPose.WORLD_FOV_MULTIPLIER;
    }

    @Override
    public float sensitivityMultiplier() {
        return M32SightPose.LOOK_MULTIPLIER;
    }

    @Override
    public float peripheryBlur() {
        return 0.0F;
    }

    @Override
    public boolean drawsLens() {
        return false;
    }

    /** The launcher stays in hand; {@link M32SightRenderer} aims it. / 发射器留在手中，由 {@link M32SightRenderer} 瞄准。 */
    @Override
    public boolean hidesHeldItem() {
        return false;
    }

    /**
     * No 2-D reticle here: the sight, its centre mark and its range scale are drawn in the hand pass. The module
     * still replaces the crosshair with this (empty) call. / 这里没有 2D 分划：瞄具、中心分划与射程刻度尺都在手部渲染中绘制。
     * 模块仍以这个（空）调用替换准星。
     */
    @Override
    public void drawReticle(DrawContext context, ScopeFrame frame) {
    }
}
