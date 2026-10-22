package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.client.hud.RoleHudDispatcher;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The one {@code InGameHud.renderMainHud} TAIL seam for the role HUD panels hidden with F1; the ordered layers and
 * their gates live in {@link RoleHudDispatcher#renderMainHudTail}. Default priority 1000 in the JSON slot of the first
 * per-role mixin it replaced, so the block keeps its place relative to other configs' renderMainHud TAIL injects such
 * as Wathe's own HUD (Mixin orders a target's mixins by priority, then global declaration order).
 * 职业 HUD 面板（F1 时隐藏）唯一的 {@code InGameHud.renderMainHud} TAIL 接口；有序各层及其门槛在
 * {@link RoleHudDispatcher#renderMainHudTail} 中。默认优先级 1000，占用其所替换的第一个职业 mixin 的 JSON 位置，因此整体
 * 相对其他配置的 renderMainHud TAIL 注入（如 Wathe 自身 HUD）的位置不变（Mixin 先按优先级、再按全局声明顺序排列）。
 */
@Mixin(InGameHud.class)
public abstract class RoleHudMainHudTailMixin {
    @Inject(method = "renderMainHud", at = @At("TAIL"))
    private void sparkwitch$renderRoleMainHudTail(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        RoleHudDispatcher.renderMainHudTail(context);
    }
}
