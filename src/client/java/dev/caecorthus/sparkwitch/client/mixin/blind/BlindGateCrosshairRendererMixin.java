package dev.caecorthus.sparkwitch.client.mixin.blind;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.client.blind.gate.BlindClientGates;
import dev.caecorthus.sparkwitch.client.blind.gate.BlindCrosshair;
import dev.doctor4t.wathe.client.gui.CrosshairRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;

/**
 * C6 crosshair gate (external Wathe seam): while the Blind view is active, Wathe's {@code renderCrosshair} draws only
 * the plain reticle. The outermost whole-method wrap (higher priority is outermost) encloses Wathe's revolver,
 * derringer, knife and bat target logic and every other mod's HEAD replacement, target-reticle swap and TAIL glyph on
 * this method (SparkWitch Taser, Hunter shotgun, Time Stealer clock, Swordfish, Grand Witch sword; SparkStrength
 * reagent/serum; SparkTraits marksman/forced melee), so none can flag a player in line.
 * C6 准星闸门（外部 Wathe 接缝）：盲人视图生效期间，Wathe 的 {@code renderCrosshair} 只绘制普通准星。最外层的整方法
 * 包裹（优先级更高者在最外层）覆盖 Wathe 左轮、德林格、刀与棒的目标逻辑，以及其他模组在此方法上的所有 HEAD 替换、
 * 目标准星替换与 TAIL 图标（SparkWitch 电击枪、猎人霰弹枪、窃时者怀表、剑鱼、大魔女之剑；SparkStrength 试剂/血清；
 * SparkTraits 神射手/强制近战），因此都无法标出视线上的玩家。
 */
@Mixin(value = CrosshairRenderer.class, priority = 2100)
public abstract class BlindGateCrosshairRendererMixin {
    @WrapMethod(method = "renderCrosshair")
    private static void sparkwitch$blindPlainCrosshair(MinecraftClient client, ClientPlayerEntity player,
                                                       DrawContext context, RenderTickCounter tickCounter,
                                                       Operation<Void> original) {
        if (BlindClientGates.viewActive()) {
            BlindCrosshair.render(client, context);
            return;
        }
        original.call(client, player, context, tickCounter);
    }
}
