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
 * The one {@code InGameHud.render} TAIL seam for the role HUD panels drawn even under F1; the ordered layers and their
 * gates live in {@link RoleHudDispatcher#renderTail}. It keeps the default priority 1000 and the JSON slot of the first
 * per-role mixin it replaced: Mixin applies a target's mixins by (priority, global declaration order) and each TAIL
 * inject lands just before the final RETURN, so the panels still run as one block at the same place relative to Fabric
 * API's {@code HudRenderCallback} (also a priority-1000 render TAIL inject, from another config) and other mods' TAIL
 * injects, and before the priority-1100 Holy Flash mask.
 * 职业 HUD 面板（F1 下仍绘制）唯一的 {@code InGameHud.render} TAIL 接口；有序各层及其门槛在
 * {@link RoleHudDispatcher#renderTail} 中。保持默认优先级 1000，并占用其所替换的第一个职业 mixin 的 JSON 位置：Mixin
 * 按（优先级，全局声明顺序）应用同一目标的 mixin，每个 TAIL 注入都插在最后一个 RETURN 之前，因此这些面板仍作为一个整体，
 * 相对 Fabric API 的 {@code HudRenderCallback}（另一配置中同为优先级 1000 的 render TAIL 注入）与其他模组 TAIL 注入的位置
 * 不变，并位于优先级 1100 的圣光弹遮罩之前。
 */
@Mixin(InGameHud.class)
public abstract class RoleHudRenderTailMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void sparkwitch$renderRoleHudTail(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        RoleHudDispatcher.renderTail((InGameHud) (Object) this, context);
    }
}
