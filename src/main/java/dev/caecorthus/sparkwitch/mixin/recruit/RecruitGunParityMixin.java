package dev.caecorthus.sparkwitch.mixin.recruit;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.civilian.recruit.RecruitRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.util.GunShootPayload;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Native Vigilante gun parity for the Recruit, a civilian (not a police role): 10 s revolver base cooldown instead of
 * 15 s and no -0.35 mood per gun hit, matching what Wathe grants the Vigilante. All four {@code isRole} reads in the
 * receiver ask about the shooter, so a Recruit target is never treated as a Vigilante. Innocent-shot punishment is
 * unchanged. An additive OR-wrap (never a {@code @Redirect}), so it composes with the Judge, Emma, Control Expert,
 * Seeker, Black Raven and Knight wrappers.
 * 新兵（平民，非警察职业）的原生义警枪械对等：左轮基础冷却 10 秒而非 15 秒、枪击不扣 0.35 理智，与 Wathe 赋予义警的效果
 * 一致。接收器中的四处 {@code isRole} 都只询问射手，因此被射中的新兵不会被当成义警。误杀惩罚不变。叠加式 OR 包装
 * （绝不使用 {@code @Redirect}），与法官、艾玛、控场专家、搜寻者、黑羽鸦、骑士的包装可任意组合。
 */
@Mixin(value = GunShootPayload.Receiver.class, remap = false)
public abstract class RecruitGunParityMixin {
    @WrapOperation(method = "receive(Ldev/doctor4t/wathe/util/GunShootPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At(value = "INVOKE", target = "Ldev/doctor4t/wathe/cca/GameWorldComponent;isRole(Lnet/minecraft/entity/player/PlayerEntity;Ldev/doctor4t/wathe/api/Role;)Z"))
    private boolean sparkwitch$recruitVigilanteParity(GameWorldComponent game, PlayerEntity player,
                                                      Role queriedRole, Operation<Boolean> original) {
        return original.call(game, player, queriedRole)
                || (queriedRole == WatheRoles.VIGILANTE && RecruitRules.isRecruit(game.getRole(player)));
    }
}
