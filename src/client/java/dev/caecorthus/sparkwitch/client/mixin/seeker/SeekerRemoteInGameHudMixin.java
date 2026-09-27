package dev.caecorthus.sparkwitch.client.mixin.seeker;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.client.seeker.remote.SeekerCctvOverlay;
import dev.caecorthus.sparkwitch.client.seeker.remote.SeekerRemoteViewClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-only HUD policy while the Seeker views remotely (gated first on {@link SeekerRemoteViewClient#isActive()}):
 * <ul>
 *   <li>{@code renderCrosshair}: {@code @WrapMethod} draws the CCTV reticle instead of Wathe's
 *   {@code CrosshairRenderer}, whose knife/gun hints come from the body and would lie from the car.</li>
 *   <li>{@code getCameraPlayer} HEAD: returns the real local player, so the hotbar stays although the camera entity
 *   is the car or viewpoint (NoellesRoles Spirit Walker precedent).</li>
 * </ul>
 * Ordering (verified against MixinExtras 0.5 / Mixin 0.8.7 bytecode, not assumed): injectors apply in ascending mixin
 * priority, and each {@code @WrapMethod} wraps whatever body exists when it is offered, so the HIGHER-priority wrapper
 * is outermost. Wathe's {@code InGameHudMixin} wraps {@code renderCrosshair} at the default 1000 and never calls the
 * original during a game, so this mixin uses priority 1100 to stay outside it (900 would never run).
 * 搜寻者遥控观看期间的纯客户端 HUD 策略（第一步判断 {@link SeekerRemoteViewClient#isActive()}）：
 * {@code renderCrosshair} 用 {@code @WrapMethod} 绘制 CCTV 准星，替代 Wathe 的 {@code CrosshairRenderer}
 * （其刀/枪提示基于本体，从小车视角看是错误的）；{@code getCameraPlayer} 在 HEAD 返回真实本地玩家，
 * 使相机实体为小车或视点时快捷栏仍然显示（参照 NoellesRoles 灵行者）。
 * 顺序（已按 MixinExtras 0.5 / Mixin 0.8.7 字节码核实，而非假设）：注入器按 mixin 优先级升序应用，每个
 * {@code @WrapMethod} 包装其被登记时已存在的方法体，因此优先级更高的包装器位于最外层。Wathe 的
 * {@code InGameHudMixin} 以默认 1000 包装 {@code renderCrosshair}，且在对局中从不调用原方法，因此本 mixin
 * 使用 1100 以位于其外层（900 将永远不会执行）。
 */
@Mixin(value = InGameHud.class, priority = 1100)
public abstract class SeekerRemoteInGameHudMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @WrapMethod(method = "renderCrosshair")
    private void sparkwitch$renderCctvCrosshair(DrawContext context, RenderTickCounter tickCounter,
                                                Operation<Void> original) {
        if (!SeekerRemoteViewClient.isActive()) {
            original.call(context, tickCounter);
            return;
        }
        SeekerCctvOverlay.renderCrosshair(context);
    }

    @Inject(method = "getCameraPlayer", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$keepBodyHotbar(CallbackInfoReturnable<PlayerEntity> cir) {
        if (!SeekerRemoteViewClient.isActive()) {
            return;
        }
        if (client.player != null) {
            cir.setReturnValue(client.player);
        }
    }
}
