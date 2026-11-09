package dev.caecorthus.sparkwitch.mixin.bear;

import dev.caecorthus.sparkwitch.roles.civilian.bear.ricochet.BearRicochetRules;
import dev.caecorthus.sparkwitch.roles.civilian.bear.ricochet.RangedSmokeCarve;
import net.minecraft.entity.player.PlayerEntity;
import org.agmas.noellesroles.bomber.BomberPlayerComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BEAR S3: a NoellesRoles timed bomb going off carves a {@link BearRicochetRules#BOMB_HOLE_RADIUS} hole at its carrier
 * (+{@link BearRicochetRules#BOMB_CENTRE_LIFT} y, NoellesRoles' own particle centre). HEAD at priority 900: HEAD
 * callbacks run in ascending priority, so this runs before SparkTraits' default-1000 HEAD that cancels a transferred
 * Conscience bomb into its non-lethal blast, which still explodes visibly. No origin scope: the bomb is "at the victim",
 * so its origin stays unknown (D3). Visual only, never cancels. NoellesRoles member: class-level {@code remap = false}.
 * BEAR S3：NoellesRoles 定时炸弹爆炸时，在其持有者处（+{@link BearRicochetRules#BOMB_CENTRE_LIFT} y，即 NoellesRoles 自身的粒子
 * 中心）炸开半径 {@link BearRicochetRules#BOMB_HOLE_RADIUS} 的洞。优先级 900 的 HEAD：HEAD 回调按优先级升序执行，因此先于
 * SparkTraits 默认 1000 的 HEAD（它把转移来的善良炸弹取消为无害爆炸，但仍会可见地爆开）。不设来源作用域：炸弹“在受害者处”，
 * 来源保持未知（D3）。纯视觉，从不取消。NoellesRoles 成员：类级 {@code remap = false}。
 */
@Mixin(value = BomberPlayerComponent.class, remap = false, priority = 900)
public abstract class BomberExplosionSmokeCarveMixin {
    @Shadow
    @Final
    private PlayerEntity player;

    @Inject(method = "explode", at = @At("HEAD"))
    private void sparkwitch$carveBombBlast(CallbackInfo ci) {
        RangedSmokeCarve.blast(player.getWorld(), player.getPos().add(0.0, BearRicochetRules.BOMB_CENTRE_LIFT, 0.0),
                BearRicochetRules.BOMB_HOLE_RADIUS);
    }
}
