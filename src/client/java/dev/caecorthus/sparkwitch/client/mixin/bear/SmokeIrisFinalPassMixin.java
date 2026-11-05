package dev.caecorthus.sparkwitch.client.mixin.bear;

import dev.caecorthus.sparkwitch.client.bear.smoke.shaderpack.SmokeShaderPackBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optional Iris seam (the modpack ships no Iris; players bring their own version): RETURN of
 * {@code IrisRenderingPipeline#finalizeLevelRendering}, i.e. right after the shader pack's composite and final passes
 * wrote the finished world image (hand included) into the main target, inside {@code WorldRenderer#render} and before
 * the entity-outline composite and the HUD. {@code SmokeShaderPackBridge} draws the BEAR smoke there. {@code @Pseudo},
 * {@code remap = false}, {@code require = 0}: with Iris absent or a renamed seam nothing is applied, the bridge sees no
 * liveness and the billboard fallback keeps the smoke opaque (fail closed). Client-only presentation.
 * 可选 Iris 接缝（整合包不带 Iris，玩家自带版本）：{@code IrisRenderingPipeline#finalizeLevelRendering} 的 RETURN，即光影包的合成
 * 与最终处理刚把完成的世界画面（含手部）写入主目标之后，仍在 {@code WorldRenderer#render} 之内，早于实体描边合成与 HUD。
 * {@code SmokeShaderPackBridge} 在此画 BEAR 烟雾。{@code @Pseudo}、{@code remap = false}、{@code require = 0}：没有 Iris 或接缝改名时
 * 什么都不应用，桥接得不到存活信号，由公告板兜底保持烟雾不透明（失败即遮挡）。纯客户端展示。
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.IrisRenderingPipeline", remap = false)
public abstract class SmokeIrisFinalPassMixin {
    @Inject(method = "finalizeLevelRendering()V", at = @At("RETURN"), require = 0, remap = false)
    private void sparkwitch$drawSmokeAfterFinalPass(CallbackInfo ci) {
        SmokeShaderPackBridge.afterFinalPass(this);
    }
}
