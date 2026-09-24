package dev.caecorthus.sparkwitch.api;

import dev.caecorthus.sparkwitch.component.WraithPlayerComponent;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithState;
import net.minecraft.entity.player.PlayerEntity;

/** Public read-only facade for downstream Wraith compatibility. / 面向下游冤魂兼容的只读公共门面。 */
public final class SparkWitchApi {
    private static LastEscapeVisionRenderer lastEscapeVisionRenderer;

    /** Client-installed renderer; the common facade has no client class references. */
    @FunctionalInterface
    public interface LastEscapeVisionRenderer {
        boolean render(PlayerEntity player, float delta);
    }

    public static void installLastEscapeVisionRenderer(LastEscapeVisionRenderer renderer) {
        lastEscapeVisionRenderer = renderer;
    }

    /** Version 1 consumes Traits' three parameters and owns the sole combined escape pass.
     * 版本 1 接收 Traits 的三个参数并负责唯一合成后处理；服务端及未初始化时为 0。 */
    public static int getLastEscapeVisionProtocolVersion() {
        return lastEscapeVisionRenderer == null ? 0 : 1;
    }

    public static boolean supportsLastEscapeVision() {
        return getLastEscapeVisionProtocolVersion() == 1;
    }

    /**
     * Render-thread only. True means a composed pass was actually rendered, not merely supported.
     * False (including dedicated servers and shader load failure) lets Traits render its fallback.
     * 仅在渲染线程调用；成功执行合成后处理才返回 true，失败时由 Traits 回退渲染。
     */
    public static boolean tryRenderLastEscapeVision(PlayerEntity player, float delta) {
        return player != null && lastEscapeVisionRenderer != null && lastEscapeVisionRenderer.render(player, delta);
    }

    private SparkWitchApi() {
    }

    public static boolean isWraithActive(PlayerEntity player) {
        return player != null
                && WraithPlayerComponent.KEY.maybeGet(player)
                .map(WraithPlayerComponent::isActive)
                .orElse(false);
    }

    /**
     * Returns whether the player is an active Wraith whose owner-visible saved alignment is KILLER.
     * Redacted client records and invalid persisted state fail closed rather than leaking an alignment guess.
     */
    public static boolean isKillerAlignedWraith(PlayerEntity player) {
        return player != null
                && WraithPlayerComponent.KEY.maybeGet(player)
                .map(wraith -> isKillerAlignedWraith(wraith.isActive(), wraith.getAlignment()))
                .orElse(false);
    }

    static boolean isKillerAlignedWraith(boolean active, WraithState.Alignment alignment) {
        return active && alignment == WraithState.Alignment.KILLER;
    }

    public static boolean isWraithRestricted(PlayerEntity player) {
        return player != null
                && WraithPlayerComponent.KEY.maybeGet(player)
                .map(WraithPlayerComponent::isRestricted)
                .orElse(false);
    }

    public static boolean isWraithPromoted(PlayerEntity player) {
        return player != null
                && WraithPlayerComponent.KEY.maybeGet(player)
                .map(WraithPlayerComponent::isPromoted)
                .orElse(false);
    }
}
