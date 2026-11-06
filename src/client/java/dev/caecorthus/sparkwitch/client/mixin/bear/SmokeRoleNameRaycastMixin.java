package dev.caecorthus.sparkwitch.client.mixin.bear;

import dev.caecorthus.sparkwitch.client.bear.smoke.occlusion.SmokeLabelOcclusion;
import dev.doctor4t.wathe.client.gui.RoleNameRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Predicate;

/**
 * Wathe's crosshair raycasts in {@code RoleNameRenderer#renderHud} (player label, corpse label, note; 2 blocks, 8 for
 * spectators) skip players and corpses hidden by BEAR smoke ({@code SmokeLabelOcclusion}); notes pass through
 * unchanged. {@code @ModifyArg} only narrows the predicate, so it composes with {@code WraithNameTagRaycastMixin} and
 * any wrapper built on the passed predicate. Pinned in {@code verifyClientMixinSelectors}.
 * Wathe 在 {@code RoleNameRenderer#renderHud} 中的准星射线（玩家标签、尸体标签、纸条；2 格，旁观者 8 格）跳过被 BEAR
 * 烟雾挡住的玩家与尸体（{@code SmokeLabelOcclusion}），纸条不变。{@code @ModifyArg} 只收窄判定，可与
 * {@code WraithNameTagRaycastMixin} 及基于该判定的包装器共存。已在 {@code verifyClientMixinSelectors} 中固定。
 */
@Mixin(RoleNameRenderer.class)
public abstract class SmokeRoleNameRaycastMixin {
    @ModifyArg(
            method = "renderHud",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/projectile/ProjectileUtil;getCollision(Lnet/minecraft/entity/Entity;Ljava/util/function/Predicate;D)Lnet/minecraft/util/hit/HitResult;"
            ),
            index = 1
    )
    private static Predicate<Entity> sparkwitch$skipSmokeHiddenLabelTargets(
            Entity viewer,
            Predicate<Entity> predicate,
            double range
    ) {
        return SmokeLabelOcclusion.filterLabelTarget(viewer, predicate, range);
    }
}
