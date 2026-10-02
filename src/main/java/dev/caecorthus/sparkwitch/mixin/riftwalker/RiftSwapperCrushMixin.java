package dev.caecorthus.sparkwitch.mixin.riftwalker;

import org.agmas.noellesroles.Noellesroles;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Reserved slot (G0) for P9: a HEAD inject on the Swapper server handler {@code lambda$registerPackets$4} with the full
 * descriptor ({@code require = 1}), at priority 1100 so the SparkFactionAPI and SparkTraits guards run first; it
 * delegates to {@code RiftSwapperCrushService} (research 06 §3). Empty, and therefore inert, until P9 lands.
 * 为 P9 预留的 mixin（G0）：在交换者服务端处理器 {@code lambda$registerPackets$4} 上以完整描述符做 HEAD 注入
 * （{@code require = 1}），优先级 1100，使 SparkFactionAPI 与 SparkTraits 的守卫先执行；委托给 {@code RiftSwapperCrushService}
 * （调研 06 §3）。P9 落地前为空，因此无效果。
 */
@Mixin(value = Noellesroles.class, priority = 1100)
public abstract class RiftSwapperCrushMixin {
}
