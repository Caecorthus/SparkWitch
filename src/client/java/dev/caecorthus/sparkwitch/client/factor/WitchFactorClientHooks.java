package dev.caecorthus.sparkwitch.client.factor;

import dev.caecorthus.sparkwitch.client.blackraven.BlackRavenClientState;
import dev.caecorthus.sparkwitch.client.blackraven.SparkTraitsInstinctVisibilityBridge;
import dev.caecorthus.sparkwitch.client.hooks.WitchInstinctSuppressionClientHooks;
import dev.caecorthus.sparkwitch.client.render.WraithClientState;
import dev.caecorthus.sparkwitch.client.render.WraithViewerRules;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorService;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorWorldComponent;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.morphling.MorphlingPlayerComponent;

/** Recipient-sanitized factor fallback; no event listener and no identity broadcast. / 接收者裁剪后的因子后备高亮，不注册事件也不广播身份。 */
public final class WitchFactorClientHooks {
    private static final Identifier EMMA_ID = Identifier.of("sparkwitch", "emma");

    private WitchFactorClientHooks() {
    }

    public static int resolveHighlight(int originalColor, Entity entity) {
        PlayerEntity viewer = MinecraftClient.getInstance().player;
        if (originalColor != -1 || !SparkWitchServerConnection.isConfirmedServer()
                || viewer == null || !(entity instanceof PlayerEntity target)
                || target == viewer || !GameFunctions.isPlayerPlayingAndAlive(viewer)
                || !GameFunctions.isPlayerPlayingAndAlive(target)
                || GameFunctions.isPlayerSpectatingOrCreative(viewer)
                || GameFunctions.isPlayerSpectatingOrCreative(target)) {
            return originalColor;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(viewer.getWorld());
        if (!game.isRunning()) {
            return originalColor;
        }
        boolean carrier = WitchFactorService.isVisibleTo(viewer, target);
        // Authorization is a server-sanitized bit, including empty networks. / 授权由服务端裁剪的标记给出，空网络也可区分。
        boolean emma = WitchFactorWorldComponent.KEY.get(viewer.getWorld()).mayExposeEmma()
                && game.getRole(target) != null && EMMA_ID.equals(game.getRole(target).identifier());
        // Both exits return the original color and every check is a side-effect-free read, so the cheap carrier/Emma
        // bits run before the costlier hard-hide gates (reflection, Traits bridge) without changing any answer.
        // 两个出口都返回原颜色且各项检查均为无副作用读取，因此先做廉价的携带者/艾玛判断，再做较贵的硬隐藏门禁
        // （反射、Traits 桥接），结果不变。
        if (!carrier && !emma || isHardHidden(viewer, target)) {
            return originalColor;
        }

        // Requery only when adding a fallback: Traits HEAD may have bypassed the event entirely.
        // 仅补后备色时复核事件；Traits 的 HEAD 返回可能完全跳过了事件。
        GetInstinctHighlight.HighlightResult event = GetInstinctHighlight.EVENT.invoker().getHighlight(target);
        return WitchFactorOutlineRules.resolve(originalColor, false, event != null && event.isSkip(),
                emma, carrier);
    }

    private static boolean isHardHidden(PlayerEntity viewer, PlayerEntity target) {
        // A key-off/range-limited absence is not a hide; these are explicit terminal privacy gates.
        // 未按本能键或普通范围外不等于隐藏；以下才是最终隐私门禁。
        return target.isInvisible() || target.isInvisibleTo(viewer)
                || SparkTraitsInstinctVisibilityBridge.isHidden(viewer, target)
                || MorphlingPlayerComponent.KEY.get(target).corpseMode
                || WraithClientState.isRestricted(viewer)
                || WraithViewerRules.shouldHideFromOrdinaryViewer(viewer, target)
                || WitchInstinctSuppressionClientHooks.shouldSuppressInstinctHighlight()
                || WitchInstinctSuppressionClientHooks.shouldSuppressSwallowedInstinctHighlight(target)
                // During Perception's window the Black Raven sees only its Feather marks. Its held-key x-ray is
                // per-target and only answers perceived players, so it needs no viewer-wide hide here.
                // 感知窗口期间黑羽鸦只看到羽刃标记。按键透视按目标作答且只回应已感知玩家，因此此处无需整体隐藏。
                || BlackRavenClientState.isPerceptionActive(viewer);
    }
}
