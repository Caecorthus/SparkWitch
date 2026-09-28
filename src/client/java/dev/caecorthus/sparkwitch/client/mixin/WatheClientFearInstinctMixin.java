package dev.caecorthus.sparkwitch.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.client.hooks.GrandWitchFearClientHooks;
import dev.caecorthus.sparkwitch.client.hooks.WitchInstinctSuppressionClientHooks;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps Wathe instinct UI present but denies suppressed instinct rendering during Grand Witch spells.
 * 保留 wathe 本能 UI，但在大魔女法术期间拒绝被压制的本能渲染。
 */
@Mixin(value = WatheClient.class, remap = false, priority = 1500)
public abstract class WatheClientFearInstinctMixin {
    /**
     * Outermost Obscure/Fear/swallow outline veto. MixinExtras applies {@code @WrapMethod} after every
     * {@code @Inject} and {@code @ModifyReturnValue} on WatheClient from any mod, so returning -1 without
     * {@code original.call()} also hides SparkTraits' priority-1000 HEAD answers (Impostor, Conscience and
     * always-on outlines). HEAD callbacks run in ascending mixin priority, so a HEAD veto here would run after
     * them and no priority bump can fix that. Exemptions (Final Moment, spectators, witch faction) stay in
     * {@link WitchInstinctSuppressionClientHooks}; the swallow veto has none, so it also beats Final Moment.
     * 最外层的障眼/恐惧/吞噬描边否决。MixinExtras 的 {@code @WrapMethod} 晚于任何模组在 WatheClient 上的全部
     * {@code @Inject} 与 {@code @ModifyReturnValue} 应用，因此不调用 {@code original.call()} 直接返回 -1，
     * 也会隐藏 SparkTraits 优先级 1000 的 HEAD 结果（内鬼、善良与常驻描边）。HEAD 回调按 mixin 优先级升序执行，
     * 此处的 HEAD 否决会排在其后，提高优先级也无法解决。豁免（最终时刻、旁观者、魔女阵营）仍由
     * {@link WitchInstinctSuppressionClientHooks} 判断；吞噬否决没有豁免，因此也优先于最终时刻。
     */
    @WrapMethod(method = "getInstinctHighlight")
    private static int sparkwitch$suppressInstinctHighlightDuringAreaSpell(
            Entity target,
            Operation<Integer> original
    ) {
        if (WitchInstinctSuppressionClientHooks.shouldSuppressSwallowedInstinctHighlight(target)
                || WitchInstinctSuppressionClientHooks.shouldSuppressInstinctHighlight()) {
            return -1;
        }
        return original.call(target);
    }

    @Inject(method = "isInstinctEnabled", at = @At("HEAD"), cancellable = true)
    private static void sparkwitch$disableInstinctWhenFeared(CallbackInfoReturnable<Boolean> cir) {
        if (GrandWitchFearClientHooks.shouldBlockInstinct()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isInstinctEnabledAndIsKiller()Z", at = @At("HEAD"), cancellable = true)
    private static void sparkwitch$disableKillerInstinctWhenFeared(CallbackInfoReturnable<Boolean> cir) {
        if (GrandWitchFearClientHooks.shouldBlockInstinct()) {
            cir.setReturnValue(false);
        }
    }
}
