package dev.caecorthus.sparkwitch.client.mixin.blackraven;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.client.blackraven.BlackRavenInstinctVeto;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Outermost "never visible to instinct" veto for a living Black Raven (plan §3.2, rules in
 * {@link BlackRavenInstinctVeto}), written like the Obscure/Fear/swallow veto in {@code WatheClientFearInstinctMixin}.
 * MixinExtras applies {@code @WrapMethod} after every {@code @Inject} and {@code @ModifyReturnValue} on
 * {@code WatheClient}, so returning -1 without {@code original.call()} drops every event result (killer instinct
 * listeners, SparkStrength tablet suspects, Seeker and Emma marks), HEAD answer (SparkTraits Conscience/Impostor, Fiend
 * Moment, Final Moment, Wraith) and RETURN fallback (Judge sentence, factor/Emma, Vendetta) and Wathe's own killer
 * default. It only vetoes, so its nesting with the other wrappers (Fear, USEC cloak, Control Expert, SparkStrength)
 * never matters for the answer; {@code BlackRavenOutlineVetoMixin} stays the final outline guarantee. A Magician
 * puppet copying the Raven re-asks this method for its stand-in, so it never glows either. {@code remap = false}: Wathe
 * is not a Minecraft class.
 * 存活黑羽鸦的最外层“永不被本能透视”否决（计划 §3.2，规则见 {@link BlackRavenInstinctVeto}），写法同
 * {@code WatheClientFearInstinctMixin} 的障眼/恐惧/吞噬否决。MixinExtras 在 {@code WatheClient} 上的全部 {@code @Inject} 与
 * {@code @ModifyReturnValue} 之后应用 {@code @WrapMethod}，因此不调用 {@code original.call()} 直接返回 -1 会丢弃所有事件结果
 * （杀手本能监听、SparkStrength 平板嫌疑人、搜寻者与艾玛标记）、HEAD 结果（SparkTraits 善良/内鬼、魔人时刻、终局时刻、冤魂）、
 * RETURN 兜底（法官判决、因子/艾玛、复仇）以及 Wathe 自身的杀手默认逻辑。它只会否决，因此与其他包装（恐惧、USEC 隐蔽、控场专家、
 * SparkStrength）的嵌套顺序不影响结果；{@code BlackRavenOutlineVetoMixin} 仍是最终的描边保证。复制黑羽鸦的魔术师皮套会以
 * 替身重新调用本方法，因此同样不发光。{@code remap = false}：Wathe 不是原版类。
 */
@Mixin(value = WatheClient.class, remap = false, priority = 3000)
public abstract class BlackRavenInstinctVetoMixin {
    @WrapMethod(method = "getInstinctHighlight")
    private static int sparkwitch$vetoBlackRavenInstinct(Entity target, Operation<Integer> original) {
        if (BlackRavenInstinctVeto.hides(MinecraftClient.getInstance().player, target)) {
            return -1;
        }
        return original.call(target);
    }
}
