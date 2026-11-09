package dev.caecorthus.sparkwitch.client.mixin.bear;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.client.bear.smoke.shaderpack.SmokeEntityOcclusion;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Client-only BEAR smoke outline veto for Iris shader packs ({@code SmokeEntityOcclusion}): an entity behind smoke has
 * no outline. A whole-method wrap, so it encloses Wathe's instinct RETURN and every other {@code hasOutline} injection
 * (instinct, faction colours, vanilla glowing, corpses). It only vetoes, so its nesting with the Blind gate (2000) and
 * the Black Raven veto (1900) does not matter. Safe off the render thread (Entity Culling's CullThread): it reads a
 * published set only, which is empty without a pack or smoke, so vanilla, Sodium and Iris without a pack are unchanged.
 * 纯客户端的 BEAR 烟雾描边否决（Iris 光影包，{@code SmokeEntityOcclusion}）：烟后面的实体没有描边。整方法包装，因此包住 Wathe 的
 * 本能 RETURN 与 {@code hasOutline} 上的其他全部注入（本能、阵营颜色、原版发光、尸体）。它只会否决，因此与盲人门禁（2000）
 * 及黑羽鸦否决（1900）的嵌套顺序无关。可在渲染线程之外安全调用（Entity Culling 的 CullThread）：只读取已发布的集合，
 * 没有光影包或没有烟时集合为空，因此原版、Sodium 与未开光影的 Iris 不受影响。
 */
@Mixin(value = MinecraftClient.class, priority = 1800)
public abstract class SmokeOutlineVetoMixin {
    @WrapMethod(method = "hasOutline")
    private boolean sparkwitch$vetoSmokeHiddenOutline(Entity entity, Operation<Boolean> original) {
        if (SmokeEntityOcclusion.hides(entity)) {
            return false;
        }
        return original.call(entity);
    }
}
