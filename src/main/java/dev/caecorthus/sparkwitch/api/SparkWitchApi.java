package dev.caecorthus.sparkwitch.api;

import dev.caecorthus.sparkwitch.component.WraithPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStun;
import dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeKillAttribution;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.UUID;

/** Public facade for narrowly scoped downstream compatibility. / 面向下游精确兼容用途的公共门面。 */
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

    /** Preserves the responsible UUID for one synchronous lethal action, including offline owners.
     * 为一次同步致死操作保留责任 UUID，包含离线责任人；嵌套调用和异常会恢复原上下文。 */
    public static void runWithKillAttribution(ServerWorld world, UUID responsiblePlayer, Runnable action) {
        JudgeKillAttribution.runWith(world, responsiblePlayer, action);
    }

    /**
     * True while a Control Expert stun locks {@code player}'s input (the server copy is authoritative). For add-on
     * sessions the payload deny-list cannot end, such as SparkStrength's drone pilot.
     * 控场专家眩晕锁定该玩家输入时为 true（以服务端为准）。供附属模组结束数据包拦截名单无法结束的会话，例如 SparkStrength
     * 的无人机驾驶。
     */
    public static boolean isControlExpertStunned(PlayerEntity player) {
        return ControlExpertStun.isStunned(player);
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
