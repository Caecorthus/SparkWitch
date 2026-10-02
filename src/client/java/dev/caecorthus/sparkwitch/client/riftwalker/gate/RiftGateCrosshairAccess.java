package dev.caecorthus.sparkwitch.client.riftwalker.gate;

import net.minecraft.util.hit.HitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Duck interface implemented on {@code GameRenderer} by {@code RiftGateCrosshairMixin}: re-runs vanilla's private
 * {@code findCrosshairTarget} with the local player's real reach while gates are ignored, so every other crosshair
 * filter (Wraith pass-through) and the reach clamp still apply. Client render thread only.
 * 由 {@code RiftGateCrosshairMixin} 在 {@code GameRenderer} 上实现的鸭子接口：在忽略门的情况下，以本地玩家的真实触及距离重新
 * 执行原版私有的 {@code findCrosshairTarget}，其他准星过滤（冤魂穿透）与距离限制照常生效。仅客户端渲染线程。
 */
public interface RiftGateCrosshairAccess {
    /** The crosshair pick without gates, or null without a camera, player or world. / 不含门的准星选取。 */
    @Nullable
    HitResult sparkwitch$pickIgnoringGates();
}
