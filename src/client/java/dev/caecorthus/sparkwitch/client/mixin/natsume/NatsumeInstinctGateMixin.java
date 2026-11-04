package dev.caecorthus.sparkwitch.client.mixin.natsume;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.client.natsume.NatsumeClientState;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 夏目安安 hypnosis: the hypnotised viewer loses instinct entirely (owner spec 2026-11-03), rules in
 * {@link NatsumeClientState#bindsLocalPlayer()}. While bound, {@code getInstinctHighlight} answers -1 for every
 * entity (players, corpses, items, notes) and Wathe's two keyed instinct gates read false, so the instinct light and
 * every key-gated add-on highlight go too. A {@code GetInstinctHighlight} listener cannot do this: a {@code skip()} at
 * any event priority is still pre-empted by HEAD answers (SparkTraits 1000, Fiend 500, Magician 600, Wraith and Black
 * Raven 2000) and refilled by RETURN fallbacks (Judge 200, factor 300, Black Raven 400); the PR's transparent
 * {@code 0x00000000} answer was even drawn as a black outline, since {@code hasOutline} treats anything but -1 as
 * outlined and ignores alpha. Vanilla server GLOWING is not instinct and stays.
 * <p>Ordering: MixinExtras applies {@code @WrapMethod} after every {@code @Inject} and {@code @ModifyReturnValue} on
 * {@code WatheClient}, so these wrappers enclose all of the above; returning without {@code original.call()} skips
 * them. Every wrapper on these methods only vetoes (Fear 1500, Black Raven 3000, USEC cloak, Control Expert,
 * SparkStrength), so their nesting order never changes the answer. {@code remap = false}: Wathe is not a Minecraft
 * class.
 * 夏目安安催眠：被催眠的观察者完全失去本能（所有者规格 2026-11-03），规则见 {@link NatsumeClientState#bindsLocalPlayer()}。
 * 受约束期间 {@code getInstinctHighlight} 对所有实体（玩家、尸体、物品、纸条）返回 -1，Wathe 的两个按键本能入口读为 false，
 * 本能增亮与所有依按键的附属高亮随之关闭。{@code GetInstinctHighlight} 监听器做不到：任何事件优先级的 {@code skip()} 仍会
 * 被 HEAD 结果（SparkTraits 1000、魔人 500、魔术师 600、冤魂与黑羽鸦 2000）抢先，并被 RETURN 兜底（大法官 200、因子 300、
 * 黑羽鸦 400）重新填上；PR 中透明的 {@code 0x00000000} 结果甚至会画成黑色描边，因为 {@code hasOutline} 把 -1 以外的值都
 * 视为描边且忽略透明度。原版服务端发光不属于本能，保持不变。
 * <p>顺序：MixinExtras 在 {@code WatheClient} 上的全部 {@code @Inject} 与 {@code @ModifyReturnValue} 之后应用
 * {@code @WrapMethod}，因此这些包装包住上述全部逻辑；不调用 {@code original.call()} 直接返回即全部跳过。这些方法上的包装都
 * 只会否决（恐惧 1500、黑羽鸦 3000、USEC 隐蔽、控场专家、SparkStrength），嵌套顺序不影响结果。{@code remap = false}：
 * Wathe 不是原版类。
 */
@Mixin(value = WatheClient.class, remap = false)
public abstract class NatsumeInstinctGateMixin {
    @WrapMethod(method = "getInstinctHighlight")
    private static int sparkwitch$vetoHypnotisedInstinct(Entity target, Operation<Integer> original) {
        if (NatsumeClientState.bindsLocalPlayer()) {
            return -1;
        }
        return original.call(target);
    }

    @WrapMethod(method = "isInstinctEnabled()Z")
    private static boolean sparkwitch$gateHypnotisedInstinct(Operation<Boolean> original) {
        return !NatsumeClientState.bindsLocalPlayer() && original.call();
    }

    @WrapMethod(method = "isInstinctEnabledAndIsKiller()Z")
    private static boolean sparkwitch$gateHypnotisedKillerInstinct(Operation<Boolean> original) {
        return !NatsumeClientState.bindsLocalPlayer() && original.call();
    }
}
