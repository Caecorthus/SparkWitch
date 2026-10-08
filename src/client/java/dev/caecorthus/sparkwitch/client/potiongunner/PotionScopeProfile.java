package dev.caecorthus.sparkwitch.client.potiongunner;

import dev.caecorthus.sparkwitch.client.scope.ScopeClient;
import dev.caecorthus.sparkwitch.client.scope.ScopeFrame;
import dev.caecorthus.sparkwitch.client.scope.ScopeProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * The anti-tank launcher's scope profile for the shared {@code client/scope} module (registered once through
 * {@link ScopeClient#registerProvider} from {@code PotionGunnerClient.init()}). It is offered while the local player
 * holds use with the launcher; {@code ScopeClient}'s view gate adds first person, the camera being the player, no
 * screen and not a spectator, which were the rest of the old launcher scope condition. The module then owns zoom,
 * mouse scaling, the Scope View setting, the lens picture, rim and scope shadow, and hiding the first-person held
 * items; the launcher owns only {@link PotionScopeRules#ZOOM_FACTOR}, {@link PotionScopeRules#SENSITIVITY_SCALE} and
 * the reticle ({@link PotionScopeOverlay}). The launcher's item use never coincides with the USEC rifle's (one active
 * item), so the two providers never both answer. Client-only presentation; the server never sees it.
 * 反坦克炮筒供共享 {@code client/scope} 模块使用的开镜配置（由 {@code PotionGunnerClient.init()} 经
 * {@link ScopeClient#registerProvider} 注册一次）。本地玩家按住使用键使用炮筒时提供；{@code ScopeClient} 的视角条件再加上
 * 第一人称、相机即玩家、未打开界面、不是旁观者，即旧炮筒开镜条件的其余部分。之后由模块负责放大、鼠标缩放、「开镜视图」
 * 设置、镜片画面、镜框与镜内阴影以及隐藏第一人称手持物品；炮筒只提供 {@link PotionScopeRules#ZOOM_FACTOR}、
 * {@link PotionScopeRules#SENSITIVITY_SCALE} 与分划（{@link PotionScopeOverlay}）。炮筒的物品使用与 USEC 步枪的从不同时发生
 * （只有一个使用中物品），因此两个提供者不会同时作答。仅为客户端表现，服务端从不知晓。
 */
public final class PotionScopeProfile implements ScopeProfile {
    public static final PotionScopeProfile INSTANCE = new PotionScopeProfile();

    private PotionScopeProfile() {
    }

    /** {@code ScopeClient} provider: this profile while the launcher is in use, else null. / 使用炮筒时返回本配置，否则为 null。 */
    public static @Nullable ScopeProfile provide() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || !PotionScopeClient.isUsingLauncher(player)) {
            return null;
        }
        return INSTANCE;
    }

    /** True when the shared module is scoped through this profile this frame. / 本帧共享模块正通过本配置开镜。 */
    public static boolean isActive() {
        return ScopeClient.activeProfile() == INSTANCE;
    }

    @Override
    public float fovMultiplier() {
        return PotionScopeRules.ZOOM_FACTOR;
    }

    @Override
    public float sensitivityMultiplier() {
        return PotionScopeRules.SENSITIVITY_SCALE;
    }

    @Override
    public void drawReticle(DrawContext context, ScopeFrame frame) {
        PotionScopeOverlay.draw(context, frame);
    }
}
