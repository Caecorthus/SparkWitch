package dev.caecorthus.sparkwitch.client.mixin.blind;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.client.blind.gate.BlindClientGates;
import dev.doctor4t.wathe.client.gui.RoleNameRenderer;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;

/**
 * C6 HUD gate (external Wathe seam): while the Blind view is active, Wathe's {@code RoleNameRenderer.renderHud} draws
 * nothing, so the crosshair player name, corpse name/role/death info and note text never reach the Blind. Every
 * {@code @WrapMethod} wraps the body that exists when it is applied and the higher priority is outermost, so this
 * wrapper encloses Wathe's own logic and every HEAD/TAIL/ModifyArg/WrapOperation from SparkWitch (witch, Black Raven,
 * Insider, Wraith), SparkTraits and NoellesRoles on the same method.
 * C6 HUD 闸门（外部 Wathe 接缝）：盲人视图生效期间，Wathe 的 {@code RoleNameRenderer.renderHud} 不绘制任何内容，
 * 准星玩家名、尸体名字/职业/死因与纸条文字都不会到达盲人。每个 {@code @WrapMethod} 包装其被应用时已存在的方法体，
 * 且优先级更高者在最外层，因此本包装覆盖 Wathe 自身逻辑以及 SparkWitch（魔女、黑羽鸦、内应、冤魂）、SparkTraits
 * 与 NoellesRoles 在同一方法上的所有 HEAD/TAIL/ModifyArg/WrapOperation。
 */
@Mixin(value = RoleNameRenderer.class, priority = 2100)
public abstract class BlindGateRoleNameRendererMixin {
    @WrapMethod(method = "renderHud")
    private static void sparkwitch$blindHidesRoleNames(TextRenderer renderer, ClientPlayerEntity player,
                                                       DrawContext context, RenderTickCounter tickCounter,
                                                       Operation<Void> original) {
        if (BlindClientGates.viewActive()) {
            return;
        }
        original.call(renderer, player, context, tickCounter);
    }
}
