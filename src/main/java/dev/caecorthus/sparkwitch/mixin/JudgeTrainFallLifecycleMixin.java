package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeTrainFallAttribution;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clear recovered fall episodes before Wathe retries train deaths. / 重试坠车死亡前清除已恢复的因果。 */
@Mixin(GameWorldComponent.class)
public abstract class JudgeTrainFallLifecycleMixin {
    @Shadow @Final private World world;

    @Inject(method = "serverTick", at = @At("HEAD"))
    private void sparkwitch$pruneJudgeFalls(CallbackInfo ci) {
        if (world instanceof ServerWorld serverWorld) JudgeTrainFallAttribution.prune(serverWorld);
    }
}
