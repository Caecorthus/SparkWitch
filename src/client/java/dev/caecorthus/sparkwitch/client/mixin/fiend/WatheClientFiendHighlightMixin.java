package dev.caecorthus.sparkwitch.client.mixin.fiend;

import dev.caecorthus.sparkwitch.client.fiend.FiendMomentHighlightClient;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fiend Moment two-way outline. Owner decision D8: during the moment the Fiend sees every living player and everyone
 * sees the Fiend, all in the Fiend's color, so this answer outranks every other outline rule for exactly these two
 * pairs (Wraith privacy, Vendetta, Guardian Shield, Grand Witch fear, SparkTraits HEAD rules, Wathe events). Any other
 * viewer/target pair returns without touching {@code cir}. The server syncs only the moment fact; the client decides
 * nothing but the color.
 * <p>Ordering: Mixin locates every HEAD node before applying any injector, then applies mixins in ascending priority,
 * each callback inserted directly before the original first instruction, so a LOWER priority runs EARLIER (checked
 * against the Mixin 0.8.7 bytecode; see also {@code ControlExpertInstinctGateMixin}). Priority 500 is strictly below
 * every other HEAD on this method: SparkTraits {@code WatheClientMixin} (default 1000), {@code WatheClientFearInstinctMixin}
 * (1500), {@code WraithWatheHighlightMixin} and {@code BlackRavenInstinctPriorityMixin} (2000). Returning early also
 * skips the RETURN fallbacks, which only ever fill {@code -1}. Hidden Wraiths stay vetoed later in
 * {@code MinecraftClient#hasOutline} by {@code WraithMinecraftClientMixin}.
 * 魔人时刻双向描边。所有者决定 D8：时刻期间魔人能看到所有存活玩家、所有人都能看到魔人，且都使用魔人的颜色，
 * 因此仅对这两种配对优先于其他所有描边规则（冤魂隐私、仇杀客、守护护盾、大魔女恐惧、SparkTraits HEAD 规则、Wathe 事件）。
 * 其他观察者/目标配对不触碰 {@code cir} 直接返回。服务端只同步时刻事实，客户端只决定颜色。
 * <p>顺序：Mixin 在应用任何注入器之前先定位所有 HEAD 节点，再按优先级升序应用，每个回调都插在原始首条指令之前，
 * 因此优先级越低越先执行（已按 Mixin 0.8.7 字节码核实，另见 {@code ControlExpertInstinctGateMixin}）。500 严格低于
 * 该方法上其他所有 HEAD：SparkTraits {@code WatheClientMixin}（默认 1000）、{@code WatheClientFearInstinctMixin}（1500）、
 * {@code WraithWatheHighlightMixin} 与 {@code BlackRavenInstinctPriorityMixin}（2000）。提前返回同时跳过只会填补 {@code -1}
 * 的 RETURN 兜底。被隐藏的冤魂仍会在之后由 {@code WraithMinecraftClientMixin} 于 {@code MinecraftClient#hasOutline} 否决。
 */
@Mixin(value = WatheClient.class, remap = false, priority = 500)
public abstract class WatheClientFiendHighlightMixin {
    @Inject(method = "getInstinctHighlight", at = @At("HEAD"), cancellable = true)
    private static void sparkwitch$fiendMomentHighlight(Entity target, CallbackInfoReturnable<Integer> cir) {
        Integer color = FiendMomentHighlightClient.highlight(target);
        if (color != null) {
            cir.setReturnValue(color);
        }
    }
}
