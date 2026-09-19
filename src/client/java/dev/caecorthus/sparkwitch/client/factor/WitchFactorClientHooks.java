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
    private static PrivateRevealProvider privateRevealProvider = (viewer, target) -> -1;

    private WitchFactorClientHooks() {
    }

    /** Optional role-owned private knowledge; the shared factor module never depends on Emma state. / 可选职业私有知识，共用因子模块不依赖爱玛状态。 */
    public static void registerPrivateRevealProvider(PrivateRevealProvider provider) {
        privateRevealProvider = java.util.Objects.requireNonNull(provider);
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
        if (!game.isRunning() || isHardHidden(viewer, target)) {
            return originalColor;
        }
        int reveal = privateRevealProvider.color(viewer, target);
        boolean carrier = WitchFactorService.isVisibleTo(viewer, target);
        // Authorization is a server-sanitized bit, including empty networks. / 授权由服务端裁剪的标记给出，空网络也可区分。
        boolean emma = WitchFactorWorldComponent.KEY.get(viewer.getWorld()).mayExposeEmma()
                && game.getRole(target) != null && EMMA_ID.equals(game.getRole(target).identifier());
        if (reveal == -1 && !carrier && !emma) {
            return originalColor;
        }

        // Requery only when adding a fallback: Traits HEAD may have bypassed the event entirely.
        // 仅补后备色时复核事件；Traits 的 HEAD 返回可能完全跳过了事件。
        GetInstinctHighlight.HighlightResult event = GetInstinctHighlight.EVENT.invoker().getHighlight(target);
        return WitchFactorOutlineRules.resolve(originalColor, false, event != null && event.isSkip(),
                reveal, emma, carrier);
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
                || (BlackRavenClientState.isEligible(viewer)
                    && (BlackRavenClientState.isPerceptionActive(viewer)
                        || BlackRavenClientState.mode() == BlackRavenClientState.InstinctMode.SENSED_ONLY));
    }

    @FunctionalInterface
    public interface PrivateRevealProvider {
        int color(PlayerEntity viewer, PlayerEntity target);
    }
}
