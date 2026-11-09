package dev.caecorthus.sparkwitch.mixin.bear;

import dev.caecorthus.sparkwitch.roles.civilian.bear.ricochet.BearRicochetRules;
import dev.caecorthus.sparkwitch.roles.civilian.bear.ricochet.RangedSmokeCarve;
import dev.doctor4t.wathe.entity.FirecrackerEntity;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BEAR S3: a Wathe firecracker's bang carves a {@link BearRicochetRules#FIRECRACKER_HOLE_RADIUS} hole into smoke, at the
 * {@code discard()} that ends its server burst branch (the only {@code discard} in {@code tick}). Visual only.
 * BEAR S3：Wathe 鞭炮炸响时在烟雾中炸开半径 {@link BearRicochetRules#FIRECRACKER_HOLE_RADIUS} 的洞，挂在结束其服务端爆响分支的
 * {@code discard()}（{@code tick} 中唯一的 {@code discard}）。纯视觉。
 */
@Mixin(FirecrackerEntity.class)
public abstract class FirecrackerSmokeCarveMixin {
    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Ldev/doctor4t/wathe/entity/FirecrackerEntity;discard()V"))
    private void sparkwitch$carveFirecrackerBang(CallbackInfo ci) {
        Entity firecracker = (Entity) (Object) this;
        RangedSmokeCarve.blast(firecracker.getWorld(), firecracker.getPos(), BearRicochetRules.FIRECRACKER_HOLE_RADIUS);
    }
}
