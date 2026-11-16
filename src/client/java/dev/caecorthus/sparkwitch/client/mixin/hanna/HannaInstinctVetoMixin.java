package dev.caecorthus.sparkwitch.client.mixin.hanna;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.client.hanna.HannaInstinctVeto;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * "Invisible to instinct" veto for a floating Hanna (owner 2026-11-11, rules in {@link HannaInstinctVeto}), written
 * like {@code BlackRavenInstinctVetoMixin}. A {@code GetInstinctHighlight} listener cannot do this: a hide at any event
 * priority is still pre-empted by HEAD answers (SparkTraits Conscience/Impostor 1000, Fiend Moment 500, Magician 600,
 * Wraith and Black Raven 2000) and refilled by RETURN fallbacks (Judge 200, factor/Emma 300, Black Raven 400), and the
 * PR's transparent {@code 0x00000000} answer was drawn as a black through-wall outline, since Wathe treats anything but
 * -1 as outlined. MixinExtras applies {@code @WrapMethod} after every {@code @Inject} and {@code @ModifyReturnValue} on
 * {@code WatheClient}, so returning -1 without {@code original.call()} drops all of them, every event result and
 * Wathe's own killer and spectator defaults.
 * <p>Ordering: among {@code @WrapMethod}s the later-applied (higher-priority) mixin wraps outermost. Priority 2900 puts
 * this wrap outside every wrapper that runs the original under changed conditions or can answer by itself (USEC cloak,
 * SparkStrength Blackout, Morphling corpse and Corrupt Cop at 1000, Perfumer 500, the Undercover arbiter 400, whose
 * thermal fallback colours players) and outside the other SparkWitch vetoes (Natsume 1000, Fear 1500), and leaves the
 * Black Raven veto (3000) outermost. This wrap only vetoes, so its order against the other veto-only wrappers never
 * changes the answer. Wathe's {@code hasOutline} and outline colour both ask this method, so no {@code hasOutline} veto
 * is added: what remains is vanilla GLOWING and other non-instinct outlines, which are not hers to hide.
 * {@code remap = false}: Wathe is not a Minecraft class.
 * 浮游中的汉娜“无法被本能透视”的否决（所有者 2026-11-11，规则见 {@link HannaInstinctVeto}），写法同
 * {@code BlackRavenInstinctVetoMixin}。{@code GetInstinctHighlight} 监听器做不到：任何事件优先级的隐藏仍会被 HEAD 结果
 * （SparkTraits 善良/内鬼 1000、魔人时刻 500、魔术师 600、冤魂与黑羽鸦 2000）抢先，并被 RETURN 兜底（大法官 200、因子/艾玛
 * 300、黑羽鸦 400）重新填上；PR 中透明的 {@code 0x00000000} 结果还会画成黑色的隔墙描边，因为 Wathe 把 -1 以外的值都视为描边。
 * MixinExtras 在 {@code WatheClient} 上的全部 {@code @Inject} 与 {@code @ModifyReturnValue} 之后应用 {@code @WrapMethod}，
 * 因此不调用 {@code original.call()} 直接返回 -1 会丢弃上述全部结果、所有事件结果以及 Wathe 自身的杀手与旁观默认逻辑。
 * <p>顺序：多个 {@code @WrapMethod} 之间，后应用（优先级更高）的 mixin 位于最外层。优先级 2900 使本包装位于所有“在改变的条件下
 * 运行原逻辑”或可自行作答的包装之外（USEC 隐蔽、SparkStrength 熄灯、变形者尸体与黑警均为 1000、调香师 500、卧底裁决 400——其
 * 热成像兜底会给玩家上色），也位于 SparkWitch 其他否决（夏目 1000、恐惧 1500）之外，并让黑羽鸦否决（3000）保持最外层。本包装
 * 只会否决，因此与其他纯否决包装的先后不影响结果。Wathe 的 {@code hasOutline} 与描边颜色都调用本方法，所以不另加
 * {@code hasOutline} 否决：剩下的只有原版发光与其他非本能描边，那些不归她隐藏。{@code remap = false}：Wathe 不是原版类。
 */
@Mixin(value = WatheClient.class, remap = false, priority = 2900)
public abstract class HannaInstinctVetoMixin {
    @WrapMethod(method = "getInstinctHighlight")
    private static int sparkwitch$vetoFloatingHannaInstinct(Entity target, Operation<Integer> original) {
        if (HannaInstinctVeto.hides(MinecraftClient.getInstance().player, target)) {
            return -1;
        }
        return original.call(target);
    }
}
