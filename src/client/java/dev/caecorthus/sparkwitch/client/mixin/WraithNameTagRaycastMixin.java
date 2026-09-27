package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.client.render.WraithNameTagPassThrough;
import dev.doctor4t.wathe.client.gui.RoleNameRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Predicate;

/**
 * Wathe's player name-tag raycast (the first {@code getCollision} in {@code renderHud}) passes through Wraiths
 * hidden from the viewer, so their name no longer reveals them and the player behind is labelled instead.
 * The body and note raycasts keep their own predicates. {@code @ModifyArg} only narrows the predicate, so it
 * composes with wrappers that build on the passed predicate.
 * Wathe 的玩家名牌射线（renderHud 中第一个 getCollision）穿过对观察者隐藏的冤魂，名字不再暴露其位置，
 * 改为显示身后的玩家；尸体与纸条射线保持原判定。@ModifyArg 只收窄判定，可与基于该判定的包装器共存。
 */
@Mixin(RoleNameRenderer.class)
public abstract class WraithNameTagRaycastMixin {
    @ModifyArg(
            method = "renderHud",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/projectile/ProjectileUtil;getCollision(Lnet/minecraft/entity/Entity;Ljava/util/function/Predicate;D)Lnet/minecraft/util/hit/HitResult;",
                    ordinal = 0
            ),
            index = 1
    )
    private static Predicate<Entity> sparkwitch$nameTagThroughHiddenWraiths(
            Entity viewer,
            Predicate<Entity> predicate,
            double range
    ) {
        return WraithNameTagPassThrough.filterNameTarget(viewer, predicate);
    }
}
