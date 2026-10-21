package dev.caecorthus.sparkwitch.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenPoliceRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.util.GunShootPayload;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Native Vigilante gun parity for the Black Raven (police variant since 2026-10-21, plan v2 §3.1): 10 s instead of
 * 15 s cooldown and no -0.35 mood per shot, on all four {@code isRole} calls in Wathe's server gun receiver;
 * innocent-shot punishment is unchanged. It tests the RAW role ({@code game.getRole}), so a disguised Raven firing a
 * picked-up revolver keeps the treatment; the acting overlay (M1) wraps the callee and never interferes. An additive
 * OR-wrap (never a {@code @Redirect}), so it composes with the Judge, Emma, Control Expert and Seeker wrappers in any
 * order. Wathe-owned target, so the class is {@code remap = false}.
 * 黑羽鸦的原生义警枪械对等（自 2026-10-21 起为警察变体，计划 v2 §3.1）：在 Wathe 服务端枪械接收器的全部四处
 * {@code isRole} 调用上赋予冷却 10 秒（而非 15 秒）、射击不扣 0.35 理智；误杀惩罚不变。判定使用原始职业
 * （{@code game.getRole}），因此伪装中的黑羽鸦用捡来的左轮开枪也享受该待遇；扮演覆盖层（M1）包装的是被调用方，
 * 不会干扰。叠加式 OR 包装（绝不使用 {@code @Redirect}），与法官、艾玛、控场专家、搜寻者的包装以任意顺序组合。
 * 目标属于 Wathe，因此类级 {@code remap = false}。
 */
@Mixin(value = GunShootPayload.Receiver.class, remap = false)
public abstract class BlackRavenPoliceGunMixin {
    @WrapOperation(method = "receive(Ldev/doctor4t/wathe/util/GunShootPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At(value = "INVOKE", target = "Ldev/doctor4t/wathe/cca/GameWorldComponent;isRole(Lnet/minecraft/entity/player/PlayerEntity;Ldev/doctor4t/wathe/api/Role;)Z"))
    private boolean sparkwitch$blackRavenPoliceExemptions(GameWorldComponent game, PlayerEntity player,
                                                          Role queriedRole, Operation<Boolean> original) {
        return original.call(game, player, queriedRole)
                || BlackRavenPoliceRules.countsAsNativeVigilanteForGun(queriedRole, game.getRole(player));
    }
}
