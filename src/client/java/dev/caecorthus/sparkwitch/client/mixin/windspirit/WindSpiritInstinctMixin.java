package dev.caecorthus.sparkwitch.client.mixin.windspirit;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.client.windspirit.WindSpiritInstinctClientRules;
import dev.caecorthus.sparkwitch.client.render.WraithClientState;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.game.GameFunctions;
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

/** Extends only Wathe's native instinct gates for a promoted Wind Spirit. / 仅为晋升风精灵扩展 Wathe 原生本能门禁。 */
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
                GameFunctions.isPlayerPlayingAndAlive(player),
                WraithClientState.isPromoted(player)
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
        if (WindSpiritInstinctClientRules.shouldEnableNativeInstinct(
                roleId(player),
                SparkWitchServerConnection.isConfirmedServer(),
                GameFunctions.isPlayerPlayingAndAlive(player),
                WraithClientState.isPromoted(player),
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
                GameFunctions.isPlayerPlayingAndAlive(player),
                WraithClientState.isPromoted(player)
        );
    }

    private static @Nullable Identifier roleId(ClientPlayerEntity player) {
        if (player == null) {
            return null;
        }
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        return role == null ? null : role.identifier();
    }
}
