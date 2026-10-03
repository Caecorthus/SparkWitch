package dev.caecorthus.sparkwitch.client.mixin.potiongunner;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.client.potiongunner.PotionScopeClient;
import dev.caecorthus.sparkwitch.client.potiongunner.PotionScopeOverlay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * While scoped, the crosshair layer draws the scope (mask, reticle, range ticks, loaded shell) instead of Wathe's
 * {@code CrosshairRenderer}; otherwise it calls through unchanged. Priority 1100 keeps this {@code @WrapMethod}
 * outside Wathe's default-priority wrapper, which never calls the original during a game (the Seeker CCTV pattern).
 * The layer is already skipped under F1; the overlay also checks {@code hudHidden} itself.
 * 开镜时，准星层绘制瞄准镜（遮罩、十字线、射程刻度、已装填弹种）而非 Wathe 的 {@code CrosshairRenderer}；否则原样
 * 调用。优先级 1100 使该 {@code @WrapMethod} 位于 Wathe 默认优先级包装器之外（后者在对局中从不调用原方法，参照搜寻者
 * CCTV）。按 F1 时该层本就不绘制；遮罩自身也会检查 {@code hudHidden}。
 */
@Mixin(value = InGameHud.class, priority = 1100)
public abstract class PotionScopeInGameHudMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @WrapMethod(method = "renderCrosshair")
    private void sparkwitch$renderScope(DrawContext context, RenderTickCounter tickCounter, Operation<Void> original) {
        if (!PotionScopeClient.isScoped(client)) {
            original.call(context, tickCounter);
            return;
        }
        PotionScopeOverlay.render(context, tickCounter);
    }
}
