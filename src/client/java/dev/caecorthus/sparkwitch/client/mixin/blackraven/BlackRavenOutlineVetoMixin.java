package dev.caecorthus.sparkwitch.client.mixin.blackraven;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.client.blackraven.BlackRavenInstinctVeto;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Final outline veto for a living Black Raven (plan §3.2), like {@code WraithMinecraftClientMixin} but as a whole-method
 * wrap, so it also encloses Wathe's instinct RETURN and every other mod's {@code hasOutline} injection: no outline of any
 * source (instinct, always-on marks, vanilla GLOWING) is drawn on the Raven for a non-exempt viewer. It only vetoes, so
 * its nesting with the SparkStrength wrappers does not matter; priority 1900 keeps it inside the Blind's priority-2000
 * gate, which stays the outermost SparkWitch {@code hasOutline} wrap.
 * 存活黑羽鸦的最终描边否决（计划 §3.2），与 {@code WraithMinecraftClientMixin} 相同但采用整方法包装，因此也包住 Wathe 的
 * 本能 RETURN 与其他模组在 {@code hasOutline} 上的全部注入：对非豁免观察者，黑羽鸦身上不绘制任何来源的描边（本能、常驻标记、
 * 原版发光）。它只会否决，因此与 SparkStrength 包装的嵌套顺序无关；优先级 1900 使其位于盲人优先级 2000 的门禁之内，
 * 后者仍是 SparkWitch 最外层的 {@code hasOutline} 包装。
 */
@Mixin(value = MinecraftClient.class, priority = 1900)
public abstract class BlackRavenOutlineVetoMixin {
    @WrapMethod(method = "hasOutline")
    private boolean sparkwitch$vetoBlackRavenOutline(Entity entity, Operation<Boolean> original) {
        if (BlackRavenInstinctVeto.hides(MinecraftClient.getInstance().player, entity)) {
            return false;
        }
        return original.call(entity);
    }
}
