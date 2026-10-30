package dev.caecorthus.sparkwitch.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.civilian.knight.KnightKillService;
import dev.caecorthus.sparkwitch.roles.civilian.knight.KnightRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.util.GunShootPayload;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Native Vigilante gun parity for the Knight: 10 s revolver base cooldown instead of 15 s and no -0.35 mood per gun
 * kill, matching what Wathe grants the Vigilante and the Veteran. Innocent-shot punishment is unchanged. An additive
 * OR-wrap (never a {@code @Redirect}), so it composes with the Judge, Emma, Control Expert and Seeker wrappers.
 * 骑士的原生义警枪械对等：左轮基础冷却 10 秒而非 15 秒、枪杀不扣 0.35 理智，与 Wathe 赋予义警、老兵的效果一致；
 * 误杀惩罚不变。叠加式 OR 包装（绝不使用 {@code @Redirect}），与法官、艾玛、控场专家、搜寻者的包装可任意组合。
 */
@Mixin(value = GunShootPayload.Receiver.class, remap = false)
public abstract class KnightPoliceGunMixin {
    @WrapOperation(method = "receive(Ldev/doctor4t/wathe/util/GunShootPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At(value = "INVOKE", target = "Ldev/doctor4t/wathe/cca/GameWorldComponent;isRole(Lnet/minecraft/entity/player/PlayerEntity;Ldev/doctor4t/wathe/api/Role;)Z"))
    private boolean sparkwitch$knightPoliceExemptions(GameWorldComponent game, PlayerEntity player,
                                                      Role queriedRole, Operation<Boolean> original) {
        return original.call(game, player, queriedRole)
                || (queriedRole == WatheRoles.VIGILANTE && KnightRules.isKnight(game.getRole(player)));
    }

    /**
     * Wathe's target kill (ordinal 1, the one that kills the aimed player). Runs after the whole kill handler,
     * including its derringer reload loop, so a wrong derringer kill can restore the spent state.
     * Wathe 的目标击杀（ordinal 1，即击杀瞄准的玩家）。在整个击杀处理（含德林加装填循环）之后运行，
     * 因此德林加误杀可以恢复已使用状态。
     */
    @WrapOperation(method = "receive(Ldev/doctor4t/wathe/util/GunShootPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At(value = "INVOKE", ordinal = 1,
                    target = "Ldev/doctor4t/wathe/game/GameFunctions;killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;)V"))
    private void sparkwitch$knightDerringerOutcome(ServerPlayerEntity victim, boolean spawnBody,
                                                   ServerPlayerEntity shooter, Identifier reason,
                                                   Operation<Void> original) {
        original.call(victim, spawnBody, shooter, reason);
        KnightKillService.afterDerringerKill(shooter, victim);
    }
}
