package dev.caecorthus.sparkwitch.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.client.render.WraithAimPassThrough;
import dev.doctor4t.wathe.item.RevolverItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

/**
 * Revolver shots pass through active Wraiths during client target selection.
 * 左轮选靶时子弹穿过激活的冤魂。
 *
 * <p>{@code @WrapMethod} encloses other mods' HEAD replacements (SparkTraits Marksman), so the scope
 * covers every selector raycast; {@link WraithAimRaycastMixin} applies the filter. The bed-hit fallback
 * is shared by the revolver, derringer, Demon Hunter pistol and knife stabs.
 * {@code @WrapMethod} 包住其他模组的 HEAD 替换（SparkTraits 精确枪手），作用域覆盖所有选靶射线。
 * 床铺命中回退由左轮、德林格、猎魔人手枪与刀刺共用。</p>
 */
@Mixin(value = RevolverItem.class, remap = false)
public abstract class WraithRevolverAimMixin {
    @WrapMethod(method = "getGunTarget")
    private static HitResult sparkwitch$aimThroughWraiths(PlayerEntity user, Operation<HitResult> original) {
        return WraithAimPassThrough.selectAs(user, () -> original.call(user));
    }

    @ModifyReturnValue(method = "findSleepingPlayerOnBed", at = @At("RETURN"))
    private static Optional<PlayerEntity> sparkwitch$skipSleepingWraith(
            Optional<PlayerEntity> sleeper,
            World world,
            BlockHitResult blockHitResult
    ) {
        return WraithAimPassThrough.filterSleepingTarget(world, sleeper);
    }
}
