package dev.caecorthus.sparkwitch.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.client.render.WraithAimPassThrough;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;

import java.util.function.Predicate;

/**
 * Applies the Wraith pass-through filter to the gun/knife selector raycast, only inside a scope opened by
 * the selector mixins for the same shooter; any other caller keeps its original predicate.
 * 仅在选靶 mixin 为同一射手开启的作用域内给选靶射线加冤魂穿透过滤；其他调用方保持原判定。
 */
@Mixin(ProjectileUtil.class)
public abstract class WraithAimRaycastMixin {
    @WrapMethod(method = "getCollision(Lnet/minecraft/entity/Entity;Ljava/util/function/Predicate;D)Lnet/minecraft/util/hit/HitResult;")
    private static HitResult sparkwitch$filterScopedAim(
            Entity entity,
            Predicate<Entity> predicate,
            double range,
            Operation<HitResult> original
    ) {
        return original.call(entity, WraithAimPassThrough.filterScopedSelection(entity, predicate), range);
    }
}
