package dev.caecorthus.sparkwitch.client.mixin.controlexpert;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.client.controlexpert.ControlExpertStatusClient;
import dev.doctor4t.wathe.client.WatheClient;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Control Expert instinct gate (Tier 1): while the local player's own Disruptor or stun counter is running, Wathe's
 * two keyed instinct gates read false, which also turns off instinct light and every key-gated highlight. Outlines
 * that need no key stay untouched, and dead, spectating or Wraith viewers are never gated.
 * <p>Ordering: MixinExtras applies {@code @WrapMethod} in its postApply stage, after every {@code @Inject} and
 * {@code @ModifyReturnValue} on WatheClient from any mod and at any priority, so this wrapper encloses all HEAD vetoes
 * and enablers and all RETURN modifiers; returning false without {@code original.call()} skips them all. HEAD
 * injectors on their own run in ascending mixin priority (lower-priority callbacks first), so a HEAD veto could be
 * pre-empted by an earlier HEAD enabler, and a RETURN modifier never sees a HEAD cancellation. Among several
 * {@code @WrapMethod}s a lower-priority mixin wraps outermost; no other mod wraps these two methods today.
 * 控场专家本能拦截（第一档）：本地玩家自己的干扰或眩晕计时进行中时，Wathe 的两个按键本能入口返回 false，
 * 本能增亮与所有需按键的高亮也随之关闭。无需按键的描边保持不变，死亡、旁观或冤魂视角永不受拦截。
 * <p>应用顺序：MixinExtras 在 postApply 阶段应用 {@code @WrapMethod}，晚于任何模组、任何优先级在 WatheClient 上的
 * 全部 {@code @Inject} 与 {@code @ModifyReturnValue}，因此本包装器包住所有 HEAD 否决与启用以及所有 RETURN 修改；
 * 不调用 {@code original.call()} 直接返回 false 即可全部跳过。单独的 HEAD 注入按 mixin 优先级升序执行
 * （低优先级的回调先执行），因此 HEAD 否决可能被更早的 HEAD 启用抢先，而 RETURN 修改也看不到 HEAD 取消。
 * 多个 {@code @WrapMethod} 之间，低优先级的 mixin 位于最外层；目前没有其他模组包装这两个方法。
 */
@Mixin(value = WatheClient.class, remap = false)
public abstract class ControlExpertInstinctGateMixin {
    @WrapMethod(method = "isInstinctEnabled()Z")
    private static boolean sparkwitch$gateControlExpertInstinct(Operation<Boolean> original) {
        return !ControlExpertStatusClient.blocksLocalInstinct() && original.call();
    }

    @WrapMethod(method = "isInstinctEnabledAndIsKiller()Z")
    private static boolean sparkwitch$gateControlExpertKillerInstinct(Operation<Boolean> original) {
        return !ControlExpertStatusClient.blocksLocalInstinct() && original.call();
    }
}
