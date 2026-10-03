package dev.caecorthus.sparkwitch.client.mixin.blind;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.client.blind.gate.BlindClientGates;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;

/**
 * D9: while the Blind view is active the first-person hand (and the held cane) is never drawn, and the block selection
 * outline is off, so the frame stays black with white lines; the hotbar still shows what is held. Whole-method wraps
 * enclose Wathe's and NoellesRoles' injections on the same methods (the Seeker's {@code renderHand} HEAD cancel stays
 * independent). Client presentation only.
 * D9：盲人视图生效期间不画第一人称手（及手中的盲杖），并关闭方块选择框，画面保持黑底白线；快捷栏仍显示手持物。
 * 整方法包裹覆盖 Wathe 与 NoellesRoles 在同一方法上的注入（搜寻者对 {@code renderHand} 的 HEAD 取消保持独立）。
 * 仅为客户端展示。
 */
@Mixin(value = GameRenderer.class, priority = 2000)
public abstract class BlindGateGameRendererMixin {
    @WrapMethod(method = "renderHand")
    private void sparkwitch$blindHidesHand(Camera camera, float tickDelta, Matrix4f matrix, Operation<Void> original) {
        if (BlindClientGates.viewActive()) {
            return;
        }
        original.call(camera, tickDelta, matrix);
    }

    @WrapMethod(method = "shouldRenderBlockOutline")
    private boolean sparkwitch$blindHidesBlockOutline(Operation<Boolean> original) {
        if (BlindClientGates.viewActive()) {
            return false;
        }
        return original.call();
    }
}
