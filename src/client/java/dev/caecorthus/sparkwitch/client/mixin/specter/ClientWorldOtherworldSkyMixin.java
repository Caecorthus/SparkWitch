package dev.caecorthus.sparkwitch.client.mixin.specter;

import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterOtherworldComponent;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Turns the sky (and the horizon fog that reads the same colour) crimson during the Otherworld, ramped over the same
 * first five seconds as the screen filter. It applies to everyone in the window, including players whose screen filter
 * yields to another shader; it snaps back when the window closes.
 * 里世界期间把天空（以及读取同一颜色的地平线雾）调成猩红，与画面滤镜一样在前 5 秒渐变。
 * 对所有处于窗口中的玩家生效（包括画面滤镜让路给其他着色器的人）；窗口结束时恢复。
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
