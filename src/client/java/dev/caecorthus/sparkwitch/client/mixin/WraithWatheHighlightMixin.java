package dev.caecorthus.sparkwitch.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.caecorthus.sparkwitch.client.guardianangel.GuardianAngelClientHooks;
import dev.caecorthus.sparkwitch.client.render.WraithViewerRules;
import dev.caecorthus.sparkwitch.client.vendetta.VendettaClientPresentation;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.special.wraith.conversion.WraithBodyRoleResolver;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

/** Resolves Wraith instinct privacy before role highlights. / 在职业高亮前裁决冤魂的本能隐私。 */
@Mixin(value = WatheClient.class, remap = false, priority = 2000)
public abstract class WraithWatheHighlightMixin {
    @ModifyExpressionValue(
            method = "getInstinctHighlight",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/cca/GameWorldComponent;getRole(Ljava/util/UUID;)Ldev/doctor4t/wathe/api/Role;"
            )
    )
    private static Role sparkwitch$useCapturedBodyRole(Role currentRole, Entity target) {
        return target instanceof PlayerBodyEntity body
                ? WraithBodyRoleResolver.resolve(body, currentRole)
                : currentRole;
    }

    @Inject(method = "getInstinctHighlight", at = @At("HEAD"), cancellable = true)
    private static void sparkwitch$resolveWraithHighlight(
            Entity target,
            CallbackInfoReturnable<Integer> cir
    ) {
        if (!SparkWitchServerConnection.isConfirmedServer()
                || !(target instanceof PlayerEntity playerTarget)) {
            return;
        }
        PlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null) {
            return;
        }

        Integer vendettaHighlight = VendettaClientPresentation.highlight(viewer, playerTarget);
        if (vendettaHighlight != null) {
            // The exact bound pair wins over every shield, instinct, and Wraith privacy color.
            // 精确绑定双方的描边高于所有护盾、本能与冤魂隐私颜色。
            cir.setReturnValue(vendettaHighlight);
            return;
        }

        Integer guardianHighlight = GuardianAngelClientHooks.shieldTargetHighlight(viewer, playerTarget);
        if (guardianHighlight != null) {
            // Guardian Shield remains owner-private and wins over non-Vendetta downstream suppression.
            // 守护护盾仍仅本人可见，并高于仇杀客之外的下游高亮抑制。
            cir.setReturnValue(guardianHighlight);
            return;
        }

        if (WraithViewerRules.shouldRevealToSpectator(viewer, playerTarget)) {
            GameWorldComponent game = GameWorldComponent.KEY.get(viewer.getWorld());
            int highlight = WatheClient.isInstinctEnabled()
                    ? Objects.requireNonNullElse(game.getRole(playerTarget), WatheRoles.CIVILIAN).color()
                    : -1;
            cir.setReturnValue(highlight);
            return;
        }
        if (WraithViewerRules.shouldHideFromOrdinaryViewer(viewer, playerTarget)) {
            cir.setReturnValue(-1);
        }
    }
}
