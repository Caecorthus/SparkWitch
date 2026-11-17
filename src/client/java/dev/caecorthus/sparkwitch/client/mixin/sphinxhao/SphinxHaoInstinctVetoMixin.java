package dev.caecorthus.sparkwitch.client.mixin.sphinxhao;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.client.sphinxhao.SphinxHaoInstinctVeto;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * "Invisible to instinct" veto for 豪气冲天's crawlers (owner spec 2026-11-17, rules in {@link SphinxHaoInstinctVeto}),
 * written exactly like {@code hanna.HannaInstinctVetoMixin}: a {@code GetInstinctHighlight} listener cannot hide a
 * target (HEAD answers pre-empt it and RETURN fallbacks refill it), and Wathe treats any answer but -1 as an outline.
 * MixinExtras applies {@code @WrapMethod} after every {@code @Inject} and {@code @ModifyReturnValue} on
 * {@code WatheClient}, so returning -1 without {@code original.call()} drops all of them, every event result and
 * Wathe's own killer and spectator defaults.
 * <p>Ordering: priority 2900, Hanna's, outside every wrapper that runs the original under changed conditions or can
 * answer by itself (USEC cloak, SparkStrength Blackout, Morphling corpse and Corrupt Cop at 1000, Perfumer 500, the
 * Undercover arbiter 400) and the other SparkWitch vetoes (Natsume 1000, Fear 1500), inside the Black Raven veto
 * (3000). This wrap only vetoes, so its order against Hanna's (same priority) or any other veto-only wrapper never
 * changes the answer. Wathe's {@code hasOutline} and outline colour both ask this method, so no {@code hasOutline}
 * veto is needed. {@code remap = false}: Wathe is not a Minecraft class. Client presentation only: the server never
 * asks for instinct.
 * 豪气冲天陆泳者“无法被本能透视”的否决（所有者规格 2026-11-17，规则见 {@link SphinxHaoInstinctVeto}），写法与
 * {@code hanna.HannaInstinctVetoMixin} 完全一致：{@code GetInstinctHighlight} 监听器无法隐藏目标（HEAD 结果会抢先、RETURN 兜底
 * 会重新填上），且 Wathe 把 -1 以外的任何答复都视为描边。MixinExtras 在 {@code WatheClient} 上的全部 {@code @Inject} 与
 * {@code @ModifyReturnValue} 之后应用 {@code @WrapMethod}，因此不调用 {@code original.call()} 直接返回 -1 会丢弃它们、所有事件
 * 结果以及 Wathe 自身的杀手与旁观默认逻辑。
 * <p>顺序：优先级 2900（与汉娜相同），位于所有“在改变的条件下运行原逻辑”或可自行作答的包装之外（USEC 隐蔽、SparkStrength 熄灯、
 * 变形者尸体与黑警均为 1000、调香师 500、卧底裁决 400），也位于 SparkWitch 其他否决（夏目 1000、恐惧 1500）之外，并在黑羽鸦否决
 * （3000）之内。本包装只会否决，因此与汉娜（同优先级）或其他纯否决包装的先后不影响结果。Wathe 的 {@code hasOutline} 与描边颜色
 * 都调用本方法，无需另加 {@code hasOutline} 否决。{@code remap = false}：Wathe 不是原版类。仅客户端展示：服务端从不查询本能。
 */
@Mixin(value = WatheClient.class, remap = false, priority = 2900)
public abstract class SphinxHaoInstinctVetoMixin {
    @WrapMethod(method = "getInstinctHighlight")
    private static int sparkwitch$vetoSphinxHaoCrawlerInstinct(Entity target, Operation<Integer> original) {
        if (SphinxHaoInstinctVeto.hides(MinecraftClient.getInstance().player, target)) {
            return -1;
        }
        return original.call(target);
    }
}
