package dev.caecorthus.sparkwitch.client.mixin.blackraven.crow;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.caecorthus.sparkwitch.client.blackraven.crow.BlackRavenCrowPilotClient;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Client-only render grid while flying the crow: vanilla {@code setupTerrain} centres the built-section grid on
 * {@code client.player}, so a crow beyond render distance from the body would draw no terrain; while the camera looks
 * through the crow the three player coordinate reads answer the camera position instead (the server re-centres the
 * chunk view on the crow separately). Like {@code SeekerRemoteTerrainGridMixin}, {@code SparkWitchClientMixinPlugin}
 * never applies this class when Sodium (which {@code @Overwrite}s {@code setupTerrain} and already centres on the
 * camera) is loaded, so it holds only these injectors. Chains with the Seeker's and SparkStrength's drone grid hooks.
 * 操控乌鸦期间的纯客户端渲染网格：原版 setupTerrain 以 client.player 为中心布置区块段网格，超出本体渲染距离的乌鸦将画不出地形；镜头通过
 * 乌鸦观看时，三处玩家坐标读取改为返回镜头位置（服务端另行把区块视野以乌鸦为中心）。与 SeekerRemoteTerrainGridMixin 相同，Sodium
 * （以 @Overwrite 替换 setupTerrain 且本就以镜头为中心）存在时 SparkWitchClientMixinPlugin 不应用本类，因此本类只包含这些注入。
 */
@Mixin(WorldRenderer.class)
public abstract class BlackRavenCrowTerrainGridMixin {
    @ModifyExpressionValue(method = "setupTerrain", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;getX()D"))
    private double sparkwitch$crowGridCenterX(double original) {
        if (!BlackRavenCrowPilotClient.isActive()) {
            return original;
        }
        return BlackRavenCrowPilotClient.renderGridCenter(original, 0);
    }

    @ModifyExpressionValue(method = "setupTerrain", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;getY()D"))
    private double sparkwitch$crowGridCenterY(double original) {
        if (!BlackRavenCrowPilotClient.isActive()) {
            return original;
        }
        return BlackRavenCrowPilotClient.renderGridCenter(original, 1);
    }

    @ModifyExpressionValue(method = "setupTerrain", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;getZ()D"))
    private double sparkwitch$crowGridCenterZ(double original) {
        if (!BlackRavenCrowPilotClient.isActive()) {
            return original;
        }
        return BlackRavenCrowPilotClient.renderGridCenter(original, 2);
    }
}
