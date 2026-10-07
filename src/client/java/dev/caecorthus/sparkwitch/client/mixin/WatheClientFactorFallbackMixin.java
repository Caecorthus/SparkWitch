package dev.caecorthus.sparkwitch.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.caecorthus.sparkwitch.client.factor.WitchFactorClientHooks;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** After ordinary/Traits HEAD and Raven's priority-400 resolver, fill absence only. / 在普通本能、Traits HEAD 和优先级400鸦裁决后，仅填补无高亮。 */
@Mixin(value = WatheClient.class, remap = false, priority = 300)
public abstract class WatheClientFactorFallbackMixin {
    @ModifyReturnValue(method = "getInstinctHighlight", at = @At("RETURN"))
    private static int sparkwitch$factorFallback(int originalColor, Entity target) {
        return WitchFactorClientHooks.resolveHighlight(originalColor, target);
    }
}
