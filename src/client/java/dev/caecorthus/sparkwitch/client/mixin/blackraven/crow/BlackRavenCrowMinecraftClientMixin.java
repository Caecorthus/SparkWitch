package dev.caecorthus.sparkwitch.client.mixin.blackraven.crow;

import dev.caecorthus.sparkwitch.client.blackraven.crow.BlackRavenCrowPilotClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Raven Form input on MinecraftClient. {@code handleInputEvents} is never cancelled (other mods' hooks keep running);
 * its HEAD settles the body (clear the item in use without a release, stop mining) and turns queued drop/use presses
 * into the crow's feather drop and block use. Attack, use, pick and block breaking of the body are cancelled at HEAD;
 * {@code doAttack} answers false. First statement of each handler: the {@code isActive()} gate.
 * MinecraftClient 上的化鸦输入。handleInputEvents 从不取消（其他模组的钩子照常运行）；其开头安顿本体（清除使用中的物品且不松手、停止挖掘），
 * 并把积压的丢弃/使用按下转为乌鸦的丢羽与方块交互。本体的攻击、使用、选取与方块破坏在开头取消；doAttack 返回 false。
 */
@Mixin(MinecraftClient.class)
public abstract class BlackRavenCrowMinecraftClientMixin {
    @Inject(method = "handleInputEvents", at = @At("HEAD"))
    private void sparkwitch$captureCrowInput(CallbackInfo ci) {
        if (!BlackRavenCrowPilotClient.isActive()) {
            return;
        }
        BlackRavenCrowPilotClient.captureInput((MinecraftClient) (Object) this);
    }

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$noAttackAsCrow(CallbackInfoReturnable<Boolean> cir) {
        if (!BlackRavenCrowPilotClient.isActive()) {
            return;
        }
        cir.setReturnValue(false);
    }

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$noItemUseAsCrow(CallbackInfo ci) {
        if (!BlackRavenCrowPilotClient.isActive()) {
            return;
        }
        ci.cancel();
    }

    @Inject(method = "doItemPick", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$noItemPickAsCrow(CallbackInfo ci) {
        if (!BlackRavenCrowPilotClient.isActive()) {
            return;
        }
        ci.cancel();
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$noBlockBreakingAsCrow(boolean breaking, CallbackInfo ci) {
        if (!BlackRavenCrowPilotClient.isActive()) {
            return;
        }
        ci.cancel();
    }
}
