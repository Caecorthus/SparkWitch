package dev.caecorthus.sparkwitch.mixin.wraith;

import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithTaskService;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs tasks for active Wraiths without running Wathe mood behavior.
 * 为激活冤魂运行任务，但不运行 Wathe 心情逻辑。
 */
@Mixin(value = PlayerMoodComponent.class, remap = false)
public abstract class WraithPlayerMoodComponentMixin {
    @Shadow
    @Final
    private PlayerEntity player;

    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$runWraithTasksWithoutMood(CallbackInfo ci) {
        if (WraithStateService.isActive(player)) {
            WraithTaskService.tick((PlayerMoodComponent) (Object) this, player);
            ci.cancel();
        }
    }
}
