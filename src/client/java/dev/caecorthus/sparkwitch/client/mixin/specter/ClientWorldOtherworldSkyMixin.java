package dev.caecorthus.sparkwitch.client.mixin.specter;

import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterOtherworldComponent;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-only presentation: blends {@code ClientWorld#getSkyColor} toward crimson during the Otherworld, ramped over the
 * same first five seconds as the screen filter. Two vanilla readers see it: the sky dome, and the fog/clear colour,
 * which {@code BackgroundRenderer} pulls part-way toward the sky colour. Wathe's {@code WorldRendererMixin} skips the
 * sky dome while the train moves (except at sundown), so on a moving train this mostly tints the horizon fog and clear
 * colour; the full crimson dome only shows on a stopped train or at sundown. It applies to every client in the window,
 * dead or alive and including players whose screen filter yields to another shader, and snaps back as soon as the
 * window closes (the screen filter fades over five seconds instead).
 * 纯客户端表现：里世界期间把 {@code ClientWorld#getSkyColor} 混向猩红，与画面滤镜一样在前 5 秒渐变。原版有两处读取：
 * 天空穹顶，以及雾/清屏颜色（{@code BackgroundRenderer} 会把它部分混向天空色）。Wathe 的 {@code WorldRendererMixin}
 * 在列车行驶时（日落除外）跳过天空穹顶，因此行驶中的列车上主要是地平线雾与清屏颜色被染红；完整的猩红天空只在列车
 * 停止或日落时出现。窗口内所有客户端都生效（无论生死，也包括画面滤镜让路给其他着色器的人）；窗口一结束立即恢复
 * （画面滤镜则在 5 秒内渐隐）。
 */
@Mixin(ClientWorld.class)
public abstract class ClientWorldOtherworldSkyMixin {
    private static final double CRIMSON_RED = 0.45D;
    private static final double CRIMSON_GREEN = 0.02D;
    private static final double CRIMSON_BLUE = 0.03D;

    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void sparkwitch$crimsonOtherworldSky(
            Vec3d cameraPos,
            float tickDelta,
            CallbackInfoReturnable<Vec3d> cir
    ) {
        ClientWorld world = (ClientWorld) (Object) this;
        SpecterOtherworldComponent otherworld = SpecterOtherworldComponent.KEY.get(world);
        if (!otherworld.isActive()) {
            return;
        }
        Vec3d vanilla = cir.getReturnValue();
        if (vanilla == null) {
            return;
        }
        float ramp = Math.min(1.0F, (otherworld.getElapsedTicks() + tickDelta) / 100.0F);
        cir.setReturnValue(new Vec3d(
                vanilla.x + (CRIMSON_RED - vanilla.x) * ramp,
                vanilla.y + (CRIMSON_GREEN - vanilla.y) * ramp,
                vanilla.z + (CRIMSON_BLUE - vanilla.z) * ramp
        ));
    }
}
