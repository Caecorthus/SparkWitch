package dev.caecorthus.sparkwitch.client.mixin.blackraven;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.caecorthus.sparkwitch.client.blackraven.BlackRavenInstinctClientHooks;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Final low-priority player-outline resolver, after Wathe/Noelles/SparkTraits hooks. / 在 Wathe、Noelles 与 SparkTraits 后执行的低优先级最终外框裁决。 */
@Mixin(value = WatheClient.class, remap = false, priority = 400)
public abstract class BlackRavenInstinctResolverMixin {
    @ModifyReturnValue(method = "getInstinctHighlight", at = @At("RETURN"))
    private static int sparkwitch$resolveBlackRavenInstinct(int originalColor, Entity target) {
        return BlackRavenInstinctClientHooks.resolveHighlight(originalColor, target);
    }
}
