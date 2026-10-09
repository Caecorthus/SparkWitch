package dev.caecorthus.sparkwitch.client.mixin.usec;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.caecorthus.sparkwitch.client.usec.UsecFireInput;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * macOS Ctrl + left-click with the AXMC (client intent only; the server validates every shot). Vanilla 1.21.1
 * {@code onMouseButton} reads {@code MinecraftClient.IS_SYSTEM_MAC} exactly once, as the guard of its Ctrl-click
 * branch: a left press with Ctrl becomes button 1 and {@code controlLeftClicks++}; a left release while that count is
 * above zero becomes button 1 and {@code controlLeftClicks--}. Sprint defaults to Left Ctrl, so a Mac player sprinting
 * with the rifle would scope in and out instead of firing from the hip. This hook answers false for that one read only when
 * {@code UsecFireInput.skipsMacControlClickRemap} says so (a left press, no screen, the rifle in the main hand, no
 * remapped release pending): the press stays button 0 and is not counted, so its release stays button 0 too. Releases
 * are never touched, so a press remapped before the rifle was drawn still gets its remapped release. Off macOS the read
 * is false and nothing is evaluated. Every other item, screen and button keeps vanilla's remap.
 * macOS 上持 AXMC 的 Ctrl + 左键（仅为客户端意图；服务端复核每次开火）。原版 1.21.1 {@code onMouseButton} 只读取一次
 * {@code MinecraftClient.IS_SYSTEM_MAC}，作为 Ctrl 点击分支的条件：按住 Ctrl 的左键按下变为按键 1 并
 * {@code controlLeftClicks++}；计数大于零时的左键松开变为按键 1 并 {@code controlLeftClicks--}。疾跑默认是左 Ctrl，因此 Mac
 * 玩家疾跑中持步枪腰射会开镜/收镜而不是开火。仅当 {@code UsecFireInput.skipsMacControlClickRemap} 判定（左键按下、无界面、
 * 主手持步枪、没有待重映射的松开）时，本钩子让这一次读取返回 false：按下保持为按键 0 且不计数，因此它的松开也保持为按键 0。
 * 从不改动松开，因此持枪前已重映射的按下仍会得到重映射的松开。非 macOS 时读取值为 false，不做任何判断。其他物品、界面与
 * 按键保持原版重映射。
 */
@Mixin(Mouse.class)
public abstract class UsecMacControlClickMixin {
    @Shadow
    private int controlLeftClicks;

    @ModifyExpressionValue(method = "onMouseButton",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;IS_SYSTEM_MAC:Z",
                    opcode = Opcodes.GETSTATIC))
    private boolean sparkwitch$keepUsecLeftPressOnMac(boolean systemMac,
                                                      @Local(argsOnly = true, ordinal = 0) int button,
                                                      @Local(argsOnly = true, ordinal = 1) int action) {
        return systemMac && !UsecFireInput.skipsMacControlClickRemap(MinecraftClient.getInstance(), button, action,
                controlLeftClicks);
    }
}
