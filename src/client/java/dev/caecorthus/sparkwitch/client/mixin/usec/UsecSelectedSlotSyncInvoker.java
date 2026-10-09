package dev.caecorthus.sparkwitch.client.mixin.usec;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Invoker for vanilla 1.21.1's private {@code ClientPlayerInteractionManager.syncSelectedSlot()}, which sends
 * {@code UpdateSelectedSlotC2SPacket} only when the client's hotbar slot differs from the last one sent. Vanilla calls
 * it from {@code tick()} (before {@code handleInputEvents}) and from most interactions ({@code interactItem},
 * {@code interactBlock}, {@code attackEntity} and others; the {@code interactItem} call is why Wathe's revolver never
 * meets a stale slot). A hotbar key drained in {@code handleInputEvents}
 * changes only the client's slot until the next tick, so {@code UsecFireInput} flushes it right before its fire packet;
 * the server, which checks the main hand, then reads the slot before the shot. Nothing else calls it.
 * 原版 1.21.1 私有方法 {@code ClientPlayerInteractionManager.syncSelectedSlot()} 的调用器：仅当客户端快捷栏位与上次发送的
 * 不同时发送 {@code UpdateSelectedSlotC2SPacket}。原版在 {@code tick()}（早于 {@code handleInputEvents}）及大多数交互中
 * （{@code interactItem}、{@code interactBlock}、{@code attackEntity} 等；正因 {@code interactItem} 会调用它，Wathe 左轮从不
 * 遇到过期栏位）调用它。{@code handleInputEvents} 中处理的快捷栏按键在下一刻
 * 之前只改变客户端栏位，因此 {@code UsecFireInput} 在发送开火数据包前先同步；检查主手的服务端便会先读到栏位再处理开火。
 * 其他地方都不调用它。
 */
@Mixin(ClientPlayerInteractionManager.class)
public interface UsecSelectedSlotSyncInvoker {
    @Invoker("syncSelectedSlot")
    void sparkwitch$syncSelectedSlot();
}
