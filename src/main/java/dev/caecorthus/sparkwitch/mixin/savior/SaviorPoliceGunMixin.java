package dev.caecorthus.sparkwitch.mixin.savior;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.civilian.savior.SaviorRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.util.GunShootPayload;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Native Vigilante gun parity for the Savior (owner 2026-11-16: police roles keep 10 s, everyone else 15 s): 10 s
 * instead of 15 s cooldown and no -0.35 mood per gun kill, on all four {@code isRole} calls in Wathe's server gun
 * receiver. It matters once the innocent-kill punishment has moved the planted revolver into the Savior's hand
 * ({@code SaviorRules.swapInRevolver}); Wathe's own innocent-shot punishment for that revolver is unchanged. An additive
 * OR-wrap (never a {@code @Redirect}), so it composes with the other police wrappers in any order. Wathe-owned target,
 * so the class is {@code remap = false}; the only {@code @At} target is Wathe's own {@code isRole}.
 * 救世主的原生义警枪械对等（所有者 2026-11-16：警类职业保持 10 秒，其他人 15 秒）：在 Wathe 服务端枪械接收器的全部四处
 * {@code isRole} 调用上赋予冷却 10 秒（而非 15 秒）、枪杀不扣 0.35 理智。误杀惩罚把预置左轮换到救世主手上之后
 * （{@code SaviorRules.swapInRevolver}）才会用到；Wathe 自身对这把左轮的误杀惩罚不变。叠加式 OR 包装（绝不使用
 * {@code @Redirect}），与其他警察包装以任意顺序组合。目标属于 Wathe，因此类级 {@code remap = false}；唯一的
 * {@code @At} 目标是 Wathe 自身的 {@code isRole}。
 */
@Mixin(value = GunShootPayload.Receiver.class, remap = false)
public abstract class SaviorPoliceGunMixin {
    @WrapOperation(method = "receive(Ldev/doctor4t/wathe/util/GunShootPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At(value = "INVOKE", target = "Ldev/doctor4t/wathe/cca/GameWorldComponent;isRole(Lnet/minecraft/entity/player/PlayerEntity;Ldev/doctor4t/wathe/api/Role;)Z"))
    private boolean sparkwitch$saviorPoliceExemptions(GameWorldComponent game, PlayerEntity player,
                                                      Role queriedRole, Operation<Boolean> original) {
        return original.call(game, player, queriedRole)
                || SaviorRules.countsAsNativeVigilanteForGun(queriedRole, game.getRole(player));
    }
}
