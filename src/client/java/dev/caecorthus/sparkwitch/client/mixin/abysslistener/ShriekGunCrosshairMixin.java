package dev.caecorthus.sparkwitch.client.mixin.abysslistener;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.caecorthus.sparkwitch.client.abysslistener.ShriekGunClientTargeting;
import dev.doctor4t.wathe.client.gui.CrosshairRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Swaps Wathe's plain crosshair for its target crosshair while a ready Shriek Gun points at a visible player. The
 * single {@code CROSSHAIR} read is shared with the Taser and Hunter shotgun hints; MixinExtras chains them, and each
 * only answers for its own item. Presentation only: the server decides every hit, and the hint never differs between
 * allies and enemies.
 * 当就绪的啸音铳指向可见玩家时，把 Wathe 的普通准星换成目标准星。唯一一次 {@code CROSSHAIR} 读取与电击枪、猎人霰弹枪
 * 提示共用；MixinExtras 会串联它们，且各自只处理自己的道具。仅用于展示：是否命中始终由服务端决定，提示对队友与敌人从无区别。
 */
@Mixin(CrosshairRenderer.class)
public abstract class ShriekGunCrosshairMixin {
    @ModifyExpressionValue(
            method = "renderCrosshair",
            at = @At(
                    value = "FIELD",
                    target = "Ldev/doctor4t/wathe/client/gui/CrosshairRenderer;CROSSHAIR:Lnet/minecraft/util/Identifier;"
            )
    )
    private static Identifier sparkwitch$showShriekGunTargetCrosshair(
            Identifier original,
            MinecraftClient client,
            ClientPlayerEntity player,
            DrawContext context,
            RenderTickCounter tickCounter
    ) {
        return ShriekGunClientTargeting.showsTargetCrosshair(player)
                ? ShriekGunClientTargeting.TARGET_CROSSHAIR : original;
    }
}
