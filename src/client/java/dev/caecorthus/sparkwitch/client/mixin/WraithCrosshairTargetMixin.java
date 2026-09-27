package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.client.render.WraithAimPassThrough;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Predicate;

/**
 * The vanilla crosshair passes through active Wraiths for the local non-spectator player, so bat/fist attacks,
 * item use on players or bodies, and doors or task blocks behind a Wraith are no longer absorbed by it.
 * 本地非旁观玩家的原版准星穿过激活冤魂，球棒/拳击、对玩家或尸体用物品、以及冤魂身后的门和任务方块不再被其吞掉。
 */
@Mixin(GameRenderer.class)
public abstract class WraithCrosshairTargetMixin {
    @ModifyArg(
            method = "findCrosshairTarget",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/projectile/ProjectileUtil;raycast(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Box;Ljava/util/function/Predicate;D)Lnet/minecraft/util/hit/EntityHitResult;"
            ),
            index = 4
    )
    private Predicate<Entity> sparkwitch$crosshairThroughWraiths(
            Entity camera,
            Vec3d start,
            Vec3d end,
            Box box,
            Predicate<Entity> predicate,
            double maxDistanceSquared
    ) {
        return WraithAimPassThrough.filterCrosshair(camera, predicate);
    }
}
