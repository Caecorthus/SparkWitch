package dev.caecorthus.sparkwitch.client.mixin.seeker;

import dev.caecorthus.sparkwitch.client.seeker.remote.SeekerRemoteViewClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Possession input lock on MinecraftClient. {@code handleInputEvents} is never cancelled (other mods' hooks keep
 * running); its HEAD only settles the body: clear the item in use without a release (a released Wathe knife would
 * stab) and stop mining. Attack, use, pick and block breaking are cancelled at HEAD; {@code doAttack} returns
 * {@code boolean}, so it answers false. Every handler starts with the isActive() gate.
 * MinecraftClient 上的附身输入锁。{@code handleInputEvents} 从不取消（其他模组的钩子照常运行）；其 HEAD 只安顿本体：
 * 清除正在使用的物品且不触发松手（松开 Wathe 的刀会刺击），并停止挖掘。攻击、使用、选取与方块破坏在 HEAD 取消；
 * {@code doAttack} 返回 {@code boolean}，因此返回 false。每个处理器都以 isActive() 门槛开头。
 */
@Mixin(MinecraftClient.class)
public abstract class SeekerRemoteMinecraftClientMixin {
    @Inject(method = "handleInputEvents", at = @At("HEAD"))
    private void sparkwitch$settleWhileViewing(CallbackInfo ci) {
        if (!SeekerRemoteViewClient.isActive()) {
            return;
        }
        SeekerRemoteViewClient.settleInput((MinecraftClient) (Object) this);
    }

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$noAttackWhileViewing(CallbackInfoReturnable<Boolean> cir) {
        if (!SeekerRemoteViewClient.isActive()) {
            return;
        }
        cir.setReturnValue(false);
    }

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$noItemUseWhileViewing(CallbackInfo ci) {
        if (!SeekerRemoteViewClient.isActive()) {
            return;
        }
        ci.cancel();
    }

    @Inject(method = "doItemPick", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$noItemPickWhileViewing(CallbackInfo ci) {
        if (!SeekerRemoteViewClient.isActive()) {
            return;
        }
        ci.cancel();
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$noBlockBreakingWhileViewing(boolean breaking, CallbackInfo ci) {
        if (!SeekerRemoteViewClient.isActive()) {
            return;
        }
        ci.cancel();
    }
}
