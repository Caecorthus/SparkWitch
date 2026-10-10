package dev.caecorthus.sparkwitch.client.mixin.potiongunner;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.caecorthus.sparkwitch.client.potiongunner.PotionFireInput;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * macOS Ctrl + left-click with the launcher (client intent only; the server validates every shot). Vanilla 1.21.1
 * {@code onMouseButton} reads {@code MinecraftClient.IS_SYSTEM_MAC} exactly once, as the guard of its Ctrl-click
 * branch, so a sprinting (Left Ctrl) left click would open the scope instead of firing. This hook answers false for
 * that one read only when {@code PotionFireInput.keepsMacLeftPress} says so (a left press, no screen, the launcher in
 * the main hand, no remapped release pending): the press stays button 0 and is not counted, so its release stays
 * button 0 too. Releases are never touched. It chains with every other answer for the same read (the AXMC, the Death
 * Ray); off macOS the read is false and nothing is evaluated. Not a scope seam: the look and wheel stay on
 * {@code client/scope}.
 * macOS 上持炮筒的 Ctrl + 左键（仅为客户端意图；服务端复核每次发射）。原版 1.21.1 {@code onMouseButton} 只读取一次
 * {@code MinecraftClient.IS_SYSTEM_MAC}，作为 Ctrl 点击分支的条件，因此疾跑（左 Ctrl）时的左键会开镜而不是发射。仅当
 * {@code PotionFireInput.keepsMacLeftPress} 判定（左键按下、无界面、主手持炮筒、没有待重映射的松开）时，本钩子让这一次读取
 * 返回 false：按下保持为按键 0 且不计数，因此它的松开也保持为按键 0。从不改动松开。它与同一读取上的其他应答（AXMC、死亡
 * 射线）串联；非 macOS 时读取值为 false，不做任何判断。这不是瞄准镜接缝：视角与滚轮仍由 {@code client/scope} 负责。
 */
@Mixin(Mouse.class)
public abstract class PotionLauncherMacControlClickMixin {
    @Shadow
    private int controlLeftClicks;

    @ModifyExpressionValue(method = "onMouseButton",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;IS_SYSTEM_MAC:Z",
                    opcode = Opcodes.GETSTATIC))
    private boolean sparkwitch$keepLauncherLeftPressOnMac(boolean systemMac,
                                                          @Local(argsOnly = true, ordinal = 0) int button,
                                                          @Local(argsOnly = true, ordinal = 1) int action) {
        return systemMac && !PotionFireInput.keepsMacLeftPress(MinecraftClient.getInstance(), button, action,
                controlLeftClicks);
    }
}
