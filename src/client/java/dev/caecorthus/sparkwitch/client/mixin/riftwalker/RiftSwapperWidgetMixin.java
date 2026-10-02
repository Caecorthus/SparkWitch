package dev.caecorthus.sparkwitch.client.mixin.riftwalker;

import org.agmas.noellesroles.client.SwapperPlayerWidget;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Reserved slot (G0) for P9's client Swapper patch (C7): let the Swapper pick a listed player who is in spectator mode
 * but alive (P9 may retarget this mixin to the Swapper screen if that is the right seam). Empty, and therefore inert,
 * until P9 lands.
 * 为 P9 的客户端交换界面补丁预留的 mixin（G0，C7）：让交换者可以点选列表中处于旁观者模式但仍存活的玩家（若交换界面才是
 * 正确接缝，P9 可改为以其为目标）。P9 落地前为空，因此无效果。
 */
@Mixin(SwapperPlayerWidget.class)
public abstract class RiftSwapperWidgetMixin {
}
