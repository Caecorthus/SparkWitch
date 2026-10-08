package dev.caecorthus.sparkwitch.client.usec;

import dev.caecorthus.sparkwitch.client.scope.ScopeClient;
import dev.caecorthus.sparkwitch.client.scope.ScopeFrame;
import dev.caecorthus.sparkwitch.client.scope.ScopeProfile;
import dev.caecorthus.sparkwitch.compat.SparkTraitsUsecBridge;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * The USEC rifle's scope profile for the shared {@code client/scope} renderer (registered once through
 * {@link ScopeClient#registerProvider}). It is offered while the local player holds use with the rifle in the main
 * hand; {@code ScopeClient} decides whether that frame is actually scoped (first person, no screen, ...). Zoom and
 * look sensitivity both follow the remembered level (S3: 4x or 8x), and the reticle is code-drawn by
 * {@link UsecReticleRenderer} in {@link UsecReticleStyle#DEFAULT}. Client-only presentation; the server never sees it.
 * USEC 步枪供共享 {@code client/scope} 渲染器使用的开镜配置（经 {@link ScopeClient#registerProvider} 注册一次）。本地玩家
 * 主手持步枪并按住使用键时提供；该帧是否真正开镜（第一人称、无界面等）由 {@code ScopeClient} 决定。放大与视角灵敏度都
 * 跟随记住的档位（S3：4 倍或 8 倍），分划由 {@link UsecReticleRenderer} 以 {@link UsecReticleStyle#DEFAULT} 用代码绘制。
 * 仅为客户端表现，服务端从不知晓。
 */
public final class UsecScopeProfile implements ScopeProfile {
    public static final UsecScopeProfile INSTANCE = new UsecScopeProfile();

    private UsecScopeProfile() {
    }

    /** {@code ScopeClient} provider: this profile while the rifle is raised, else null. / 举枪时返回本配置，否则为 null。 */
    public static @Nullable ScopeProfile provide() {
        if (!SparkWitchServerConnection.isConfirmedServer()) {
            return null;
        }
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || player.isSpectator() || !UsecRifleClient.isUsingRifleInMainHand(player)) {
            return null;
        }
        return INSTANCE;
    }

    /** True when the shared renderer is scoped through this profile this frame. / 本帧共享渲染器正通过本配置开镜。 */
    public static boolean isActive() {
        return ScopeClient.isScoped() && ScopeClient.activeProfile() == INSTANCE;
    }

    @Override
    public float fovMultiplier() {
        return UsecZoomState.fovMultiplier();
    }

    @Override
    public float sensitivityMultiplier() {
        return UsecZoomState.fovMultiplier();
    }

    @Override
    public void drawReticle(DrawContext context, ScopeFrame frame) {
        MinecraftClient client = MinecraftClient.getInstance();
        // AP holdover follows the shooter's own Marksman multiplier (fails closed to 1.0 without SparkTraits).
        // AP 抬枪刻度跟随射手自己的精确枪手倍率（没有 SparkTraits 时回退为 1.0）。
        double marksman = SparkTraitsUsecBridge.marksmanRangeMultiplier(client.player);
        UsecReticleRenderer.draw(context, client.textRenderer, frame, UsecReticleStyle.DEFAULT,
                client.getWindow().getScaleFactor(), marksman, UsecZoomState.fovMultiplier());
    }
}
