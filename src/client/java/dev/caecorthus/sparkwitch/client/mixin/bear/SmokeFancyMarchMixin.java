package dev.caecorthus.sparkwitch.client.mixin.bear;

import dev.caecorthus.sparkwitch.client.bear.smoke.render.SmokeRenderer;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fast/Fancy: runs the BEAR smoke march at {@code WorldRenderer#render}'s {@code "translucent"} profiler swaps (one
 * per graphics branch, right before translucent terrain). The full entity flush has run by then, so main depth holds
 * opaque terrain and every entity but no stained glass, water or translucent hull panels, and smoke behind them is
 * still drawn. The renderer acts only in Fast/Fancy without Iris (Iris defers entity draws past this point); a no-op
 * otherwise. Client-only presentation.
 * Fast/Fancy：在 {@code WorldRenderer#render} 的 {@code "translucent"} 分析器切换处（每个画质分支一处，紧挨半透明地形之前）
 * 执行 BEAR 烟雾步进。此时实体已全部刷新，主深度包含不透明地形与全部实体，但不含彩色玻璃、水和车厢半透明板，因此它们后面的
 * 烟照样画出。渲染器只在未装 Iris 的 Fast/Fancy 下动作（Iris 会把实体绘制推迟到此处之后）；其他情况不做任何事。纯客户端展示。
 */
@Mixin(WorldRenderer.class)
public abstract class SmokeFancyMarchMixin {
    @Inject(method = "render", at = @At(value = "CONSTANT", args = "stringValue=translucent"))
    private void sparkwitch$marchSmokeBeforeTranslucent(CallbackInfo ci) {
        SmokeRenderer.beforeTranslucentTerrain();
    }
}
