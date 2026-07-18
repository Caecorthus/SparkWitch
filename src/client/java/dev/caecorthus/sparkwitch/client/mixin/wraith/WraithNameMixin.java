package dev.caecorthus.sparkwitch.client.mixin.wraith;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.client.wraith.WraithClientState;
import dev.caecorthus.sparkwitch.client.wraith.WraithSteveProjection;
import dev.caecorthus.sparkwitch.client.wraith.WraithViewerRules;
import dev.doctor4t.wathe.api.event.CanSeeBodyRole;
import dev.doctor4t.wathe.api.event.ShouldShowCohort;
import dev.doctor4t.wathe.client.gui.RoleNameRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Removes names, cohorts, and corpse identity from anonymous or hidden Wraith views. / 从匿名或隐藏的冤魂视角中移除姓名、同阵营提示和尸体身份。 */
@Mixin(value = RoleNameRenderer.class, priority = 2000)
public abstract class WraithNameMixin {
    @WrapOperation(
            method = "renderHud",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/PlayerEntity;getDisplayName()Lnet/minecraft/text/Text;"
            )
    )
    private static Text sparkwitch$hideProjectedPlayerName(
            PlayerEntity target,
            Operation<Text> original
    ) {
        PlayerEntity viewer = MinecraftClient.getInstance().player;
        if (WraithSteveProjection.shouldAnonymizePlayer(target)
                || WraithViewerRules.shouldHideFromViewer(viewer, target)) {
            return Text.literal("");
        }
        return original.call(target);
    }

    @WrapOperation(
            method = "renderHud",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/api/event/ShouldShowCohort;getCohortResult(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/entity/player/PlayerEntity;)Ldev/doctor4t/wathe/api/event/ShouldShowCohort$CohortResult;"
            )
    )
    private static ShouldShowCohort.CohortResult sparkwitch$hideProjectedCohort(
            ShouldShowCohort event,
            PlayerEntity viewer,
            PlayerEntity target,
            Operation<ShouldShowCohort.CohortResult> original
    ) {
        if (WraithClientState.isActive(viewer)
                || WraithViewerRules.shouldHideFromViewer(viewer, target)) {
            return ShouldShowCohort.CohortResult.hide(Integer.MAX_VALUE);
        }
        return original.call(event, viewer, target);
    }

    @WrapOperation(
            method = "renderHud",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/api/event/CanSeeBodyRole;canSee(Lnet/minecraft/entity/Entity;)Z"
            )
    )
    private static boolean sparkwitch$hideProjectedBodyIdentity(
            CanSeeBodyRole event,
            Entity viewer,
            Operation<Boolean> original
    ) {
        if (viewer instanceof PlayerEntity player && WraithClientState.isActive(player)) {
            return false;
        }
        return original.call(event, viewer);
    }
}
