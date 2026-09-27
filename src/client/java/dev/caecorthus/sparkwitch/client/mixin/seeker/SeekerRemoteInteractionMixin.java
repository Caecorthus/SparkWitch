package dev.caecorthus.sparkwitch.client.mixin.seeker;

import dev.caecorthus.sparkwitch.client.seeker.remote.SeekerRemoteViewClient;
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
 * Last client layer of the possession lock: attack and interact calls are cancelled at HEAD before any packet is
 * sent (FAIL stops vanilla's per-hand retry). The crosshair ray starts at the remote focus, but the server measures
 * reach from the body, so both sides must refuse; the server's Fabric callbacks stay authoritative.
 * {@code stopUsingItem} clears the item instead of releasing it, because a released Wathe knife sends its stab.
 * Every handler starts with the isActive() gate.
 * 附身锁的最后一道客户端防线：攻击与交互调用在 HEAD 取消，任何数据包都不会发出（FAIL 会阻止原版按手重试）。
 * 准星射线从遥控焦点发出，而服务端按本体计算触及距离，因此两端都必须拒绝；服务端的 Fabric 回调仍是权威。
 * {@code stopUsingItem} 改为清除物品而不是松手，因为松开 Wathe 的刀会发出刺击。每个处理器都以 isActive() 门槛开头。
 */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class SeekerRemoteInteractionMixin {
    @Inject(method = "attackBlock", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$noBlockAttackWhileViewing(BlockPos pos, Direction direction,
                                                      CallbackInfoReturnable<Boolean> cir) {
        if (!SeekerRemoteViewClient.isActive()) {
            return;
        }
        cir.setReturnValue(false);
    }

    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$noEntityAttackWhileViewing(PlayerEntity player, Entity target, CallbackInfo ci) {
        if (!SeekerRemoteViewClient.isActive()) {
            return;
        }
        ci.cancel();
    }

    @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$noBlockUseWhileViewing(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult,
                                                   CallbackInfoReturnable<ActionResult> cir) {
        if (!SeekerRemoteViewClient.isActive()) {
            return;
        }
        cir.setReturnValue(ActionResult.FAIL);
    }

    @Inject(method = "interactItem", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$noItemUseWhileViewing(PlayerEntity player, Hand hand,
                                                  CallbackInfoReturnable<ActionResult> cir) {
        if (!SeekerRemoteViewClient.isActive()) {
            return;
        }
        cir.setReturnValue(ActionResult.FAIL);
    }

    @Inject(method = "interactEntity", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$noEntityUseWhileViewing(PlayerEntity player, Entity entity, Hand hand,
                                                    CallbackInfoReturnable<ActionResult> cir) {
        if (!SeekerRemoteViewClient.isActive()) {
            return;
        }
        cir.setReturnValue(ActionResult.FAIL);
    }

    @Inject(method = "interactEntityAtLocation", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$noEntityUseAtWhileViewing(PlayerEntity player, Entity entity, EntityHitResult hitResult,
                                                      Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (!SeekerRemoteViewClient.isActive()) {
            return;
        }
        cir.setReturnValue(ActionResult.FAIL);
    }

    @Inject(method = "stopUsingItem", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$clearInsteadOfRelease(PlayerEntity player, CallbackInfo ci) {
        if (!SeekerRemoteViewClient.isActive()) {
            return;
        }
        player.clearActiveItem();
        ci.cancel();
    }
}
