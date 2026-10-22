package dev.caecorthus.sparkwitch.client.mixin.saboteur;

import dev.caecorthus.sparkwitch.client.render.LocalViewerSnapshot;
import dev.caecorthus.sparkwitch.client.render.WraithClientState;
import dev.caecorthus.sparkwitch.client.saboteur.SaboteurInstinctClientRules;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Extends Wathe's native killer instinct below the existing confusion and fear vetoes. / 在既有混乱与恐惧否决之后扩展 wathe 原生杀手本能。 */
@Mixin(value = WatheClient.class, remap = false, priority = 1000)
public abstract class SaboteurInstinctMixin {
    @Inject(method = "isInstinctEnabledAndIsKiller()Z", at = @At("HEAD"), cancellable = true)
    private static void sparkwitch$enablePromotedSaboteurInstinct(CallbackInfoReturnable<Boolean> cir) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return;
        }
        // The viewer's role comes from the per-pass memo; every other role leaves before the Wraith and key reads.
        // 观察者职业来自按 pass 的缓存；其他职业在冤魂状态与按键读取之前即返回。
        Role role = LocalViewerSnapshot.role(player);
        Identifier roleId = role == null ? null : role.identifier();
        if (!SaboteurInstinctClientRules.isExtendedRole(roleId)) {
            return;
        }
        if (SaboteurInstinctClientRules.shouldEnableNativeInstinct(
                false,
                roleId,
                SparkWitchServerConnection.isConfirmedServer(),
                WraithClientState.isActive(player),
                WraithClientState.isPromoted(player),
                WatheClient.instinctKeybind.isPressed()
        )) {
            cir.setReturnValue(true);
        }
    }
}
