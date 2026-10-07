package dev.caecorthus.sparkwitch.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.util.GunShootPayload;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Native Vigilante gun parity for the Judge: 10 s instead of 15 s cooldown and no -0.35 mood per shot, on all four
 * {@code isRole} calls in Wathe's server gun receiver; innocent-shot punishment is unchanged. An additive OR-wrap
 * (never a {@code @Redirect}), so it composes with the Emma, Control Expert and Seeker wrappers in any order.
 * 大法官的原生义警枪械对等：在 Wathe 服务端枪械接收器的全部四处 {@code isRole} 调用上赋予冷却 10 秒（而非 15 秒）、
 * 射击不扣 0.35 理智；误杀惩罚不变。叠加式 OR 包装（绝不使用 {@code @Redirect}），与艾玛、控场专家、搜寻者的包装以任意顺序组合。
 */
@Mixin(value = GunShootPayload.Receiver.class, remap = false)
public abstract class JudgePoliceGunMixin {
    @WrapOperation(method = "receive(Ldev/doctor4t/wathe/util/GunShootPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At(value = "INVOKE", target = "Ldev/doctor4t/wathe/cca/GameWorldComponent;isRole(Lnet/minecraft/entity/player/PlayerEntity;Ldev/doctor4t/wathe/api/Role;)Z"))
    private boolean sparkwitch$judgePoliceExemptions(GameWorldComponent game, PlayerEntity player,
                                                     Role queriedRole, Operation<Boolean> original) {
        return original.call(game, player, queriedRole)
                || JudgeRules.countsAsNativeVigilanteForGun(queriedRole, game.getRole(player));
    }
}
