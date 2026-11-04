package dev.caecorthus.sparkwitch.mixin.dominator;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.caecorthus.sparkwitch.roles.killer.dominator.DominatorForcedShotScope;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The forced shot is one plain shot: while it is inside Wathe's gun receiver the shooter reads as not sneaking, so no
 * crouch-gated gun behaviour starts from it. Today that is SparkTraits' Niko burst, which its receiver hook schedules
 * only for a sneaking police revolver ({@code VigilanteVeteranTraitService.scheduleNikoRevolverBurstRepeats}); its two
 * repeats would call {@code ShouldPunishGunShooter} outside the forced shot's wrapper (backfire, mood 0, an unmarked
 * revolver drop). SparkTraits has no public seam to veto the burst and AGENTS.md forbids mixins on its internals, so
 * the vanilla state it reads is answered instead. Read-only view, no tracked-data write; scoped to the synchronous
 * forced shot by server entity identity, so every other caller (client thread included) is untouched. If SparkTraits
 * ever stops gating on {@code isSneaking}, {@code SchedulerForcedShotConsequenceMixin} still keeps 小脑 away.
 * 强制开枪只是一发普通射击：其在 Wathe 枪械接收器内时，射手读作未潜行，因此不会由它触发任何以蹲下为条件的枪械行为。目前即
 * SparkTraits 的 Niko 连射，其接收器钩子只对潜行中的警察左轮排定连射；两发补射会在强制开枪包装之外调用
 * {@code ShouldPunishGunShooter}（走火、理智清零、掉出一把无标记左轮）。SparkTraits 没有可否决连射的公共接缝，且 AGENTS.md
 * 禁止对其内部做 mixin，因此改为应答它读取的原版状态。只读视图，不写同步数据；按服务端实体同一性限定在同步强制开枪内，其他
 * 调用方（含客户端线程）不受影响。若 SparkTraits 将来不再以 {@code isSneaking} 为条件，{@code SchedulerForcedShotConsequenceMixin}
 * 仍可防止“小脑”。
 */
@Mixin(Entity.class)
public abstract class EntityForcedShotSneakMixin {
    @ModifyReturnValue(method = "isSneaking", at = @At("RETURN"))
    private boolean sparkwitch$forcedShotIsNotCrouched(boolean sneaking) {
        return sneaking && !DominatorForcedShotScope.isFiring((Entity) (Object) this);
    }
}
