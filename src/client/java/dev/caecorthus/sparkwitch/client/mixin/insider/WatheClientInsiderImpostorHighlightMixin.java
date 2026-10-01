package dev.caecorthus.sparkwitch.client.mixin.insider;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.client.insider.InsiderInstinctClientHooks;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Insider disguise for SparkTraits Impostor viewers (D3). SparkTraits' HEAD on {@code getInstinctHighlight} answers
 * its civilian green {@code 0x4EDD35} for an Insider when the viewer has the Impostor trait, before any Wathe event
 * listener runs, so the Insider listener never gets a say. This wrapper always calls the original and rewrites only
 * that exact green to the Impostor blue, and only for a living local Impostor looking at another living Insider,
 * visible or not (SparkTraits paints an invisible real Impostor blue too); every other answer passes through untouched.
 * <p>Ordering on {@code WatheClient.getInstinctHighlight} (Wathe 1.5.6): HEAD callbacks run in ascending mixin
 * priority (Fiend moment 500 on its unmerged branch, SparkTraits {@code WatheClientMixin} 1000,
 * {@code WatheClientFearInstinctMixin} 1500, {@code WraithWatheHighlightMixin} and
 * {@code BlackRavenInstinctPriorityMixin} 2000), then the {@code GetInstinctHighlight} event, Wathe's default, and
 * the RETURN fallbacks that only fill {@code -1} (Black Raven 400, factor 300, Judge 200). MixinExtras applies
 * {@code @WrapMethod} in its post-apply stage, after every {@code @Inject}, {@code @ModifyExpressionValue},
 * {@code @ModifyConstant} and {@code @ModifyReturnValue} from any mod at any priority, so this wrapper sees the final
 * answer. No other {@code @WrapMethod} targets this method on the base branch; the planned Obscure/Fear wrapper
 * (SparkWitch PR #23) commutes with this one because it only ever returns {@code -1}.
 * 面向 SparkTraits 内鬼观察者的内应伪装（D3）。当观察者拥有内鬼词条时，SparkTraits 在 {@code getInstinctHighlight}
 * 的 HEAD 会在任何 Wathe 事件监听器之前为内应给出平民绿 {@code 0x4EDD35}，内应监听器因此没有机会作答。本包装器始终调用
 * 原方法，只把这一确切的绿色改为内鬼蓝，且仅限存活的本地内鬼观察其他存活的内应，无论其是否隐身（SparkTraits
 * 同样把隐身的真实内鬼描成蓝色）；其他结果原样通过。
 * <p>{@code WatheClient.getInstinctHighlight}（Wathe 1.5.6）上的顺序：HEAD 回调按 mixin 优先级升序执行（未合并分支上的
 * 魔人时刻 500、SparkTraits {@code WatheClientMixin} 1000、{@code WatheClientFearInstinctMixin} 1500、
 * {@code WraithWatheHighlightMixin} 与 {@code BlackRavenInstinctPriorityMixin} 2000），随后是
 * {@code GetInstinctHighlight} 事件、Wathe 默认逻辑，以及只填补 {@code -1} 的 RETURN 兜底（黑羽鸦 400、因子 300、
 * 法官 200）。MixinExtras 在 post-apply 阶段应用 {@code @WrapMethod}，晚于任何模组、任何优先级的全部
 * {@code @Inject}、{@code @ModifyExpressionValue}、{@code @ModifyConstant} 与 {@code @ModifyReturnValue}，
 * 因此本包装器看到的是最终结果。基线分支上没有其他 {@code @WrapMethod} 包装该方法；计划中的障眼/恐惧包装器
 * （SparkWitch PR #23）只会返回 {@code -1}，与本包装器的先后顺序互不影响。
 */
@Mixin(value = WatheClient.class, remap = false)
public abstract class WatheClientInsiderImpostorHighlightMixin {
    @WrapMethod(method = "getInstinctHighlight")
    private static int sparkwitch$insiderImpostorDisguise(Entity target, Operation<Integer> original) {
        return InsiderInstinctClientHooks.recolorImpostorView(target, original.call(target));
    }
}
