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
 * The M32A1's M2A1 1x reflex sight for the shared {@code client/scope} module (registered once through
 * {@link ScopeClient#registerProvider} by {@code M32LauncherClient}). It is offered while the local player aims
 * ({@link M32ClientState#isAiming}: use held with the launcher in the main hand) on a confirmed SparkWitch server;
 * {@code ScopeClient}'s view gate adds first person, the camera being the player and no screen. The sight is lensless
 * ({@link #drawsLens} false): true 1x, unscaled look, no lens, rim, mask or periphery blur, and the module still hides
 * the hand and swaps the crosshair for {@link M32ReticleOverlay}. The wheel turns the range dial
 * ({@link M32RangeDial}) and never reaches the hotbar. The launcher's item use never coincides with the USEC rifle's
 * or the anti-tank launcher's (one active item), so no other provider answers at the same time. Client-only
 * presentation; the server never sees it.
 * M32A1 的 M2A1 1 倍反射式瞄具，供共享 {@code client/scope} 模块使用（由 {@code M32LauncherClient} 经
 * {@link ScopeClient#registerProvider} 注册一次）。在已确认的 SparkWitch 服务器上、本地玩家瞄准时提供
 * （{@link M32ClientState#isAiming}：主手持发射器并按住使用键）；{@code ScopeClient} 的视角条件再加上第一人称、相机即玩家与
 * 未打开界面。该瞄具无镜片（{@link #drawsLens} 为 false）：真实 1 倍、视角不缩放，没有镜片、镜框、遮罩或镜外模糊；模块仍会
 * 隐藏手并以 {@link M32ReticleOverlay} 替换准星。滚轮转动射程刻度（{@link M32RangeDial}），从不切换快捷栏。发射器的物品使用
 * 与 USEC 步枪或反坦克炮筒的从不同时发生（只有一个使用中物品），因此不会有其他提供者同时作答。仅为客户端表现，服务端从不知晓。
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

    /** True 1x: a reflex sight never zooms. / 真实 1 倍：反射式瞄具从不放大。 */
    @Override
    public float fovMultiplier() {
        return 1.0F;
    }

    @Override
    public float sensitivityMultiplier() {
        return 1.0F;
    }

    @Override
    public float peripheryBlur() {
        return 0.0F;
    }

    @Override
    public boolean drawsLens() {
        return false;
    }

    /** Every wheel event while aiming turns the dial; none reaches the hotbar. / 瞄准时每个滚轮事件都转动刻度。 */
    @Override
    public boolean onWheel(double notches) {
        M32RangeDial.scroll(notches);
        return true;
    }

    @Override
    public void drawReticle(DrawContext context, ScopeFrame frame) {
        M32ReticleOverlay.draw(context, frame);
    }
}
