package dev.caecorthus.sparkwitch.client.mixin.witchmaiden;

import dev.caecorthus.sparkwitch.client.witchmaiden.FocusedFootstepsInputController;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reasserts forced input after vanilla refresh and immediately before movement. / 在原版刷新输入后、实际移动前重新应用强制输入。 */
@Mixin(ClientPlayerEntity.class)
public abstract class FocusedFootstepsClientPlayerMovementMixin {
    @Inject(method = "tickNewAi", at = @At("TAIL"))
    private void sparkwitch$applyFocusedFootstepsConsumedMovement(CallbackInfo ci) {
        ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
        // Vanilla has copied Input into these travel fields; this is the final planar movement boundary.
        // 原版已将 Input 复制到移动字段；这里是平面移动的最终消费边界。
        FocusedFootstepsInputController.applyConsumedMovement(player);
    }

    @Inject(
            method = "tickMovement",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/input/Input;tick(ZF)V",
                    shift = At.Shift.AFTER
            )
    )
    private void sparkwitch$applyFocusedFootstepsPlanarInput(CallbackInfo ci) {
        ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
        // Reinforce after raw input refresh; tickNewAi later enforces the consumed movement fields.
        // 在原始输入刷新后再次覆盖；tickNewAi 随后会强制最终消费的移动字段。
        FocusedFootstepsInputController.applyPlanarInput((Input) player.input, player);
    }

    @Inject(
            method = "tickMovement",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;tickMovement()V",
                    shift = At.Shift.BEFORE
            )
    )
    private void sparkwitch$applyFocusedFootstepsSprintPhase(CallbackInfo ci) {
        ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
        // Vanilla may rewrite movement after Input.tick, so restore planar input at its final consumer boundary.
        // 原版可能在 Input.tick 后改写移动值，因此在最终消费边界重新应用平面输入。
        FocusedFootstepsInputController.applyPlanarInput((Input) player.input, player);
        FocusedFootstepsInputController.applySprintPhase(player);
    }
}
