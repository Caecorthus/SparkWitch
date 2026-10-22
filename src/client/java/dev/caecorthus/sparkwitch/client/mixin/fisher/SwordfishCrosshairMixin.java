package dev.caecorthus.sparkwitch.client.mixin.fisher;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.caecorthus.sparkwitch.client.fisher.SwordfishClientTargeting;
import dev.doctor4t.wathe.client.gui.CrosshairRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives the Swordfish Wathe's knife crosshair feel: the target crosshair plus the knife glyph while a player is in reach.
 * The single {@code CROSSHAIR} read is shared with the Taser and Hunter hints; MixinExtras chains them and each answers
 * only for its own item. Presentation only.
 * 让剑鱼拥有 Wathe 刀的准星手感：有玩家在范围内时显示目标准星与刀图标。唯一一次 {@code CROSSHAIR} 读取与电击枪、
 * 猎人的提示共用；MixinExtras 会串联它们，且各自只处理自己的道具。仅用于展示。
 */
@Mixin(CrosshairRenderer.class)
public abstract class SwordfishCrosshairMixin {
    // One showsTarget() per renderCrosshair call: HEAD forgets it, the CROSSHAIR read stores it and the TAIL glyph
    // reuses it (nothing in between changes the main hand or the knife target). When Wathe's own target branch skips
    // the CROSSHAIR read, the TAIL still asks for itself, as before.
    // 每次 renderCrosshair 只调用一次 showsTarget()：HEAD 清除，CROSSHAIR 读取处保存，TAIL 的图标复用（两者之间主手与
    // 刀目标都不会变化）。Wathe 自身目标分支跳过 CROSSHAIR 读取时，TAIL 仍与以前一样自行判断。
    @Unique
    private static boolean sparkwitch$targetKnown;
    @Unique
    private static boolean sparkwitch$showsTarget;

    @Inject(method = "renderCrosshair", at = @At("HEAD"))
    private static void sparkwitch$forgetSwordfishTarget(
            MinecraftClient client,
            ClientPlayerEntity player,
            DrawContext context,
            RenderTickCounter tickCounter,
            CallbackInfo ci
    ) {
        sparkwitch$targetKnown = false;
    }

    @ModifyExpressionValue(
            method = "renderCrosshair",
            at = @At(
                    value = "FIELD",
                    target = "Ldev/doctor4t/wathe/client/gui/CrosshairRenderer;CROSSHAIR:Lnet/minecraft/util/Identifier;"
            )
    )
    private static Identifier sparkwitch$showSwordfishTargetCrosshair(
            Identifier original,
            MinecraftClient client,
            ClientPlayerEntity player,
            DrawContext context,
            RenderTickCounter tickCounter
    ) {
        boolean shows = SwordfishClientTargeting.showsTarget(player);
        sparkwitch$showsTarget = shows;
        sparkwitch$targetKnown = true;
        return shows ? SwordfishClientTargeting.TARGET_CROSSHAIR : original;
    }

    /** Same place and size as Wathe's knife glyph (centre + (-5, 5), 10x7). / 位置与尺寸同 Wathe 刀图标。 */
    @Inject(method = "renderCrosshair", at = @At("TAIL"))
    private static void sparkwitch$drawSwordfishGlyph(
            MinecraftClient client,
            ClientPlayerEntity player,
            DrawContext context,
            RenderTickCounter tickCounter,
            CallbackInfo ci
    ) {
        if (client.options.getPerspective().isFirstPerson() && (sparkwitch$targetKnown
                ? sparkwitch$showsTarget : SwordfishClientTargeting.showsTarget(player))) {
            context.drawGuiTexture(SwordfishClientTargeting.ATTACK_GLYPH,
                    context.getScaledWindowWidth() / 2 - 5, context.getScaledWindowHeight() / 2 + 5, 10, 7);
        }
    }
}
