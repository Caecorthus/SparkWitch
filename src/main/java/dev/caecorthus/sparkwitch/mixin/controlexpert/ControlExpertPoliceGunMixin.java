package dev.caecorthus.sparkwitch.mixin.controlexpert;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.util.GunShootPayload;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Native Vigilante gun exemptions only (10 s instead of 15 s cooldown, no -0.35 mood per shot) on all four
 * {@code isRole} calls in the server gun receiver; innocent-shot punishment is unchanged. An additive OR-wrap, so it
 * composes with {@code EmmaPoliceGunMixin} in either order. Never a {@code @Redirect}.
 * 仅在服务端枪械接收器的全部四处 {@code isRole} 调用上赋予原生义警的枪械豁免（冷却 10 秒而非 15 秒、射击不扣 0.35 理智），
 * 误杀惩罚不变。这是叠加式 OR 包装，与 {@code EmmaPoliceGunMixin} 以任意顺序组合；绝不使用 {@code @Redirect}。
 */
@Mixin(value = GunShootPayload.Receiver.class, remap = false)
public abstract class ControlExpertPoliceGunMixin {
    @WrapOperation(method = "receive(Ldev/doctor4t/wathe/util/GunShootPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At(value = "INVOKE", target = "Ldev/doctor4t/wathe/cca/GameWorldComponent;isRole(Lnet/minecraft/entity/player/PlayerEntity;Ldev/doctor4t/wathe/api/Role;)Z"))
    private boolean sparkwitch$controlExpertPoliceExemptions(GameWorldComponent game, PlayerEntity player,
                                                             Role queriedRole, Operation<Boolean> original) {
        return original.call(game, player, queriedRole)
                || ControlExpertRules.countsAsNativeVigilanteForGun(queriedRole, game.getRole(player));
    }
}
