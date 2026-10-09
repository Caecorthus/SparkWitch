package dev.caecorthus.sparkwitch.client.mixin.usec;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.caecorthus.sparkwitch.client.usec.UsecCrosshairTargeting;
import dev.doctor4t.wathe.client.gui.CrosshairRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Swaps Wathe's plain crosshair for its target crosshair while a ready, chambered AXMC at the hip points at a visible
 * player, as Wathe does for the revolver. The single {@code CROSSHAIR} read is shared with the Taser, Hunter shotgun,
 * Shriek Gun, Clock and Swordfish hints; MixinExtras chains them, and each only answers for its own item. While scoped
 * the scope draws instead of this method, and the rule says no anyway. Presentation only: the server decides every shot.
 * 当腰射位就绪且已上膛的 AXMC 指向可见玩家时，把 Wathe 的普通准星换成目标准星，与 Wathe 对左轮的处理相同。唯一一次
 * {@code CROSSHAIR} 读取与电击枪、猎人霰弹枪、啸音铳、时钟和剑鱼提示共用；MixinExtras 会串联它们，且各自只处理自己的道具。
 * 开镜时由瞄准镜绘制而不调用本方法，规则本身也会拒绝。仅用于展示：每一枪都由服务端判定。
 */
@Mixin(CrosshairRenderer.class)
public abstract class UsecCrosshairMixin {
    @ModifyExpressionValue(
            method = "renderCrosshair",
            at = @At(
                    value = "FIELD",
                    target = "Ldev/doctor4t/wathe/client/gui/CrosshairRenderer;CROSSHAIR:Lnet/minecraft/util/Identifier;"
            )
    )
    private static Identifier sparkwitch$showUsecTargetCrosshair(
            Identifier original,
            MinecraftClient client,
            ClientPlayerEntity player,
            DrawContext context,
            RenderTickCounter tickCounter
    ) {
        return UsecCrosshairTargeting.showsTargetCrosshair(player) ? UsecCrosshairTargeting.TARGET_CROSSHAIR : original;
    }
}
