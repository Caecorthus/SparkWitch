package dev.caecorthus.sparkwitch.client.mixin.scope;

import dev.caecorthus.sparkwitch.client.scope.ScopePictureInPicture;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-only seams of the PiP lens pass ({@code ScopePictureInPicture}), which calls {@code WorldRenderer#render} a
 * second time per frame while scoped:
 * <ul>
 *   <li>{@code render} HEAD (read-only, like Fabric's own HEAD hook): remembers the main pass's projection (bobbing,
 *   hurt tilt, recoil and nausea included), which the lens narrows in clip space.</li>
 *   <li>around the {@code setupTerrain} call in {@code render}: during the lens pass only, {@code RenderSystem}'s
 *   projection is the main pass's for the call and the lens's again afterwards. Sodium's overwritten
 *   {@code setupTerrain} marks its visibility graph dirty whenever that projection differs from its previous call, so
 *   the lens would otherwise force two graph rebuilds per frame (one for the lens, one for the next main pass). The sky
 *   before it and everything after it draw with the lens projection. These target the INVOKE in {@code render}, never
 *   the overwritten method itself.</li>
 *   <li>after each {@code Framebuffer#beginWrite(Z)} in {@code render}: during the lens pass only, puts the viewport
 *   back on the lens target. Vanilla re-binds {@code client.getFramebuffer()} with {@code beginWrite(false)} right
 *   after clearing the window-sized outline target and after its outline post pass, both of which leave a window-sized
 *   viewport.</li>
 *   <li>{@code renderClouds} / {@code renderWeather} HEAD: cancelled during the lens pass only (the lens skips clouds
 *   and weather). Fabric injects HEAD hooks into both, so no renderer mod overwrites them.</li>
 * </ul>
 * Non-cancellable {@code @Inject}s inside {@code render} add no captured locals, so Wathe's {@code @Local} and
 * NoellesRoles' {@code CAPTURE_FAILHARD} in the same method are unaffected. Outside the lens pass nothing changes.
 * 画中画镜内渲染（{@code ScopePictureInPicture}）的纯客户端接缝；开镜时它每帧第二次调用 {@code WorldRenderer#render}：
 * {@code render} HEAD（只读，与 Fabric 自己的 HEAD 钩子相同）记录主渲染的投影（含视角摇晃、受伤倾斜、后坐力与反胃），镜内在
 * 裁剪空间中收窄它；{@code render} 中 {@code setupTerrain} 调用前后，仅在镜内渲染期间让 {@code RenderSystem} 的投影在该调用
 * 期间为主渲染的投影、之后恢复为镜内投影——Sodium 覆盖的 {@code setupTerrain} 只要该投影与上次调用不同就把可见性图标记为脏，
 * 否则镜内渲染会让每帧重建两次图（镜内一次、下一次主渲染一次）；它之前的天空与之后的一切都用镜内投影绘制；这两个钩子作用于
 * {@code render} 中的调用指令，从不作用于被覆盖的方法本身；{@code render} 中每次 {@code Framebuffer#beginWrite(Z)} 之后，仅在镜内渲染期间把视口恢复到镜内目标——原版
 * 清除窗口尺寸的描边目标后、以及描边后处理之后都会用 {@code beginWrite(false)} 重新绑定 {@code client.getFramebuffer()}，两者都会
 * 留下窗口尺寸的视口；{@code renderClouds} / {@code renderWeather} HEAD 仅在镜内渲染期间取消（镜内不画云与天气），Fabric 在两者
 * 都注入了 HEAD 钩子，因此没有渲染模组覆盖它们。{@code render} 内的不可取消 {@code @Inject} 不新增被捕获的局部变量，
 * 不影响同一方法中 Wathe 的 {@code @Local} 与 NoellesRoles 的 {@code CAPTURE_FAILHARD}。镜内渲染之外不做任何改变。
 */
@Mixin(WorldRenderer.class)
public abstract class ScopeWorldRendererMixin {
    private static final String TERRAIN_SETUP = "Lnet/minecraft/client/render/WorldRenderer;setupTerrain("
            + "Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/Frustum;ZZ)V";

    @Inject(method = "render", at = @At("HEAD"))
    private void sparkwitch$captureScopeProjection(RenderTickCounter tickCounter, boolean renderBlockOutline,
                                                   Camera camera, GameRenderer gameRenderer,
                                                   LightmapTextureManager lightmapTextureManager,
                                                   Matrix4f positionMatrix, Matrix4f projectionMatrix,
                                                   CallbackInfo ci) {
        ScopePictureInPicture.captureMainProjection(projectionMatrix);
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = TERRAIN_SETUP))
    private void sparkwitch$lensTerrainSetupStart(CallbackInfo ci) {
        ScopePictureInPicture.beforeTerrainSetup();
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = TERRAIN_SETUP, shift = At.Shift.AFTER))
    private void sparkwitch$lensTerrainSetupEnd(CallbackInfo ci) {
        ScopePictureInPicture.afterTerrainSetup();
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gl/Framebuffer;beginWrite(Z)V", shift = At.Shift.AFTER))
    private void sparkwitch$keepLensViewport(CallbackInfo ci) {
        ScopePictureInPicture.restoreLensViewport();
    }

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$skipLensClouds(CallbackInfo ci) {
        if (ScopePictureInPicture.isRenderingLens()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderWeather", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$skipLensWeather(CallbackInfo ci) {
        if (ScopePictureInPicture.isRenderingLens()) {
            ci.cancel();
        }
    }
}
