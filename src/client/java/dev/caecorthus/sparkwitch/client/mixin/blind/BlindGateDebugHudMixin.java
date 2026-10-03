package dev.caecorthus.sparkwitch.client.mixin.blind;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.client.blind.gate.BlindClientGates;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;

/**
 * F3 gate: while the Blind view is active the debug screen (and its charts, which read the same flag) never shows, so
 * its entity counts, targeted-entity lines and debug crosshair cannot reveal hidden players. Wathe already hides it
 * for a living player in a round with its HUD on; this outermost wrap also covers a swallowed Blind and Wathe's HUD
 * being off. Presentation only; the F3 toggle state itself is untouched.
 * F3 闸门：盲人视图生效期间，调试界面（以及读取同一标记的图表）一律不显示，其实体计数、指向实体信息与调试准星都无法
 * 暴露被隐藏的玩家。Wathe 已在对局中对开启 HUD 的存活玩家隐藏它；本最外层包裹还覆盖被吞下的盲人与 Wathe HUD
 * 关闭的情况。仅为展示，不改动 F3 开关本身。
 */
@Mixin(value = DebugHud.class, priority = 2000)
public abstract class BlindGateDebugHudMixin {
    @WrapMethod(method = "shouldShowDebugHud")
    private boolean sparkwitch$blindHidesDebugHud(Operation<Boolean> original) {
        if (BlindClientGates.viewActive()) {
            return false;
        }
        return original.call();
    }
}
