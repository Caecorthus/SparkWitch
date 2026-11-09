package dev.caecorthus.sparkwitch.mixin.bear;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.roles.civilian.bear.ricochet.BearRicochetRules;
import dev.caecorthus.sparkwitch.roles.civilian.bear.ricochet.RangedSmokeCarve;
import dev.caecorthus.sparkwitch.util.hitscan.RangedOriginScope;
import dev.doctor4t.wathe.entity.GrenadeEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Wathe grenade blast (BEAR D3 + S3): the whole {@code onCollision} runs inside a {@code RangedOriginScope} at the
 * grenade's position, so the Rys-T front test sees the blast centre rather than the thrower's eyes; on the server the
 * blast then carves a {@link BearRicochetRules#GRENADE_HOLE_RADIUS} hole into any smoke. The wrapper encloses every other
 * mixin on the method (Judge attribution, Raven Form, Magician playback) and changes nothing they see.
 * Wathe 手雷爆炸（BEAR D3 + S3）：整个 {@code onCollision} 在以手雷位置为来源的 {@code RangedOriginScope} 内执行，因此 Rys-T
 * 正面判定看到的是爆心而非投掷者眼睛；随后在服务端于烟雾中炸开半径 {@link BearRicochetRules#GRENADE_HOLE_RADIUS} 的洞。该包装
 * 包住此方法上的其他所有 mixin（法官归因、化鸦、魔术师回放），不改变它们看到的任何东西。
 */
@Mixin(GrenadeEntity.class)
public abstract class GrenadeEntityOriginSmokeCarveMixin {
    @WrapMethod(method = "onCollision")
    private void sparkwitch$scopeGrenadeBlast(HitResult hitResult, Operation<Void> original) {
        Entity grenade = (Entity) (Object) this;
        Vec3d centre = grenade.getPos();
        RangedOriginScope.run(centre, () -> original.call(hitResult));
        RangedSmokeCarve.blast(grenade.getWorld(), centre, BearRicochetRules.GRENADE_HOLE_RADIUS);
    }
}
