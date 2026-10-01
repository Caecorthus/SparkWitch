package dev.caecorthus.sparkwitch.client.mixin.timestealer;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.caecorthus.sparkwitch.client.timestealer.TimeStealerClientTargeting;
import dev.doctor4t.wathe.client.gui.CrosshairRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Swaps Wathe's plain crosshair for its target crosshair while a ready Clock points at a visible player. The single
 * {@code CROSSHAIR} read is shared with the Hunter shotgun and Taser hints; MixinExtras chains them, and each only
 * answers for its own item. Presentation only: the server decides every theft.
 * 当就绪的时钟指向可见玩家时，把 Wathe 的普通准星换成目标准星。唯一一次 {@code CROSSHAIR} 读取与猎人霰弹枪、
 * 电击枪提示共用；MixinExtras 会串联它们，且各自只处理自己的道具。仅用于展示：是否窃取始终由服务端决定。
 */
@Mixin(CrosshairRenderer.class)
public abstract class TimeStealerClockCrosshairMixin {
    @ModifyExpressionValue(
            method = "renderCrosshair",
            at = @At(
                    value = "FIELD",
                    target = "Ldev/doctor4t/wathe/client/gui/CrosshairRenderer;CROSSHAIR:Lnet/minecraft/util/Identifier;"
            )
    )
    private static Identifier sparkwitch$showClockTargetCrosshair(
            Identifier original,
            MinecraftClient client,
            ClientPlayerEntity player,
            DrawContext context,
            RenderTickCounter tickCounter
    ) {
        return TimeStealerClientTargeting.showsTargetCrosshair(player)
                ? TimeStealerClientTargeting.TARGET_CROSSHAIR
                : original;
    }
}
