package dev.caecorthus.sparkwitch.mixin.sherry;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.caecorthus.sparkwitch.roles.civilian.sherry.SherryDimLight;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import net.minecraft.entity.player.PlayerEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 橘雪莉's dim-light reprieve (PR #206): below light level 10 her task sanity drain is halved. It scales only Wathe's
 * own per-task drain constant inside the mood loop, the Bell Echo scaler's exact seam, so every other {@code setMood}
 * caller (Fear, gun penalties, her own wrong-deduction penalty, task gains) still lands in full. Pinned Wathe 1.5.6
 * bytecode: serverTick and clientTick each have exactly one GETSTATIC {@code MOOD_DRAIN}. Only
 * {@code @ModifyExpressionValue}, never {@code @Redirect}: MixinExtras chains this handler with the Bell Echo scaler on
 * the same read in either order (both multiply), and nothing here touches the Noa / Abyss {@code Map.size()} handlers
 * or the SparkTraits redirects. Non-Sherry players get the constant back unchanged. Both sides read Wathe's synced role
 * map and the local light level, so the client drain prediction follows the server value. The target uses only a
 * Wathe constant, so {@code remap = false} holds in dev and production.
 * 橘雪莉的暗处理智补偿（PR #206）：亮度低于 10 时任务理智消耗减半。只缩放 Wathe 情绪循环内它自身的单任务消耗常量，与敲钟人回响
 * 缩放使用完全相同的接缝，因此其他所有 {@code setMood} 调用方（恐惧、枪械惩罚、她自己的推演失败惩罚、任务回复）仍全额生效。
 * 锁定 Wathe 1.5.6 字节码：serverTick 与 clientTick 各恰有一次 GETSTATIC {@code MOOD_DRAIN}。只用
 * {@code @ModifyExpressionValue}，从不 {@code @Redirect}：MixinExtras 会把本处理器与回响缩放按任意顺序串联（两者都是相乘），
 * 不触碰诺亚/聆渊者的 {@code Map.size()} 处理器与 SparkTraits 的重定向。非雪莉玩家原样拿回常量。双端都读取 Wathe 已同步的
 * 职业表与本地亮度，因此客户端的消耗预测跟随服务端数值。目标只含 Wathe 常量，因此 {@code remap = false} 在开发与生产环境均成立。
 */
@Mixin(value = PlayerMoodComponent.class, remap = false)
public abstract class PlayerMoodComponentSherryDimLightMixin {
    @Shadow
    @Final
    private PlayerEntity player;

    @ModifyExpressionValue(
            method = {"serverTick", "clientTick"},
            at = @At(
                    value = "FIELD",
                    target = "Ldev/doctor4t/wathe/game/GameConstants;MOOD_DRAIN:F",
                    opcode = Opcodes.GETSTATIC
            ),
            require = 2,
            allow = 2
    )
    private float sparkwitch$halveSherryDrainInDimLight(float drain) {
        return SherryDimLight.scaleDrain(player, drain);
    }
}
