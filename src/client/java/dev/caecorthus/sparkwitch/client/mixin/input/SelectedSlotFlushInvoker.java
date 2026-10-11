package dev.caecorthus.sparkwitch.client.mixin.input;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Neutral invoker for vanilla 1.21.1's private {@code ClientPlayerInteractionManager.syncSelectedSlot()}, which sends
 * {@code UpdateSelectedSlotC2SPacket} only when the client's hotbar slot differs from the last one sent. Vanilla calls
 * it from {@code tick()} (before {@code handleInputEvents}) and from most interactions ({@code interactItem} among
 * them). A hotbar key drained in {@code handleInputEvents} changes only the client's slot until the next tick, so a
 * custom fire packet sent in the same tick must flush it first; {@code PotionFireInput} and {@code UsecFireInput} do,
 * right before their fire packet (the Death Ray needs none: its server never reads the held item). It is the only
 * invoker of this method: two mixin interfaces adding one method name to one target would clash, so new callers reuse
 * this one (the AXMC's own invoker was folded in on 2026-10-11).
 * 原版 1.21.1 私有方法 {@code ClientPlayerInteractionManager.syncSelectedSlot()} 的中立调用器：仅当客户端快捷栏位与上次发送的
 * 不同时发送 {@code UpdateSelectedSlotC2SPacket}。原版在 {@code tick()}（早于 {@code handleInputEvents}）及大多数交互（包括
 * {@code interactItem}）中调用它。{@code handleInputEvents} 中处理的快捷栏按键在下一刻之前只改变客户端栏位，因此同一刻发送的
 * 自定义开火数据包必须先同步；{@code PotionFireInput} 与 {@code UsecFireInput} 在发送开火数据包前调用它（死亡射线不需要：其
 * 服务端从不读取手持物品）。它是该方法唯一的调用器：两个 mixin 接口向同一目标添加同名方法会冲突，因此新的调用方复用本接口
 * （AXMC 自己的调用器已于 2026-10-11 并入）。
 */
@Mixin(ClientPlayerInteractionManager.class)
public interface SelectedSlotFlushInvoker {
    @Invoker("syncSelectedSlot")
    void sparkwitch$flushSelectedSlot();
}
