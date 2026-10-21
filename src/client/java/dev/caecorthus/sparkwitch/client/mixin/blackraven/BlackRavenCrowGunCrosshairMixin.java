package dev.caecorthus.sparkwitch.client.mixin.blackraven;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.client.blackraven.crow.BlackRavenCrowAim;
import dev.doctor4t.wathe.client.gui.CrosshairRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Keeps Wathe's revolver and derringer crosshair target pip consistent with the selectors: its private
 * {@code getVisibleGunTarget(player, range)} also lights up for a Raven Form crow strictly nearer than its pick, with
 * the same geometry as {@link BlackRavenCrowAim}. Chains with the Magician puppet wrapper and SparkTraits Marksman's
 * HEAD range replacement. Presentation only.
 * 使 Wathe 左轮与德林加的准星目标提示与选靶一致：其私有的 {@code getVisibleGunTarget(player, range)} 对严格更近的化鸦乌鸦
 * 同样亮起，几何与 {@link BlackRavenCrowAim} 相同。与魔术师皮套包装及 SparkTraits 神射手的 HEAD 射程替换串联。仅用于展示。
 */
@Mixin(value = CrosshairRenderer.class, remap = false)
public abstract class BlackRavenCrowGunCrosshairMixin {
    @WrapMethod(method = "getVisibleGunTarget")
    private static HitResult sparkwitch$preferNearerCrow(PlayerEntity player, double range,
                                                         Operation<HitResult> original) {
        return BlackRavenCrowAim.preferNearerCrow(player, original.call(player, range), range);
    }
}
