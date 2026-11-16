package dev.caecorthus.sparkwitch.client.mixin.bear;

import dev.caecorthus.sparkwitch.client.bear.smoke.SmokeTargetOcclusion;
import dev.doctor4t.wathe.client.gui.RoleNameRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Predicate;

/**
 * Wathe's crosshair raycasts in {@code RoleNameRenderer#renderHud} (player label, corpse label, note; 2 blocks, 8 for
 * spectators) skip players and corpses hidden by BEAR smoke ({@code SmokeLabelOcclusion}), and a hidden Magician
 * puppet, which is labelled as its copied player ({@code SmokeTargetOcclusion#filterLabelTarget}); notes pass through
 * unchanged. {@code @ModifyArg} only narrows the predicate, so it composes with {@code WraithNameTagRaycastMixin} and
 * any wrapper built on the passed predicate. Priority 1100 applies it after the default-priority
 * {@code MagicianPlaybackRoleNameMixin}, whose widening would otherwise let a hidden puppet back in. Pinned in
 * {@code verifyClientMixinSelectors}.
 * Wathe 在 {@code RoleNameRenderer#renderHud} 中的准星射线（玩家标签、尸体标签、纸条；2 格，旁观者 8 格）跳过被 BEAR
 * 烟雾挡住的玩家与尸体（{@code SmokeLabelOcclusion}），以及被挡住的魔术师皮套（它以被复制玩家的名义显示标签，
 * {@code SmokeTargetOcclusion#filterLabelTarget}），纸条不变。{@code @ModifyArg} 只收窄判定，可与
 * {@code WraithNameTagRaycastMixin} 及基于该判定的包装器共存。优先级 1100 使其在默认优先级的
 * {@code MagicianPlaybackRoleNameMixin} 之后应用，否则后者的放宽会把被挡住的皮套重新放进来。已在
 * {@code verifyClientMixinSelectors} 中固定。
 */
@Mixin(value = RoleNameRenderer.class, priority = 1100)
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
        return SmokeTargetOcclusion.filterLabelTarget(viewer, predicate, range);
    }
}
