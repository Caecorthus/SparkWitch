package dev.caecorthus.sparkwitch.client.mixin.usec;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.client.usec.UsecInstinctCloak;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * USEC scoped instinct cloak (owner, 2026-10-08; rules in {@code client/usec/UsecInstinctCloak}). A scoped USEC within
 * 12 blocks is hidden from the viewer's keyed instinct x-ray while always-on highlights stay. Wathe folds every event
 * result, HEAD answer and RETURN fallback into one int colour, which no longer says whether a key was needed, so the
 * colour cannot be filtered afterwards; and a listener returning {@code skip()} (priority 100) would also wipe the
 * always-on highlights. Instead, for a cloaked target only, the whole {@code getInstinctHighlight} runs with Wathe's two
 * keyed instinct gates reading false, i.e. as if the viewer had released the key: Wathe drops a keybind event result
 * ({@code requiresKeybind && !isInstinctEnabled()}) and its null-result default ({@code isInstinctEnabledAndIsKiller()}
 * first), add-ons that key on those gates drop theirs, and every {@code always} answer is returned unchanged.
 * <p>Ordering: MixinExtras applies {@code @WrapMethod} after every {@code @Inject} and {@code @ModifyReturnValue} on
 * {@code WatheClient} from any mod and at any priority, so these wrappers enclose all HEAD answers (SparkTraits 1000,
 * Fiend 500, Magician 600, Wraith and Black Raven 2000), all RETURN fallbacks (Judge 200, factor 300, Black Raven 400)
 * and all gate enablers (Curser, Saboteur, Wind Spirit, Black Raven). The highlight wrapper never answers by itself
 * and the gate wrappers only veto, like every other wrapper there (Fear 1500, Control Expert, SparkStrength Perfumer
 * 500 and Corrupt Cop), so their nesting order does not matter. {@code remap = false}: Wathe is not a Minecraft class.
 * USEC 开镜本能隐蔽（所有者 2026-10-08；规则见 {@code client/usec/UsecInstinctCloak}）。12 格内开镜的 USEC 对观察者的按键
 * 本能透视隐藏，常驻高亮保留。Wathe 把所有事件结果、HEAD 结果与 RETURN 兜底合并成一个 int 颜色，已无法得知是否需要按键，
 * 因此不能事后过滤颜色；而返回 {@code skip()}（优先级 100）的监听器会连常驻高亮一起抹掉。因此仅对被隐蔽的目标，整个
 * {@code getInstinctHighlight} 在 Wathe 两个按键本能入口读为 false 的状态下运行，即视为观察者松开了本能键：Wathe 丢弃需按键
 * 的事件结果（{@code requiresKeybind && !isInstinctEnabled()}）与无事件结果时的默认逻辑（先检查
 * {@code isInstinctEnabledAndIsKiller()}），依这两个入口判定的附属模组同样丢弃其结果，所有 {@code always} 结果原样返回。
 * <p>顺序：MixinExtras 在任何模组、任意优先级对 {@code WatheClient} 的全部 {@code @Inject} 与 {@code @ModifyReturnValue}
 * 之后应用 {@code @WrapMethod}，因此这些包装包住所有 HEAD 结果（SparkTraits 1000、魔人 500、魔术师 600、冤魂与黑羽鸦
 * 2000）、所有 RETURN 兜底（大法官 200、因子 300、黑羽鸦 400）以及所有入口启用器（诅咒者、破坏者、风精灵、黑羽鸦）。
 * 高亮包装自身从不作答，入口包装只会否决，与该处其他包装（恐惧 1500、控场专家、SparkStrength 调香师 500 与黑警）相同，
 * 因此嵌套顺序无关紧要。{@code remap = false}：Wathe 不是原版类。
 */
@Mixin(value = WatheClient.class, remap = false)
public abstract class UsecInstinctCloakMixin {
    @WrapMethod(method = "getInstinctHighlight")
    private static int sparkwitch$cloakScopedUsec(Entity target, Operation<Integer> original) {
        if (!UsecInstinctCloak.cloaks(target)) {
            return original.call(target);
        }
        return UsecInstinctCloak.evaluate(true, () -> original.call(target));
    }

    @WrapMethod(method = "isInstinctEnabled()Z")
    private static boolean sparkwitch$closeInstinctForCloakedUsec(Operation<Boolean> original) {
        return !UsecInstinctCloak.inCloakedEvaluation() && original.call();
    }

    @WrapMethod(method = "isInstinctEnabledAndIsKiller()Z")
    private static boolean sparkwitch$closeKillerInstinctForCloakedUsec(Operation<Boolean> original) {
        return !UsecInstinctCloak.inCloakedEvaluation() && original.call();
    }
}
