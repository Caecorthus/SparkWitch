package dev.caecorthus.sparkwitch.client.mixin.witchmaiden;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.caecorthus.sparkwitch.client.witchmaiden.WitherGoheiFireInput;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * macOS Ctrl + left-click with the Wither Gohei (client intent only; the server validates every shot). Vanilla's
 * Ctrl-click branch would turn a sprinting (Left Ctrl) left click into a right click; this answers false for its one
 * {@code IS_SYSTEM_MAC} read only when {@code WitherGoheiFireInput.keepsMacLeftPress} says so, chaining with the
 * launcher, AXMC and Death Ray answers for the same read. Releases are never touched.
 * macOS 上持凋灵御币的 Ctrl + 左键（仅为客户端意图；服务端复核每次射击）。原版 Ctrl 点击分支会把疾跑（左 Ctrl）时的左键变成
 * 右键；仅当 {@code WitherGoheiFireInput.keepsMacLeftPress} 判定时，本钩子让那一次 {@code IS_SYSTEM_MAC} 读取返回 false，
 * 并与同一读取上的炮筒、AXMC 与死亡射线应答串联。从不改动松开。
 */
@Mixin(Mouse.class)
public abstract class WitherGoheiMacControlClickMixin {
    @Shadow
    private int controlLeftClicks;

    @ModifyExpressionValue(method = "onMouseButton",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;IS_SYSTEM_MAC:Z",
                    opcode = Opcodes.GETSTATIC))
    private boolean sparkwitch$keepGoheiLeftPressOnMac(boolean systemMac,
                                                          @Local(argsOnly = true, ordinal = 0) int button,
                                                          @Local(argsOnly = true, ordinal = 1) int action) {
        return systemMac && !WitherGoheiFireInput.keepsMacLeftPress(MinecraftClient.getInstance(), button, action,
                controlLeftClicks);
    }
}
