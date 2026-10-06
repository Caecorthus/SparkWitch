package dev.caecorthus.sparkwitch.client.mixin.controlexpert;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.caecorthus.sparkwitch.client.controlexpert.ControlExpertStunClient;
import dev.caecorthus.sparkwitch.client.grandwitch.RecruitmentHoldClient;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStunRules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The hotbar scroll bypasses KeyBinding, so it is blocked here, but only in game: with a screen open (pause menu,
 * options) scrolling still reaches the screen. Buttons and cursor movement are never touched, so screens stay
 * clickable. Mouse look is wrapped, not cancelled, and is locked only when the owner enables LOCK_CAMERA or while the
 * Grand Witch recruitment hold runs (the hold reuses this stun lock through {@code ControlExpertStun.isStunned}).
 * 快捷栏滚轮绕过 KeyBinding，因此在此阻止，但仅限游戏内：打开界面（暂停菜单、设置）时滚轮仍会传给界面。
 * 从不改动鼠标按键与光标移动，界面始终可以点击。鼠标视角采用包装而非取消，仅当所有者启用 LOCK_CAMERA 时或大魔女
 * 招募定身期间才锁定（定身通过 {@code ControlExpertStun.isStunned} 复用此眩晕锁）。
 */
@Mixin(Mouse.class)
public abstract class ControlExpertStunMouseMixin {
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$blockStunnedHotbarScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (MinecraftClient.getInstance().currentScreen == null && ControlExpertStunClient.isLocalStunned()) {
            ci.cancel();
        }
    }

    @WrapWithCondition(
            method = "updateMouse",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V")
    )
    private boolean sparkwitch$lockStunnedCamera(ClientPlayerEntity player, double cursorDeltaX, double cursorDeltaY) {
        // The Grand Witch recruitment hold always freezes mouse look; LOCK_CAMERA keeps governing the stun alone.
        // 大魔女招募定身始终冻结鼠标视角；LOCK_CAMERA 仍只决定眩晕本身。
        if (RecruitmentHoldClient.isLocalHeld()) {
            return false;
        }
        return !(ControlExpertStunRules.LOCK_CAMERA && ControlExpertStunClient.isLocalStunned());
    }
}
