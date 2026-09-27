package dev.caecorthus.sparkwitch.client.mixin.controlexpert;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.caecorthus.sparkwitch.client.controlexpert.TaserClientTargeting;
import dev.doctor4t.wathe.client.gui.CrosshairRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Swaps Wathe's plain crosshair for its target crosshair while a ready Taser points at a visible player. The single
 * {@code CROSSHAIR} read is shared with the Hunter shotgun hint; MixinExtras chains both, and each only answers for
 * its own item. Presentation only: the server decides every hit.
 * 当就绪的电击枪指向可见玩家时，把 Wathe 的普通准星换成目标准星。唯一一次 {@code CROSSHAIR} 读取与猎人霰弹枪
 * 提示共用；MixinExtras 会串联两者，且各自只处理自己的道具。仅用于展示：是否命中始终由服务端决定。
 */
@Mixin(CrosshairRenderer.class)
public abstract class TaserCrosshairMixin {
    @ModifyExpressionValue(
            method = "renderCrosshair",
            at = @At(
                    value = "FIELD",
                    target = "Ldev/doctor4t/wathe/client/gui/CrosshairRenderer;CROSSHAIR:Lnet/minecraft/util/Identifier;"
            )
    )
    private static Identifier sparkwitch$showTaserTargetCrosshair(
            Identifier original,
            MinecraftClient client,
            ClientPlayerEntity player,
            DrawContext context,
            RenderTickCounter tickCounter
    ) {
        return TaserClientTargeting.showsTargetCrosshair(player) ? TaserClientTargeting.TARGET_CROSSHAIR : original;
    }
}
