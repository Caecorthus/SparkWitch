package dev.caecorthus.sparkwitch.client.mixin.windspirit;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.client.render.LocalViewerSnapshot;
import dev.caecorthus.sparkwitch.client.windspirit.WindSpiritInstinctClientRules;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Extends only Wathe's native instinct gates for a promoted Wind Spirit. Every input except the instinct key is a
 * local-viewer fact read from the per-pass memo ({@link LocalViewerSnapshot}); the key stays a live per-call read.
 * 仅为晋升风精灵扩展 Wathe 原生本能门禁。除本能键外的所有输入都是本地观察者事实，读取自按 pass 的缓存；本能键仍逐次实时读取。
 */
@Mixin(value = WatheClient.class, remap = false, priority = 1000)
public abstract class WindSpiritInstinctMixin {
    @WrapOperation(
            method = "getInstinctHighlight",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/client/WatheClient;isKiller()Z"
            )
    )
    private static boolean sparkwitch$windSpiritUsesNativeKillerHighlight(
            Operation<Boolean> original
    ) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        return WindSpiritInstinctClientRules.shouldUseNativeKillerHighlight(
                original.call(),
                roleId(player),
                SparkWitchServerConnection.isConfirmedServer(),
                LocalViewerSnapshot.playingAndAlive(player),
                LocalViewerSnapshot.wraithPromoted(player)
        );
    }

    @Inject(method = "isInstinctEnabledAndIsKiller", at = @At("HEAD"), cancellable = true)
    private static void sparkwitch$windSpiritCanUseInstinct(
            CallbackInfoReturnable<Boolean> cir
    ) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return;
        }
        Identifier roleId = roleId(player);
        // Every other role fails the rule, so it leaves before the per-call key read.
        // 其他职业必然不满足规则，因此在逐次按键读取之前返回。
        if (!WindSpiritInstinctClientRules.hasNativeInstinctVisuals(roleId)) {
            return;
        }
        if (WindSpiritInstinctClientRules.shouldEnableNativeInstinct(
                roleId,
                SparkWitchServerConnection.isConfirmedServer(),
                LocalViewerSnapshot.playingAndAlive(player),
                LocalViewerSnapshot.wraithPromoted(player),
                WatheClient.instinctKeybind.isPressed()
        )) {
            cir.setReturnValue(true);
        }
    }

    @ModifyExpressionValue(
            method = "getInstinctHighlight",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/math/MathHelper;hsvToRgb(FFF)I"
            )
    )
    private static int sparkwitch$useWindSpiritColorForNativeKillerTarget(int originalColor) {
        return resolveNativePlayerColor(originalColor);
    }

    @ModifyConstant(
            method = "getInstinctHighlight",
            constant = @Constant(intValue = 5168437)
    )
    private static int sparkwitch$useWindSpiritColorForNativeCivilianTarget(int originalColor) {
        return resolveNativePlayerColor(originalColor);
    }

    private static int resolveNativePlayerColor(int originalColor) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        return WindSpiritInstinctClientRules.resolveNativePlayerHighlightColor(
                originalColor,
                roleId(player),
                SparkWitchServerConnection.isConfirmedServer(),
                LocalViewerSnapshot.playingAndAlive(player),
                LocalViewerSnapshot.wraithPromoted(player)
        );
    }

    private static @Nullable Identifier roleId(ClientPlayerEntity player) {
        if (player == null) {
            return null;
        }
        Role role = LocalViewerSnapshot.role(player);
        return role == null ? null : role.identifier();
    }
}
