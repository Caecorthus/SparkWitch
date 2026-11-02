package dev.caecorthus.sparkwitch.client.mixin.stowaway;

import dev.caecorthus.sparkwitch.client.stowaway.StowawayClientVisibility;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Owner request 2026-11-02: an unseen Stowaway ({@code canSee == false}) is never revealed by the instinct resolver,
 * regardless of the viewer's role (killer, kind-trait killer, Corrupt Cop, Insider, any Witch, ...). Priority 400
 * runs before SparkWitch's Fiend rule (500), SparkTraits (1000), Grand Witch fear (1500) and Wraith/Black Raven (2000),
 * and cancels at HEAD so no later resolver can add a color for this target.
 * 所有者 2026-11-02 需求：没有视线（{@code canSee == false}）的偷渡客永远不会被本能解析器暴露，无论观察者是什么职业
 * （杀手、善良词条杀手、黑警、内应、各种魔女等）。优先级 400 早于 SparkWitch 魔人规则（500）、SparkTraits（1000）、
 * 大魔女恐惧（1500）与冤魂/黑羽鸦（2000），并在 HEAD 取消，后续解析器无法再为该目标添加颜色。
 */
@Mixin(value = WatheClient.class, remap = false, priority = 400)
public abstract class WatheClientStowawayInstinctMixin {
    @Inject(method = "getInstinctHighlight", at = @At("HEAD"), cancellable = true)
    private static void sparkwitch$hideUnseenStowawayFromInstinct(
            Entity target,
            CallbackInfoReturnable<Integer> cir
    ) {
        if (StowawayClientVisibility.shouldHideFromLocalViewer(target)) {
            cir.setReturnValue(-1);
        }
    }
}
