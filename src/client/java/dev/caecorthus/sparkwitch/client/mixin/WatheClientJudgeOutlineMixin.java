package dev.caecorthus.sparkwitch.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.caecorthus.sparkwitch.client.judge.JudgeClientHooks;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** After Raven (400) and factor (300), fill absence only; existing colors always win. / 在鸦400与因子300之后仅补无高亮，已有颜色优先。 */
@Mixin(value = WatheClient.class, remap = false, priority = 200)
public abstract class WatheClientJudgeOutlineMixin {
    @ModifyReturnValue(method = "getInstinctHighlight", at = @At("RETURN"))
    private static int sparkwitch$judgeFallback(int originalColor, Entity target) {
        return JudgeClientHooks.resolveHighlight(originalColor, target);
    }
}
