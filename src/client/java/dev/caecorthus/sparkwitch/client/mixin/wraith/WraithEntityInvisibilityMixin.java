package dev.caecorthus.sparkwitch.client.mixin.wraith;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.caecorthus.sparkwitch.client.wraith.WraithViewerRules;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Applies the viewer-local Wraith visibility policy after vanilla invisibility. / 在原版隐身判定后应用观察者本地的冤魂可见性策略。 */
@Mixin(value = Entity.class, priority = 100)
public abstract class WraithEntityInvisibilityMixin {
    @ModifyReturnValue(method = "isInvisibleTo", at = @At("RETURN"))
    private boolean sparkwitch$resolveWraithInvisibility(boolean original, PlayerEntity viewer) {
        Entity self = (Entity) (Object) this;
        if (self instanceof PlayerEntity target) {
            if (WraithViewerRules.shouldHideFromViewer(viewer, target)) {
                return true;
            }
            if (WraithViewerRules.shouldRevealWraithTarget(viewer, target)) {
                return false;
            }
        }
        return original;
    }
}
