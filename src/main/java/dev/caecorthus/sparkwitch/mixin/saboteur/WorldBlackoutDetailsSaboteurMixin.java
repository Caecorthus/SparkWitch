package dev.caecorthus.sparkwitch.mixin.saboteur;

import dev.caecorthus.sparkwitch.roles.killer.saboteur.SaboteurLightOutageService;
import dev.doctor4t.wathe.cca.WorldBlackoutComponent;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Coordinates only Wathe's per-lamp writes with Sabotage leases. / 仅协调 Wathe 逐灯写入与破坏技能租约。 */
@Mixin(WorldBlackoutComponent.BlackoutDetails.class)
public abstract class WorldBlackoutDetailsSaboteurMixin {
    @Shadow
    @Final
    private BlockPos pos;

    @Shadow
    @Final
    private boolean original;

    @Unique
    private boolean sparkwitch$initialized;

    @Unique
    private boolean sparkwitch$coordinatedEnd;

    @Unique
    private SaboteurLightOutageService.WatheEndAction sparkwitch$endAction =
            SaboteurLightOutageService.WatheEndAction.NATIVE;

    @Inject(method = "init", at = @At("HEAD"))
    private void sparkwitch$beginWatheLampSource(World world, CallbackInfo ci) {
        if (!sparkwitch$initialized) {
            sparkwitch$initialized = true;
            SaboteurLightOutageService.onWatheInit(world, pos, this, original);
        }
    }

    @Inject(method = "end", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$coordinateWatheLampEnd(World world, CallbackInfo ci) {
        // Wathe calls end() twice; suppress only a coordinated second write, never Wathe-only behavior.
        // Wathe 会调用两次 end()；仅拦截已协调的第二次写入，不改变纯 Wathe 行为。
        if (sparkwitch$coordinatedEnd) {
            ci.cancel();
            return;
        }
        sparkwitch$endAction = SaboteurLightOutageService.onWatheEnd(world, pos, this);
        sparkwitch$coordinatedEnd = sparkwitch$endAction.coordinatesLampRestoration();
        if (sparkwitch$endAction == SaboteurLightOutageService.WatheEndAction.KEEP_DARK) {
            ci.cancel();
        }
    }

    @Inject(method = "end", at = @At("RETURN"))
    private void sparkwitch$restorePreSabotageLampState(World world, CallbackInfo ci) {
        if (sparkwitch$endAction == SaboteurLightOutageService.WatheEndAction.RESTORE_AFTER_NATIVE) {
            SaboteurLightOutageService.afterWatheEnd(world, pos);
        }
    }

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)Z"
            )
    )
    private boolean sparkwitch$protectLocalOutageDuringWatheFlicker(
            World world,
            BlockPos pos,
            BlockState proposedState
    ) {
        return world.setBlockState(
                pos,
                SaboteurLightOutageService.protectWatheFlicker(world, pos, proposedState)
        );
    }
}
