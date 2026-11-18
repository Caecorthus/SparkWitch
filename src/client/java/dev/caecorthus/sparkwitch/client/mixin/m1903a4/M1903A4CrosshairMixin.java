package dev.caecorthus.sparkwitch.client.mixin.m1903a4;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.caecorthus.sparkwitch.client.m1903a4.M1903A4CrosshairTargeting;
import dev.doctor4t.wathe.client.gui.CrosshairRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Swaps Wathe's plain crosshair for its target crosshair while a charging M1903A4 points at a visible player (Wathe's
 * revolver pair, no new textures). The single {@code CROSSHAIR} read is shared with the AXMC, Taser, Hunter shotgun,
 * Shriek Gun, Clock and Swordfish hints; MixinExtras chains them, and each only answers for its own item. Presentation
 * only: the server decides every shot.
 * 当正在蓄力的 M1903A4 指向可见玩家时，把 Wathe 的普通准星换成目标准星（Wathe 左轮的一对准星，不新增贴图）。唯一一次
 * {@code CROSSHAIR} 读取与 AXMC、电击枪、猎人霰弹枪、啸音铳、时钟和剑鱼提示共用；MixinExtras 会串联它们，且各自只处理
 * 自己的道具。仅用于展示：每一枪都由服务端判定。
 */
@Mixin(CrosshairRenderer.class)
public abstract class M1903A4CrosshairMixin {
    @ModifyExpressionValue(
            method = "renderCrosshair",
            at = @At(
                    value = "FIELD",
                    target = "Ldev/doctor4t/wathe/client/gui/CrosshairRenderer;CROSSHAIR:Lnet/minecraft/util/Identifier;"
            )
    )
    private static Identifier sparkwitch$showM1903a4TargetCrosshair(
            Identifier original,
            MinecraftClient client,
            ClientPlayerEntity player,
            DrawContext context,
            RenderTickCounter tickCounter
    ) {
        return M1903A4CrosshairTargeting.showsTargetCrosshair(player)
                ? M1903A4CrosshairTargeting.TARGET_CROSSHAIR
                : original;
    }
}
