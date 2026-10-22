package dev.caecorthus.sparkwitch.client.mixin.blackraven.crow;

import dev.caecorthus.sparkwitch.client.blackraven.crow.BlackRavenCrowPilotClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Last client layer of the crow's body lock: the body's attack and interact calls are cancelled at HEAD before any
 * packet is sent (FAIL stops vanilla's per-hand retry); the crow's own right-click goes through
 * {@code black_raven_crow_use} instead. {@code stopUsingItem} clears instead of releasing (a released Wathe knife
 * stabs). The server's own body guards stay authoritative. First statement: the {@code isActive()} gate.
 * 乌鸦期间本体锁的最后一道客户端防线：本体的攻击与交互调用在开头取消，任何数据包都不会发出（FAIL 阻止原版按手重试）；乌鸦自己的右键改走
 * black_raven_crow_use。stopUsingItem 改为清除而非松手（松开 Wathe 的刀会刺击）。服务端的本体守卫仍是权威。
 */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class BlackRavenCrowInteractionMixin {
    @Inject(method = "attackBlock", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$noBlockAttackAsCrow(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (!BlackRavenCrowPilotClient.isActive()) {
            return;
        }
        cir.setReturnValue(false);
    }

    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$noEntityAttackAsCrow(PlayerEntity player, Entity target, CallbackInfo ci) {
        if (!BlackRavenCrowPilotClient.isActive()) {
            return;
        }
        ci.cancel();
    }

    @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$noBlockUseAsCrow(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult,
                                            CallbackInfoReturnable<ActionResult> cir) {
        if (!BlackRavenCrowPilotClient.isActive()) {
            return;
        }
        cir.setReturnValue(ActionResult.FAIL);
    }

    @Inject(method = "interactItem", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$noItemUseAsCrow(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (!BlackRavenCrowPilotClient.isActive()) {
            return;
        }
        cir.setReturnValue(ActionResult.FAIL);
    }

    @Inject(method = "interactEntity", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$noEntityUseAsCrow(PlayerEntity player, Entity entity, Hand hand,
                                             CallbackInfoReturnable<ActionResult> cir) {
        if (!BlackRavenCrowPilotClient.isActive()) {
            return;
        }
        cir.setReturnValue(ActionResult.FAIL);
    }

    @Inject(method = "interactEntityAtLocation", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$noEntityUseAtAsCrow(PlayerEntity player, Entity entity, EntityHitResult hitResult,
                                               Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (!BlackRavenCrowPilotClient.isActive()) {
            return;
        }
        cir.setReturnValue(ActionResult.FAIL);
    }

    @Inject(method = "stopUsingItem", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$clearInsteadOfReleaseAsCrow(PlayerEntity player, CallbackInfo ci) {
        if (!BlackRavenCrowPilotClient.isActive()) {
            return;
        }
        player.clearActiveItem();
        ci.cancel();
    }
}
