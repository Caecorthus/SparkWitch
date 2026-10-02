package dev.caecorthus.sparkwitch.mixin.riftwalker;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate.RiftGateEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Two vanilla 1.21.1 deflection side effects that must not apply to Rift Gates. A gate deflection
 * ({@code RiftGateProjectileService.DEFLECTION}) is a teleport, a reflection or a capped pass-through, never a change
 * of thrower; every other deflector keeps vanilla behaviour, and each hook wraps exactly one vanilla instruction.
 * <ol>
 *     <li>{@code deflect} re-sets the same owner, and {@code PersistentProjectileEntity.setOwner} turns a player-owned
 *     DISALLOWED projectile into ALLOWED, so a ninja shuriken that touched a gate could be picked up by anyone. The
 *     pickup permission is restored around that call. Server only in practice (clients skip {@code setOwner}).</li>
 *     <li>{@code hitOrDeflect} remembers the deflector as {@code lastDeflectedEntity} and skips {@code deflect} for it
 *     on every later contact, so a gate met twice in a row (facing gates) became transparent: no pass counted, no
 *     teleport, and the move behind it was never block-checked. A gate is never remembered (the previous value is
 *     kept); the pass ledger caps repeats, and a capped pass ends inside the gate volume, where vanilla's segment test
 *     cannot hit the gate again.</li>
 * </ol>
 * 原版 1.21.1 偏转的两个副作用不得作用于裂隙门。门的偏转（{@code RiftGateProjectileService.DEFLECTION}）是传送、反弹或达到
 * 上限后的穿过，从不更换投掷者；其他偏转者保持原版行为，每个钩子恰好包装一条原版指令。
 * 1）{@code deflect} 会重设同一主人，而 {@code PersistentProjectileEntity.setOwner} 会把玩家拥有、DISALLOWED 的投掷物改成
 * ALLOWED，导致碰过门的忍者手里剑可被任何人拾取。此处在该调用前后恢复拾取权限。实际只在服务端发生（客户端跳过 {@code setOwner}）。
 * 2）{@code hitOrDeflect} 会把偏转者记为 {@code lastDeflectedEntity}，之后每次接触都跳过对它的 {@code deflect}，因此连续两次
 * 碰到同一扇门（相对的门）时门变得透明：不计次数、不传送，门后的移动也不做方块检测。门从不被记住（保留原值）；穿门次数由
 * 计数器限制，达到上限的穿过结束在门体积内，原版线段检测无法再次命中该门。
 */
@Mixin(ProjectileEntity.class)
public abstract class RiftProjectileDeflectionMixin {
    @WrapOperation(method = "deflect", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/projectile/ProjectileEntity;setOwner(Lnet/minecraft/entity/Entity;)V"))
    private void sparkwitch$keepPickupThroughRiftGate(ProjectileEntity projectile, @Nullable Entity owner,
                                                      Operation<Void> original,
                                                      @Local(argsOnly = true, ordinal = 0) @Nullable Entity deflector) {
        if (!(deflector instanceof RiftGateEntity) || !(projectile instanceof PersistentProjectileEntity persistent)) {
            original.call(projectile, owner);
            return;
        }
        PersistentProjectileEntity.PickupPermission pickup = persistent.pickupType;
        original.call(projectile, owner);
        persistent.pickupType = pickup;
    }

    @WrapOperation(method = "hitOrDeflect", at = @At(value = "FIELD",
            target = "Lnet/minecraft/entity/projectile/ProjectileEntity;lastDeflectedEntity:Lnet/minecraft/entity/Entity;",
            opcode = Opcodes.PUTFIELD))
    private void sparkwitch$neverRememberRiftGate(ProjectileEntity projectile, @Nullable Entity deflector,
                                                  Operation<Void> original) {
        if (!(deflector instanceof RiftGateEntity)) {
            original.call(projectile, deflector);
        }
    }
}
