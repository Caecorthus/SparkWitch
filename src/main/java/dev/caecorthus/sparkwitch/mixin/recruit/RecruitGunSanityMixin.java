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
 * The Recruit's gun kills cost no sanity (role spec, PR #176), and nothing else: the Recruit is a civilian, not a
 * police role, so its revolver stays on Wathe's passenger cooldown of 15 s (owner 2026-11-16: police roles keep 10 s,
 * everyone else 15 s). Wathe's receiver asks {@code isRole} four times: Vigilante then Veteran for the -0.35 mood per
 * gun kill, and again Vigilante then Veteran for the cooldown. This wrap answers only the first one (ordinal 0, the
 * mood check's Vigilante read); the cooldown reads are left alone. That read asks about the shooter, so a Recruit
 * target is never treated as a Vigilante. Innocent-shot punishment is unchanged. An additive OR-wrap (never a
 * {@code @Redirect}), so it composes with the police wrappers on the same call.
 * 新兵用枪击杀不扣理智（职业设定，PR #176），仅此而已：新兵是平民而非警类职业，因此其左轮保持 Wathe 乘客规则的 15 秒冷却
 * （所有者 2026-11-16：警类职业保持 10 秒，其他人 15 秒）。Wathe 的接收器四次询问 {@code isRole}：先义警后老兵用于枪杀扣
 * 0.35 理智，再先义警后老兵用于冷却。本包装只回答第一处（ordinal 0，理智判定的义警读取）；冷却的读取保持不动。该读取只询问
 * 射手，因此被射中的新兵不会被当成义警。误杀惩罚不变。叠加式 OR 包装（绝不使用 {@code @Redirect}），与同一调用上的警察包装
 * 可任意组合。
 */
@Mixin(value = GunShootPayload.Receiver.class, remap = false)
public abstract class RecruitGunSanityMixin {
    @WrapOperation(method = "receive(Ldev/doctor4t/wathe/util/GunShootPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At(value = "INVOKE", ordinal = 0,
                    target = "Ldev/doctor4t/wathe/cca/GameWorldComponent;isRole(Lnet/minecraft/entity/player/PlayerEntity;Ldev/doctor4t/wathe/api/Role;)Z"))
    private boolean sparkwitch$recruitGunKillKeepsSanity(GameWorldComponent game, PlayerEntity player,
                                                         Role queriedRole, Operation<Boolean> original) {
        return original.call(game, player, queriedRole)
                || (queriedRole == WatheRoles.VIGILANTE && RecruitRules.isRecruit(game.getRole(player)));
    }
}
