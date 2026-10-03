package dev.caecorthus.sparkwitch.client.mixin.blind;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.client.blind.gate.BlindClientGates;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * C5 outline veto: while the Blind view is active no entity has a vanilla outline, so no Fiend Moment, Judge,
 * Glimmerfish, Final Moment, Depression, Jester or GLOWING outline reaches the Blind. The whole-method wrap encloses
 * Wathe's instinct RETURN and every SparkWitch / other-mod injection on {@code hasOutline}; the Blind's own line art
 * never uses the outline framebuffer.
 * C5 描边否决：盲人视图生效期间任何实体都没有原版描边，魔人时刻、法官、发光鱼、最后时刻、抑郁、小丑与原版发光描边
 * 都不会到达盲人。整方法包裹覆盖 Wathe 的本能 RETURN 以及 SparkWitch / 其他模组在 {@code hasOutline} 上的所有注入；
 * 盲人自己的线稿从不使用描边帧缓冲。
 */
@Mixin(value = MinecraftClient.class, priority = 2000)
public abstract class BlindGateMinecraftClientMixin {
    @WrapMethod(method = "hasOutline")
    private boolean sparkwitch$blindOutlineVeto(Entity entity, Operation<Boolean> original) {
        if (BlindClientGates.viewActive()) {
            return false;
        }
        return original.call(entity);
    }
}
