package dev.caecorthus.sparkwitch.client.mixin.seeker;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.caecorthus.sparkwitch.client.seeker.remote.SeekerRemoteViewClient;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Client-only render grid for the remote view (unlimited range, 2026-10-04): vanilla {@code setupTerrain} centres the
 * built-section grid ({@code BuiltChunkStorage.updateCameraPosition}) on {@code client.player}, so a car or camera
 * beyond render distance from the body would draw no terrain (nor entities, which need ready sections). While the
 * camera looks through the Seeker's bound focus, the three player coordinate reads return the camera position instead
 * ({@link SeekerRemoteViewClient#renderGridCenter}); the server re-centres the chunk view on the device separately.
 * Pass-through otherwise, including while connecting with the camera still on the body.
 * <p>Sodium {@code @Overwrite}s {@code setupTerrain}; Mixin refuses any injector into an overwritten method at apply
 * time, even with {@code require} set to 0, so {@code SparkWitchClientMixinPlugin} never applies this class when
 * Sodium is loaded (Sodium already builds and culls sections around the camera). This class therefore holds only these
 * injectors. Plain {@code INVOKE} selectors are remapped by Loom; {@code @ModifyExpressionValue} chains with
 * SparkStrength's drone pilot grid mixin on the same calls, and each passes the value through outside its own view.
 * 遥控视角的纯客户端渲染网格（无限距离，2026-10-04）：原版 {@code setupTerrain} 以 {@code client.player} 为中心布置已构建区块段
 * 网格，小车或摄像头超出本体渲染距离后将画不出地形（也画不出需要就绪区块段的实体）。镜头通过搜寻者已绑定的焦点观看时，
 * 三处玩家坐标读取改为返回镜头位置（{@link SeekerRemoteViewClient#renderGridCenter}）；服务端另行把区块视野重新以设备为中心。
 * 其他情况（包括相机仍在本体上的连接阶段）原样传递。
 * Sodium 以 {@code @Overwrite} 替换 {@code setupTerrain}；Mixin 在应用阶段拒绝向被覆盖的方法注入，即使把 {@code require} 设为 0 也无效，
 * 因此 Sodium 存在时 {@code SparkWitchClientMixinPlugin} 不应用本类（Sodium 本就以镜头为中心构建与剔除区块段）。本类只包含这些
 * 注入。普通 {@code INVOKE} 选择器会被 Loom 重映射；{@code @ModifyExpressionValue} 可与 SparkStrength 无人机驾驶的网格 mixin
 * 在同一调用上链式共存，双方在各自视角之外都原样传递数值。
 */
@Mixin(WorldRenderer.class)
public abstract class SeekerRemoteTerrainGridMixin {
    @ModifyExpressionValue(method = "setupTerrain", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;getX()D"))
    private double sparkwitch$seekerGridCenterX(double original) {
        if (!SeekerRemoteViewClient.isActive()) {
            return original;
        }
        return SeekerRemoteViewClient.renderGridCenter(original, 0);
    }

    @ModifyExpressionValue(method = "setupTerrain", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;getY()D"))
    private double sparkwitch$seekerGridCenterY(double original) {
        if (!SeekerRemoteViewClient.isActive()) {
            return original;
        }
        return SeekerRemoteViewClient.renderGridCenter(original, 1);
    }

    @ModifyExpressionValue(method = "setupTerrain", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;getZ()D"))
    private double sparkwitch$seekerGridCenterZ(double original) {
        if (!SeekerRemoteViewClient.isActive()) {
            return original;
        }
        return SeekerRemoteViewClient.renderGridCenter(original, 2);
    }
}
